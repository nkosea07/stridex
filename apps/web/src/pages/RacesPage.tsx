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

const statusColors: Record<RaceStatus, string> = {
  DRAFT: 'bg-muted text-muted-foreground',
  REGISTRATION_OPEN: 'bg-primary/20 text-primary',
  REGISTRATION_CLOSED: 'bg-warning/20 text-warning-foreground',
  IN_PROGRESS: 'bg-live/20 text-live',
  COMPLETED: 'bg-success/20 text-success',
  CANCELLED: 'bg-destructive/20 text-destructive',
}

const raceTypeLabels: Record<RaceType, string> = {
  MARATHON: 'Marathon',
  HALF_MARATHON: 'Half Marathon',
  ULTRA_MARATHON: 'Ultra Marathon',
  '10K': '10K',
  '5K': '5K',
  TRAIL: 'Trail Run',
  CYCLING: 'Cycling',
  TRIATHLON: 'Triathlon',
  OTHER: 'Other',
}

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
  })

  const loadRaces = async () => {
    setIsLoading(true)
    setHasLoadError(false)
    try {
      const data = await racesApi.list(showArchived)
      setRaces(data)
    } catch {
      setHasLoadError(true)
      toast.error('Failed to load races')
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
      })
      toast.success('Race created')
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
      })
      await loadRaces()
    } catch {
      toast.error('Failed to create race')
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await racesApi.delete(id)
      toast.success('Race deleted')
      await loadRaces()
    } catch {
      toast.error('Failed to delete race')
    }
  }

  const handleArchive = async (id: string) => {
    try {
      await racesApi.archive(id)
      toast.success('Race archived')
      await loadRaces()
    } catch {
      toast.error('Failed to archive race')
    }
  }

  const handleRestore = async (id: string) => {
    try {
      await racesApi.restore(id)
      toast.success('Race restored')
      await loadRaces()
    } catch {
      toast.error('Failed to restore race')
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
      toast.success('Race cloned')
      setIsCloneDialogOpen(false)
      setCloneSource(null)
      await loadRaces()
    } catch {
      toast.error('Failed to clone race')
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
            Create Race
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
            placeholder="Search races..."
            className="pl-10"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <Select value={statusFilter} onValueChange={setStatusFilter}>
          <SelectTrigger className="w-[180px]">
            <Filter className="mr-2 h-4 w-4" />
            <SelectValue placeholder="Filter by status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Statuses</SelectItem>
            <SelectItem value="DRAFT">Draft</SelectItem>
            <SelectItem value="REGISTRATION_OPEN">Registration Open</SelectItem>
            <SelectItem value="REGISTRATION_CLOSED">Registration Closed</SelectItem>
            <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
            <SelectItem value="COMPLETED">Completed</SelectItem>
            <SelectItem value="CANCELLED">Cancelled</SelectItem>
          </SelectContent>
        </Select>
        <Button
          variant={showArchived ? 'default' : 'outline'}
          onClick={() => setShowArchived((value) => !value)}
        >
          <Archive className="mr-2 h-4 w-4" />
          {showArchived ? 'Hide Archived' : 'Show Archived'}
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
                      {raceTypeLabels[race.raceType]} - {race.distanceKm ?? '-'}km
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
                        View Details
                      </DropdownMenuItem>
                      <DropdownMenuItem onClick={(e) => { e.stopPropagation(); openCloneDialog(race) }}>
                        <Copy className="mr-2 h-4 w-4" />
                        Clone
                      </DropdownMenuItem>
                      {race.archivedAt ? (
                        <DropdownMenuItem onClick={(e) => { e.stopPropagation(); void handleRestore(race.id) }}>
                          <ArchiveRestore className="mr-2 h-4 w-4" />
                          Restore
                        </DropdownMenuItem>
                      ) : (
                        <DropdownMenuItem onClick={(e) => { e.stopPropagation(); void handleArchive(race.id) }}>
                          <Archive className="mr-2 h-4 w-4" />
                          Archive
                        </DropdownMenuItem>
                      )}
                      <DropdownMenuSeparator />
                      <DropdownMenuItem onClick={(e) => { e.stopPropagation(); void handleDelete(race.id) }} className="text-destructive">
                        <Trash2 className="mr-2 h-4 w-4" />
                        Delete
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
                  <span>{new Date(race.raceDate).toLocaleDateString()}</span>
                </div>
                <div className="flex items-center gap-1">
                  <Users className="h-3 w-3" />
                  <span>{race.participantCount?.toLocaleString() || 0}</span>
                </div>
              </div>
              <div className="flex items-center justify-between pt-2">
                <Badge className={statusColors[race.status]}>{race.status.replace('_', ' ')}</Badge>
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
            <DialogTitle>Create Race</DialogTitle>
            <DialogDescription>Add a new race event.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <div>
              <Label>Name</Label>
              <Input value={newRace.name} onChange={(e) => setNewRace((v) => ({ ...v, name: e.target.value }))} />
            </div>
            <div>
              <Label>Description</Label>
              <Textarea value={newRace.description} onChange={(e) => setNewRace((v) => ({ ...v, description: e.target.value }))} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>Type</Label>
                <Select value={newRace.raceType} onValueChange={(value) => setNewRace((v) => ({ ...v, raceType: value as RaceType }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {Object.keys(raceTypeLabels).map((type) => (
                      <SelectItem key={type} value={type}>{raceTypeLabels[type as RaceType]}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label>Race Date</Label>
                <Input type="date" value={newRace.raceDate} onChange={(e) => setNewRace((v) => ({ ...v, raceDate: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>City</Label>
                <Input value={newRace.city} onChange={(e) => setNewRace((v) => ({ ...v, city: e.target.value }))} />
              </div>
              <div>
                <Label>Country</Label>
                <Input value={newRace.country} onChange={(e) => setNewRace((v) => ({ ...v, country: e.target.value }))} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>Distance (km)</Label>
                <Input type="number" value={newRace.distanceKm} onChange={(e) => setNewRace((v) => ({ ...v, distanceKm: e.target.value }))} />
              </div>
              <div>
                <Label>Max Participants</Label>
                <Input type="number" value={newRace.maxParticipants} onChange={(e) => setNewRace((v) => ({ ...v, maxParticipants: e.target.value }))} />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCreateDialogOpen(false)}>Cancel</Button>
            <Button onClick={() => void handleCreateRace()}>Create</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>}

      {canManageRaces && <Dialog open={isCloneDialogOpen} onOpenChange={setIsCloneDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Clone Race</DialogTitle>
            <DialogDescription>
              Copy categories, checkpoints, and registration fields from {cloneSource?.name ?? 'the selected race'}.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <div>
              <Label>New Name</Label>
              <Input value={cloneName} onChange={(e) => setCloneName(e.target.value)} />
            </div>
            <div>
              <Label>Race Date</Label>
              <Input type="date" value={cloneDate} onChange={(e) => setCloneDate(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsCloneDialogOpen(false)}>Cancel</Button>
            <Button onClick={() => void handleCloneRace()} disabled={!cloneName.trim()}>Clone</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>}
    </div>
  )
}
