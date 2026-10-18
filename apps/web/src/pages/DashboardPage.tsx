import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Flag, Users, MapPin, Calendar, ArrowRight, Plus, AlertCircle, RefreshCcw } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { dashboardApi, racesApi } from '@/lib/api'
import type { DashboardStats, Race } from '@/types'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

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
  const { t } = useTranslation()
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [recentRaces, setRecentRaces] = useState<Race[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [statsError, setStatsError] = useState(false)
  const [racesError, setRacesError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let mounted = true
    const load = async () => {
      setIsLoading(true)
      setStatsError(false)
      setRacesError(false)
      const [statsResult, racesResult] = await Promise.allSettled([
        dashboardApi.getStats(),
        racesApi.list(),
      ])
      if (!mounted) return
      if (statsResult.status === 'fulfilled') setStats(statsResult.value)
      else setStatsError(true)
      if (racesResult.status === 'fulfilled') setRecentRaces(racesResult.value.slice(0, 5))
      else setRacesError(true)
      setIsLoading(false)
    }
    load()
    return () => {
      mounted = false
    }
  }, [reloadKey])

  const hasLoadError = statsError || racesError
  const metricValue = (value: number | undefined) => {
    if (isLoading) return '--'
    if (statsError) return '—'
    return value ?? 0
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{t('nav:dashboard')}</h1>
          <p className="text-muted-foreground">Race operations overview</p>
        </div>
        <Button onClick={() => navigate('/admin/races')}>
          <Plus className="mr-2 h-4 w-4" />
          Manage Races
        </Button>
      </div>

      {hasLoadError && (
        <Alert variant="destructive">
          <AlertCircle />
          <AlertTitle>{t('nav:dashboardLoadErrorTitle')}</AlertTitle>
          <AlertDescription className="flex flex-wrap items-center gap-3">
            <span>{t('nav:dashboardLoadErrorDescription')}</span>
            <Button variant="outline" size="sm" onClick={() => setReloadKey((value) => value + 1)}>
              <RefreshCcw className="mr-2 h-4 w-4" />
              {t('nav:retry')}
            </Button>
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Total Races</CardTitle>
            <Flag className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{metricValue(stats?.totalRaces)}</div>
            <p className="text-xs text-muted-foreground">
              {statsError ? t('nav:dashboardStatsUnavailable') : `${stats?.activeRaces ?? 0} active`}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Participants</CardTitle>
            <Users className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {statsError ? '—' : isLoading ? '--' : (stats?.totalParticipants ?? 0).toLocaleString()}
            </div>
            <p className="text-xs text-muted-foreground">
              {statsError ? t('nav:dashboardStatsUnavailable') : 'across all races'}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Live Tracking</CardTitle>
            <MapPin className="h-4 w-4 text-live" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{metricValue(stats?.liveTracking)}</div>
            <p className="text-xs text-muted-foreground">
              {statsError ? t('nav:dashboardStatsUnavailable') : 'athletes active in last 10 min'}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Upcoming (30d)</CardTitle>
            <Calendar className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{metricValue(stats?.upcomingRaces)}</div>
            <p className="text-xs text-muted-foreground">
              {statsError ? t('nav:dashboardStatsUnavailable') : `completed today: ${stats?.completedToday ?? 0}`}
            </p>
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
            {!isLoading && !racesError && recentRaces.length === 0 && (
              <div className="rounded-lg border border-dashed p-6 text-center">
                <p className="font-medium">{t('nav:noDashboardRacesTitle')}</p>
                <p className="mt-1 text-sm text-muted-foreground">{t('nav:noDashboardRacesDescription')}</p>
                <Button className="mt-4" variant="outline" onClick={() => navigate('/admin/races')}>
                  <Plus className="mr-2 h-4 w-4" />
                  {t('nav:createFirstRace')}
                </Button>
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
