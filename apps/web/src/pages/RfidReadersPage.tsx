import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Activity, AlertTriangle, Clock3, Cpu, KeyRound, Plus, Radio, RefreshCw, Wifi } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { checkpointsApi, racesApi, rfidApi } from '@/lib/api'
import type { Checkpoint, Race, RfidRaceOverview, RfidReader, RfidReaderCreateResponse } from '@/types'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { RfidTimingPanel, clockOffsetWarning } from '@/components/rfid/RfidTimingPanel'
import { WavesPanel } from '@/components/rfid/WavesPanel'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { formatDateTime as formatLocaleDateTime, formatNumber } from '@/lib/format'

const gatewayStatusColors = {
  ONLINE: 'bg-success/20 text-success',
  DEGRADED: 'bg-warning/20 text-warning-foreground',
  OFFLINE: 'bg-muted text-muted-foreground',
} as const

const readerStatusColors = {
  ACTIVE: 'bg-primary/20 text-primary',
  REVOKED: 'bg-destructive/20 text-destructive',
} as const

function formatDateTime(value?: string) {
  return value ? formatLocaleDateTime(value) : '-'
}

export function RfidReadersPage() {
  const { raceId } = useParams()
  const { t } = useTranslation()
  const { user } = useAuth()
  const canManageTiming = isAdminRole(user?.role)
  const [race, setRace] = useState<Race | null>(null)
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([])
  const [readers, setReaders] = useState<RfidReader[]>([])
  const [overview, setOverview] = useState<RfidRaceOverview | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [createdReader, setCreatedReader] = useState<RfidReaderCreateResponse | null>(null)
  const [createForm, setCreateForm] = useState({ checkpointId: '', name: '', externalId: '' })

  const checkpointNames = useMemo(
    () => new Map(checkpoints.map((checkpoint) => [checkpoint.id, checkpoint.name])),
    [checkpoints],
  )

  const load = async () => {
    if (!raceId) {
      return
    }
    setIsLoading(true)
    try {
      const [raceData, checkpointsData, readersData, overviewData] = await Promise.all([
        racesApi.get(raceId),
        checkpointsApi.list(raceId),
        rfidApi.listReaders(raceId),
        rfidApi.getOverview(raceId),
      ])
      setRace(raceData)
      setCheckpoints(checkpointsData)
      setReaders(readersData)
      setOverview(overviewData)
    } catch {
      toast.error(t('rfid:ui.failedToLoadRfidMonitoring'))
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [raceId])

  const rfidCheckpoints = useMemo(
    () => checkpoints.filter((checkpoint) => checkpoint.hasRfidReader),
    [checkpoints],
  )

  const openCreate = () => {
    setCreatedReader(null)
    setCreateForm({
      checkpointId: rfidCheckpoints[0]?.id ?? '',
      name: '',
      externalId: '',
    })
    setCreateOpen(true)
  }

  const handleCreate = async () => {
    if (!raceId) return
    if (!createForm.checkpointId || !createForm.name.trim() || !createForm.externalId.trim()) {
      toast.error(t('rfid:ui.checkpointNameAndExternalIdAre'))
      return
    }
    setCreating(true)
    try {
      const created = await rfidApi.createReader(raceId, {
        checkpointId: createForm.checkpointId,
        name: createForm.name.trim(),
        externalId: createForm.externalId.trim(),
      })
      setCreatedReader(created)
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('rfid:toasts.createFailed'))
    } finally {
      setCreating(false)
    }
  }

  const handleRotateKey = async (reader: RfidReader) => {
    if (!raceId || !window.confirm(t('rfid:keys.rotateConfirm', { name: reader.name }))) return
    try {
      const rotated = await rfidApi.rotateReaderKey(raceId, reader.id)
      setCreatedReader(rotated)
      setCreateOpen(true)
      toast.success(t('rfid:keys.rotated', { name: reader.name }))
      await load()
    } catch {
      toast.error(t('rfid:keys.rotateFailed'))
    }
  }

  const handleRevoke = async (reader: RfidReader) => {
    if (!raceId) return
    try {
      await rfidApi.revokeReader(raceId, reader.id)
      toast.success(t('rfid:ui.readerRevoked'))
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('rfid:toasts.revokeFailed'))
    }
  }

  const cards = [
    {
      title: t('rfid:ui.readers'),
      value: overview?.totalReaders ?? 0,
      detail: t('rfid:overview.readerHealth', { online: overview?.onlineReaders ?? 0, degraded: overview?.degradedReaders ?? 0, offline: overview?.offlineReaders ?? 0 }),
      icon: <Radio className="h-4 w-4 text-primary" />,
    },
    {
      title: t('rfid:ui.queueDepth'),
      value: overview?.queueDepth ?? 0,
      detail: t('rfid:overview.silenceTimeout', { seconds: overview?.readerSilenceTimeoutSeconds ?? 0 }),
      icon: <Clock3 className="h-4 w-4 text-warning-foreground" />,
    },
    {
      title: t('rfid:ui.duplicateRate'),
      value: t('rfid:overview.percent', { value: formatNumber(overview?.duplicateRate ?? 0, { minimumFractionDigits: 1, maximumFractionDigits: 1 }) }),
      detail: t('rfid:overview.duplicates', { count: overview?.duplicateCount ?? 0 }),
      icon: <Activity className="h-4 w-4 text-live" />,
    },
    {
      title: t('rfid:ui.unknownTags'),
      value: t('rfid:overview.percent', { value: formatNumber(overview?.unknownCredentialRate ?? 0, { minimumFractionDigits: 1, maximumFractionDigits: 1 }) }),
      detail: t('rfid:overview.unmatched', { count: overview?.unknownCredentialCount ?? 0 }),
      icon: <AlertTriangle className="h-4 w-4 text-destructive" />,
    },
    {
      title: t('rfid:ui.ingressLag'),
      value: overview?.averageIngressLagMillis != null ? t('rfid:overview.milliseconds', { value: formatNumber(overview.averageIngressLagMillis) }) : '-',
      detail: t('rfid:overview.oldestQueued', { seconds: overview?.oldestQueuedReadAgeSeconds ?? 0 }),
      icon: <Cpu className="h-4 w-4 text-muted-foreground" />,
    },
  ]

  if (isLoading && !race) {
    return <div className="p-6">{t('rfid:ui.loadingRfidMonitoring')}</div>
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{t('rfid:ui.rfidMonitoring')}</h1>
          <p className="text-sm text-muted-foreground">
            {t('rfid:overviewDescription', { race: race?.name ?? t('rfid:raceFallback') })}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => void load()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            {t('rfid:ui.refresh')}
          </Button>
          <Button onClick={openCreate} disabled={rfidCheckpoints.length === 0}>
            <Plus className="mr-2 h-4 w-4" />
            {t('rfid:ui.newReader')}
          </Button>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {cards.map((card) => (
          <Card key={card.title}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">{card.title}</CardTitle>
              {card.icon}
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{card.value}</div>
              <p className="mt-1 text-xs text-muted-foreground">{card.detail}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {raceId && <RfidTimingPanel raceId={raceId} canManage={canManageTiming} />}

      {raceId && <WavesPanel raceId={raceId} canManage={canManageTiming} />}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('rfid:ui.registeredReaders')}</CardTitle>
          <CardDescription>{t('rfid:ui.perCheckpointReaderStatusForThis')}</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('rfid:ui.name')}</TableHead>
                <TableHead>{t('rfid:ui.checkpoint')}</TableHead>
                <TableHead>{t('rfid:ui.profile')}</TableHead>
                <TableHead>{t('rfid:ui.gateway')}</TableHead>
                <TableHead>{t('rfid:ui.backlog')}</TableHead>
                <TableHead>{t('rfid:ui.lastSeen')}</TableHead>
                <TableHead>{t('rfid:ui.lastProcessed')}</TableHead>
                <TableHead>{t('rfid:ui.status')}</TableHead>
                <TableHead className="text-right">{t('rfid:ui.actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {readers.map((reader) => (
                <TableRow key={reader.id}>
                  <TableCell className="font-medium">
                    <div>{reader.name}</div>
                    <div className="text-xs text-muted-foreground">{reader.externalId}</div>
                  </TableCell>
                  <TableCell>{reader.checkpointId ? checkpointNames.get(reader.checkpointId) ?? reader.checkpointId : '-'}</TableCell>
                  <TableCell>
                    <div className="text-sm">{reader.hardwareProfile ?? '-'}</div>
                    <div className="text-xs text-muted-foreground">{reader.readerClass ?? '-'}</div>
                  </TableCell>
                  <TableCell>
                    <div>{reader.gatewayName ?? '-'}</div>
                    <div className="text-xs text-muted-foreground">{reader.hardwareModel ?? reader.firmwareVersion ?? '-'}</div>
                  </TableCell>
                  <TableCell>{reader.gatewayBacklog ?? 0}</TableCell>
                  <TableCell>
                    <div>{formatDateTime(reader.lastSeenAt || reader.lastHeartbeatAt)}</div>
                    {reader.clockOffsetMs != null && (
                      <div className={clockOffsetWarning(reader.clockOffsetMs) ? 'text-xs text-destructive' : 'text-xs text-muted-foreground'}>
                        {clockOffsetWarning(reader.clockOffsetMs)
                          ? t('rfid:timing.clockOffsetWarning', { offset: reader.clockOffsetMs })
                          : t('rfid:timing.clockOffset', { offset: reader.clockOffsetMs })}
                      </div>
                    )}
                  </TableCell>
                  <TableCell>{formatDateTime(reader.lastProcessedAt)}</TableCell>
                  <TableCell className="space-y-1">
                    <div className="flex flex-wrap gap-2">
                      <Badge className={reader.status ? readerStatusColors[reader.status] : 'bg-muted text-muted-foreground'}>
                        {reader.status}
                      </Badge>
                      <Badge className={reader.gatewayStatus ? gatewayStatusColors[reader.gatewayStatus] : 'bg-muted text-muted-foreground'}>
                        <Wifi className="mr-1 h-3 w-3" />
                        {reader.gatewayStatus ?? 'UNKNOWN'}
                      </Badge>
                    </div>
                    <div className="text-xs text-destructive">{reader.lastError || ''}</div>
                  </TableCell>
                  <TableCell className="text-right">
                    {canManageTiming && reader.status !== 'REVOKED' && (
                      <Button variant="ghost" size="sm" onClick={() => void handleRotateKey(reader)}>
                        {t('rfid:keys.rotate')}
                      </Button>
                    )}
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => void handleRevoke(reader)}
                      disabled={reader.status === 'REVOKED'}
                    >
                      {t('rfid:ui.revoke')}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {readers.length === 0 && (
                <TableRow>
                  <TableCell colSpan={9} className="text-sm text-muted-foreground">
                    {t('rfid:ui.noRfidReadersRegisteredForThis')}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('rfid:ui.newRfidReader')}</DialogTitle>
            <DialogDescription>
              {t('rfid:ui.registerAnEdgeReaderForA')}
            </DialogDescription>
          </DialogHeader>

          {createdReader ? (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">
                {t('rfid:readerLabel')} <span className="font-medium text-foreground">{createdReader.name}</span> {t('rfid:ui.wasCreatedCopyTheApiKey')}
              </p>
              <div className="flex items-center gap-2 rounded-md border border-border bg-muted/40 p-3">
                <KeyRound className="h-4 w-4 text-primary" />
                <code className="flex-1 break-all font-mono text-sm">{createdReader.apiKey}</code>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    void navigator.clipboard.writeText(createdReader.apiKey)
                    toast.success(t('rfid:ui.apiKeyCopied'))
                  }}
                >
                  {t('rfid:ui.copy')}
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="reader-checkpoint">{t('rfid:ui.checkpoint')}</Label>
                <Select
                  value={createForm.checkpointId}
                  onValueChange={(value) => setCreateForm((form) => ({ ...form, checkpointId: value }))}
                >
                  <SelectTrigger id="reader-checkpoint">
                    <SelectValue placeholder={t('rfid:ui.selectACheckpoint')} />
                  </SelectTrigger>
                  <SelectContent>
                    {rfidCheckpoints.map((checkpoint) => (
                      <SelectItem key={checkpoint.id} value={checkpoint.id}>
                        {checkpoint.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="reader-name">{t('rfid:ui.name')}</Label>
                <Input
                  id="reader-name"
                  value={createForm.name}
                  onChange={(e) => setCreateForm((form) => ({ ...form, name: e.target.value }))}
                  placeholder={t('rfid:ui.finishLineReader')}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="reader-external">{t('rfid:ui.externalId')}</Label>
                <Input
                  id="reader-external"
                  value={createForm.externalId}
                  onChange={(e) => setCreateForm((form) => ({ ...form, externalId: e.target.value }))}
                  placeholder={t('rfid:ui.reader01')}
                />
              </div>
            </div>
          )}

          <DialogFooter>
            {createdReader ? (
              <Button onClick={() => setCreateOpen(false)}>{t('rfid:ui.done')}</Button>
            ) : (
              <>
                <Button variant="outline" onClick={() => setCreateOpen(false)} disabled={creating}>
                  {t('rfid:ui.cancel')}
                </Button>
                <Button onClick={() => void handleCreate()} disabled={creating}>
                  {creating ? t('rfid:creating') : t('rfid:createReader')}
                </Button>
              </>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
