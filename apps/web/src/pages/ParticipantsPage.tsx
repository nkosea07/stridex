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
import type { ExternalRefundAttempt, ImportJob, Participant, ParticipantStatus, Payment, Penalty, RaceCategory } from '@/types'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { useTranslation } from 'react-i18next'
import { formatCurrency } from '@/lib/format'

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

const IMPORT_POLL_INTERVAL_MS = 2000
const IMPORT_POLL_MAX_ATTEMPTS = 150

const OFFICIAL_STATUS_DECISIONS: ParticipantStatus[] = ['DISQUALIFIED', 'DNF', 'DNS', 'FINISHED']

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
  const previewFileRef = useRef<File | null>(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [preview, setPreview] = useState<import('@/types').ImportPreview | null>(null)
  const [importSubmitting, setImportSubmitting] = useState(false)
  const importPollRef = useRef<ReturnType<typeof setTimeout> | null>(null)
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
    originalStatus: '' as ParticipantStatus | '',
    statusReason: '',
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
  const [refundLoading, setRefundLoading] = useState(false)
  const [refundLoadError, setRefundLoadError] = useState(false)
  const [refundEligibleIds, setRefundEligibleIds] = useState<string[]>([])
  const [refundPayments, setRefundPayments] = useState<Payment[]>([])
  const [refundAttempts, setRefundAttempts] = useState<ExternalRefundAttempt[]>([])
  const [refundPaymentId, setRefundPaymentId] = useState('')
  const [refundAttemptId, setRefundAttemptId] = useState('')
  const refundAttempt = refundAttempts.find((attempt) => attempt.id === refundAttemptId)

  const closeRefundDialog = () => {
    setRefundParticipantId(null)
    setRefundReason('')
    setRefundReference('')
    setRefundOutcome('REFUNDED')
    setRefundPayments([])
    setRefundAttempts([])
    setRefundPaymentId('')
    setRefundAttemptId('')
    setRefundLoadError(false)
  }

  const openRefundDialog = (participantId: string) => {
    closeRefundDialog()
    setRefundParticipantId(participantId)
    if (!raceId) return
    setRefundLoading(true)
    setRefundLoadError(false)
    void Promise.all([
      participantsApi.eligibleRefundPayments(raceId, participantId),
      participantsApi.listRefundAttempts(raceId, participantId),
    ]).then(([payments, attempts]) => {
      setRefundPayments(payments)
      setRefundAttempts(attempts)
      if (payments.length === 1) setRefundPaymentId(payments[0].id)
      const pending = attempts.filter((attempt) => attempt.status === 'PENDING')
      if (pending.length === 1) setRefundAttemptId(pending[0].id)
    }).catch(() => {
      setRefundLoadError(true)
      toast.error(t('common:refundLoadFailed'))
    }).finally(() => setRefundLoading(false))
  }

  const loadParticipants = async () => {
    if (!raceId) return
    setIsLoading(true)
    try {
      const [data, eligibleIds] = await Promise.all([
        participantsApi.list(raceId),
        user?.role === 'SUPER_ADMIN' ? participantsApi.eligibleRefundParticipantIds(raceId) : Promise.resolve([]),
      ])
      setParticipants(data)
      setRefundEligibleIds(eligibleIds)
    } catch {
      toast.error(t('participants:ui.failedToLoadParticipants'))
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
    previewFileRef.current = file
    setPreviewLoading(true)
    try {
      setPreview(await participantsApi.previewImport(raceId, file))
      setPreviewOpen(true)
    } catch {
      toast.error(t('participants:ui.failedToPreviewImportFile'))
    } finally {
      setPreviewLoading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const watchImportJob = (jobId: string, attempt = 0) => {
    if (!raceId || attempt > IMPORT_POLL_MAX_ATTEMPTS) return
    importPollRef.current = setTimeout(async () => {
      const job = await participantsApi.getImportJob(raceId, jobId).catch(() => null)
      if (job && (job.status === 'COMPLETED' || job.status === 'FAILED')) {
        importPollRef.current = null
        void loadImportJobs()
        if (job.status === 'COMPLETED') {
          void loadParticipants()
          toast.success(t('common:importCompleted', { success: job.successCount ?? 0, skipped: job.errorCount ?? 0 }))
        } else {
          toast.error(t('common:importFailed'))
        }
        return
      }
      watchImportJob(jobId, attempt + 1)
    }, IMPORT_POLL_INTERVAL_MS)
  }

  useEffect(() => () => {
    if (importPollRef.current) clearTimeout(importPollRef.current)
  }, [])

  const confirmImport = async () => {
    if (!raceId || !previewFileRef.current || importSubmitting) return
    setImportSubmitting(true)
    try {
      const job = await participantsApi.importCsv(raceId, previewFileRef.current)
      toast.success(t('common:importStarted'))
      setPreviewOpen(false)
      setPreview(null)
      previewFileRef.current = null
      void loadImportJobs()
      if (job?.id) watchImportJob(job.id)
    } catch {
      toast.error(t('common:importStartFailed'))
    } finally {
      setImportSubmitting(false)
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
      toast.error(t('participants:ui.failedToDownloadBib'))
    }
  }

  const handleExportParticipants = async (format: 'csv' | 'xlsx') => {
    if (!raceId) return
    try {
      const blob = await participantsApi.exportParticipants(raceId, format)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `participants-${raceId}.${format}`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error(t('participants:ui.failedToExportParticipants'))
    }
  }

  const handleCheckIn = async (participantId: string) => {
    if (!raceId) return
    try {
      await participantsApi.checkIn(raceId, participantId)
      toast.success(t('participants:ui.participantCheckedIn'))
      await loadParticipants()
    } catch {
      toast.error(t('participants:ui.failedToCheckInParticipant'))
    }
  }

  const handleDelete = async (participantId: string) => {
    if (!raceId) return
    try {
      await participantsApi.delete(raceId, participantId)
      toast.success(t('participants:ui.participantDeleted'))
      await loadParticipants()
    } catch {
      toast.error(t('participants:ui.failedToDeleteParticipant'))
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
      toast.success(t('participants:ui.paymentMarkedAsVerified'))
      await loadParticipants()
    } catch {
      toast.error(t('participants:ui.failedToVerifyPayment'))
    }
  }

  const handleStartPayment = async (participantId: string) => {
    if (!raceId) return
    const promoCode = window.prompt('Promo code (optional)') ?? undefined
    try {
      const payment = await participantsApi.startCheckout(raceId, participantId, promoCode || undefined)
      if (payment.checkoutUrl) {
        window.open(payment.checkoutUrl, '_blank', 'noopener')
        toast.success(t('participants:ui.paymentCheckoutOpenedInANew'))
      } else {
        toast.error(t('participants:ui.noCheckoutUrlReturned'))
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to start payment')
    }
  }

  const handleStageRefund = async () => {
    if (!raceId || !refundParticipantId || !refundReason.trim()) return
    setRefundBusy(true)
    try {
      const attempt = await participantsApi.stageExternalRefund(raceId, refundParticipantId,
        refundReason.trim(), refundPaymentId || undefined)
      setRefundAttempts((existing) => [attempt, ...existing])
      setRefundAttemptId(attempt.id)
      toast.success(t('common:refundAttemptStaged'))
    } catch (error) {
      toast.error(error instanceof ApiError && error.code === 'REFUND_ATTEMPT_ALREADY_PENDING'
        ? t('common:refundAttemptAlreadyPending') : t('common:refundStageFailed'))
    } finally {
      setRefundBusy(false)
    }
  }

  const handleReconcileRefund = async () => {
    if (!raceId || !refundParticipantId || !refundAttempt || !refundReason.trim() || !refundReference.trim()) return
    setRefundBusy(true)
    try {
      await participantsApi.reconcileExternalRefund(raceId, refundParticipantId, refundAttempt.id, {
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
      originalStatus: participant.status,
      statusReason: '',
    })
    setEditOpen(true)
  }

  const handleSaveEdit = async () => {
    if (!raceId) return
    if (!editForm.firstName.trim() || !editForm.lastName.trim() || !editForm.email.trim()) {
      toast.error(t('participants:ui.firstNameLastNameAndEmail'))
      return
    }
    const officialDecision = editForm.status !== editForm.originalStatus
      && OFFICIAL_STATUS_DECISIONS.includes(editForm.status as ParticipantStatus)
    if (officialDecision && editForm.statusReason.trim().length < 3) {
      toast.error(t('participants:statusReasonRequired'))
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
        statusReason: officialDecision ? editForm.statusReason.trim() : undefined,
      })
      toast.success(t('participants:ui.participantUpdated'))
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
      toast.error(t('participants:ui.failedToLoadPenalties'))
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
      toast.error(t('participants:ui.enterANonZeroNumberOf'))
      return
    }
    if (penaltyReason.trim().length < 5) {
      toast.error(t('participants:penaltyReasonRequired'))
      return
    }
    setPenaltyBusy(true)
    try {
      await participantsApi.addPenalty(raceId, penaltyParticipant.id, {
        seconds,
        reason: penaltyReason.trim(),
      })
      toast.success(t('participants:ui.penaltyAdded'))
      setPenaltySeconds('')
      setPenaltyReason('')
      await loadPenalties(penaltyParticipant.id)
    } catch {
      toast.error(t('participants:ui.failedToAddPenalty'))
    } finally {
      setPenaltyBusy(false)
    }
  }

  const handleRemovePenalty = async (penaltyId: string) => {
    if (!raceId || !penaltyParticipant) return
    try {
      await participantsApi.removePenalty(raceId, penaltyParticipant.id, penaltyId)
      toast.success(t('participants:ui.penaltyRemoved'))
      await loadPenalties(penaltyParticipant.id)
    } catch {
      toast.error(t('participants:ui.failedToRemovePenalty'))
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
      toast.success(t('participants:ui.participantAdded'))
      setIsAddDialogOpen(false)
      setNewParticipant({ firstName: '', lastName: '', email: '', phone: '', dateOfBirth: '', gender: '', nationality: '' })
      await loadParticipants()
    } catch {
      toast.error(t('participants:ui.failedToAddParticipant'))
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
          <h1 className="text-2xl font-bold tracking-tight">{t('participants:ui.participants')}</h1>
          <p className="text-muted-foreground">{t('participants:ui.manageRaceParticipantsAndRegistrations')}</p>
        </div>
        <div className="flex items-center gap-2">
          <input ref={fileInputRef} type="file" accept=".csv" className="hidden" onChange={handleFileChange} />
          <Button variant="outline" onClick={() => setIsJobsOpen(true)}>
            <History className="mr-2 h-4 w-4" />
            {t('participants:ui.imports')}
          </Button>
          <Button variant="outline" onClick={() => fileInputRef.current?.click()}>
            <Upload className="mr-2 h-4 w-4" />
            {t('participants:ui.importCsv')}
          </Button>
          <Button variant="outline" onClick={() => void handleExportParticipants('csv')}>
            <Download className="mr-2 h-4 w-4" />
            {t('common:exportCsv')}
          </Button>
          <Button variant="outline" onClick={() => void handleExportParticipants('xlsx')}>
            <Download className="mr-2 h-4 w-4" />
            {t('common:exportXlsx')}
          </Button>
          <Button onClick={() => setIsAddDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            {t('participants:ui.addParticipant')}
          </Button>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-4">
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('participants:ui.totalRegistered')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{total.toLocaleString()}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('participants:ui.checkedIn')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{checkedIn.toLocaleString()}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('participants:ui.onCourse')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{onCourse.toLocaleString()}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('participants:ui.finished')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{finished.toLocaleString()}</div></CardContent></Card>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input placeholder={t('participants:ui.searchByNameEmailOrBib')} className="pl-10" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[180px]"><Filter className="mr-2 h-4 w-4" /><SelectValue placeholder={t('participants:ui.filterByStatus')} /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('participants:ui.allStatuses')}</SelectItem>
            <SelectItem value="REGISTERED">{t('participants:ui.registered')}</SelectItem>
            <SelectItem value="CONFIRMED">{t('participants:ui.confirmed')}</SelectItem>
            <SelectItem value="CHECKED_IN">{t('participants:ui.checkedIn')}</SelectItem>
            <SelectItem value="STARTED">{t('participants:ui.started')}</SelectItem>
            <SelectItem value="FINISHED">{t('participants:ui.finished')}</SelectItem>
            <SelectItem value="DNF">{t('participants:ui.dnf')}</SelectItem>
            <SelectItem value="DNS">{t('participants:ui.dns')}</SelectItem>
            <SelectItem value="PENDING_REVIEW">{t('participants:ui.pendingReview')}</SelectItem>
            <SelectItem value="WAITLISTED">{t('participants:ui.waitlisted')}</SelectItem>
            <SelectItem value="REJECTED">{t('participants:ui.rejected')}</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('participants:ui.name')}</TableHead>
                <TableHead>{t('participants:ui.bib')}</TableHead>
                <TableHead>{t('participants:ui.email')}</TableHead>
                <TableHead>{t('participants:ui.status')}</TableHead>
                <TableHead>{t('participants:ui.payment')}</TableHead>
                <TableHead>{t('participants:ui.checkedIn')}</TableHead>
                <TableHead className="w-[120px]">{t('participants:ui.actions')}</TableHead>
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
                              {t('participants:ui.approve')}
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => void handleReview(p.id, 'WAITLIST')}>
                              <Clock className="mr-2 h-4 w-4" />
                              {t('participants:ui.waitlist')}
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => void handleReview(p.id, 'REJECT')}>
                              <XCircle className="mr-2 h-4 w-4" />
                              {t('participants:ui.reject')}
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                          </>
                        )}
                        {(p.paymentStatus === 'UNPAID' || p.paymentStatus === 'PENDING') && (
                          <>
                            <DropdownMenuItem onClick={() => void handleStartPayment(p.id)}>
                              <CreditCard className="mr-2 h-4 w-4" />
                              {t('participants:ui.startOnlinePayment')}
                            </DropdownMenuItem>
                            {canManageAdminActions && (
                              <DropdownMenuItem onClick={() => void handleVerifyPayment(p.id)}>
                                <CheckCircle className="mr-2 h-4 w-4" />
                                {t('participants:ui.verifyPayment')}
                              </DropdownMenuItem>
                            )}
                            <DropdownMenuSeparator />
                          </>
                        )}
                        {user?.role === 'SUPER_ADMIN' && p.paymentStatus === 'PAID' && refundEligibleIds.includes(p.id) && (
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
                          {t('participants:ui.checkIn')}
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => void handleDownloadBib(p.id, `${p.firstName} ${p.lastName}`)}>
                          <Printer className="mr-2 h-4 w-4" />
                          {t('participants:ui.printBib')}
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem onClick={() => openEdit(p)}>
                          <Timer className="mr-2 h-4 w-4" />
                          {t('participants:ui.editParticipant')}
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => void openPenalties(p)}>
                          <Timer className="mr-2 h-4 w-4" />
                          {t('participants:ui.managePenalties')}
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem className="text-destructive" onClick={() => void handleDelete(p.id)}>
                          <Trash2 className="mr-2 h-4 w-4" />
                          {t('participants:ui.delete')}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {!isLoading && filteredParticipants.length === 0 && <p className="p-4 text-sm text-muted-foreground">{t('participants:ui.noParticipantsFound')}</p>}
        </CardContent>
      </Card>

      <Dialog open={isJobsOpen} onOpenChange={setIsJobsOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader><DialogTitle>{t('participants:ui.recentImports')}</DialogTitle></DialogHeader>
          <div className="max-h-[400px] overflow-y-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('participants:ui.file')}</TableHead>
                  <TableHead>{t('participants:ui.status')}</TableHead>
                  <TableHead>{t('participants:ui.progress')}</TableHead>
                  <TableHead>{t('participants:ui.result')}</TableHead>
                  <TableHead>{t('participants:ui.date')}</TableHead>
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
                      {job.rowErrors && job.rowErrors.length > 0 && (
                        <details className="mt-1">
                          <summary className="cursor-pointer text-muted-foreground">
                            {t('common:importRowErrorsSummary', { count: job.errorCount ?? job.rowErrors.length })}
                          </summary>
                          <ul className="mt-1 max-h-40 space-y-0.5 overflow-y-auto">
                            {job.rowErrors.map((rowError) => (
                              <li key={`${job.id}-${rowError.row}`}>
                                {t('common:importRowError', { row: rowError.row, reason: rowError.reason })}
                              </li>
                            ))}
                          </ul>
                          {(job.errorCount ?? 0) > job.rowErrors.length && (
                            <p className="mt-1 text-muted-foreground">
                              {t('common:importRowErrorsTruncated', { count: job.rowErrors.length })}
                            </p>
                          )}
                        </details>
                      )}
                    </TableCell>
                    <TableCell className="text-xs">
                      {new Date(job.createdAt).toLocaleString()}
                    </TableCell>
                  </TableRow>
                ))}
                {importJobs.length === 0 && (
                  <TableRow><TableCell colSpan={5} className="text-center text-muted-foreground py-4">{t('participants:ui.noImportHistory')}</TableCell></TableRow>
                )}
              </TableBody>
            </Table>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => void loadImportJobs()}>{t('participants:ui.refresh')}</Button>
            <Button onClick={() => setIsJobsOpen(false)}>{t('participants:ui.close')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{t('participants:ui.addParticipant')}</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div className="grid grid-cols-2 gap-3">
              <div><Label>{t('participants:ui.firstName')}</Label><Input value={newParticipant.firstName} onChange={(e) => setNewParticipant((v) => ({ ...v, firstName: e.target.value }))} /></div>
              <div><Label>{t('participants:ui.lastName')}</Label><Input value={newParticipant.lastName} onChange={(e) => setNewParticipant((v) => ({ ...v, lastName: e.target.value }))} /></div>
            </div>
            <div><Label>{t('participants:ui.email')}</Label><Input type="email" value={newParticipant.email} onChange={(e) => setNewParticipant((v) => ({ ...v, email: e.target.value }))} /></div>
            <div><Label>{t('participants:ui.phone')}</Label><Input value={newParticipant.phone} onChange={(e) => setNewParticipant((v) => ({ ...v, phone: e.target.value }))} /></div>
            <div className="grid grid-cols-2 gap-3">
              <div><Label>{t('participants:ui.dateOfBirth')}</Label><Input type="date" value={newParticipant.dateOfBirth} onChange={(e) => setNewParticipant((v) => ({ ...v, dateOfBirth: e.target.value }))} /></div>
              <div><Label>{t('participants:ui.gender')}</Label><Select value={newParticipant.gender} onValueChange={(value) => setNewParticipant((v) => ({ ...v, gender: value }))}><SelectTrigger><SelectValue placeholder={t('participants:ui.select')} /></SelectTrigger><SelectContent><SelectItem value="MALE">{t('participants:ui.male')}</SelectItem><SelectItem value="FEMALE">{t('participants:ui.female')}</SelectItem><SelectItem value="OTHER">{t('participants:ui.other')}</SelectItem><SelectItem value="PREFER_NOT_TO_SAY">{t('participants:ui.preferNotToSay')}</SelectItem></SelectContent></Select></div>
            </div>
            <div><Label>{t('participants:ui.nationality')}</Label><Input value={newParticipant.nationality} onChange={(e) => setNewParticipant((v) => ({ ...v, nationality: e.target.value }))} /></div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsAddDialogOpen(false)}>{t('participants:ui.cancel')}</Button>
            <Button onClick={() => void handleAddParticipant()}>{t('participants:ui.add')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{t('participants:ui.editParticipant')}</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div className="grid grid-cols-2 gap-3">
              <div><Label>{t('participants:ui.firstName')}</Label><Input value={editForm.firstName} onChange={(e) => setEditForm((v) => ({ ...v, firstName: e.target.value }))} /></div>
              <div><Label>{t('participants:ui.lastName')}</Label><Input value={editForm.lastName} onChange={(e) => setEditForm((v) => ({ ...v, lastName: e.target.value }))} /></div>
            </div>
            <div><Label>{t('participants:ui.email')}</Label><Input type="email" value={editForm.email} onChange={(e) => setEditForm((v) => ({ ...v, email: e.target.value }))} /></div>
            <div className="grid grid-cols-2 gap-3">
              <div><Label>{t('participants:ui.phone')}</Label><Input value={editForm.phone} onChange={(e) => setEditForm((v) => ({ ...v, phone: e.target.value }))} /></div>
              <div><Label>{t('participants:ui.bibNumber')}</Label><Input value={editForm.bibNumber} onChange={(e) => setEditForm((v) => ({ ...v, bibNumber: e.target.value }))} /></div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>{t('participants:ui.category')}</Label>
                <Select value={editForm.categoryId} onValueChange={(value) => setEditForm((v) => ({ ...v, categoryId: value }))}>
                  <SelectTrigger><SelectValue placeholder={t('participants:ui.select')} /></SelectTrigger>
                  <SelectContent>
                    {categories.map((category) => (
                      <SelectItem key={category.id} value={category.id}>{category.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label>{t('participants:ui.status')}</Label>
                <Select value={editForm.status} onValueChange={(value) => setEditForm((v) => ({ ...v, status: value as ParticipantStatus }))}>
                  <SelectTrigger><SelectValue placeholder={t('participants:ui.select')} /></SelectTrigger>
                  <SelectContent>
                    {(['REGISTERED', 'CONFIRMED', 'CHECKED_IN', 'STARTED', 'DNF', 'DNS', 'DISQUALIFIED', 'FINISHED'] as ParticipantStatus[]).map((status) => (
                      <SelectItem key={status} value={status}>{status.replace('_', ' ')}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {editForm.status !== editForm.originalStatus
                && OFFICIAL_STATUS_DECISIONS.includes(editForm.status as ParticipantStatus) && (
                <div className="col-span-2">
                  <Label htmlFor="status-reason">{t('participants:statusReason')}</Label>
                  <Input id="status-reason" value={editForm.statusReason} maxLength={500}
                    onChange={(e) => setEditForm((v) => ({ ...v, statusReason: e.target.value }))} />
                </div>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)} disabled={editSaving}>{t('participants:ui.cancel')}</Button>
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
                <p className="text-sm text-muted-foreground">{t('participants:ui.noPenaltiesRecorded')}</p>
              ) : (
                penalties.map((penalty) => (
                  <div key={penalty.id} className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                    <span>
                      <span className="font-medium">{penalty.seconds}s</span>
                      {penalty.reason ? <span className="text-muted-foreground"> — {penalty.reason}</span> : null}
                    </span>
                    {canManageAdminActions && (
                      <Button variant="ghost" size="sm" className="text-destructive" onClick={() => void handleRemovePenalty(penalty.id)}>
                        {t('participants:ui.remove')}
                      </Button>
                    )}
                  </div>
                ))
              )}
            </div>
            {canManageAdminActions && (
              <div className="grid grid-cols-3 gap-3">
                <div><Label>{t('participants:ui.seconds')}</Label><Input type="number" value={penaltySeconds} onChange={(e) => setPenaltySeconds(e.target.value)} /></div>
                <div className="col-span-2"><Label>{t('participants:ui.reason')}</Label><Input value={penaltyReason} onChange={(e) => setPenaltyReason(e.target.value)} placeholder={t('participants:penaltyReasonPlaceholder')} /></div>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPenaltyOpen(false)}>{t('participants:ui.close')}</Button>
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
          <div className="space-y-4" aria-busy={refundLoading}>
            {refundLoading && <p role="status">{t('common:refundLoading')}</p>}
            {!refundLoading && refundLoadError && (
              <p role="alert" className="text-sm text-destructive">{t('common:refundLoadFailed')}</p>
            )}
            {!refundLoading && !refundLoadError && refundPayments.length === 0 && !refundAttempt && (
              <p role="status">{t('common:refundNoEligiblePayment')}</p>
            )}
            {refundPayments.length > 0 && !refundAttempt && (
              <div className="space-y-2">
                <Label htmlFor="refund-payment">{t('common:refundPayment')}</Label>
                <Select value={refundPaymentId} onValueChange={setRefundPaymentId}>
                  <SelectTrigger id="refund-payment"><SelectValue placeholder={t('common:refundSelectPayment')} /></SelectTrigger>
                  <SelectContent>
                    {refundPayments.map((payment) => (
                      <SelectItem key={payment.id} value={payment.id}>
                        {formatCurrency(payment.payableAmount ?? payment.amount ?? 0, payment.currency)} · {payment.reference}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
            {refundAttempts.filter((attempt) => attempt.status === 'PENDING').length > 1 && (
              <div className="space-y-2">
                <Label htmlFor="refund-attempt">{t('common:refundAttempt')}</Label>
                <Select value={refundAttemptId} onValueChange={setRefundAttemptId}>
                  <SelectTrigger id="refund-attempt"><SelectValue placeholder={t('common:refundSelectAttempt')} /></SelectTrigger>
                  <SelectContent>
                    {refundAttempts.filter((attempt) => attempt.status === 'PENDING').map((attempt) => (
                      <SelectItem key={attempt.id} value={attempt.id}>
                        {formatCurrency(attempt.amount, attempt.currency ?? undefined)} · {attempt.paymentReference}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
            {refundAttempt && (
              <div role="status" className="rounded-md border border-border p-3 text-sm">
                <p>{t('common:refundAttempt')}: {refundAttempt.id}</p>
                <p>{t('common:refundAmount')}: {formatCurrency(refundAttempt.amount, refundAttempt.currency ?? undefined)}</p>
                <p>{t('common:refundPaymentReference')}: {refundAttempt.paymentReference}</p>
                <p>{t('common:labels.status')}: {refundAttempt.status}</p>
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor="refund-reason">{t('common:refundReason')}</Label>
              <Input id="refund-reason" value={refundReason} onChange={(event) => setRefundReason(event.target.value)} />
            </div>
            {!refundAttempt && refundPayments.length > 0 && (
              <Button variant="outline" disabled={refundBusy || refundLoading || !refundReason.trim() || !refundPaymentId}
                onClick={() => void handleStageRefund()}>{t('common:stageExternalRefund')}</Button>
            )}
            {refundAttempt?.status === 'PENDING' && (
              <>
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
              </>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={refundBusy} onClick={closeRefundDialog}>
              {t('common:actions.cancel')}
            </Button>
            <Button disabled={refundBusy || refundLoading || refundAttempt?.status !== 'PENDING' || !refundReason.trim() || !refundReference.trim()}
              onClick={() => void handleReconcileRefund()}>{t('common:recordRefundOutcome')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>{t('common:importPreviewTitle')}</DialogTitle>
            <DialogDescription>
              {preview ? t('common:importPreviewSummary', {
                total: preview.totalRows, valid: preview.validRows, duplicates: preview.duplicateRows,
              }) : ''}
            </DialogDescription>
          </DialogHeader>
          {previewLoading && <p role="status">{t('common:importPreviewLoading')}</p>}
          {preview && (
            <div className="max-h-[50vh] overflow-auto rounded-md border">
              <Table>
                <TableHeader><TableRow><TableHead>#</TableHead><TableHead>{t('participants:ui.email')}</TableHead><TableHead>{t('participants:ui.name')}</TableHead><TableHead>{t('participants:ui.issues')}</TableHead></TableRow></TableHeader>
                <TableBody>
                  {preview.rows.map((row) => (
                    <TableRow key={row.rowNumber}>
                      <TableCell>{row.rowNumber}</TableCell>
                      <TableCell>{row.values.email}</TableCell>
                      <TableCell>{[row.values.firstName, row.values.lastName].filter(Boolean).join(' ')}</TableCell>
                      <TableCell className={row.errors.length ? 'text-destructive' : 'text-muted-foreground'}>
                        {row.errors.length ? row.errors.join('; ') : t('common:importPreviewValid')}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setPreviewOpen(false)}>{t('common:actions.cancel')}</Button>
            <Button disabled={!preview || preview.validRows === 0 || importSubmitting} onClick={() => void confirmImport()}>
              {importSubmitting ? t('common:importSubmitting') : t('common:importPreviewConfirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
