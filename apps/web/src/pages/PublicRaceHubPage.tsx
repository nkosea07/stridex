import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ChevronRight, RefreshCcw, Search, Trophy } from 'lucide-react'
import useSWR from 'swr'
import { publicTrackingApi } from '@/lib/api'
import type { PublicAthleteSummary, PublicRaceHub } from '@/types'
import { PublicRaceHero } from '@/components/public/PublicRaceHero'
import { PublicAthleteSearch } from '@/components/public/PublicAthleteSearch'
import { PublicLeaderboard } from '@/components/public/PublicLeaderboard'
import { PublicAthleteCard } from '@/components/public/PublicAthleteCard'
import { PublicRaceResultsPanel } from '@/components/public/PublicRaceResultsPanel'
import { PublicRaceRegistrationPanel } from '@/components/public/PublicRaceRegistrationPanel'
import { RaceHeatmap } from '@/components/public/RaceHeatmap'
import { RaceMediaGallery } from '@/components/public/RaceMediaGallery'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'
import { PUBLIC_BROWSER_DEMO_RACE_ID } from '@/router/publicLoaders'
import type { PublicRaceHubLoaderData } from '@/router/publicLoaders'

const HUB_POLL_INTERVAL_MS = 15000

interface PublicRaceHubPageProps {
  raceId?: string
  initialData?: PublicRaceHubLoaderData
}

export function PublicRaceHubPage({ raceId: routeRaceId, initialData }: PublicRaceHubPageProps = {}) {
  const params = useParams<{ raceId: string }>()
  const raceId = routeRaceId ?? params.raceId
  const location = useLocation()
  const searchSectionRef = useRef<HTMLElement | null>(null)
  const isBrowserDemo = raceId === PUBLIC_BROWSER_DEMO_RACE_ID
  const hasInitialData = initialData?.raceId === raceId
  const fallbackData = hasInitialData && initialData
    ? { hub: initialData.hub, items: initialData.liveAthletes }
    : undefined
  const swrKey = raceId && !isBrowserDemo ? ['public-race-hub', raceId] : null
  const [visibilityDenied, setVisibilityDenied] = useState(false)

  const {
    data: swrData,
    error: swrError,
    isLoading: swrIsLoading,
    isValidating: swrIsValidating,
    mutate,
  } = useSWR<{ hub: PublicRaceHub; items: PublicAthleteSummary[] }>(
    swrKey,
    async () => {
      const [hubData, liveData] = await Promise.all([
        publicTrackingApi.getRaceHub(raceId as string),
        publicTrackingApi.getLiveFeed(raceId as string, { limit: 100 }),
      ])
      return { hub: hubData, items: liveData.items }
    },
    {
      fallbackData,
      revalidateOnMount: isBrowserDemo || hasInitialData ? false : undefined,
      revalidateOnFocus: !isBrowserDemo,
      revalidateOnReconnect: !isBrowserDemo,
      refreshInterval: isBrowserDemo ? 0 : HUB_POLL_INTERVAL_MS,
      keepPreviousData: false,
      onSuccess: () => setVisibilityDenied(false),
    },
  )

  const error = isBrowserDemo ? undefined : swrError
  const unavailable = Boolean(error) || visibilityDenied
  const live = isBrowserDemo ? fallbackData : unavailable ? undefined : swrData
  const isLoading = isBrowserDemo ? false : swrIsLoading
  const isValidating = isBrowserDemo ? false : swrIsValidating
  const hub = live?.hub ?? null
  const liveAthletes = live?.items ?? []
  const liveCount = liveAthletes.length
  const [query, setQuery] = useState('')
  const [athletes, setAthletes] = useState<PublicAthleteSummary[]>(initialData?.liveAthletes ?? [])
  const [isSearching, setIsSearching] = useState(false)
  const trimmedQuery = query.trim()
  const displayedAthletes = isBrowserDemo || unavailable ? [] : athletes
  const returnTo = `${location.pathname}${location.search}${location.hash}`

  useEffect(() => {
    if (error) {
      setVisibilityDenied(true)
      setAthletes([])
      void mutate(undefined, { revalidate: false })
      toast.error('Failed to refresh public race data')
    }
  }, [error, mutate])


  useEffect(() => {
    if (!raceId || isBrowserDemo || isLoading) return

    if (!trimmedQuery) {
      return
    }

    let cancelled = false
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          setIsSearching(true)
          const rows = await publicTrackingApi.searchAthletes(raceId, trimmedQuery)

          if (!cancelled && !unavailable) {
            setAthletes(rows)
          }
        } catch {
          if (!cancelled) {
            setAthletes([])
            toast.error('Failed to refresh athlete results')
          }
        } finally {
          if (!cancelled) {
            setIsSearching(false)
          }
        }
      })()
    }, 180)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [unavailable, isBrowserDemo, isLoading, raceId, trimmedQuery])

  useEffect(() => {
    setVisibilityDenied(false)
  }, [raceId])

  useEffect(() => {
    setQuery('')
    setIsSearching(false)
    setAthletes([])
  }, [raceId])

  useEffect(() => {
    if (!raceId || isBrowserDemo || trimmedQuery || isLoading || unavailable) return
    setAthletes(liveAthletes)
  }, [unavailable, isBrowserDemo, isLoading, liveAthletes, raceId, trimmedQuery])

  const leaderboard = useMemo(() => hub?.leaderboard ?? [], [hub])

  const handleRefresh = () => {
    if (isBrowserDemo) return
    void mutate()
  }

  if (!raceId) {
    return <div className="min-h-screen bg-slate-950" />
  }

  if (isLoading && !hub) {
    return (
      <div className="min-h-screen bg-[linear-gradient(180deg,_#020617,_#0f172a_38%,_#e2e8f0_38%,_#f8fafc_100%)] px-4 py-8">
        <div className="mx-auto max-w-6xl animate-pulse space-y-4">
          <div className="h-64 rounded-[28px] bg-white/10" />
          <div className="h-28 rounded-[28px] bg-white/70" />
          <div className="grid gap-4 lg:grid-cols-[1.1fr,0.9fr]">
            <div className="h-96 rounded-[28px] bg-white/80" />
            <div className="h-96 rounded-[28px] bg-white/80" />
          </div>
        </div>
      </div>
    )
  }

  if (!hub) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950 px-4 text-center text-slate-200">
        Public race data is not available.
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-[linear-gradient(180deg,_#020617,_#0f172a_36%,_#e2e8f0_36%,_#f8fafc_100%)]">
      <div className="mx-auto max-w-6xl px-4 py-5 sm:px-6 sm:py-8">
        <div className="mb-4 flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={() => searchSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
            className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-2 text-xs font-bold uppercase tracking-[0.18em] text-slate-200 backdrop-blur-sm"
          >
            <Search className="h-3.5 w-3.5" />
            Find Athlete
          </button>

          {!isBrowserDemo && (
            <Button
              type="button"
              variant="secondary"
              className="rounded-full border border-white/10 bg-white/5 px-4 text-xs font-bold uppercase tracking-[0.18em] text-slate-100 hover:bg-white/10"
              onClick={() => void handleRefresh()}
              disabled={isValidating}
            >
              <RefreshCcw className={`mr-2 h-3.5 w-3.5 ${isValidating ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
          )}
        </div>

        <div className="space-y-5">
          <PublicRaceHero hub={hub} liveCount={liveCount} />

          {hub.resultsPublished && <PublicRaceResultsPanel raceId={raceId} />}

          <PublicRaceRegistrationPanel raceId={raceId} hub={hub} returnTo={returnTo} />

          {!isBrowserDemo && <RaceHeatmap raceId={raceId} />}

          {!isBrowserDemo && <RaceMediaGallery raceId={raceId} />}

          <PublicAthleteSearch
            query={query}
            onQueryChange={setQuery}
            resultCount={displayedAthletes.length}
            isSearching={isSearching}
          />

          <div className="grid gap-5 lg:grid-cols-[0.95fr,1.05fr]">
            <div className="space-y-5">
              <PublicLeaderboard raceId={raceId} athletes={leaderboard} />

              <section className="rounded-[28px] border border-slate-200/70 bg-white/80 p-5 shadow-[0_16px_50px_rgba(15,23,42,0.08)] backdrop-blur-sm">
                <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-500">Free on Web</p>
                <h2 className="mt-2 text-2xl font-black uppercase tracking-[-0.04em] text-slate-950">
                  Keep following here, save favorites in your account
                </h2>
                <p className="mt-3 max-w-xl text-sm leading-6 text-slate-600">
                  Public race tracking stays open on web. Create a free account when you want favorite athletes,
                  alerts, and the fuller spectator experience in the mobile app.
                </p>
                <Button
                  asChild
                  className="mt-4 rounded-full bg-slate-950 px-5 text-xs font-bold uppercase tracking-[0.18em] text-cyan-100 hover:bg-slate-800"
                >
                  <Link to="/register" state={{ from: returnTo }}>
                    Create Free Account
                    <ChevronRight className="ml-2 h-4 w-4" />
                  </Link>
                </Button>
              </section>
            </div>

            <section
              ref={searchSectionRef}
              className="rounded-[28px] border border-slate-200/70 bg-white/80 p-4 shadow-[0_16px_50px_rgba(15,23,42,0.08)] backdrop-blur-sm"
            >
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-500">Live Athletes</p>
                  <h2 className="mt-1 text-xl font-black uppercase tracking-[-0.04em] text-slate-950">
                    Search Results
                  </h2>
                </div>
                <div className="inline-flex items-center gap-2 rounded-full bg-slate-950 px-3 py-1 font-mono text-xs font-bold text-cyan-100">
                  <Trophy className="h-3.5 w-3.5" />
                  {displayedAthletes.length}
                </div>
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {displayedAthletes.length > 0 ? (
                  displayedAthletes.map((athlete) => (
                    <PublicAthleteCard key={athlete.participantId} athlete={athlete} raceId={raceId} />
                  ))
                ) : (
                  <div className="sm:col-span-2 rounded-3xl border border-dashed border-slate-200 bg-slate-50 px-4 py-10 text-center text-sm text-slate-500">
                    No athletes matched that search.
                  </div>
                )}
              </div>
            </section>
          </div>
        </div>
      </div>
    </div>
  )
}
