import { useEffect, useMemo } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { Activity, ArrowLeft, ChevronRight, Copy, Flag, MapPin, RefreshCcw, Timer } from 'lucide-react'
import useSWR from 'swr'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { publicTrackingApi } from '@/lib/api'
import type { PublicAthleteTracking, PublicCheckpointSplit } from '@/types'
import { AthleteTrackPlayback } from '@/components/public/AthleteTrackPlayback'
import { FollowButton } from '@/components/public/FollowButton'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'
import type { PublicAthleteTrackingLoaderData } from '@/router/publicLoaders'

const TRACKING_POLL_INTERVAL_MS = 15000

function formatPredictedFinish(value?: string) {
  if (!value) return '---'

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value

  return parsed.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function formatPaceTick(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = Math.round(totalSeconds % 60)
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

function buildPaceSeries(splits: PublicCheckpointSplit[]) {
  return splits
    .filter((split) => split.paceSecondsPerKm != null && split.distanceMeters != null && split.distanceMeters > 0)
    .map((split, index) => ({
      label: split.name ?? `Split ${index + 1}`,
      distanceKm: Number(((split.distanceMeters as number) / 1000).toFixed(1)),
      paceSeconds: split.paceSecondsPerKm as number,
    }))
}

function statusTone(status: string) {
  switch (status) {
    case 'FINISHED':
      return 'border-emerald-300/40 bg-emerald-400/10 text-emerald-100'
    case 'STARTED':
      return 'border-cyan-300/40 bg-cyan-400/10 text-cyan-100'
    case 'DNF':
    case 'DISQUALIFIED':
      return 'border-rose-300/40 bg-rose-400/10 text-rose-100'
    default:
      return 'border-white/15 bg-white/5 text-slate-100'
  }
}

interface PublicAthleteTrackingPageProps {
  raceId?: string
  participantId?: string
  initialData?: PublicAthleteTrackingLoaderData
}

export function PublicAthleteTrackingPage({
  raceId: routeRaceId,
  participantId: routeParticipantId,
  initialData,
}: PublicAthleteTrackingPageProps = {}) {
  const params = useParams<{ raceId: string; participantId: string }>()
  const raceId = routeRaceId ?? params.raceId
  const participantId = routeParticipantId ?? params.participantId
  const location = useLocation()
  const hasInitialData = initialData?.raceId === raceId && initialData?.participantId === participantId
  const returnTo = `${location.pathname}${location.search}${location.hash}`

  const {
    data: tracking = null,
    error,
    isLoading,
    isValidating,
    mutate,
  } = useSWR<PublicAthleteTracking>(
    raceId && participantId ? ['public-athlete-tracking', raceId, participantId] : null,
    () => publicTrackingApi.getAthleteTracking(raceId as string, participantId as string),
    {
      fallbackData: hasInitialData ? initialData?.tracking : undefined,
      revalidateOnMount: hasInitialData ? false : undefined,
      refreshInterval: TRACKING_POLL_INTERVAL_MS,
      keepPreviousData: true,
    },
  )

  useEffect(() => {
    if (error) {
      toast.error('Failed to refresh athlete tracking')
    }
  }, [error])

  const recentSplits = useMemo(() => tracking?.splits.slice(-6).reverse() ?? [], [tracking])
  const paceSeries = useMemo(() => buildPaceSeries(tracking?.splits ?? []), [tracking])

  const handleRefresh = () => {
    void mutate()
  }

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href)
      toast.success('Athlete link copied')
    } catch {
      toast.error('Failed to copy athlete link')
    }
  }

  if (!raceId || !participantId) {
    return <div className="min-h-screen bg-slate-950" />
  }

  if (isLoading && !tracking) {
    return (
      <div className="min-h-screen bg-[radial-gradient(circle_at_top,_rgba(34,211,238,0.14),_transparent_28%),linear-gradient(180deg,_#020617,_#0f172a_45%,_#111827)] px-4 py-8">
        <div className="mx-auto max-w-4xl animate-pulse space-y-4">
          <div className="h-72 rounded-[30px] bg-white/10" />
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="h-40 rounded-[28px] bg-white/10" />
            <div className="h-40 rounded-[28px] bg-white/10" />
          </div>
          <div className="h-96 rounded-[28px] bg-white/10" />
        </div>
      </div>
    )
  }

  if (!tracking) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-950 px-4 text-center text-slate-200">
        Athlete tracking is not available.
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top,_rgba(34,211,238,0.14),_transparent_28%),linear-gradient(180deg,_#020617,_#0f172a_45%,_#111827)]">
      <div className="mx-auto max-w-4xl px-4 py-5 sm:px-6 sm:py-8">
        <div className="mb-4 flex items-center justify-between gap-3">
          <Link
            to={`/race/${raceId}`}
            className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-2 text-xs font-bold uppercase tracking-[0.18em] text-slate-200 backdrop-blur-sm"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            Race Hub
          </Link>

          <div className="flex items-center gap-2">
            <FollowButton raceId={raceId} participantId={participantId} />
            <Button
              type="button"
              variant="secondary"
              className="rounded-full border border-white/10 bg-white/5 px-4 text-xs font-bold uppercase tracking-[0.18em] text-slate-100 hover:bg-white/10"
              onClick={() => void handleCopyLink()}
            >
              <Copy className="mr-2 h-3.5 w-3.5" />
              Copy Link
            </Button>
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
          </div>
        </div>

        <section className="relative overflow-hidden rounded-[30px] border border-white/10 bg-slate-950 text-white shadow-[0_30px_80px_rgba(2,6,23,0.55)]">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(34,211,238,0.22),_transparent_32%),linear-gradient(135deg,_rgba(15,23,42,0.95),_rgba(2,6,23,0.98))]" />
          <div className="relative p-5 sm:p-7">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-2">
                  <Badge className={cn('rounded-full border px-3 py-1 font-mono text-[10px] uppercase tracking-[0.18em]', statusTone(tracking.status))}>
                    {tracking.status}
                  </Badge>
                  <Badge variant="outline" className="rounded-full border-white/15 bg-white/5 font-mono text-[10px] uppercase tracking-[0.18em] text-slate-200">
                    Bib {tracking.bibNumber || '---'}
                  </Badge>
                </div>
                <h1 className="mt-4 text-3xl font-black uppercase tracking-[-0.05em] text-white sm:text-4xl">
                  {tracking.participantName}
                </h1>
                <p className="mt-2 max-w-2xl text-sm text-slate-300">
                  Public athlete tracking with checkpoint progress and projected finish timing.
                </p>
              </div>

              <div className="rounded-3xl border border-cyan-300/15 bg-cyan-400/10 px-4 py-3 text-right">
                <p className="text-[10px] uppercase tracking-[0.22em] text-cyan-100/80">Predicted Finish</p>
                <p className="mt-1 font-mono text-3xl font-black text-white">{formatPredictedFinish(tracking.predictedFinishTime)}</p>
              </div>
            </div>

            <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div className="rounded-3xl border border-white/10 bg-white/5 p-4">
                <p className="flex items-center gap-2 text-[11px] uppercase tracking-[0.2em] text-slate-400">
                  <Flag className="h-3.5 w-3.5 text-cyan-300" />
                  Latest Checkpoint
                </p>
                <p className="mt-2 text-lg font-bold text-white">{tracking.lastCheckpoint || 'Awaiting start'}</p>
              </div>
              <div className="rounded-3xl border border-white/10 bg-white/5 p-4">
                <p className="flex items-center gap-2 text-[11px] uppercase tracking-[0.2em] text-slate-400">
                  <Timer className="h-3.5 w-3.5 text-amber-300" />
                  Elapsed
                </p>
                <p className="mt-2 font-mono text-lg font-bold text-white">{tracking.elapsedTime || '--:--:--'}</p>
              </div>
              <div className="rounded-3xl border border-white/10 bg-white/5 p-4">
                <p className="flex items-center gap-2 text-[11px] uppercase tracking-[0.2em] text-slate-400">
                  <Activity className="h-3.5 w-3.5 text-emerald-300" />
                  Average Pace
                </p>
                <p className="mt-2 font-mono text-lg font-bold text-white">{tracking.averagePace || '--'}</p>
              </div>
              <div className="rounded-3xl border border-white/10 bg-white/5 p-4">
                <p className="flex items-center gap-2 text-[11px] uppercase tracking-[0.2em] text-slate-400">
                  <MapPin className="h-3.5 w-3.5 text-rose-300" />
                  Remaining
                </p>
                <p className="mt-2 font-mono text-lg font-bold text-white">
                  {tracking.distanceRemaining !== undefined && tracking.distanceRemaining !== null
                    ? `${tracking.distanceRemaining.toFixed(1)} km`
                    : '---'}
                </p>
              </div>
            </div>
          </div>
        </section>

        <div className="mt-5 grid gap-5 lg:grid-cols-[0.88fr,1.12fr]">
          <section className="rounded-[28px] border border-white/10 bg-white/6 p-4 text-white shadow-[0_20px_60px_rgba(2,6,23,0.35)] backdrop-blur-sm">
            <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">Athlete Snapshot</p>
            <div className="mt-4 space-y-3">
              <div className="rounded-3xl border border-white/10 bg-slate-950/50 p-4">
                <p className="text-[10px] uppercase tracking-[0.18em] text-slate-400">Status</p>
                <p className="mt-1 text-lg font-black uppercase tracking-[-0.03em] text-white">{tracking.status}</p>
              </div>
              <div className="rounded-3xl border border-white/10 bg-slate-950/50 p-4">
                <p className="text-[10px] uppercase tracking-[0.18em] text-slate-400">Latest Split</p>
                <p className="mt-1 text-lg font-bold text-white">{tracking.lastCheckpoint || 'No split recorded'}</p>
                <p className="mt-2 font-mono text-sm text-slate-300">{tracking.elapsedTime || '--:--:--'}</p>
              </div>
              <div className="rounded-3xl border border-white/10 bg-slate-950/50 p-4">
                <p className="text-[10px] uppercase tracking-[0.18em] text-slate-400">Projected Finish Window</p>
                <p className="mt-1 font-mono text-2xl font-black text-cyan-100">
                  {formatPredictedFinish(tracking.predictedFinishTime)}
                </p>
              </div>

              <div className="rounded-3xl border border-cyan-300/15 bg-cyan-400/10 p-4">
                <p className="text-[10px] uppercase tracking-[0.18em] text-cyan-100/80">Free on Web</p>
                <p className="mt-2 text-base font-bold text-white">Follow this athlete here, unlock favorites and alerts with an account.</p>
                <p className="mt-2 text-sm leading-6 text-slate-200">
                  Public tracking stays available on web. A free account adds saved athletes, alerting, and the richer mobile app experience.
                </p>
                <Button
                  asChild
                  className="mt-4 rounded-full bg-white px-4 text-xs font-bold uppercase tracking-[0.18em] text-slate-950 hover:bg-cyan-50"
                >
                  <Link to="/register" state={{ from: returnTo }}>
                    Create Free Account
                    <ChevronRight className="ml-2 h-4 w-4" />
                  </Link>
                </Button>
              </div>
            </div>
          </section>

          <section className="rounded-[28px] border border-white/10 bg-white/6 p-4 text-white shadow-[0_20px_60px_rgba(2,6,23,0.35)] backdrop-blur-sm">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">Pace Profile</p>
                <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em] text-white">Pace by Split</h2>
              </div>
              <Badge variant="outline" className="rounded-full border-white/15 bg-white/5 font-mono text-[10px] uppercase tracking-[0.18em] text-slate-200">
                min/km
              </Badge>
            </div>

            {paceSeries.length > 0 ? (
              <div className="mt-4 h-56 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={paceSeries} margin={{ top: 8, right: 12, left: -12, bottom: 0 }}>
                    <defs>
                      <linearGradient id="paceFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#22d3ee" stopOpacity={0.6} />
                        <stop offset="95%" stopColor="#22d3ee" stopOpacity={0.05} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(148,163,184,0.2)" />
                    <XAxis
                      dataKey="label"
                      stroke="#94a3b8"
                      tick={{ fontSize: 11 }}
                      tickLine={false}
                      axisLine={false}
                    />
                    <YAxis
                      stroke="#94a3b8"
                      tick={{ fontSize: 11 }}
                      tickLine={false}
                      axisLine={false}
                      tickFormatter={formatPaceTick}
                      width={48}
                    />
                    <Tooltip
                      contentStyle={{ background: '#020617', border: '1px solid rgba(148,163,184,0.3)', borderRadius: 12 }}
                      labelStyle={{ color: '#e2e8f0' }}
                      formatter={(value) => [`${formatPaceTick(Number(value))} /km`, 'Pace']}
                    />
                    <Area
                      type="monotone"
                      dataKey="paceSeconds"
                      stroke="#22d3ee"
                      strokeWidth={2}
                      fill="url(#paceFill)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <div className="mt-4 rounded-3xl border border-dashed border-white/10 bg-slate-950/40 px-4 py-10 text-center text-sm text-slate-400">
                Pace data appears once checkpoint distances are recorded.
              </div>
            )}
          </section>

          <section className="rounded-[28px] border border-white/10 bg-white/6 p-4 text-white shadow-[0_20px_60px_rgba(2,6,23,0.35)] backdrop-blur-sm">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">Split Timeline</p>
                <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em] text-white">Latest Six Splits</h2>
              </div>
              <Badge variant="outline" className="rounded-full border-white/15 bg-white/5 font-mono text-[10px] uppercase tracking-[0.18em] text-slate-200">
                {tracking.splits.length} total
              </Badge>
            </div>

            <div className="mt-5 space-y-3">
              {recentSplits.length > 0 ? (
                recentSplits.map((split, index) => (
                  <div
                    key={`${split.name || 'split'}-${split.time || index}`}
                    className="flex items-center justify-between rounded-3xl border border-white/10 bg-slate-950/55 px-4 py-3"
                  >
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-cyan-400/10 font-mono text-xs font-bold text-cyan-100">
                        {recentSplits.length - index}
                      </div>
                      <div>
                        <p className="font-bold uppercase tracking-[0.01em] text-white">{split.name || 'Checkpoint'}</p>
                        <p className="text-xs text-slate-400">Public timing split</p>
                      </div>
                    </div>
                    <p className="font-mono text-sm font-bold text-white">{split.time || '--:--:--'}</p>
                  </div>
                ))
              ) : (
                <div className="rounded-3xl border border-dashed border-white/10 bg-slate-950/40 px-4 py-12 text-center text-sm text-slate-400">
                  No public splits recorded yet.
                </div>
              )}
            </div>
          </section>
        </div>

        <div className="mt-5">
          <AthleteTrackPlayback raceId={raceId} participantId={participantId} />
        </div>
      </div>
    </div>
  )
}
