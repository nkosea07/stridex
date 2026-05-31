import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ChevronRight, RefreshCcw, Search, Trophy } from 'lucide-react'
import { publicTrackingApi } from '@/lib/api'
import type { PublicAthleteSummary, PublicRaceHub } from '@/types'
import { PublicRaceHero } from '@/components/public/PublicRaceHero'
import { PublicAthleteSearch } from '@/components/public/PublicAthleteSearch'
import { PublicLeaderboard } from '@/components/public/PublicLeaderboard'
import { PublicAthleteCard } from '@/components/public/PublicAthleteCard'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'

export function PublicRaceHubPage() {
  const { raceId } = useParams<{ raceId: string }>()
  const location = useLocation()
  const searchSectionRef = useRef<HTMLElement | null>(null)
  const [hub, setHub] = useState<PublicRaceHub | null>(null)
  const [athletes, setAthletes] = useState<PublicAthleteSummary[]>([])
  const [liveAthletes, setLiveAthletes] = useState<PublicAthleteSummary[]>([])
  const [liveCount, setLiveCount] = useState(0)
  const [query, setQuery] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [isSearching, setIsSearching] = useState(false)
  const trimmedQuery = query.trim()
  const returnTo = `${location.pathname}${location.search}${location.hash}`

  useEffect(() => {
    if (!raceId) return

    let cancelled = false

    void (async () => {
      try {
        setIsLoading(true)
        const [hubData, liveData] = await Promise.all([
          publicTrackingApi.getRaceHub(raceId),
          publicTrackingApi.getLiveAthletes(raceId),
        ])

        if (cancelled) return

        setHub(hubData)
        setLiveAthletes(liveData)
        setLiveCount(liveData.length)
        setAthletes(liveData)
      } catch {
        if (!cancelled) {
          toast.error('Failed to load the public race hub')
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [raceId])

  useEffect(() => {
    if (!raceId || isLoading) return

    if (!trimmedQuery) {
      return
    }

    let cancelled = false
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          setIsSearching(true)
          const rows = await publicTrackingApi.searchAthletes(raceId, trimmedQuery)

          if (!cancelled) {
            setAthletes(rows)
          }
        } catch {
          if (!cancelled) {
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
  }, [isLoading, raceId, trimmedQuery])

  useEffect(() => {
    if (trimmedQuery || isLoading) return
    setAthletes(liveAthletes)
  }, [isLoading, liveAthletes, trimmedQuery])

  const leaderboard = useMemo(() => hub?.leaderboard ?? [], [hub])

  const handleRefresh = async () => {
    if (!raceId) return

    try {
      setIsRefreshing(true)
      const [hubData, liveData] = await Promise.all([
        publicTrackingApi.getRaceHub(raceId),
        publicTrackingApi.getLiveAthletes(raceId),
      ])
      const rows = trimmedQuery
        ? await publicTrackingApi.searchAthletes(raceId, trimmedQuery)
        : liveData

      setHub(hubData)
      setLiveAthletes(liveData)
      setLiveCount(liveData.length)
      setAthletes(rows)
    } catch {
      toast.error('Failed to refresh public race data')
    } finally {
      setIsRefreshing(false)
    }
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

          <Button
            type="button"
            variant="secondary"
            className="rounded-full border border-white/10 bg-white/5 px-4 text-xs font-bold uppercase tracking-[0.18em] text-slate-100 hover:bg-white/10"
            onClick={() => void handleRefresh()}
            disabled={isRefreshing}
          >
            <RefreshCcw className={`mr-2 h-3.5 w-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>

        <div className="space-y-5">
          <PublicRaceHero hub={hub} liveCount={liveCount} />

          <PublicAthleteSearch
            query={query}
            onQueryChange={setQuery}
            resultCount={athletes.length}
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
                  {athletes.length}
                </div>
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {athletes.length > 0 ? (
                  athletes.map((athlete) => (
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
