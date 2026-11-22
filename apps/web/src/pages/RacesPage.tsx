import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  Plus,
  Search,
  Filter,
  Flag,
  Users,
  Calendar,
  MapPin,
  MoreHorizontal,
  Eye,
  Trash2,
  Copy,
  Archive,
  ArchiveRestore,
  RefreshCcw,
  AlertCircle,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { racesApi } from '@/lib/api'
import type { Race, RaceType, RaceStatus } from '@/types'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { toast } from 'sonner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { getRaceNavigationForRole } from '@/config/adminNavigation'
import { formatNumber, formatDate } from '@/lib/format'

const statusColors: Record<RaceStatus, string> = {
  DRAFT: 'bg-muted text-muted-foreground',
  REGISTRATION_OPEN: 'bg-primary/20 text-primary',
  REGISTRATION_CLOSED: 'bg-warning/20 text-warning-foreground',
  IN_PROGRESS: 'bg-live/20 text-live',
  COMPLETED: 'bg-success/20 text-success',
  CANCELLED: 'bg-destructive/20 text-destructive',
}

const RACE_TYPES: RaceType[] = ['MARATHON', 'HALF_MARATHON', 'ULTRA_MARATHON', '10K', '5K', 'TRAIL', 'CYCLING', 'TRIATHLON', 'OTHER']

export function RacesPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { user } = useAuth()
  const canManageRaces = isAdminRole(user?.role)
  const [races, setRaces] = useState<Race[]>([])
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isCloneDialogOpen, setIsCloneDialogOpen] = useState(false)
  const [cloneSource, setCloneSource] = useState<Race | null>(null)
  const [cloneName, setCloneName] = useState('')
  const [cloneDate, setCloneDate] = useState('')
  const [showArchived, setShowArchived] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [hasLoadError, setHasLoadError] = useState(false)
  const [newRace, setNewRace] = useState({
    name: '',
    description: '',
    raceType: 'MARATHON' as RaceType,
    city: '',
    country: '',
    raceDate: '',
    distanceKm: '',
    maxParticipants: '',
    timezone: 'UTC',
    registrationFee: '',
    earlyBirdFee: '',
    earlyBirdEndsAt: '',
    teamFee: '',
    bibPrefix: '',
    bibWidth: '4',
    medalPlaces: '3',
    posterUrl: '',
    bibTemplate: 'CLASSIC',
    bibAccentColor: '#0F766E',
  })

  const loadRaces = async () => {
    setIsLoading(true)
    setHasLoadError(false)
    try {
      const data = await racesApi.list(showArchived)
      setRaces(data)
    } catch {
      setHasLoadError(true)
      toast.error(t('admin:races.toasts.loadFailed'))
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    loadRaces()
  }, [showArchived])

  const filteredRaces = useMemo(
    () =>
      races.filter((race) => {
        const matchesSearch =
          race.name.toLowerCase().includes(search.toLowerCase()) ||
          race.city?.toLowerCase().includes(search.toLowerCase()) ||
          race.country?.toLowerCase().includes(search.toLowerCase())
        const matchesStatus = statusFilter === 'all' || race.status === statusFilter
        return matchesSearch && matchesStatus
      }),
    [races, search, statusFilter],
  )
  const visibleCapabilities = getRaceNavigationForRole(user?.role)
  const hasActiveFilters = Boolean(search.trim()) || statusFilter !== 'all' || showArchived
  const showEmptyCapabilityState = !isLoading && !hasLoadError && races.length === 0 && !hasActiveFilters

  const handleCreateRace = async () => {
    try {
      await racesApi.create({
        name: newRace.name,
        description: newRace.description,
        raceType: newRace.raceType,
        city: newRace.city,
        country: newRace.country,
        raceDate: newRace.raceDate,
        distanceKm: newRace.distanceKm ? Number(newRace.distanceKm) : undefined,
        maxParticipants: newRace.maxParticipants ? Number(newRace.maxParticipants) : undefined,
        timezone: newRace.timezone || 'UTC',
        registrationFee: newRace.registrationFee ? Number(newRace.registrationFee) : undefined,
        earlyBirdFee: newRace.earlyBirdFee ? Number(newRace.earlyBirdFee) : undefined,
        earlyBirdEndsAt: newRace.earlyBirdEndsAt ? new Date(newRace.earlyBirdEndsAt).toISOString() : undefined,
        teamFee: newRace.teamFee ? Number(newRace.teamFee) : undefined,
        bibPrefix: newRace.bibPrefix || undefined,
        bibWidth: newRace.bibWidth ? Number(newRace.bibWidth) : undefined,
        medalPlaces: newRace.medalPlaces ? Number(newRace.medalPlaces) : undefined,
        posterUrl: newRace.posterUrl || undefined,
        bibTemplate: newRace.bibTemplate || undefined,
        bibAccentColor: newRace.bibAccentColor || undefined,
      })
      toast.success(t('admin:races.toasts.created'))
      setIsCreateDialogOpen(false)
      setNewRace({
        name: '',
        description: '',
        raceType: 'MARATHON',
        city: '',
        country: '',
        raceDate: '',
        distanceKm: '',
        maxParticipants: '',
        timezone: 'UTC',
        registrationFee: '',
        earlyBirdFee: '',
        earlyBirdEndsAt: '',
        teamFee: '',
        bibPrefix: '',
        bibWidth: '4',
        medalPlaces: '3',
        posterUrl: '',
        bibTemplate: 'CLASSIC',
        bibAccentColor: '#0F766E',
      })
      await loadRaces()
    } catch {
      toast.error(t('admin:races.toasts.createFailed'))
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await racesApi.delete(id)
      toast.success(t('admin:races.toasts.deleted'))
      await loadRaces()
    } catch {
      toast.error(t('admin:races.toasts.deleteFailed'))
    }
  }

  const handleArchive = async (id: string) => {
    try {
      await racesApi.archive(id)
      toast.success(t('admin:races.toasts.archived'))
      await loadRaces()
    } catch {
      toast.error(t('admin:races.toasts.archiveFailed'))
    }
  }

  const handleRestore = async (id: string) => {
    try {
      await racesApi.restore(id)
      toast.success(t('admin:races.toasts.restored'))
      await loadRaces()
    } catch {
      toast.error(t('admin:races.toasts.restoreFailed'))
    }
  }

  const openCloneDialog = (race: Race) => {
    setCloneSource(race)
    setCloneName(`${race.name} (Copy)`)
    setCloneDate(race.raceDate ? race.raceDate.slice(0, 10) : '')
    setIsCloneDialogOpen(true)
  }

  const handleCloneRace = async () => {
    if (!cloneSource) return
    try {
      await racesApi.clone(cloneSource.id, {
        name: cloneName,
        raceDate: cloneDate || undefined,
      })
      toast.success(t('admin:races.toasts.cloned'))
      setIsCloneDialogOpen(false)
      setCloneSource(null)
      await loadRaces()
    } catch {
      toast.error(t('admin:races.toasts.cloneFailed'))
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{t(canManageRaces ? 'nav:races' : 'nav:operations')}</h1>
          <p className="text-muted-foreground">
            {t(canManageRaces ? 'nav:manageRacesDescription' : 'nav:operateRacesDescription')}
          </p>
        </div>
        {canManageRaces && (
          <Button onClick={() => setIsCreateDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            {t('admin:races.create')}
          </Button>
        )}
      </div>

      {hasLoadError && (
        <Alert variant="destructive">
          <AlertCircle />
          <AlertTitle>{t('nav:racesLoadErrorTitle')}</AlertTitle>
          <AlertDescription className="flex flex-wrap items-center gap-3">
            <span>{t('nav:racesLoadErrorDescription')}</span>
            <Button variant="outline" size="sm" onClick={() => void loadRaces()}>
              <RefreshCcw className="mr-2 h-4 w-4" />
              {t('nav:retry')}
            </Button>
          </AlertDescription>
        </Alert>
      )}

      <div className="flex flex-col gap-4 sm:flex-row">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder={t('admin:races.searchPlaceholder')}
              aria-label={t('admin:races.searchPlaceholder')}
            className="pl-10"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[180px]">
            <Filter className="mr-2 h-4 w-4" />
            <SelectValue placeholder={t('admin:races.filterStatus')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('admin:races.allStatuses')}</SelectItem>
            <SelectItem value="DRAFT">{t('admin:races.status.DRAFT')}</SelectItem>
            <SelectItem value="REGISTRATION_OPEN">{t('admin:races.status.REGISTRATION_OPEN')}</SelectItem>
            <SelectItem value="REGISTRATION_CLOSED">{t('admin:races.status.REGISTRATION_CLOSED')}</SelectItem>
            <SelectItem value="IN_PROGRESS">{t('admin:races.status.IN_PROGRESS')}</SelectItem>
            <SelectItem value="COMPLETED">{t('admin:races.status.COMPLETED')}</SelectItem>
            <SelectItem value="CANCELLED">{t('admin:races.status.CANCELLED')}</SelectItem>
          </SelectContent>
        </Select>
        <Button
          variant={showArchived ? 'default' : 'outline'}
          onClick={() => setShowArchived((value) => !value)}
        >
          <Archive className="mr-2 h-4 w-4" />
          {showArchived ? t('admin:races.hideArchived') : t('admin:races.showArchived')}
        </Button>
      </div>

      {hasLoadError ? null : isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label={t('nav:loadingRaces')}>
          {[0, 1, 2].map((item) => (
            <div key={item} className="h-44 animate-pulse rounded-xl border bg-muted/40" />
          ))}
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filteredRaces.map((race) => (
          <Card
            key={race.id}
            className="group cursor-pointer transition-all hover:border-primary/50"
            onClick={() => navigate(`/admin/races/${race.id}`)}
          >
            <CardHeader className="pb-3">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                    <Flag className="h-5 w-5 text-primary" />
                  </div>
                  <div>
                    <CardTitle className="text-base line-clamp-1">{race.name}</CardTitle>
                    <CardDescription className="text-xs">
                      {t('admin:races.typeAndDistance', { type: t(`admin:races.types.${race.raceType}`), distance: race.distanceKm != null ? formatNumber(race.distanceKm, { maximumFractionDigits: 1 }) : '-' })}
                    </CardDescription>
                  </div>
                </div>
                {canManageRaces && (
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                      <Button variant="ghost" size="icon" className="h-8 w-8 opacity-0 group-hover:opacity-100">
                        <MoreHorizontal className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={(e) => { e.stopPropagation(); navigate(`/admin/races/${race.id}`) }}>
                        <Eye className="mr-2 h-4 w-4" />
                        {t('admin:races.viewDetails')}
                      </DropdownMenuItem>
                      <DropdownMenuItem onClick={(e) => { e.stopPropagation(); openCloneDialog(race) }}>
                        <Copy className="mr-2 h-4 w-4" />
                        {t('admin:races.clone')}
                      </DropdownMenuItem>
                      {race.archivedAt ? (
                        <DropdownMenuItem onClick={(e) => { e.stopPropagation(); void handleRestore(race.id) }}>
                          <ArchiveRestore className="mr-2 h-4 w-4" />
                          {t('admin:races.restore')}
                        </DropdownMenuItem>
                      ) : (
                        <DropdownMenuItem onClick={(e) => { e.stopPropagation(); void handleArchive(race.id) }}>
                          <Archive className="mr-2 h-4 w-4" />
                          {t('admin:races.archive')}
                        </DropdownMenuItem>
                      )}
                      <DropdownMenuSeparator />
                      <DropdownMenuItem onClick={(e) => { e.stopPropagation(); void handleDelete(race.id) }} className="text-destructive">
                        <Trash2 className="mr-2 h-4 w-4" />
                        {t('admin:races.delete')}
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                )}
              </div>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex items-center gap-4 text-sm text-muted-foreground">
                <div className="flex items-center gap-1">
                  <MapPin className="h-3 w-3" />
                  <span>{race.city}, {race.country}</span>
                </div>
              </div>
              <div className="flex items-center gap-4 text-sm text-muted-foreground">
                <div className="flex items-center gap-1">
                  <Calendar className="h-3 w-3" />
                  <span>{formatDate(race.raceDate)}</span>
                </div>
                <div className="flex items-center gap-1">
                  <Users className="h-3 w-3" />
                  <span>{formatNumber(race.participantCount ?? 0)}</span>
                </div>
              </div>
              <div className="flex items-center justify-between pt-2">
                <Badge className={statusColors[race.status]}>{t(`admin:races.status.${race.status}`)}</Badge>
              </div>
            </CardContent>
          </Card>
          ))}
        </div>
      )}

      {showEmptyCapabilityState && (
        <Card className="border-dashed">
          <CardHeader>
            <CardTitle>{t('nav:noRacesTitle')}</CardTitle>
            <CardDescription>
              {canManageRaces ? t('nav:noRacesAdminDescription') : t('nav:noRacesOperationsDescription')}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <p className="text-sm font-medium">{t('nav:availableOperations')}</p>
              <p className="text-sm text-muted-foreground">{t('nav:availableOperationsDescription')}</p>
            </div>
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {visibleCapabilities.map((item) => (
                <div key={item.to} className="flex items-center gap-2 rounded-lg border bg-muted/20 px-3 py-2 text-sm">
                  <item.icon className="h-4 w-4 text-primary" />
                  <span>{t(item.labelKey)}</span>
                </div>
              ))}
            </div>
            {canManageRaces ? (
              <Button onClick={() => setIsCreateDialogOpen(true)}>
                <Plus className="mr-2 h-4 w-4" />
                {t('nav:createFirstRace')}
              </Button>
            ) : (
              <p className="text-sm text-muted-foreground">{t('nav:operationsAssignmentHint')}</p>
            )}
          </CardContent>
        </Card>
      )}

      {!isLoading && !hasLoadError && filteredRaces.length === 0 && !showEmptyCapabilityState && (
        <Card className="border-dashed">
          <CardContent className="py-10 text-center">
            <p className="font-medium">{t('nav:noMatchingRacesTitle')}</p>
            <p className="mt-1 text-sm text-muted-foreground">{t('nav:noMatchingRacesDescription')}</p>
          </CardContent>
        </Card>
      )}

      {canManageRaces && <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('admin:races.createTitle')}</DialogTitle>
            <DialogDescription>{t('admin:races.createDescription')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <div>
              <Label>{t('admin:races.fields.name')}</Label>
              <Input value={newRace.name} onChange={(e) => setNewRace((v) => ({ ...v, name: e.target.value }))} />
            </div>
            <div>
              <Label>{t('admin:races.fields.description')}</Label>
              <Textarea value={newRace.description} onChange={(e) => setNewRace((v) => ({ ...v, description: e.target.value }))} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>{t('admin:races.fields.type')}</Label>
                <Select value={newRace.raceType} onValueChange={(value) => setNewRace((v) => ({ ...v, raceType: value as RaceType }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {RACE_TYPES.map((type) => (
                      <SelectItem key={type} value={type}>{t(`admin:races.types.${type}`)}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label>{t('admin:races.fields.raceDate')}</Label>
                <Input type="date" value={newRace.raceDate} onChange={(e) => setNewRace((v) => ({ ...v, raceDate: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>{t('admin:races.fields.city')}</Label>
                <Input value={newRace.city} onChange={(e) => setNewRace((v) => ({ ...v, city: e.target.value }))} />
              </div>
              <div>
                <Label>{t('admin:races.fields.country')}</Label>
                <Input value={newRace.country} onChange={(e) => setNewRace((v) => ({ ...v, country: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>{t('admin:races.fields.distance')}</Label>
                <Input type="number" value={newRace.distanceKm} onChange={(e) => setNewRace((v) => ({ ...v, distanceKm: e.target.value }))} />
              </div>
              <div>
                <Label>{t('admin:races.fields.maxParticipants')}</Label>
                <Input type="number" value={newRace.maxParticipants} onChange={(e) => setNewRace((v) => ({ ...v, maxParticipants: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>{t('admin:races.fields.timezone')}</Label>
                <Input value={newRace.timezone} onChange={(e) => setNewRace((v) => ({ ...v, timezone: e.target.value }))} placeholder={t('admin:races.fields.timezonePlaceholder')} />
              </div>
              <div>
                <Label>{t('admin:races.fields.posterUrl')}</Label>
                <Input value={newRace.posterUrl} onChange={(e) => setNewRace((v) => ({ ...v, posterUrl: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div><Label>{t('admin:races.fields.registrationFee')}</Label><Input type="number" value={newRace.registrationFee} onChange={(e) => setNewRace((v) => ({ ...v, registrationFee: e.target.value }))} /></div>
              <div><Label>{t('admin:races.fields.earlyBirdFee')}</Label><Input type="number" value={newRace.earlyBirdFee} onChange={(e) => setNewRace((v) => ({ ...v, earlyBirdFee: e.target.value }))} /></div>
              <div><Label>{t('admin:races.fields.teamFee')}</Label><Input type="number" value={newRace.teamFee} onChange={(e) => setNewRace((v) => ({ ...v, teamFee: e.target.value }))} /></div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div><Label>{t('admin:races.fields.earlyBirdEnds')}</Label><Input type="datetime-local" value={newRace.earlyBirdEndsAt} onChange={(e) => setNewRace((v) => ({ ...v, earlyBirdEndsAt: e.target.value }))} /></div>
              <div><Label>{t('admin:races.fields.bibPrefix')}</Label><Input value={newRace.bibPrefix} onChange={(e) => setNewRace((v) => ({ ...v, bibPrefix: e.target.value }))} /></div>
            </div>
            <div className="grid grid-cols-4 gap-3">
              <div><Label>{t('admin:races.fields.bibWidth')}</Label><Input type="number" value={newRace.bibWidth} onChange={(e) => setNewRace((v) => ({ ...v, bibWidth: e.target.value }))} /></div>
              <div><Label>{t('admin:races.fields.medalPlaces')}</Label><Input type="number" value={newRace.medalPlaces} onChange={(e) => setNewRace((v) => ({ ...v, medalPlaces: e.target.value }))} /></div>
              <div><Label>{t('admin:races.fields.bibTemplate')}</Label><Input value={newRace.bibTemplate} onChange={(e) => setNewRace((v) => ({ ...v, bibTemplate: e.target.value }))} /></div>
              <div><Label>{t('admin:races.fields.bibColor')}</Label><Input type="color" value={newRace.bibAccentColor} onChange={(e) => setNewRace((v) => ({ ...v, bibAccentColor: e.target.value }))} /></div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCreateDialogOpen(false)}>{t('admin:races.cancel')}</Button>
            <Button onClick={() => void handleCreateRace()}>{t('admin:races.submitCreate')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>}

      {canManageRaces && <Dialog open={isCloneDialogOpen} onOpenChange={setIsCloneDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('admin:races.cloneTitle')}</DialogTitle>
            <DialogDescription>
              {t('admin:races.cloneDescription', { name: cloneSource?.name ?? t('admin:races.selectedRace') })}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <div>
              <Label>{t('admin:races.fields.newName')}</Label>
              <Input value={cloneName} onChange={(e) => setCloneName(e.target.value)} />
            </div>
            <div>
              <Label>{t('admin:races.fields.raceDate')}</Label>
              <Input type="date" value={cloneDate} onChange={(e) => setCloneDate(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCloneDialogOpen(false)}>{t('admin:races.cancel')}</Button>
            <Button onClick={() => void handleCloneRace()} disabled={!cloneName.trim()}>{t('admin:races.clone')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>}
    </div>
  )
}
