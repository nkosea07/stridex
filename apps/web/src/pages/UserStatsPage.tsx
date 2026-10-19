import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertCircle, MapPin, RefreshCcw, UserMinus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { favoritesApi, participantsApi, resultsApi } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { toast } from 'sonner'
import type { FavoriteAthlete, RaceResult } from '@/types'
import { getPreferredCapability, useAuth } from '@/contexts/AuthContext'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

type MyRegistration = {
  raceId: string
  status: string
}

type SessionSnapshot = {
  userId: string | null
  generation: number
}

export function UserStatsPage() {
  const { user, getSessionGeneration } = useAuth()
  const userId = user?.id ?? null
  const sessionGeneration = getSessionGeneration()
  const sessionRef = useRef<SessionSnapshot>({ userId, generation: sessionGeneration })
  sessionRef.current = { userId, generation: sessionGeneration }
  const { t } = useTranslation()
  const activeCapability = getPreferredCapability(user)
  const isAthleteMode = activeCapability === 'ATHLETE'
  const isSpectatorMode = activeCapability === 'SPECTATOR'
  const [registrations, setRegistrations] = useState<MyRegistration[]>([])
  const [results, setResults] = useState<RaceResult[]>([])
  const [favorites, setFavorites] = useState<FavoriteAthlete[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [hasLoadError, setHasLoadError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let mounted = true
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    setRegistrations([])
    setResults([])
    setFavorites([])
    setHasLoadError(false)
    const load = async () => {
      setIsLoading(true)
      try {
        const [myRegistrations, myResults, myFavorites] = await Promise.all([
          isAthleteMode ? participantsApi.myRegistrations() : Promise.resolve<MyRegistration[]>([]),
          isAthleteMode ? resultsApi.my() : Promise.resolve<RaceResult[]>([]),
          isSpectatorMode ? favoritesApi.list() : Promise.resolve<FavoriteAthlete[]>([]),
        ])
        if (!mounted || !isCurrentSession()) return
        setRegistrations(myRegistrations)
        setResults(myResults)
        setFavorites(myFavorites)
      } catch {
        if (!mounted || !isCurrentSession()) return
        setHasLoadError(true)
      } finally {
        if (mounted && isCurrentSession()) setIsLoading(false)
      }
    }
    void load()
    return () => {
      mounted = false
    }
  }, [isAthleteMode, isSpectatorMode, reloadKey, sessionGeneration, userId])

  const finishedCount = useMemo(
    () => results.filter((result) => result.status === 'finished').length,
    [results],
  )
  const bestOverall = useMemo(() => {
    const positions = results
      .map((result) => result.overallPosition)
      .filter((position): position is number => typeof position === 'number' && position > 0)
    return positions.length ? Math.min(...positions) : null
  }, [results])

  const handleUnfollow = async (favorite: FavoriteAthlete) => {
    if (!favorite.raceId) return
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    try {
      await favoritesApi.unfollow(favorite.raceId, favorite.participantId)
      if (!isCurrentSession()) return
      setFavorites((current) => current.filter((item) => item.participantId !== favorite.participantId))
      toast.success(t('nav:removedFromFavorites'))
    } catch {
      if (isCurrentSession()) toast.error(t('nav:favoritesUpdateFailed'))
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{t(isSpectatorMode ? 'nav:following' : 'nav:myPerformance')}</h1>
        <p className="text-muted-foreground">
          {t(isSpectatorMode ? 'nav:followingDescription' : 'nav:myPerformanceDescription')}
        </p>
      </div>

      {hasLoadError && (
        <Alert variant="destructive">
          <AlertCircle />
          <AlertTitle>{t('nav:personalDataUnavailable')}</AlertTitle>
          <AlertDescription className="flex flex-wrap items-center gap-3">
            <span>{t('nav:personalDataUnavailableDescription')}</span>
            <Button variant="outline" size="sm" onClick={() => setReloadKey((value) => value + 1)}>
              <RefreshCcw className="mr-2 h-4 w-4" />
              {t('nav:retry')}
            </Button>
          </AlertDescription>
        </Alert>
      )}

      <div className={`grid gap-4 ${isAthleteMode ? 'md:grid-cols-4' : 'md:grid-cols-1'}`}>
        {isAthleteMode && <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-muted-foreground">{t('nav:registrationsCount')}</CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-semibold">{isLoading ? '--' : registrations.length}</CardContent>
        </Card>}
        {isAthleteMode && <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-muted-foreground">{t('nav:resultsCount')}</CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-semibold">{isLoading ? '--' : results.length}</CardContent>
        </Card>}
        {isAthleteMode && <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-muted-foreground">{t('nav:finishedRaces')}</CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-semibold">{isLoading ? '--' : finishedCount}</CardContent>
        </Card>}
        {isAthleteMode && <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-muted-foreground">{t('nav:bestOverallPosition')}</CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-semibold">{isLoading ? '--' : bestOverall ?? '-'}</CardContent>
        </Card>}
        {isSpectatorMode && <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-muted-foreground">{t('nav:followedAthletes')}</CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-semibold">{isLoading ? '--' : favorites.length}</CardContent>
        </Card>}
      </div>

      {isSpectatorMode && <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('nav:followedAthletes')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {favorites.length === 0 ? (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">{t('nav:noFollowedAthletes')}</p>
              <Button asChild variant="outline">
                <Link to="/race/demo">{t('nav:openPublicTracking')}</Link>
              </Button>
            </div>
          ) : (
            favorites.map((favorite) => (
              <div
                key={favorite.participantId}
                className="flex items-center justify-between rounded-2xl border border-border px-4 py-3"
              >
                <div>
                  <p className="font-medium">
                    {favorite.participantName || t('nav:athlete')}
                    {favorite.bibNumber ? <span className="text-muted-foreground"> #{favorite.bibNumber}</span> : null}
                  </p>
                  <p className="flex items-center gap-1 text-xs text-muted-foreground">
                    <MapPin className="h-3 w-3" />
                    {favorite.raceName || t('nav:race')}
                    {favorite.lastCheckpoint ? ` • ${favorite.lastCheckpoint}` : ''}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {favorite.status ? <Badge variant="outline">{favorite.status}</Badge> : null}
                  {favorite.raceId ? (
                    <Button asChild size="sm" variant="outline">
                      <Link to={`/race/${favorite.raceId}/athlete/${favorite.participantId}`}>{t('nav:track')}</Link>
                    </Button>
                  ) : null}
                  <Button size="icon" variant="ghost" onClick={() => void handleUnfollow(favorite)}>
                    <UserMinus className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>}
    </div>
  )
}
