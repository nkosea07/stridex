import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Flag, Users, MapPin, Calendar, ArrowRight, Plus } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { dashboardApi, racesApi } from '@/lib/api'
import type { DashboardStats, Race } from '@/types'

const statusColors: Record<string, string> = {
  DRAFT: 'bg-muted text-muted-foreground',
  REGISTRATION_OPEN: 'bg-primary/20 text-primary',
  REGISTRATION_CLOSED: 'bg-warning/20 text-warning-foreground',
  IN_PROGRESS: 'bg-live/20 text-live',
  COMPLETED: 'bg-success/20 text-success',
  CANCELLED: 'bg-destructive/20 text-destructive',
}

export function DashboardPage() {
  const navigate = useNavigate()
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [recentRaces, setRecentRaces] = useState<Race[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let mounted = true
    const load = async () => {
      setIsLoading(true)
      setError(null)
      try {
        const [statsData, races] = await Promise.all([
          dashboardApi.getStats(),
          racesApi.list(),
        ])
        if (!mounted) return
        setStats(statsData)
        setRecentRaces(races.slice(0, 5))
      } catch {
        if (!mounted) return
        setError('Failed to load dashboard data')
      } finally {
        if (mounted) setIsLoading(false)
      }
    }
    load()
    return () => {
      mounted = false
    }
  }, [])

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-muted-foreground">Race operations overview</p>
        </div>
        <Button onClick={() => navigate('/admin/races')}>
          <Plus className="mr-2 h-4 w-4" />
          Manage Races
        </Button>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Total Races</CardTitle>
            <Flag className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{isLoading ? '--' : stats?.totalRaces ?? 0}</div>
            <p className="text-xs text-muted-foreground">{stats?.activeRaces ?? 0} active</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Participants</CardTitle>
            <Users className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{isLoading ? '--' : (stats?.totalParticipants ?? 0).toLocaleString()}</div>
            <p className="text-xs text-muted-foreground">across all races</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Live Tracking</CardTitle>
            <MapPin className="h-4 w-4 text-live" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{isLoading ? '--' : stats?.liveTracking ?? 0}</div>
            <p className="text-xs text-muted-foreground">athletes active in last 10 min</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Upcoming (30d)</CardTitle>
            <Calendar className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{isLoading ? '--' : stats?.upcomingRaces ?? 0}</div>
            <p className="text-xs text-muted-foreground">completed today: {stats?.completedToday ?? 0}</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle className="text-base">Recent Races</CardTitle>
            <CardDescription>Latest race events</CardDescription>
          </div>
          <Button variant="ghost" size="sm" onClick={() => navigate('/admin/races')}>
            View all
            <ArrowRight className="ml-2 h-4 w-4" />
          </Button>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {recentRaces.map((race) => (
              <div
                key={race.id}
                className="flex items-center justify-between rounded-lg border p-3 cursor-pointer"
                onClick={() => navigate(`/admin/races/${race.id}`)}
              >
                <div>
                  <p className="font-medium">{race.name}</p>
                  <p className="text-sm text-muted-foreground">
                    {race.city}, {race.country} - {race.distanceKm ?? '-'}km
                  </p>
                </div>
                <Badge className={statusColors[race.status] ?? statusColors.DRAFT}>
                  {race.status.replace('_', ' ')}
                </Badge>
              </div>
            ))}
            {!isLoading && recentRaces.length === 0 && (
              <p className="text-sm text-muted-foreground">No races found.</p>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
