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
import { checkpointsApi, routeApi } from '@/lib/api'
import { toast } from 'sonner'
import type { Checkpoint, CheckpointType, CheckpointRecord } from '@/types'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { useTranslation } from 'react-i18next'

const checkpointTypeColors: Record<CheckpointType, string> = {
  START: 'bg-primary/20 text-primary',
  INTERMEDIATE: 'bg-muted text-muted-foreground',
  WATER_STATION: 'bg-info/20 text-info',
  MEDICAL: 'bg-destructive/20 text-destructive',
  FINISH: 'bg-chart-3/20 text-chart-3',
}

export function CheckpointsPage() {
  const { raceId } = useParams()
  const { user } = useAuth()
  const { t } = useTranslation()
  const canManageRaceConfiguration = isAdminRole(user?.role)
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
  const [correctionForm, setCorrectionForm] = useState({ adjustedRecordedAt: '', reason: '' })
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
      toast.success(`Imported ${points.length} route points`)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to import GPX')
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
      toast.success(`Exported ${format.toUpperCase()}`)
    } catch {
      toast.error(`Failed to export ${format.toUpperCase()}`)
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
      setRecords(await checkpointsApi.listRecords(raceId, checkpoint.id))
    } catch {
      toast.error('Failed to load records')
    } finally {
      setRecordsLoading(false)
    }
  }

  const beginCorrection = (record: CheckpointRecord) => {
    setCorrectionRecord(record)
    setCorrectionForm({
      adjustedRecordedAt: record.adjustedRecordedAt ? record.adjustedRecordedAt.slice(0, 16) : '',
      reason: record.adjustmentReason ?? '',
    })
  }

  const handleCorrect = async () => {
    if (!raceId || !recordsCheckpoint || !correctionRecord) return
    setCorrecting(true)
    try {
      await checkpointsApi.correctRecord(raceId, recordsCheckpoint.id, correctionRecord.id, {
        adjustedRecordedAt: correctionForm.adjustedRecordedAt
          ? new Date(correctionForm.adjustedRecordedAt).toISOString()
          : undefined,
        reason: correctionForm.reason.trim() || undefined,
      })
      toast.success('Record corrected')
      setCorrectionRecord(null)
      setRecords(await checkpointsApi.listRecords(raceId, recordsCheckpoint.id))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to correct record')
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
      toast.error('Failed to load checkpoints')
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
      })
      toast.success('Checkpoint added')
      setIsAddDialogOpen(false)
      setNewCheckpoint({ name: '', checkpointType: 'INTERMEDIATE', distanceFromStartKm: '', latitude: '', longitude: '', orderIndex: '', cutoffTime: '', hasRfidReader: false, hasQrScanner: false })
      await loadCheckpoints()
    } catch {
      toast.error('Failed to add checkpoint')
    }
  }

  const handleRecordTime = async () => {
    if (!raceId || !selectedCheckpoint || !recordBib) return
    try {
      await checkpointsApi.recordTime(raceId, selectedCheckpoint.id, {
        method: recordMethod,
        participantBibNumber: recordBib,
      } as never)
      toast.success(`Time recorded for ${recordBib}`)
      setIsRecordDialogOpen(false)
      setRecordBib('')
      setRecordMethod('MANUAL')
      await loadCheckpoints()
    } catch {
      toast.error('Failed to record checkpoint')
    }
  }

  const handleDelete = async (checkpointId: string) => {
    if (!raceId) return
    try {
      await checkpointsApi.delete(raceId, checkpointId)
      toast.success('Checkpoint deleted')
      await loadCheckpoints()
    } catch {
      toast.error('Failed to delete checkpoint')
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
          <h1 className="text-2xl font-bold tracking-tight">Checkpoints</h1>
          <p className="text-muted-foreground">Manage timing points and records</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            onClick={() => void handleExport('csv')}
            disabled={!raceId || exporting}
          >
            <Download className="mr-2 h-4 w-4" />
            Export CSV
          </Button>
          <Button
            variant="outline"
            onClick={() => void handleExport('gpx')}
            disabled={!raceId || exporting}
          >
            <Download className="mr-2 h-4 w-4" />
            Export GPX
          </Button>
          {canManageRaceConfiguration && (
            <>
              <Button variant="outline" onClick={() => gpxInputRef.current?.click()}>
                <Upload className="mr-2 h-4 w-4" />
                Import GPX
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
            Add Checkpoint
          </Button>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-4">
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Total Checkpoints</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{checkpoints.length}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">RFID Enabled</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{checkpoints.filter((cp) => cp.hasRfidReader).length}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">QR Enabled</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{checkpoints.filter((cp) => cp.hasQrScanner).length}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Total Records</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{totalRecords.toLocaleString()}</div></CardContent></Card>
      </div>

      <div className="relative max-w-sm">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <Input placeholder="Search checkpoints..." className="pl-10" value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>#</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Distance</TableHead>
                <TableHead>Cutoff</TableHead>
                <TableHead>Equipment</TableHead>
                <TableHead>Records</TableHead>
                <TableHead>Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((checkpoint, index) => (
                <TableRow key={checkpoint.id}>
                  <TableCell>{index + 1}</TableCell>
                  <TableCell className="font-medium">{checkpoint.name}</TableCell>
                  <TableCell><Badge className={checkpointTypeColors[checkpoint.checkpointType]}>{checkpoint.checkpointType.replace('_', ' ')}</Badge></TableCell>
                  <TableCell>{checkpoint.distanceFromStartKm ?? '-'}km</TableCell>
                  <TableCell>{checkpoint.cutoffTime || '-'}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      {checkpoint.hasRfidReader && <Badge variant="outline" className="gap-1"><Wifi className="h-3 w-3" />RFID</Badge>}
                      {checkpoint.hasQrScanner && <Badge variant="outline" className="gap-1"><QrCode className="h-3 w-3" />QR</Badge>}
                    </div>
                  </TableCell>
                  <TableCell>{(checkpoint.recordCount || 0).toLocaleString()}</TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon" onClick={() => openRecordDialog(checkpoint)}>
                      <PlayCircle className="h-4 w-4" />
                    </Button>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon"><MoreHorizontal className="h-4 w-4" /></Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => openRecordDialog(checkpoint)}>Record Time</DropdownMenuItem>
                        <DropdownMenuItem onClick={() => void openRecords(checkpoint)}>View Records</DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem className="text-destructive" onClick={() => void handleDelete(checkpoint.id)}>
                          <Trash2 className="mr-2 h-4 w-4" />Delete
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
          <DialogHeader><DialogTitle>Add Checkpoint</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div><Label>Name</Label><Input value={newCheckpoint.name} onChange={(e) => setNewCheckpoint((v) => ({ ...v, name: e.target.value }))} /></div>
            <div className="grid grid-cols-2 gap-3">
              <div><Label>Type</Label><Select value={newCheckpoint.checkpointType} onValueChange={(value) => setNewCheckpoint((v) => ({ ...v, checkpointType: value as CheckpointType }))}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="START">Start</SelectItem><SelectItem value="INTERMEDIATE">Intermediate</SelectItem><SelectItem value="WATER_STATION">Water Station</SelectItem><SelectItem value="MEDICAL">Medical</SelectItem><SelectItem value="FINISH">Finish</SelectItem></SelectContent></Select></div>
              <div><Label>Order Index</Label><Input type="number" value={newCheckpoint.orderIndex} onChange={(e) => setNewCheckpoint((v) => ({ ...v, orderIndex: e.target.value }))} /></div>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div><Label>Distance (km)</Label><Input type="number" value={newCheckpoint.distanceFromStartKm} onChange={(e) => setNewCheckpoint((v) => ({ ...v, distanceFromStartKm: e.target.value }))} /></div>
              <div><Label>Lat</Label><Input type="number" value={newCheckpoint.latitude} onChange={(e) => setNewCheckpoint((v) => ({ ...v, latitude: e.target.value }))} /></div>
              <div><Label>Lng</Label><Input type="number" value={newCheckpoint.longitude} onChange={(e) => setNewCheckpoint((v) => ({ ...v, longitude: e.target.value }))} /></div>
            </div>
            <div><Label>Cutoff (HH:mm:ss)</Label><Input value={newCheckpoint.cutoffTime} onChange={(e) => setNewCheckpoint((v) => ({ ...v, cutoffTime: e.target.value }))} /></div>
            <div className="flex gap-4">
              <label className="text-sm flex items-center gap-2"><input type="checkbox" checked={newCheckpoint.hasRfidReader} onChange={(e) => setNewCheckpoint((v) => ({ ...v, hasRfidReader: e.target.checked }))} /> RFID</label>
              <label className="text-sm flex items-center gap-2"><input type="checkbox" checked={newCheckpoint.hasQrScanner} onChange={(e) => setNewCheckpoint((v) => ({ ...v, hasQrScanner: e.target.checked }))} /> QR</label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsAddDialogOpen(false)}>Cancel</Button>
            <Button onClick={() => void handleAddCheckpoint()}>Add</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={isRecordDialogOpen} onOpenChange={setIsRecordDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Record Checkpoint Time</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div><Label>Checkpoint</Label><Input value={selectedCheckpoint?.name || ''} disabled /></div>
            <div><Label>Participant Bib Number</Label><Input value={recordBib} onChange={(e) => setRecordBib(e.target.value)} /></div>
            <div><Label>Method</Label><Select value={recordMethod} onValueChange={setRecordMethod}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="MANUAL">Manual</SelectItem><SelectItem value="RFID">RFID</SelectItem><SelectItem value="QR_CODE">QR Code</SelectItem><SelectItem value="GPS_AUTO">GPS</SelectItem></SelectContent></Select></div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsRecordDialogOpen(false)}>Cancel</Button>
            <Button onClick={() => void handleRecordTime()}>Record</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={recordsOpen} onOpenChange={(open) => { setRecordsOpen(open); if (!open) setCorrectionRecord(null) }}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Records — {recordsCheckpoint?.name}</DialogTitle>
          </DialogHeader>
          {recordsLoading ? (
            <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>
          ) : records.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">No records for this checkpoint.</p>
          ) : (
            <div className="space-y-2">
              {records.map((record) => (
                <div key={record.id} className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                  <div>
                    <div className="font-medium">
                      {record.participantName ?? record.participantId}
                      {record.bibNumber ? ` · ${record.bibNumber}` : ''}
                    </div>
                    <div className="text-xs text-muted-foreground">
                      {new Date(record.recordedAt).toLocaleString()} · {record.recordMethod}
                      {record.isCutoffMissed ? ' · cutoff missed' : ''}
                      {record.adjustedRecordedAt ? ` · adjusted ${new Date(record.adjustedRecordedAt).toLocaleString()}` : ''}
                    </div>
                  </div>
                  {canManageRaceConfiguration && <Button variant="ghost" size="sm" onClick={() => beginCorrection(record)}>Adjust</Button>}
                </div>
              ))}
            </div>
          )}

          {correctionRecord && (
            <div className="mt-2 space-y-3 rounded-md border border-primary/30 bg-primary/5 p-3">
              <p className="text-sm font-medium">Adjust {correctionRecord.participantName ?? correctionRecord.participantId}</p>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label>Adjusted time</Label>
                  <Input
                    type="datetime-local"
                    value={correctionForm.adjustedRecordedAt}
                    onChange={(e) => setCorrectionForm((f) => ({ ...f, adjustedRecordedAt: e.target.value }))}
                  />
                </div>
                <div>
                  <Label>Reason</Label>
                  <Input
                    value={correctionForm.reason}
                    onChange={(e) => setCorrectionForm((f) => ({ ...f, reason: e.target.value }))}
                    placeholder="Optional"
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={() => setCorrectionRecord(null)} disabled={correcting}>Cancel</Button>
                <Button size="sm" onClick={() => void handleCorrect()} disabled={correcting}>{correcting ? 'Saving…' : 'Apply'}</Button>
              </div>
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setRecordsOpen(false)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
