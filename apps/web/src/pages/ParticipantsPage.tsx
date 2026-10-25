import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Plus, Search, Upload, Download, MoreHorizontal, Trash2, CheckCircle, XCircle, Clock, CreditCard, Filter, Printer, History, Timer, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { ApiError, participantsApi, categoriesApi } from '@/lib/api'
import { toast } from 'sonner'
import type { ImportJob, Participant, ParticipantStatus, Penalty, RaceCategory } from '@/types'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { useTranslation } from 'react-i18next'

const statusColors: Record<ParticipantStatus, string> = {
  REGISTERED: 'bg-muted text-muted-foreground',
  CONFIRMED: 'bg-info/20 text-info',
  CHECKED_IN: 'bg-primary/20 text-primary',
  STARTED: 'bg-live/20 text-live',
  DNF: 'bg-warning/20 text-warning-foreground',
  DNS: 'bg-muted text-muted-foreground',
  DISQUALIFIED: 'bg-destructive/20 text-destructive',
  FINISHED: 'bg-success/20 text-success',
  PENDING_REVIEW: 'bg-warning/20 text-warning-foreground',
  WAITLISTED: 'bg-info/20 text-info',
  REJECTED: 'bg-destructive/20 text-destructive',
}

const paymentColors: Record<string, string> = {
  UNPAID: 'bg-destructive/20 text-destructive',
  PENDING: 'bg-warning/20 text-warning-foreground',
  PAID: 'bg-success/20 text-success',
  WAIVED: 'bg-muted text-muted-foreground',
  REFUNDED: 'bg-info/20 text-info',
}

export function ParticipantsPage() {
  const { raceId } = useParams()
  const { user } = useAuth()
  const { t } = useTranslation()
  const canManageAdminActions = isAdminRole(user?.role)
  const [participants, setParticipants] = useState<Participant[]>([])
  const [importJobs, setImportJobs] = useState<ImportJob[]>([])
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false)
  const [isJobsOpen, setIsJobsOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [newParticipant, setNewParticipant] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    dateOfBirth: '',
    gender: '',
    nationality: '',
  })
  const [categories, setCategories] = useState<RaceCategory[]>([])
  const [editOpen, setEditOpen] = useState(false)
  const [editSaving, setEditSaving] = useState(false)
  const [editForm, setEditForm] = useState({
    id: '',
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    bibNumber: '',
    categoryId: '',
    status: '' as ParticipantStatus | '',
  })
  const [penaltyOpen, setPenaltyOpen] = useState(false)
  const [penaltyParticipant, setPenaltyParticipant] = useState<Participant | null>(null)
  const [penalties, setPenalties] = useState<Penalty[]>([])
  const [penaltySeconds, setPenaltySeconds] = useState('')
  const [penaltyReason, setPenaltyReason] = useState('')
  const [penaltyBusy, setPenaltyBusy] = useState(false)
  const [refundParticipantId, setRefundParticipantId] = useState<string | null>(null)
  const [refundReason, setRefundReason] = useState('')
  const [refundReference, setRefundReference] = useState('')
  const [refundOutcome, setRefundOutcome] = useState<'REFUNDED' | 'NOT_REFUNDED'>('REFUNDED')
  const [refundBusy, setRefundBusy] = useState(false)

  const closeRefundDialog = () => {
    setRefundParticipantId(null)
    setRefundReason('')
    setRefundReference('')
    setRefundOutcome('REFUNDED')
  }

  const openRefundDialog = (participantId: string) => {
    closeRefundDialog()
    setRefundParticipantId(participantId)
  }

  const loadParticipants = async () => {
    if (!raceId) return
    setIsLoading(true)
    try {
      const data = await participantsApi.list(raceId)
      setParticipants(data)
    } catch {
      toast.error('Failed to load participants')
    } finally {
      setIsLoading(false)
    }
  }

  const loadImportJobs = async () => {
    if (!raceId) return
    try {
      const data = await participantsApi.listImportJobs(raceId)
      setImportJobs(data)
    } catch {
      console.error('Failed to load import jobs')
    }
  }

  useEffect(() => {
    void loadParticipants()
    void loadImportJobs()
    if (raceId) {
      categoriesApi.list(raceId).then(setCategories).catch(() => undefined)
    }
  }, [raceId])

  const filteredParticipants = useMemo(
    () =>
      participants.filter((p) => {
        const matchesSearch =
          p.firstName.toLowerCase().includes(search.toLowerCase()) ||
          p.lastName.toLowerCase().includes(search.toLowerCase()) ||
          p.email.toLowerCase().includes(search.toLowerCase()) ||
          p.bibNumber?.toLowerCase().includes(search.toLowerCase())
        const matchesStatus = statusFilter === 'all' || p.status === statusFilter
        return matchesSearch && matchesStatus
      }),
    [participants, search, statusFilter],
  )

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file || !raceId) return
    try {
      await participantsApi.importCsv(raceId, file)
      toast.success(`Import started for ${file.name}. Check status in "Recent Imports".`)
      void loadImportJobs()
    } catch {
      toast.error('Failed to start CSV import')
    }
  }

  const handleDownloadBib = async (participantId: string, name: string) => {
    if (!raceId) return
    try {
      const blob = await participantsApi.downloadBib(raceId, participantId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `bib-${name.replace(/\s+/g, '-')}.pdf`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error('Failed to download Bib')
    }
  }

  const handleExportCsv = async () => {
    if (!raceId) return
    try {
      const blob = await participantsApi.exportCsv(raceId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `participants-${raceId}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error('Failed to export participants')
    }
  }

  const handleCheckIn = async (participantId: string) => {
    if (!raceId) return
    try {
      await participantsApi.checkIn(raceId, participantId)
      toast.success('Participant checked in')
      await loadParticipants()
    } catch {
      toast.error('Failed to check in participant')
    }
  }

  const handleDelete = async (participantId: string) => {
    if (!raceId) return
    try {
      await participantsApi.delete(raceId, participantId)
      toast.success('Participant deleted')
      await loadParticipants()
    } catch {
      toast.error('Failed to delete participant')
    }
  }

  const handleReview = async (participantId: string, decision: 'APPROVE' | 'REJECT' | 'WAITLIST') => {
    if (!raceId) return
    let reason: string | undefined
    if (decision === 'REJECT') {
      reason = window.prompt('Reason for rejection (optional)') ?? undefined
    }
    try {
      await participantsApi.review(raceId, participantId, decision, reason)
      toast.success(`Registration ${decision.toLowerCase()}d`)
      await loadParticipants()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update registration')
    }
  }

  const handleVerifyPayment = async (participantId: string) => {
    if (!raceId) return
    try {
      await participantsApi.verifyPayment(raceId, participantId, { status: 'PAID' })
      toast.success('Payment marked as verified')
      await loadParticipants()
    } catch {
      toast.error('Failed to verify payment')
    }
  }

  const handleStartPayment = async (participantId: string) => {
    if (!raceId) return
    const promoCode = window.prompt('Promo code (optional)') ?? undefined
    try {
      const payment = await participantsApi.startCheckout(raceId, participantId, promoCode || undefined)
      if (payment.checkoutUrl) {
        window.open(payment.checkoutUrl, '_blank', 'noopener')
        toast.success('Payment checkout opened in a new tab')
      } else {
        toast.error('No checkout URL returned')
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to start payment')
    }
  }

  const handleStageRefund = async () => {
    if (!raceId || !refundParticipantId || !refundReason.trim()) return
    setRefundBusy(true)
    try {
      await participantsApi.stageExternalRefund(raceId, refundParticipantId, refundReason.trim())
      toast.success(t('common:refundAttemptStaged'))
    } catch (error) {
      toast.error(error instanceof ApiError && error.code === 'REFUND_ATTEMPT_ALREADY_PENDING'
        ? t('common:refundAttemptAlreadyPending') : t('common:refundStageFailed'))
    } finally {
      setRefundBusy(false)
    }
  }

  const handleReconcileRefund = async () => {
    if (!raceId || !refundParticipantId || !refundReason.trim() || !refundReference.trim()) return
    setRefundBusy(true)
    try {
      await participantsApi.reconcileExternalRefund(raceId, refundParticipantId, {
        providerReference: refundReference.trim(), reason: refundReason.trim(), outcome: refundOutcome,
      })
      toast.success(t('common:refundOutcomeRecorded'))
      closeRefundDialog()
      await loadParticipants()
    } catch {
      toast.error(t('common:refundReconcileFailed'))
    } finally {
      setRefundBusy(false)
    }
  }

  const openEdit = (participant: Participant) => {
    setEditForm({
      id: participant.id,
      firstName: participant.firstName,
      lastName: participant.lastName,
      email: participant.email,
      phone: participant.phone ?? '',
      bibNumber: participant.bibNumber ?? '',
      categoryId: participant.categoryId ?? '',
      status: participant.status,
    })
    setEditOpen(true)
  }

  const handleSaveEdit = async () => {
    if (!raceId) return
    if (!editForm.firstName.trim() || !editForm.lastName.trim() || !editForm.email.trim()) {
      toast.error('First name, last name, and email are required')
      return
    }
    setEditSaving(true)
    try {
      await participantsApi.update(raceId, editForm.id, {
        firstName: editForm.firstName.trim(),
        lastName: editForm.lastName.trim(),
        email: editForm.email.trim(),
        phone: editForm.phone.trim() || undefined,
        bibNumber: editForm.bibNumber.trim() || undefined,
        categoryId: editForm.categoryId || undefined,
        status: editForm.status || undefined,
      })
      toast.success('Participant updated')
      setEditOpen(false)
      await loadParticipants()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update participant')
    } finally {
      setEditSaving(false)
    }
  }

  const loadPenalties = async (participantId: string) => {
    if (!raceId) return
    try {
      setPenalties(await participantsApi.listPenalties(raceId, participantId))
    } catch {
      toast.error('Failed to load penalties')
    }
  }

  const openPenalties = async (participant: Participant) => {
    setPenaltyParticipant(participant)
    setPenalties([])
    setPenaltySeconds('')
    setPenaltyReason('')
    setPenaltyOpen(true)
    await loadPenalties(participant.id)
  }

  const handleAddPenalty = async () => {
    if (!raceId || !penaltyParticipant) return
    const seconds = Number(penaltySeconds)
    if (!Number.isFinite(seconds) || seconds === 0) {
      toast.error('Enter a non-zero number of seconds')
      return
    }
    setPenaltyBusy(true)
    try {
      await participantsApi.addPenalty(raceId, penaltyParticipant.id, {
        seconds,
        reason: penaltyReason.trim() || undefined,
      })
      toast.success('Penalty added')
      setPenaltySeconds('')
      setPenaltyReason('')
      await loadPenalties(penaltyParticipant.id)
    } catch {
      toast.error('Failed to add penalty')
    } finally {
      setPenaltyBusy(false)
    }
  }

  const handleRemovePenalty = async (penaltyId: string) => {
    if (!raceId || !penaltyParticipant) return
    try {
      await participantsApi.removePenalty(raceId, penaltyParticipant.id, penaltyId)
      toast.success('Penalty removed')
      await loadPenalties(penaltyParticipant.id)
    } catch {
      toast.error('Failed to remove penalty')
    }
  }

  const handleAddParticipant = async () => {
    if (!raceId) return
    try {
      await participantsApi.create(raceId, {
        firstName: newParticipant.firstName,
        lastName: newParticipant.lastName,
        email: newParticipant.email,
        phone: newParticipant.phone || undefined,
        dateOfBirth: newParticipant.dateOfBirth || undefined,
        gender: (newParticipant.gender || undefined) as Participant['gender'],
        nationality: newParticipant.nationality || undefined,
      })
      toast.success('Participant added')
      setIsAddDialogOpen(false)
      setNewParticipant({ firstName: '', lastName: '', email: '', phone: '', dateOfBirth: '', gender: '', nationality: '' })
      await loadParticipants()
    } catch {
      toast.error('Failed to add participant')
    }
  }

  const total = participants.length
  const checkedIn = participants.filter((p) => p.checkedInAt).length
  const onCourse = participants.filter((p) => p.status === 'STARTED').length
  const finished = participants.filter((p) => p.status === 'FINISHED').length

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Participants</h1>
          <p className="text-muted-foreground">Manage race participants and registrations</p>
        </div>
        <div className="flex items-center gap-2">
          <input ref={fileInputRef} type="file" accept=".csv" className="hidden" onChange={handleFileChange} />
          <Button variant="outline" onClick={() => setIsJobsOpen(true)}>
            <History className="mr-2 h-4 w-4" />
            Imports
          </Button>
          <Button variant="outline" onClick={() => fileInputRef.current?.click()}>
            <Upload className="mr-2 h-4 w-4" />
            Import CSV
          </Button>
          <Button variant="outline" onClick={() => void handleExportCsv()}>
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Button onClick={() => setIsAddDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            Add Participant
          </Button>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-4">
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Total Registered</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{total.toLocaleString()}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Checked In</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{checkedIn.toLocaleString()}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">On Course</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{onCourse.toLocaleString()}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Finished</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{finished.toLocaleString()}</div></CardContent></Card>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input placeholder="Search by name, email, or bib..." className="pl-10" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[180px]"><Filter className="mr-2 h-4 w-4" /><SelectValue placeholder="Filter by status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Statuses</SelectItem>
            <SelectItem value="REGISTERED">Registered</SelectItem>
            <SelectItem value="CONFIRMED">Confirmed</SelectItem>
            <SelectItem value="CHECKED_IN">Checked In</SelectItem>
            <SelectItem value="STARTED">Started</SelectItem>
            <SelectItem value="FINISHED">Finished</SelectItem>
            <SelectItem value="DNF">DNF</SelectItem>
            <SelectItem value="DNS">DNS</SelectItem>
            <SelectItem value="PENDING_REVIEW">Pending Review</SelectItem>
            <SelectItem value="WAITLISTED">Waitlisted</SelectItem>
            <SelectItem value="REJECTED">Rejected</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Bib</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Payment</TableHead>
                <TableHead>Checked In</TableHead>
                <TableHead className="w-[120px]">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredParticipants.map((p) => (
                <TableRow key={p.id}>
                  <TableCell className="font-medium">{p.firstName} {p.lastName}</TableCell>
                  <TableCell className="font-mono">{p.bibNumber || '-'}</TableCell>
                  <TableCell>{p.email}</TableCell>
                  <TableCell><Badge className={statusColors[p.status]}>{p.status.replace(/_/g, ' ')}</Badge></TableCell>
                  <TableCell>
                    {p.paymentStatus ? (
                      <Badge className={paymentColors[p.paymentStatus] ?? 'bg-muted text-muted-foreground'}>{p.paymentStatus}</Badge>
                    ) : '-'}
                  </TableCell>
                  <TableCell>{p.checkedInAt ? new Date(p.checkedInAt).toLocaleString() : '-'}</TableCell>
                  <TableCell>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon"><MoreHorizontal className="h-4 w-4" /></Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        {canManageAdminActions && p.status === 'PENDING_REVIEW' && (
                          <>
                            <DropdownMenuItem onClick={() => void handleReview(p.id, 'APPROVE')}>
                              <CheckCircle className="mr-2 h-4 w-4" />
                              Approve
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => void handleReview(p.id, 'WAITLIST')}>
                              <Clock className="mr-2 h-4 w-4" />
                              Waitlist
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => void handleReview(p.id, 'REJECT')}>
                              <XCircle className="mr-2 h-4 w-4" />
                              Reject
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                          </>
                        )}
                        {(p.paymentStatus === 'UNPAID' || p.paymentStatus === 'PENDING') && (
                          <>
                            <DropdownMenuItem onClick={() => void handleStartPayment(p.id)}>
                              <CreditCard className="mr-2 h-4 w-4" />
                              Start Online Payment
                            </DropdownMenuItem>
                            {canManageAdminActions && (
                              <DropdownMenuItem onClick={() => void handleVerifyPayment(p.id)}>
                                <CheckCircle className="mr-2 h-4 w-4" />
                                Verify Payment
                              </DropdownMenuItem>
                            )}
                            <DropdownMenuSeparator />
                          </>
                        )}
                        {user?.role === 'SUPER_ADMIN' && (p.paymentStatus === 'PAID' || p.paymentStatus === 'REFUNDED') && (
                          <>
                            <DropdownMenuItem onClick={() => openRefundDialog(p.id)}>
                              <RotateCcw className="mr-2 h-4 w-4" />
                              {t('common:manageExternalRefund')}
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                          </>
                        )}
                        <DropdownMenuItem onClick={() => void handleCheckIn(p.id)}>
                          <CheckCircle className="mr-2 h-4 w-4" />
                          Check In
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => void handleDownloadBib(p.id, `${p.firstName} ${p.lastName}`)}>
                          <Printer className="mr-2 h-4 w-4" />
                          Print Bib
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem onClick={() => openEdit(p)}>
                          <Timer className="mr-2 h-4 w-4" />
                          Edit Participant
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => void openPenalties(p)}>
                          <Timer className="mr-2 h-4 w-4" />
                          Manage Penalties
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem className="text-destructive" onClick={() => void handleDelete(p.id)}>
                          <Trash2 className="mr-2 h-4 w-4" />
                          Delete
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {!isLoading && filteredParticipants.length === 0 && <p className="p-4 text-sm text-muted-foreground">No participants found.</p>}
        </CardContent>
      </Card>

      <Dialog open={isJobsOpen} onOpenChange={setIsJobsOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader><DialogTitle>Recent Imports</DialogTitle></DialogHeader>
          <div className="max-h-[400px] overflow-y-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>File</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Progress</TableHead>
                  <TableHead>Result</TableHead>
                  <TableHead>Date</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {importJobs.map((job) => (
                  <TableRow key={job.id}>
                    <TableCell className="font-medium max-w-[150px] truncate">{job.fileName}</TableCell>
                    <TableCell>
                      <Badge variant={job.status === 'COMPLETED' ? 'default' : job.status === 'FAILED' ? 'destructive' : 'outline'}>
                        {job.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-xs">
                      {job.processedRows} / {job.totalRows || '?'}
                    </TableCell>
                    <TableCell className="text-xs">
                      <span className="text-success">{job.successCount}</span> / <span className="text-destructive">{job.errorCount}</span>
                    </TableCell>
                    <TableCell className="text-xs">
                      {new Date(job.createdAt).toLocaleString()}
                    </TableCell>
                  </TableRow>
                ))}
                {importJobs.length === 0 && (
                  <TableRow><TableCell colSpan={5} className="text-center text-muted-foreground py-4">No import history.</TableCell></TableRow>
                )}
              </TableBody>
            </Table>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => void loadImportJobs()}>Refresh</Button>
            <Button onClick={() => setIsJobsOpen(false)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Add Participant</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div className="grid grid-cols-2 gap-3">
              <div><Label>First Name</Label><Input value={newParticipant.firstName} onChange={(e) => setNewParticipant((v) => ({ ...v, firstName: e.target.value }))} /></div>
              <div><Label>Last Name</Label><Input value={newParticipant.lastName} onChange={(e) => setNewParticipant((v) => ({ ...v, lastName: e.target.value }))} /></div>
            </div>
            <div><Label>Email</Label><Input type="email" value={newParticipant.email} onChange={(e) => setNewParticipant((v) => ({ ...v, email: e.target.value }))} /></div>
            <div><Label>Phone</Label><Input value={newParticipant.phone} onChange={(e) => setNewParticipant((v) => ({ ...v, phone: e.target.value }))} /></div>
            <div className="grid grid-cols-2 gap-3">
              <div><Label>Date of Birth</Label><Input type="date" value={newParticipant.dateOfBirth} onChange={(e) => setNewParticipant((v) => ({ ...v, dateOfBirth: e.target.value }))} /></div>
              <div><Label>Gender</Label><Select value={newParticipant.gender} onValueChange={(value) => setNewParticipant((v) => ({ ...v, gender: value }))}><SelectTrigger><SelectValue placeholder="Select" /></SelectTrigger><SelectContent><SelectItem value="MALE">Male</SelectItem><SelectItem value="FEMALE">Female</SelectItem><SelectItem value="OTHER">Other</SelectItem><SelectItem value="PREFER_NOT_TO_SAY">Prefer not to say</SelectItem></SelectContent></Select></div>
            </div>
            <div><Label>Nationality</Label><Input value={newParticipant.nationality} onChange={(e) => setNewParticipant((v) => ({ ...v, nationality: e.target.value }))} /></div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsAddDialogOpen(false)}>Cancel</Button>
            <Button onClick={() => void handleAddParticipant()}>Add</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Edit Participant</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div className="grid grid-cols-2 gap-3">
              <div><Label>First Name</Label><Input value={editForm.firstName} onChange={(e) => setEditForm((v) => ({ ...v, firstName: e.target.value }))} /></div>
              <div><Label>Last Name</Label><Input value={editForm.lastName} onChange={(e) => setEditForm((v) => ({ ...v, lastName: e.target.value }))} /></div>
            </div>
            <div><Label>Email</Label><Input type="email" value={editForm.email} onChange={(e) => setEditForm((v) => ({ ...v, email: e.target.value }))} /></div>
            <div className="grid grid-cols-2 gap-3">
              <div><Label>Phone</Label><Input value={editForm.phone} onChange={(e) => setEditForm((v) => ({ ...v, phone: e.target.value }))} /></div>
              <div><Label>Bib Number</Label><Input value={editForm.bibNumber} onChange={(e) => setEditForm((v) => ({ ...v, bibNumber: e.target.value }))} /></div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>Category</Label>
                <Select value={editForm.categoryId} onValueChange={(value) => setEditForm((v) => ({ ...v, categoryId: value }))}>
                  <SelectTrigger><SelectValue placeholder="Select" /></SelectTrigger>
                  <SelectContent>
                    {categories.map((category) => (
                      <SelectItem key={category.id} value={category.id}>{category.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label>Status</Label>
                <Select value={editForm.status} onValueChange={(value) => setEditForm((v) => ({ ...v, status: value as ParticipantStatus }))}>
                  <SelectTrigger><SelectValue placeholder="Select" /></SelectTrigger>
                  <SelectContent>
                    {(['REGISTERED', 'CONFIRMED', 'CHECKED_IN', 'STARTED', 'DNF', 'DNS', 'DISQUALIFIED', 'FINISHED'] as ParticipantStatus[]).map((status) => (
                      <SelectItem key={status} value={status}>{status.replace('_', ' ')}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)} disabled={editSaving}>Cancel</Button>
            <Button onClick={() => void handleSaveEdit()} disabled={editSaving}>{editSaving ? 'Saving…' : 'Save'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={penaltyOpen} onOpenChange={setPenaltyOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Time Penalties — {penaltyParticipant?.firstName} {penaltyParticipant?.lastName}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              {penalties.length === 0 ? (
                <p className="text-sm text-muted-foreground">No penalties recorded.</p>
              ) : (
                penalties.map((penalty) => (
                  <div key={penalty.id} className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                    <span>
                      <span className="font-medium">{penalty.seconds}s</span>
                      {penalty.reason ? <span className="text-muted-foreground"> — {penalty.reason}</span> : null}
                    </span>
                    {canManageAdminActions && (
                      <Button variant="ghost" size="sm" className="text-destructive" onClick={() => void handleRemovePenalty(penalty.id)}>
                        Remove
                      </Button>
                    )}
                  </div>
                ))
              )}
            </div>
            {canManageAdminActions && (
              <div className="grid grid-cols-3 gap-3">
                <div><Label>Seconds</Label><Input type="number" value={penaltySeconds} onChange={(e) => setPenaltySeconds(e.target.value)} /></div>
                <div className="col-span-2"><Label>Reason</Label><Input value={penaltyReason} onChange={(e) => setPenaltyReason(e.target.value)} placeholder="Optional" /></div>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPenaltyOpen(false)}>Close</Button>
            {canManageAdminActions && (
              <Button onClick={() => void handleAddPenalty()} disabled={penaltyBusy}>{penaltyBusy ? 'Adding…' : 'Add penalty'}</Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={refundParticipantId !== null} onOpenChange={(open) => {
        if (!open && !refundBusy) closeRefundDialog()
      }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common:manageExternalRefund')}</DialogTitle>
            <DialogDescription>{t('common:externalRefundInstructions')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="refund-reason">{t('common:refundReason')}</Label>
              <Input id="refund-reason" value={refundReason} onChange={(event) => setRefundReason(event.target.value)} />
            </div>
            {participants.find((participant) => participant.id === refundParticipantId)?.paymentStatus !== 'REFUNDED' && (
              <Button variant="outline" disabled={refundBusy || !refundReason.trim()}
                onClick={() => void handleStageRefund()}>{t('common:stageExternalRefund')}</Button>
            )}
            <div className="space-y-2">
              <Label htmlFor="refund-reference">{t('common:refundProviderReference')}</Label>
              <Input id="refund-reference" value={refundReference}
                onChange={(event) => setRefundReference(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="refund-outcome">{t('common:refundOutcome')}</Label>
              <Select value={refundOutcome} onValueChange={(value) => setRefundOutcome(value as 'REFUNDED' | 'NOT_REFUNDED')}>
                <SelectTrigger id="refund-outcome"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="REFUNDED">{t('common:refundConfirmed')}</SelectItem>
                  <SelectItem value="NOT_REFUNDED">{t('common:refundNotCompleted')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={refundBusy} onClick={closeRefundDialog}>
              {t('common:actions.cancel')}
            </Button>
            <Button disabled={refundBusy || !refundReason.trim() || !refundReference.trim()}
              onClick={() => void handleReconcileRefund()}>{t('common:recordRefundOutcome')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
