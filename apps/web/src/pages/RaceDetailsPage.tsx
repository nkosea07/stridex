import { useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Flag, Users, MapPin, Timer, Trophy, Calendar, Play, Pause, CheckCircle, Pencil } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { racesApi, participantsApi, checkpointsApi, resultsApi, trackingApi } from '@/lib/api'
import type { Race, RaceStatus, Participant, Checkpoint, RaceResult, LiveTrackingData } from '@/types'
import { toast } from 'sonner'

const statusColors: Record<RaceStatus, string> = {
  DRAFT: 'bg-muted text-muted-foreground',
  REGISTRATION_OPEN: 'bg-primary/20 text-primary',
  REGISTRATION_CLOSED: 'bg-warning/20 text-warning-foreground',
  IN_PROGRESS: 'bg-live/20 text-live',
  COMPLETED: 'bg-success/20 text-success',
  CANCELLED: 'bg-destructive/20 text-destructive',
}

const statusActions: Record<RaceStatus, { label: string; nextStatus: RaceStatus; icon: React.ReactNode }[]> = {
  DRAFT: [{ label: 'Open Registration', nextStatus: 'REGISTRATION_OPEN', icon: <Play className="h-4 w-4" /> }],
  REGISTRATION_OPEN: [{ label: 'Close Registration', nextStatus: 'REGISTRATION_CLOSED', icon: <Pause className="h-4 w-4" /> }],
  REGISTRATION_CLOSED: [{ label: 'Start Race', nextStatus: 'IN_PROGRESS', icon: <Play className="h-4 w-4" /> }],
  IN_PROGRESS: [{ label: 'Complete Race', nextStatus: 'COMPLETED', icon: <CheckCircle className="h-4 w-4" /> }],
  COMPLETED: [],
  CANCELLED: [],
}

export function RaceDetailPage() {
  const { raceId } = useParams()
  const navigate = useNavigate()

  const [race, setRace] = useState<Race | null>(null)
  const [participants, setParticipants] = useState<Participant[]>([])
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([])
  const [results, setResults] = useState<RaceResult[]>([])
  const [live, setLive] = useState<LiveTrackingData[]>([])

  const load = async () => {
    if (!raceId) return
    try {
      const [raceData, participantsData, checkpointsData, resultsData, liveData] = await Promise.all([
        racesApi.get(raceId),
        participantsApi.list(raceId),
        checkpointsApi.list(raceId),
        resultsApi.list(raceId),
        trackingApi.getLiveData(raceId),
      ])
      setRace(raceData)
      setParticipants(participantsData)
      setCheckpoints(checkpointsData)
      setResults(resultsData)
      setLive(liveData)
    } catch {
      toast.error('Failed to load race details')
    }
  }

  useEffect(() => {
    void load()
  }, [raceId])

  const byStatus = useMemo(() => {
    const finished = participants.filter((p) => p.status === 'FINISHED').length
    const started = participants.filter((p) => p.status === 'STARTED').length
    const checkedIn = participants.filter((p) => p.status === 'CHECKED_IN').length
    return { finished, started, checkedIn }
  }, [participants])

  const registrationProgress = race?.maxParticipants
    ? ((participants.length || 0) / race.maxParticipants) * 100
    : 0

  const finishProgress = participants.length
    ? (byStatus.finished / participants.length) * 100
    : 0

  const handleStatusChange = async (status: RaceStatus) => {
    if (!raceId) return
    try {
      await racesApi.updateStatus(raceId, status)
      toast.success('Race status updated')
      await load()
    } catch {
      toast.error('Failed to update race status')
    }
  }

  const [editOpen, setEditOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editForm, setEditForm] = useState({
    name: '',
    description: '',
    city: '',
    country: '',
    raceDate: '',
    startTime: '',
    distanceKm: '',
    maxParticipants: '',
    registrationFee: '',
    currency: 'USD',
    isPublicRegistration: true,
    requireApproval: false,
    waitlistEnabled: false,
    requireWaiver: false,
    waiverText: '',
  })

  const openEdit = () => {
    if (!race) return
    setEditForm({
      name: race.name ?? '',
      description: race.description ?? '',
      city: race.city ?? '',
      country: race.country ?? '',
      raceDate: race.raceDate ? race.raceDate.slice(0, 10) : '',
      startTime: race.startTime ?? '',
      distanceKm: race.distanceKm != null ? String(race.distanceKm) : '',
      maxParticipants: race.maxParticipants != null ? String(race.maxParticipants) : '',
      registrationFee: race.registrationFee != null ? String(race.registrationFee) : '',
      currency: race.currency ?? 'USD',
      isPublicRegistration: race.isPublicRegistration,
      requireApproval: race.requireApproval,
      waitlistEnabled: race.waitlistEnabled,
      requireWaiver: race.requireWaiver,
      waiverText: race.waiverText ?? '',
    })
    setEditOpen(true)
  }

  const handleSaveRace = async () => {
    if (!raceId) return
    if (!editForm.name.trim()) {
      toast.error('Race name is required')
      return
    }
    if (editForm.requireWaiver && !editForm.waiverText.trim()) {
      toast.error('Waiver text is required when a waiver is enforced')
      return
    }
    setSaving(true)
    try {
      await racesApi.update(raceId, {
        name: editForm.name.trim(),
        description: editForm.description.trim() || undefined,
        city: editForm.city.trim() || undefined,
        country: editForm.country.trim() || undefined,
        raceDate: editForm.raceDate,
        startTime: editForm.startTime || undefined,
        distanceKm: editForm.distanceKm ? Number(editForm.distanceKm) : undefined,
        maxParticipants: editForm.maxParticipants ? Number(editForm.maxParticipants) : undefined,
        registrationFee: editForm.registrationFee ? Number(editForm.registrationFee) : undefined,
        currency: editForm.currency || undefined,
        isPublicRegistration: editForm.isPublicRegistration,
        requireApproval: editForm.requireApproval,
        waitlistEnabled: editForm.waitlistEnabled,
        requireWaiver: editForm.requireWaiver,
        waiverText: editForm.waiverText.trim() || undefined,
      })
      toast.success('Race updated')
      setEditOpen(false)
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update race')
    } finally {
      setSaving(false)
    }
  }

  if (!race) {
    return <div className="p-6">Loading race...</div>
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
              <Flag className="h-6 w-6 text-primary" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">{race.name}</h1>
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <MapPin className="h-3 w-3" />
                <span>{race.city}, {race.country}</span>
                <span>-</span>
                <Calendar className="h-3 w-3" />
                <span>{new Date(race.raceDate).toLocaleDateString()}</span>
              </div>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Badge className={statusColors[race.status]}>{race.status.replace('_', ' ')}</Badge>
          {statusActions[race.status]?.map((action) => (
            <Button key={action.nextStatus} variant="outline" onClick={() => void handleStatusChange(action.nextStatus)}>
              {action.icon}
              <span className="ml-2">{action.label}</span>
            </Button>
          ))}
          <Button variant="outline" onClick={openEdit}>
            <Pencil className="mr-2 h-4 w-4" />
            Edit
          </Button>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="cursor-pointer" onClick={() => navigate(`/admin/races/${raceId}/participants`)}>
          <CardHeader className="flex flex-row items-center justify-between pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Participants</CardTitle><Users className="h-4 w-4 text-muted-foreground" /></CardHeader>
          <CardContent><div className="text-2xl font-bold">{participants.length.toLocaleString()}</div><Progress value={registrationProgress} className="mt-2 h-1" /></CardContent>
        </Card>

        <Card className="cursor-pointer" onClick={() => navigate(`/admin/races/${raceId}/live`)}>
          <CardHeader className="flex flex-row items-center justify-between pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Live Tracking</CardTitle><MapPin className="h-4 w-4 text-live" /></CardHeader>
          <CardContent><div className="text-2xl font-bold">{live.length.toLocaleString()}</div><p className="text-xs text-muted-foreground">Athletes on course</p></CardContent>
        </Card>

        <Card className="cursor-pointer" onClick={() => navigate(`/admin/races/${raceId}/checkpoints`)}>
          <CardHeader className="flex flex-row items-center justify-between pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Checkpoints</CardTitle><Timer className="h-4 w-4 text-muted-foreground" /></CardHeader>
          <CardContent><div className="text-2xl font-bold">{checkpoints.length}</div><p className="text-xs text-muted-foreground">Configured timing points</p></CardContent>
        </Card>

        <Card className="cursor-pointer" onClick={() => navigate(`/admin/races/${raceId}/results`)}>
          <CardHeader className="flex flex-row items-center justify-between pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Finished</CardTitle><Trophy className="h-4 w-4 text-primary" /></CardHeader>
          <CardContent><div className="text-2xl font-bold">{byStatus.finished.toLocaleString()}</div><Progress value={finishProgress} className="mt-2 h-1" /></CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Race Snapshot</CardTitle>
          <CardDescription>Operational status and summary</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">Registered</span><span>{participants.length}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">Checked In</span><span>{byStatus.checkedIn}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">Started</span><span>{byStatus.started}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">Official Results</span><span>{results.filter((r) => r.isOfficial).length}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">Distance</span><span>{race.distanceKm ?? '-'} km</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">Max Participants</span><span>{race.maxParticipants ?? '-'}</span></div>
        </CardContent>
      </Card>

      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Edit race</DialogTitle>
            <DialogDescription>Update the race configuration.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <div className="space-y-2">
              <Label htmlFor="race-name">Name</Label>
              <Input id="race-name" value={editForm.name} onChange={(e) => setEditForm((f) => ({ ...f, name: e.target.value }))} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="race-description">Description</Label>
              <Input id="race-description" value={editForm.description} onChange={(e) => setEditForm((f) => ({ ...f, description: e.target.value }))} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="race-city">City</Label>
                <Input id="race-city" value={editForm.city} onChange={(e) => setEditForm((f) => ({ ...f, city: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="race-country">Country</Label>
                <Input id="race-country" value={editForm.country} onChange={(e) => setEditForm((f) => ({ ...f, country: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="race-date">Race date</Label>
                <Input id="race-date" type="date" value={editForm.raceDate} onChange={(e) => setEditForm((f) => ({ ...f, raceDate: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="race-start">Start time</Label>
                <Input id="race-start" type="time" value={editForm.startTime} onChange={(e) => setEditForm((f) => ({ ...f, startTime: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="race-distance">Distance (km)</Label>
                <Input id="race-distance" type="number" step="0.1" value={editForm.distanceKm} onChange={(e) => setEditForm((f) => ({ ...f, distanceKm: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="race-max">Max participants</Label>
                <Input id="race-max" type="number" value={editForm.maxParticipants} onChange={(e) => setEditForm((f) => ({ ...f, maxParticipants: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="race-fee">Registration fee</Label>
                <Input id="race-fee" type="number" step="0.01" value={editForm.registrationFee} onChange={(e) => setEditForm((f) => ({ ...f, registrationFee: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="race-currency">Currency</Label>
                <Input id="race-currency" maxLength={3} value={editForm.currency} onChange={(e) => setEditForm((f) => ({ ...f, currency: e.target.value.toUpperCase() }))} />
              </div>
            </div>
            <div className="space-y-3 rounded-md border border-border p-3">
              <div className="flex items-center justify-between">
                <Label htmlFor="race-public">Public registration</Label>
                <Switch id="race-public" checked={editForm.isPublicRegistration} onCheckedChange={(v) => setEditForm((f) => ({ ...f, isPublicRegistration: v }))} />
              </div>
              <div className="flex items-center justify-between">
                <Label htmlFor="race-approval">Require approval</Label>
                <Switch id="race-approval" checked={editForm.requireApproval} onCheckedChange={(v) => setEditForm((f) => ({ ...f, requireApproval: v }))} />
              </div>
              <div className="flex items-center justify-between">
                <Label htmlFor="race-waitlist">Waitlist enabled</Label>
                <Switch id="race-waitlist" checked={editForm.waitlistEnabled} onCheckedChange={(v) => setEditForm((f) => ({ ...f, waitlistEnabled: v }))} />
              </div>
              <div className="flex items-center justify-between">
                <Label htmlFor="race-waiver">Require waiver</Label>
                <Switch id="race-waiver" checked={editForm.requireWaiver} onCheckedChange={(v) => setEditForm((f) => ({ ...f, requireWaiver: v }))} />
              </div>
              {editForm.requireWaiver && (
                <div className="space-y-2">
                  <Label htmlFor="race-waiver-text">Waiver text</Label>
                  <Input id="race-waiver-text" value={editForm.waiverText} onChange={(e) => setEditForm((f) => ({ ...f, waiverText: e.target.value }))} />
                </div>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={() => void handleSaveRace()} disabled={saving}>
              {saving ? 'Saving…' : 'Save changes'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
