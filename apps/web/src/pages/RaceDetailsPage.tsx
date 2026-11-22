import { useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Ban, Flag, Users, MapPin, Timer, Trophy, Calendar, Play, Pause, CheckCircle, Pencil } from 'lucide-react'
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
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { toast } from 'sonner'
import { formatNumber, formatDate } from '@/lib/format'

const statusColors: Record<RaceStatus, string> = {
  DRAFT: 'bg-muted text-muted-foreground',
  REGISTRATION_OPEN: 'bg-primary/20 text-primary',
  REGISTRATION_CLOSED: 'bg-warning/20 text-warning-foreground',
  IN_PROGRESS: 'bg-live/20 text-live',
  COMPLETED: 'bg-success/20 text-success',
  CANCELLED: 'bg-destructive/20 text-destructive',
}

const statusActions: Record<RaceStatus, { label: string; nextStatus: RaceStatus; icon: React.ReactNode }[]> = {
  DRAFT: [{ label: 'raceDetails:ui.openRegistration', nextStatus: 'REGISTRATION_OPEN', icon: <Play className="h-4 w-4" /> }],
  REGISTRATION_OPEN: [{ label: 'raceDetails:ui.closeRegistration', nextStatus: 'REGISTRATION_CLOSED', icon: <Pause className="h-4 w-4" /> }],
  REGISTRATION_CLOSED: [{ label: 'raceDetails:ui.startRace', nextStatus: 'IN_PROGRESS', icon: <Play className="h-4 w-4" /> }],
  IN_PROGRESS: [{ label: 'raceDetails:ui.completeRace', nextStatus: 'COMPLETED', icon: <CheckCircle className="h-4 w-4" /> }],
  COMPLETED: [],
  CANCELLED: [],
}

export function RaceDetailPage() {
  const { raceId } = useParams()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { user } = useAuth()
  const canManageRace = isAdminRole(user?.role)

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
      toast.error(t('raceDetails:ui.failedToLoadRaceDetails'))
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

  const handleStatusChange = async (status: RaceStatus, reason?: string) => {
    if (!raceId) return
    try {
      await racesApi.updateStatus(raceId, status, reason)
      toast.success(t('raceDetails:ui.raceStatusUpdated'))
      await load()
    } catch {
      toast.error(t('raceDetails:ui.failedToUpdateRaceStatus'))
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
      toast.error(t('raceDetails:ui.raceNameIsRequired'))
      return
    }
    if (editForm.requireWaiver && !editForm.waiverText.trim()) {
      toast.error(t('raceDetails:ui.waiverTextIsRequiredWhenA'))
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
      toast.success(t('raceDetails:ui.raceUpdated'))
      setEditOpen(false)
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('raceDetails:toasts.updateFailed'))
    } finally {
      setSaving(false)
    }
  }

  if (!race) {
    return <div className="p-6">{t('raceDetails:ui.loadingRace')}</div>
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
                <span>{formatDate(race.raceDate)}</span>
              </div>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Badge className={statusColors[race.status]}>{race.status.replace('_', ' ')}</Badge>
          {canManageRace && statusActions[race.status]?.map((action) => (
              <Button key={action.nextStatus} variant="outline" onClick={() => void handleStatusChange(action.nextStatus)}>
                {action.icon}
                <span className="ml-2">{t(action.label)}</span>
              </Button>
            ))}
          {canManageRace && race.status !== 'CANCELLED' && race.status !== 'COMPLETED' && (
            <Button variant="outline" className="text-destructive" onClick={() => {
              const reason = window.prompt(t('raceDetails:cancelReasonPrompt'))
              if (reason !== null) void handleStatusChange('CANCELLED', reason)
            }}>
              <Ban className="mr-2 h-4 w-4" />
              {t('raceDetails:ui.cancelRace')}
            </Button>
          )}
          {canManageRace && (
            <Button variant="outline" onClick={openEdit}>
              <Pencil className="mr-2 h-4 w-4" />
              {t('raceDetails:ui.edit')}
            </Button>
          )}
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="cursor-pointer" onClick={() => navigate(`/admin/races/${raceId}/participants`)}>
          <CardHeader className="flex flex-row items-center justify-between pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('raceDetails:ui.participants')}</CardTitle><Users className="h-4 w-4 text-muted-foreground" /></CardHeader>
          <CardContent><div className="text-2xl font-bold">{formatNumber(participants.length)}</div><Progress value={registrationProgress} className="mt-2 h-1" /></CardContent>
        </Card>

        <Card className="cursor-pointer" onClick={() => navigate(`/admin/races/${raceId}/live`)}>
          <CardHeader className="flex flex-row items-center justify-between pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('raceDetails:ui.liveTracking')}</CardTitle><MapPin className="h-4 w-4 text-live" /></CardHeader>
          <CardContent><div className="text-2xl font-bold">{formatNumber(live.length)}</div><p className="text-xs text-muted-foreground">{t('raceDetails:ui.athletesOnCourse')}</p></CardContent>
        </Card>

        <Card className="cursor-pointer" onClick={() => navigate(`/admin/races/${raceId}/checkpoints`)}>
          <CardHeader className="flex flex-row items-center justify-between pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('raceDetails:ui.checkpoints')}</CardTitle><Timer className="h-4 w-4 text-muted-foreground" /></CardHeader>
          <CardContent><div className="text-2xl font-bold">{checkpoints.length}</div><p className="text-xs text-muted-foreground">{t('raceDetails:ui.configuredTimingPoints')}</p></CardContent>
        </Card>

        <Card className="cursor-pointer" onClick={() => navigate(`/admin/races/${raceId}/rfid`)}>
          <CardHeader className="flex flex-row items-center justify-between pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('tags:openTimingPage')}</CardTitle><Timer className="h-4 w-4 text-muted-foreground" /></CardHeader>
          <CardContent>
            <Button variant="link" className="h-auto p-0 text-sm" onClick={(event) => { event.stopPropagation(); navigate(`/admin/races/${raceId}/tags`) }}>
              {t('tags:openTagPage')}
            </Button>
          </CardContent>
        </Card>

        <Card className="cursor-pointer" onClick={() => navigate(`/admin/races/${raceId}/results`)}>
          <CardHeader className="flex flex-row items-center justify-between pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('raceDetails:ui.finished')}</CardTitle><Trophy className="h-4 w-4 text-primary" /></CardHeader>
          <CardContent><div className="text-2xl font-bold">{formatNumber(byStatus.finished)}</div><Progress value={finishProgress} className="mt-2 h-1" /></CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('raceDetails:ui.raceSnapshot')}</CardTitle>
          <CardDescription>{t('raceDetails:ui.operationalStatusAndSummary')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.registered')}</span><span>{participants.length}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.checkedIn')}</span><span>{byStatus.checkedIn}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.started')}</span><span>{byStatus.started}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.officialResults')}</span><span>{results.filter((r) => r.isOfficial).length}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.distance')}</span><span>{race.distanceKm != null ? t('raceDetails:distanceKm', { value: formatNumber(race.distanceKm, { maximumFractionDigits: 1 }) }) : '-'}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.maxParticipants')}</span><span>{race.maxParticipants ?? '-'}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.timezone')}</span><span>{race.timezone ?? 'UTC'}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.earlyBird')}</span><span>{race.earlyBirdFee ?? '-'} {race.currency}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.teamFee')}</span><span>{race.teamFee ?? '-'} {race.currency}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.bibPrefix')}</span><span>{race.bibPrefix || '-'}</span></div>
          <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.medalPlaces')}</span><span>{race.medalPlaces ?? 3}</span></div>
          {race.cancellationReason && <div className="flex justify-between text-sm"><span className="text-muted-foreground">{t('raceDetails:ui.cancellation')}</span><span>{race.cancellationReason}</span></div>}
        </CardContent>
      </Card>

      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('raceDetails:ui.editRace')}</DialogTitle>
            <DialogDescription>{t('raceDetails:ui.updateTheRaceConfiguration')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4">
            <div className="space-y-2">
              <Label htmlFor="race-name">{t('raceDetails:ui.name')}</Label>
              <Input id="race-name" value={editForm.name} onChange={(e) => setEditForm((f) => ({ ...f, name: e.target.value }))} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="race-description">{t('raceDetails:ui.description')}</Label>
              <Input id="race-description" value={editForm.description} onChange={(e) => setEditForm((f) => ({ ...f, description: e.target.value }))} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="race-city">{t('raceDetails:ui.city')}</Label>
                <Input id="race-city" value={editForm.city} onChange={(e) => setEditForm((f) => ({ ...f, city: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="race-country">{t('raceDetails:ui.country')}</Label>
                <Input id="race-country" value={editForm.country} onChange={(e) => setEditForm((f) => ({ ...f, country: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="race-date">{t('raceDetails:ui.raceDate')}</Label>
                <Input id="race-date" type="date" value={editForm.raceDate} onChange={(e) => setEditForm((f) => ({ ...f, raceDate: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="race-start">{t('raceDetails:ui.startTime')}</Label>
                <Input id="race-start" type="time" value={editForm.startTime} onChange={(e) => setEditForm((f) => ({ ...f, startTime: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="race-distance">{t('raceDetails:ui.distanceKm')}</Label>
                <Input id="race-distance" type="number" step="0.1" value={editForm.distanceKm} onChange={(e) => setEditForm((f) => ({ ...f, distanceKm: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="race-max">{t('raceDetails:ui.maxParticipants2')}</Label>
                <Input id="race-max" type="number" value={editForm.maxParticipants} onChange={(e) => setEditForm((f) => ({ ...f, maxParticipants: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="race-fee">{t('raceDetails:ui.registrationFee')}</Label>
                <Input id="race-fee" type="number" step="0.01" value={editForm.registrationFee} onChange={(e) => setEditForm((f) => ({ ...f, registrationFee: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="race-currency">{t('raceDetails:ui.currency')}</Label>
                <Input id="race-currency" maxLength={3} value={editForm.currency} onChange={(e) => setEditForm((f) => ({ ...f, currency: e.target.value.toUpperCase() }))} />
              </div>
            </div>
            <div className="space-y-3 rounded-md border border-border p-3">
              <div className="flex items-center justify-between">
                <Label htmlFor="race-public">{t('raceDetails:ui.publicRegistration')}</Label>
                <Switch id="race-public" checked={editForm.isPublicRegistration} onCheckedChange={(v) => setEditForm((f) => ({ ...f, isPublicRegistration: v }))} />
              </div>
              <div className="flex items-center justify-between">
                <Label htmlFor="race-approval">{t('raceDetails:ui.requireApproval')}</Label>
                <Switch id="race-approval" checked={editForm.requireApproval} onCheckedChange={(v) => setEditForm((f) => ({ ...f, requireApproval: v }))} />
              </div>
              <div className="flex items-center justify-between">
                <Label htmlFor="race-waitlist">{t('raceDetails:ui.waitlistEnabled')}</Label>
                <Switch id="race-waitlist" checked={editForm.waitlistEnabled} onCheckedChange={(v) => setEditForm((f) => ({ ...f, waitlistEnabled: v }))} />
              </div>
              <div className="flex items-center justify-between">
                <Label htmlFor="race-waiver">{t('raceDetails:ui.requireWaiver')}</Label>
                <Switch id="race-waiver" checked={editForm.requireWaiver} onCheckedChange={(v) => setEditForm((f) => ({ ...f, requireWaiver: v }))} />
              </div>
              {editForm.requireWaiver && (
                <div className="space-y-2">
                  <Label htmlFor="race-waiver-text">{t('raceDetails:ui.waiverText')}</Label>
                  <Input id="race-waiver-text" value={editForm.waiverText} onChange={(e) => setEditForm((f) => ({ ...f, waiverText: e.target.value }))} />
                </div>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)} disabled={saving}>
              {t('raceDetails:ui.cancel')}
            </Button>
            <Button onClick={() => void handleSaveRace()} disabled={saving}>
              {saving ? t('raceDetails:saving') : t('raceDetails:saveChanges')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
