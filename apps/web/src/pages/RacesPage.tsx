import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
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
import { toast } from 'sonner'

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
  const [races, setRaces] = useState<Race[]>([])
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
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
    try {
      const data = await racesApi.list()
      setRaces(data)
    } catch {
      toast.error('Failed to load races')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    loadRaces()
  }, [])

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

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Races</h1>
          <p className="text-muted-foreground">Manage your race events</p>
        </div>
        <Button onClick={() => setIsCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Create Race
        </Button>
      </div>

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
      </div>

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
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onClick={(e) => { e.stopPropagation(); void handleDelete(race.id) }} className="text-destructive">
                      <Trash2 className="mr-2 h-4 w-4" />
                      Delete
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
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

      {!isLoading && filteredRaces.length === 0 && (
        <p className="text-sm text-muted-foreground">No races found.</p>
      )}

      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
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
      </Dialog>
    </div>
  )
}
