import { useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Flag, Users, MapPin, Timer, Trophy, Calendar, Play, Pause, CheckCircle } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
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
    </div>
  )
}
