import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Plus, Search, MoreHorizontal, Trash2, PlayCircle, Wifi, QrCode, Download, Upload } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ApiError, checkpointsApi, routeApi } from '@/lib/api'
import { formatDateTime, formatNumber } from '@/lib/format'
import { toast } from 'sonner'
import type { Checkpoint, CheckpointType, CheckpointRecord } from '@/types'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { useTranslation } from 'react-i18next'
import { SelfScanClaimsPanel } from '@/components/checkpoints/SelfScanClaimsPanel'
import { SelfScanQrDialog, acceptsSelfScan } from '@/components/checkpoints/SelfScanQrDialog'

const checkpointTypeColors: Record<CheckpointType, string> = {
  START: 'bg-primary/20 text-primary',
  INTERMEDIATE: 'bg-muted text-muted-foreground',
  WATER_STATION: 'bg-info/20 text-info',
  MEDICAL: 'bg-destructive/20 text-destructive',
  FINISH: 'bg-chart-3/20 text-chart-3',
}

type RecordAction = 'adjust' | 'void' | 'restore' | 'reassign'

const RECORD_ACTION_ERRORS = new Set([
  'TIMING_SLOT_OCCUPIED',
  'REASSIGN_SAME_PARTICIPANT',
  'PARTICIPANT_NOT_ENTERED',
  'RESULTS_CERTIFIED',
  'CORRECTION_REASON_REQUIRED',
])

export function CheckpointsPage() {
  const { raceId } = useParams()
  const { user } = useAuth()
  const { t } = useTranslation()
  const canManageRaceConfiguration = isAdminRole(user?.role)
  const canReviewSelfScans = canManageRaceConfiguration || user?.role === 'OFFICIAL'
  const [selfScanCheckpoint, setSelfScanCheckpoint] = useState<Checkpoint | null>(null)
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([])
  const [search, setSearch] = useState('')
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false)
  const [isRecordDialogOpen, setIsRecordDialogOpen] = useState(false)
  const [selectedCheckpoint, setSelectedCheckpoint] = useState<Checkpoint | null>(null)
  const [recordBib, setRecordBib] = useState('')
  const [recordMethod, setRecordMethod] = useState<string>('MANUAL')
  const [exporting, setExporting] = useState(false)
  const [recordsOpen, setRecordsOpen] = useState(false)
  const [recordsCheckpoint, setRecordsCheckpoint] = useState<Checkpoint | null>(null)
  const [records, setRecords] = useState<CheckpointRecord[]>([])
  const [recordsLoading, setRecordsLoading] = useState(false)
  const [correctionRecord, setCorrectionRecord] = useState<CheckpointRecord | null>(null)
  const [correctionForm, setCorrectionForm] = useState({ adjustedRecordedAt: '', reason: '', bib: '' })
  const [correctionMode, setCorrectionMode] = useState<RecordAction>('adjust')
  const [correcting, setCorrecting] = useState(false)
  const gpxInputRef = useRef<HTMLInputElement>(null)
  const timingInputRef = useRef<HTMLInputElement>(null)

  const handleImportTiming = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file || !raceId) return
    try {
      const result = await checkpointsApi.importTiming(raceId, file)
      toast.success(t('common:timingImportResult', { imported: result.imported, total: result.totalRows }))
      if (result.errors.length > 0) {
        toast.error(t('common:timingImportErrors', { count: result.errors.length }))
      }
    } catch {
      toast.error(t('common:timingImportFailed'))
    } finally {
      if (timingInputRef.current) timingInputRef.current.value = ''
    }
  }

  const handleImportGpx = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file || !raceId) return
    try {
      const points = await routeApi.importGpx(raceId, file)
      toast.success(t('checkpoints:toasts.routeImported', { count: points.length }))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('checkpoints:toasts.gpxImportFailed'))
    }
  }
  const [newCheckpoint, setNewCheckpoint] = useState({
    name: '',
    checkpointType: 'INTERMEDIATE' as CheckpointType,
    distanceFromStartKm: '',
    latitude: '',
    longitude: '',
    orderIndex: '',
    cutoffTime: '',
    hasRfidReader: false,
    hasQrScanner: false,
    expectedPasses: '1',
    minPassSeconds: '60',
    firstPassIsStart: false,
  })

  const handleExport = async (format: 'csv' | 'gpx') => {
    if (!raceId) return
    setExporting(true)
    try {
      const blob = format === 'gpx'
        ? await routeApi.exportGpx(raceId)
        : await checkpointsApi.exportCheckpoints(raceId, 'csv')
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = format === 'gpx' ? `route-${raceId}.gpx` : `checkpoints-${raceId}.csv`
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
      toast.success(t('checkpoints:toasts.exported', { format: format.toUpperCase() }))
    } catch {
      toast.error(t('checkpoints:toasts.exportFailed', { format: format.toUpperCase() }))
    } finally {
      setExporting(false)
    }
  }

  const openRecords = async (checkpoint: Checkpoint) => {
    if (!raceId) return
    setRecordsCheckpoint(checkpoint)
    setRecordsOpen(true)
    setRecordsLoading(true)
    try {
      setRecords(await checkpointsApi.listRecords(raceId, checkpoint.id, canManageRaceConfiguration))
    } catch {
      toast.error(t('checkpoints:ui.failedToLoadRecords'))
    } finally {
      setRecordsLoading(false)
    }
  }

  const beginCorrection = (record: CheckpointRecord, mode: RecordAction = 'adjust') => {
    setCorrectionRecord(record)
    setCorrectionMode(mode)
    setCorrectionForm({
      adjustedRecordedAt: mode === 'adjust' && record.adjustedRecordedAt ? record.adjustedRecordedAt.slice(0, 16) : '',
      reason: mode === 'adjust' ? record.adjustmentReason ?? '' : '',
      bib: '',
    })
  }

  const handleCorrect = async () => {
    if (!raceId || !recordsCheckpoint || !correctionRecord) return
    if (correctionForm.reason.trim().length < 5) {
      toast.error(t('checkpoints:correction.reasonRequired'))
      return
    }
    if (correctionMode === 'reassign' && !correctionForm.bib.trim()) {
      toast.error(t('checkpoints:recordActions.bibRequired'))
      return
    }
    setCorrecting(true)
    const reason = correctionForm.reason.trim()
    try {
      if (correctionMode === 'void') {
        await checkpointsApi.voidRecord(raceId, recordsCheckpoint.id, correctionRecord.id, reason)
      } else if (correctionMode === 'restore') {
        await checkpointsApi.restoreRecord(raceId, recordsCheckpoint.id, correctionRecord.id, reason)
      } else if (correctionMode === 'reassign') {
        await checkpointsApi.reassignRecord(raceId, recordsCheckpoint.id, correctionRecord.id, {
          participantBibNumber: correctionForm.bib.trim(),
          reason,
        })
      } else {
        await checkpointsApi.correctRecord(raceId, recordsCheckpoint.id, correctionRecord.id, {
          adjustedRecordedAt: correctionForm.adjustedRecordedAt
            ? new Date(correctionForm.adjustedRecordedAt).toISOString()
            : undefined,
          reason,
        })
      }
      toast.success(t(`checkpoints:recordActions.done.${correctionMode}`))
      setCorrectionRecord(null)
      setRecords(await checkpointsApi.listRecords(raceId, recordsCheckpoint.id, canManageRaceConfiguration))
    } catch (error) {
      const code = error instanceof ApiError ? error.code : undefined
      toast.error(code && RECORD_ACTION_ERRORS.has(code)
        ? t(`checkpoints:recordActions.errors.${code}`)
        : t(`checkpoints:recordActions.failed.${correctionMode}`))
    } finally {
      setCorrecting(false)
    }
  }

  const loadCheckpoints = async () => {
    if (!raceId) return
    try {
      const data = await checkpointsApi.list(raceId)
      setCheckpoints(data)
    } catch {
      toast.error(t('checkpoints:ui.failedToLoadCheckpoints'))
    }  }

  useEffect(() => {
    void loadCheckpoints()
  }, [raceId])

  const filtered = useMemo(
    () => checkpoints.filter((cp) => cp.name.toLowerCase().includes(search.toLowerCase())),
    [checkpoints, search],
  )

  const handleAddCheckpoint = async () => {
    if (!raceId) return
    try {
      await checkpointsApi.create(raceId, {
        name: newCheckpoint.name,
        checkpointType: newCheckpoint.checkpointType,
        distanceFromStartKm: newCheckpoint.distanceFromStartKm ? Number(newCheckpoint.distanceFromStartKm) : undefined,
        latitude: newCheckpoint.latitude ? Number(newCheckpoint.latitude) : undefined,
        longitude: newCheckpoint.longitude ? Number(newCheckpoint.longitude) : undefined,
        orderIndex: Number(newCheckpoint.orderIndex || checkpoints.length),
        cutoffTime: newCheckpoint.cutoffTime || undefined,
        hasRfidReader: newCheckpoint.hasRfidReader,
        hasQrScanner: newCheckpoint.hasQrScanner,
        expectedPasses: Number(newCheckpoint.expectedPasses || 1),
        minPassSeconds: Number(newCheckpoint.minPassSeconds || 60),
        firstPassIsStart: newCheckpoint.checkpointType === 'FINISH' && newCheckpoint.firstPassIsStart,
      })
      toast.success(t('checkpoints:ui.checkpointAdded'))
      setIsAddDialogOpen(false)
      setNewCheckpoint({ name: '', checkpointType: 'INTERMEDIATE', distanceFromStartKm: '', latitude: '', longitude: '', orderIndex: '', cutoffTime: '', hasRfidReader: false, hasQrScanner: false, expectedPasses: '1', minPassSeconds: '60', firstPassIsStart: false })
      await loadCheckpoints()
    } catch {
      toast.error(t('checkpoints:ui.failedToAddCheckpoint'))
    }
  }

  const handleRecordTime = async () => {
    if (!raceId || !selectedCheckpoint || !recordBib) return
    try {
      await checkpointsApi.recordTime(raceId, selectedCheckpoint.id, {
        method: recordMethod,
        participantBibNumber: recordBib,
      } as never)
      toast.success(t('checkpoints:toasts.timeRecorded', { bib: recordBib }))
      setIsRecordDialogOpen(false)
      setRecordBib('')
      setRecordMethod('MANUAL')
      await loadCheckpoints()
    } catch {
      toast.error(t('checkpoints:ui.failedToRecordCheckpoint'))
    }
  }

  const handleDelete = async (checkpointId: string) => {
    if (!raceId) return
    try {
      await checkpointsApi.delete(raceId, checkpointId)
      toast.success(t('checkpoints:ui.checkpointDeleted'))
      await loadCheckpoints()
    } catch {
      toast.error(t('checkpoints:ui.failedToDeleteCheckpoint'))
    }
  }

  const openRecordDialog = (checkpoint: Checkpoint) => {
    setSelectedCheckpoint(checkpoint)
    setIsRecordDialogOpen(true)
  }

  const totalRecords = checkpoints.reduce((sum, cp) => sum + (cp.recordCount || 0), 0)

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{t('checkpoints:ui.checkpoints')}</h1>
          <p className="text-muted-foreground">{t('checkpoints:ui.manageTimingPointsAndRecords')}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            onClick={() => void handleExport('csv')}
            disabled={!raceId || exporting}
          >
            <Download className="mr-2 h-4 w-4" />
            {t('checkpoints:ui.exportCsv')}
          </Button>
          <Button
            variant="outline"
            onClick={() => void handleExport('gpx')}
            disabled={!raceId || exporting}
          >
            <Download className="mr-2 h-4 w-4" />
            {t('checkpoints:ui.exportGpx')}
          </Button>
          {canManageRaceConfiguration && (
            <>
              <Button variant="outline" onClick={() => gpxInputRef.current?.click()}>
                <Upload className="mr-2 h-4 w-4" />
                {t('checkpoints:ui.importGpx')}
              </Button>
              <Button variant="outline" onClick={() => timingInputRef.current?.click()}>
                <Upload className="mr-2 h-4 w-4" />
                {t('common:importTiming')}
              </Button>
              <input ref={timingInputRef} type="file" accept=".csv,text/csv" className="hidden"
                onChange={(e) => void handleImportTiming(e)} />
              <input
                ref={gpxInputRef}
                type="file"
                accept=".gpx,application/gpx+xml"
                className="hidden"
                onChange={(e) => void handleImportGpx(e)}
              />
            </>
          )}
          <Button onClick={() => setIsAddDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            {t('checkpoints:ui.addCheckpoint')}
          </Button>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-4">
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('checkpoints:ui.totalCheckpoints')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{checkpoints.length}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('checkpoints:ui.rfidEnabled')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{checkpoints.filter((cp) => cp.hasRfidReader).length}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('checkpoints:ui.qrEnabled')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{checkpoints.filter((cp) => cp.hasQrScanner).length}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('checkpoints:ui.totalRecords')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{formatNumber(totalRecords)}</div></CardContent></Card>
      </div>

      {canReviewSelfScans && raceId && <SelfScanClaimsPanel raceId={raceId} />}

      <div className="relative max-w-sm">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input placeholder={t('checkpoints:ui.searchCheckpoints')} className="pl-10" value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>#</TableHead>
                <TableHead>{t('checkpoints:ui.name')}</TableHead>
                <TableHead>{t('checkpoints:ui.type')}</TableHead>
                <TableHead>{t('checkpoints:ui.distance')}</TableHead>
                <TableHead>{t('checkpoints:ui.cutoff')}</TableHead>
                <TableHead>{t('checkpoints:ui.equipment')}</TableHead>
                <TableHead>{t('checkpoints:ui.records')}</TableHead>
                <TableHead>{t('checkpoints:ui.actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((checkpoint, index) => (
                <TableRow key={checkpoint.id}>
                  <TableCell>{index + 1}</TableCell>
                  <TableCell className="font-medium">{checkpoint.name}</TableCell>
                  <TableCell><Badge className={checkpointTypeColors[checkpoint.checkpointType]}>{checkpoint.checkpointType.replace('_', ' ')}</Badge></TableCell>
                  <TableCell>{checkpoint.distanceFromStartKm != null ? t('checkpoints:distanceKm', { value: formatNumber(checkpoint.distanceFromStartKm, { maximumFractionDigits: 2 }) }) : '-'}</TableCell>
                  <TableCell>{checkpoint.cutoffTime || '-'}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      {checkpoint.hasRfidReader && <Badge variant="outline" className="gap-1"><Wifi className="h-3 w-3" />{t('checkpoints:ui.rfid')}</Badge>}
                      {checkpoint.hasQrScanner && <Badge variant="outline" className="gap-1"><QrCode className="h-3 w-3" />{t('checkpoints:ui.qr')}</Badge>}
                    </div>
                  </TableCell>
                  <TableCell>{formatNumber(checkpoint.recordCount || 0)}</TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon" onClick={() => openRecordDialog(checkpoint)}>
                      <PlayCircle className="h-4 w-4" />
                    </Button>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon"><MoreHorizontal className="h-4 w-4" /></Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => openRecordDialog(checkpoint)}>{t('checkpoints:ui.recordTime')}</DropdownMenuItem>
                        <DropdownMenuItem onClick={() => void openRecords(checkpoint)}>{t('checkpoints:ui.viewRecords')}</DropdownMenuItem>
                        {canReviewSelfScans && acceptsSelfScan(checkpoint) && (
                          <DropdownMenuItem onClick={() => setSelfScanCheckpoint(checkpoint)}>
                            <QrCode className="mr-2 h-4 w-4" aria-hidden="true" />{t('checkpoints:selfScan.menuItem')}
                          </DropdownMenuItem>
                        )}
                        <DropdownMenuSeparator />
                        <DropdownMenuItem className="text-destructive" onClick={() => void handleDelete(checkpoint.id)}>
                          <Trash2 className="mr-2 h-4 w-4" />{t('checkpoints:ui.delete')}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{t('checkpoints:ui.addCheckpoint')}</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div><Label>{t('checkpoints:ui.name')}</Label><Input value={newCheckpoint.name} onChange={(e) => setNewCheckpoint((v) => ({ ...v, name: e.target.value }))} /></div>
            <div className="grid grid-cols-2 gap-3">
              <div><Label>{t('checkpoints:ui.type')}</Label><Select value={newCheckpoint.checkpointType} onValueChange={(value) => setNewCheckpoint((v) => ({ ...v, checkpointType: value as CheckpointType }))}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="START">{t('checkpoints:ui.start')}</SelectItem><SelectItem value="INTERMEDIATE">{t('checkpoints:ui.intermediate')}</SelectItem><SelectItem value="WATER_STATION">{t('checkpoints:ui.waterStation')}</SelectItem><SelectItem value="MEDICAL">{t('checkpoints:ui.medical')}</SelectItem><SelectItem value="FINISH">{t('checkpoints:ui.finish')}</SelectItem></SelectContent></Select></div>
              <div><Label>{t('checkpoints:ui.orderIndex')}</Label><Input type="number" value={newCheckpoint.orderIndex} onChange={(e) => setNewCheckpoint((v) => ({ ...v, orderIndex: e.target.value }))} /></div>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div><Label>{t('checkpoints:ui.distanceKm')}</Label><Input type="number" value={newCheckpoint.distanceFromStartKm} onChange={(e) => setNewCheckpoint((v) => ({ ...v, distanceFromStartKm: e.target.value }))} /></div>
              <div><Label>{t('checkpoints:ui.lat')}</Label><Input type="number" value={newCheckpoint.latitude} onChange={(e) => setNewCheckpoint((v) => ({ ...v, latitude: e.target.value }))} /></div>
              <div><Label>{t('checkpoints:ui.lng')}</Label><Input type="number" value={newCheckpoint.longitude} onChange={(e) => setNewCheckpoint((v) => ({ ...v, longitude: e.target.value }))} /></div>
            </div>
            <div><Label>{t('checkpoints:ui.cutoffHhMmSs')}</Label><Input value={newCheckpoint.cutoffTime} onChange={(e) => setNewCheckpoint((v) => ({ ...v, cutoffTime: e.target.value }))} /></div>
            <div className="flex gap-4">
              <label className="text-sm flex items-center gap-2"><input type="checkbox" checked={newCheckpoint.hasRfidReader} onChange={(e) => setNewCheckpoint((v) => ({ ...v, hasRfidReader: e.target.checked }))} /> {t('checkpoints:ui.rfid')}</label>
              <label className="text-sm flex items-center gap-2"><input type="checkbox" checked={newCheckpoint.hasQrScanner} onChange={(e) => setNewCheckpoint((v) => ({ ...v, hasQrScanner: e.target.checked }))} /> {t('checkpoints:ui.qr')}</label>
              <div>
                <Label htmlFor="expected-passes">{t('checkpoints:passes.expected')}</Label>
                <Input id="expected-passes" type="number" min={1} max={200} value={newCheckpoint.expectedPasses}
                  onChange={(e) => setNewCheckpoint((v) => ({ ...v, expectedPasses: e.target.value }))} />
                <p className="text-xs text-muted-foreground">{t('checkpoints:passes.expectedHelp')}</p>
              </div>
              <div>
                <Label htmlFor="min-pass-seconds">{t('checkpoints:passes.minSeconds')}</Label>
                <Input id="min-pass-seconds" type="number" min={1} value={newCheckpoint.minPassSeconds}
                  onChange={(e) => setNewCheckpoint((v) => ({ ...v, minPassSeconds: e.target.value }))} />
              </div>
              {newCheckpoint.checkpointType === 'FINISH' && (
                <label className="text-sm flex items-center gap-2 col-span-2">
                  <input type="checkbox" checked={newCheckpoint.firstPassIsStart}
                    onChange={(e) => setNewCheckpoint((v) => ({ ...v, firstPassIsStart: e.target.checked }))} />
                  {t('checkpoints:passes.firstPassIsStart')}
                </label>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsAddDialogOpen(false)}>{t('checkpoints:ui.cancel')}</Button>
            <Button onClick={() => void handleAddCheckpoint()}>{t('checkpoints:ui.add')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isRecordDialogOpen} onOpenChange={setIsRecordDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{t('checkpoints:ui.recordCheckpointTime')}</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div><Label>{t('checkpoints:ui.checkpoint')}</Label><Input value={selectedCheckpoint?.name || ''} disabled /></div>
            <div><Label>{t('checkpoints:ui.participantBibNumber')}</Label><Input value={recordBib} onChange={(e) => setRecordBib(e.target.value)} /></div>
            <div><Label>{t('checkpoints:ui.method')}</Label><Select value={recordMethod} onValueChange={setRecordMethod}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="MANUAL">{t('checkpoints:ui.manual')}</SelectItem><SelectItem value="RFID">{t('checkpoints:ui.rfid')}</SelectItem><SelectItem value="QR_CODE">{t('checkpoints:ui.qrCode')}</SelectItem><SelectItem value="GPS_AUTO">{t('checkpoints:ui.gps')}</SelectItem></SelectContent></Select></div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsRecordDialogOpen(false)}>{t('checkpoints:ui.cancel')}</Button>
            <Button onClick={() => void handleRecordTime()}>{t('checkpoints:ui.record')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={recordsOpen} onOpenChange={(open) => { setRecordsOpen(open); if (!open) setCorrectionRecord(null) }}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t('checkpoints:recordActions.title', { name: recordsCheckpoint?.name ?? '' })}</DialogTitle>
          </DialogHeader>
          {recordsLoading ? (
            <p className="py-6 text-center text-sm text-muted-foreground">{t('checkpoints:ui.loading')}</p>
          ) : records.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">{t('checkpoints:ui.noRecordsForThisCheckpoint')}</p>
          ) : (
            <div className="space-y-2">
              {records.map((record) => (
                <div key={record.id}
                  className={`flex flex-wrap items-center justify-between gap-2 rounded-md border border-border px-3 py-2 text-sm ${record.voidedAt ? 'opacity-70' : ''}`}>
                  <div>
                    <div className="flex flex-wrap items-center gap-2 font-medium">
                      <span className={record.voidedAt ? 'line-through' : undefined}>
                        {record.participantName ?? record.participantId}
                        {record.bibNumber ? ` · ${record.bibNumber}` : ''}
                      </span>
                      {record.voidedAt && <Badge variant="destructive">{t('checkpoints:recordActions.voided')}</Badge>}
                    </div>
                    <div className="text-xs text-muted-foreground">
                      {formatDateTime(record.recordedAt)} · {record.recordMethod}
                      {record.isCutoffMissed ? ` · ${t('checkpoints:recordActions.cutoffMissed')}` : ''}
                      {record.adjustedRecordedAt
                        ? ` · ${t('checkpoints:recordActions.adjustedTo', { time: formatDateTime(record.adjustedRecordedAt) })}`
                        : ''}
                    </div>
                    {record.voidedAt && (
                      <div className="text-xs text-muted-foreground">
                        {t('checkpoints:recordActions.voidedDetail', { time: formatDateTime(record.voidedAt), reason: record.voidReason ?? '' })}
                      </div>
                    )}
                  </div>
                  {canManageRaceConfiguration && (
                    <div className="flex flex-wrap gap-1">
                      {record.voidedAt ? (
                        <Button variant="ghost" size="sm" onClick={() => beginCorrection(record, 'restore')}>
                          {t('checkpoints:recordActions.action.restore')}
                        </Button>
                      ) : (
                        <>
                          <Button variant="ghost" size="sm" onClick={() => beginCorrection(record, 'adjust')}>{t('checkpoints:ui.adjust')}</Button>
                          <Button variant="ghost" size="sm" onClick={() => beginCorrection(record, 'reassign')}>
                            {t('checkpoints:recordActions.action.reassign')}
                          </Button>
                          <Button variant="ghost" size="sm" className="text-destructive" onClick={() => beginCorrection(record, 'void')}>
                            {t('checkpoints:recordActions.action.void')}
                          </Button>
                        </>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}

          {correctionRecord && (
            <div className="mt-2 space-y-3 rounded-md border border-primary/30 bg-primary/5 p-3">
              <p className="text-sm font-medium">
                {t(`checkpoints:recordActions.heading.${correctionMode}`, {
                  name: correctionRecord.participantName ?? correctionRecord.participantId,
                })}
              </p>
              {correctionMode !== 'adjust' && (
                <p className="text-xs text-muted-foreground">{t(`checkpoints:recordActions.help.${correctionMode}`)}</p>
              )}
              <div className="grid gap-3 sm:grid-cols-2">
                {correctionMode === 'adjust' && (
                  <div>
                    <Label htmlFor="record-adjusted-time">{t('checkpoints:ui.adjustedTime')}</Label>
                    <Input
                      id="record-adjusted-time"
                      type="datetime-local"
                      value={correctionForm.adjustedRecordedAt}
                      onChange={(e) => setCorrectionForm((f) => ({ ...f, adjustedRecordedAt: e.target.value }))}
                    />
                  </div>
                )}
                {correctionMode === 'reassign' && (
                  <div>
                    <Label htmlFor="record-reassign-bib">{t('checkpoints:recordActions.bib')}</Label>
                    <Input
                      id="record-reassign-bib"
                      value={correctionForm.bib}
                      maxLength={50}
                      onChange={(e) => setCorrectionForm((f) => ({ ...f, bib: e.target.value }))}
                    />
                  </div>
                )}
                <div>
                  <Label htmlFor="record-action-reason">{t('checkpoints:ui.reason')}</Label>
                  <Input
                    id="record-action-reason"
                    value={correctionForm.reason}
                    maxLength={500}
                    onChange={(e) => setCorrectionForm((f) => ({ ...f, reason: e.target.value }))}
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={() => setCorrectionRecord(null)} disabled={correcting}>{t('checkpoints:ui.cancel')}</Button>
                <Button size="sm" variant={correctionMode === 'void' ? 'destructive' : 'default'}
                  onClick={() => void handleCorrect()} disabled={correcting}>
                  {correcting ? t('checkpoints:recordActions.saving') : t(`checkpoints:recordActions.action.${correctionMode}`)}
                </Button>
              </div>
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setRecordsOpen(false)}>{t('checkpoints:ui.close')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {raceId && (
        <SelfScanQrDialog
          raceId={raceId}
          checkpoint={selfScanCheckpoint}
          canRotate={canManageRaceConfiguration}
          onOpenChange={(open) => { if (!open) setSelfScanCheckpoint(null) }}
        />
      )}
    </div>
  )
}
