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
  return value ? new Date(value).toLocaleString() : '-'
}

export function RfidReadersPage() {
  const { raceId } = useParams()
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
      toast.error('Failed to load RFID monitoring')
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
      toast.error('Checkpoint, name, and external id are required')
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
      toast.error(error instanceof Error ? error.message : 'Failed to create reader')
    } finally {
      setCreating(false)
    }
  }

  const handleRevoke = async (reader: RfidReader) => {
    if (!raceId) return
    try {
      await rfidApi.revokeReader(raceId, reader.id)
      toast.success('Reader revoked')
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to revoke reader')
    }
  }

  const cards = [
    {
      title: 'Readers',
      value: overview?.totalReaders ?? 0,
      detail: `${overview?.onlineReaders ?? 0} online / ${overview?.degradedReaders ?? 0} degraded / ${overview?.offlineReaders ?? 0} offline`,
      icon: <Radio className="h-4 w-4 text-primary" />,
    },
    {
      title: 'Queue Depth',
      value: overview?.queueDepth ?? 0,
      detail: `silence timeout ${overview?.readerSilenceTimeoutSeconds ?? 0}s`,
      icon: <Clock3 className="h-4 w-4 text-warning-foreground" />,
    },
    {
      title: 'Duplicate Rate',
      value: `${(overview?.duplicateRate ?? 0).toFixed(1)}%`,
      detail: `${overview?.duplicateCount ?? 0} duplicate events`,
      icon: <Activity className="h-4 w-4 text-live" />,
    },
    {
      title: 'Unknown Tags',
      value: `${(overview?.unknownCredentialRate ?? 0).toFixed(1)}%`,
      detail: `${overview?.unknownCredentialCount ?? 0} unmatched reads`,
      icon: <AlertTriangle className="h-4 w-4 text-destructive" />,
    },
    {
      title: 'Ingress Lag',
      value: overview?.averageIngressLagMillis != null ? `${overview.averageIngressLagMillis} ms` : '-',
      detail: `oldest queued read ${overview?.oldestQueuedReadAgeSeconds ?? 0}s`,
      icon: <Cpu className="h-4 w-4 text-muted-foreground" />,
    },
  ]

  if (isLoading && !race) {
    return <div className="p-6">Loading RFID monitoring...</div>
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">RFID Monitoring</h1>
          <p className="text-sm text-muted-foreground">
            {race?.name ?? 'Race'} reader health, ingest backlog, and timing-event quality signals.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => void load()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            Refresh
          </Button>
          <Button onClick={openCreate} disabled={rfidCheckpoints.length === 0}>
            <Plus className="mr-2 h-4 w-4" />
            New reader
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

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Registered Readers</CardTitle>
          <CardDescription>Per-checkpoint reader status for this race.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Checkpoint</TableHead>
                <TableHead>Profile</TableHead>
                <TableHead>Gateway</TableHead>
                <TableHead>Backlog</TableHead>
                <TableHead>Last Seen</TableHead>
                <TableHead>Last Processed</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
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
                  <TableCell>{formatDateTime(reader.lastSeenAt || reader.lastHeartbeatAt)}</TableCell>
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
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => void handleRevoke(reader)}
                      disabled={reader.status === 'REVOKED'}
                    >
                      Revoke
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {readers.length === 0 && (
                <TableRow>
                  <TableCell colSpan={9} className="text-sm text-muted-foreground">
                    No RFID readers registered for this race.
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
            <DialogTitle>New RFID reader</DialogTitle>
            <DialogDescription>
              Register an edge reader for a checkpoint that has RFID enabled.
            </DialogDescription>
          </DialogHeader>

          {createdReader ? (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">
                Reader <span className="font-medium text-foreground">{createdReader.name}</span> was created. Copy the
                API key now — it is shown only once.
              </p>
              <div className="flex items-center gap-2 rounded-md border border-border bg-muted/40 p-3">
                <KeyRound className="h-4 w-4 text-primary" />
                <code className="flex-1 break-all font-mono text-sm">{createdReader.apiKey}</code>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    void navigator.clipboard.writeText(createdReader.apiKey)
                    toast.success('API key copied')
                  }}
                >
                  Copy
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="reader-checkpoint">Checkpoint</Label>
                <Select
                  value={createForm.checkpointId}
                  onValueChange={(value) => setCreateForm((form) => ({ ...form, checkpointId: value }))}
                >
                  <SelectTrigger id="reader-checkpoint">
                    <SelectValue placeholder="Select a checkpoint" />
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
                <Label htmlFor="reader-name">Name</Label>
                <Input
                  id="reader-name"
                  value={createForm.name}
                  onChange={(e) => setCreateForm((form) => ({ ...form, name: e.target.value }))}
                  placeholder="Finish line reader"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="reader-external">External id</Label>
                <Input
                  id="reader-external"
                  value={createForm.externalId}
                  onChange={(e) => setCreateForm((form) => ({ ...form, externalId: e.target.value }))}
                  placeholder="reader-01"
                />
              </div>
            </div>
          )}

          <DialogFooter>
            {createdReader ? (
              <Button onClick={() => setCreateOpen(false)}>Done</Button>
            ) : (
              <>
                <Button variant="outline" onClick={() => setCreateOpen(false)} disabled={creating}>
                  Cancel
                </Button>
                <Button onClick={() => void handleCreate()} disabled={creating}>
                  {creating ? 'Creating…' : 'Create reader'}
                </Button>
              </>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
