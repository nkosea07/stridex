import { Link } from 'react-router-dom'
import { ArrowRight, Medal, Timer } from 'lucide-react'
import type { PublicAthleteSummary } from '@/types'
import { Badge } from '@/components/ui/badge'

interface PublicLeaderboardProps {
  raceId: string
  athletes: PublicAthleteSummary[]
}

function formatPredictedFinish(value?: string) {
  if (!value) return '---'

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value

  return parsed.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

export function PublicLeaderboard({ raceId, athletes }: PublicLeaderboardProps) {
  return (
    <section className="rounded-[28px] border border-slate-200/70 bg-white/80 p-4 shadow-[0_16px_50px_rgba(15,23,42,0.08)] backdrop-blur-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-500">Leaderboard</p>
          <h2 className="mt-1 text-xl font-black uppercase tracking-[-0.04em] text-slate-950">Front Of Race</h2>
        </div>
        <Badge variant="outline" className="rounded-full border-slate-200 bg-slate-50 font-mono text-[10px] uppercase tracking-[0.18em] text-slate-700">
          Top {athletes.length}
        </Badge>
      </div>

      <div className="mt-4 space-y-3">
        {athletes.length > 0 ? (
          athletes.map((athlete, index) => (
            <Link
              key={athlete.participantId}
              to={`/race/${raceId}/athlete/${athlete.participantId}`}
              className="group flex items-center gap-3 rounded-3xl border border-slate-200 bg-slate-950 px-4 py-3 text-white transition-transform hover:-translate-y-0.5"
            >
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-white/8 text-center">
                <div>
                  <Medal className="mx-auto h-3.5 w-3.5 text-amber-300" />
                  <span className="mt-1 block font-mono text-[11px] font-bold">#{index + 1}</span>
                </div>
              </div>

              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-black uppercase tracking-[0.01em] text-white">
                      {athlete.participantName}
                    </p>
                    <div className="mt-1 flex items-center gap-2 text-[11px] text-slate-300">
                      <span className="font-mono">BIB {athlete.bibNumber || '---'}</span>
                      <span>•</span>
                      <span>{athlete.status}</span>
                    </div>
                  </div>

                  <ArrowRight className="mt-1 h-4 w-4 shrink-0 text-slate-500 transition-transform group-hover:translate-x-0.5" />
                </div>

                <div className="mt-3 grid grid-cols-3 gap-2 text-[11px]">
                  <div className="rounded-2xl border border-white/10 bg-white/5 px-2.5 py-2">
                    <p className="uppercase tracking-[0.18em] text-slate-400">Last CP</p>
                    <p className="mt-1 truncate font-bold text-white">{athlete.lastCheckpoint || 'Awaiting'}</p>
                  </div>
                  <div className="rounded-2xl border border-white/10 bg-white/5 px-2.5 py-2">
                    <p className="uppercase tracking-[0.18em] text-slate-400">Elapsed</p>
                    <p className="mt-1 font-mono font-bold text-white">{athlete.elapsedTime || '--:--:--'}</p>
                  </div>
                  <div className="rounded-2xl border border-white/10 bg-white/5 px-2.5 py-2">
                    <p className="flex items-center gap-1 uppercase tracking-[0.18em] text-slate-400">
                      <Timer className="h-3 w-3" />
                      Est
                    </p>
                    <p className="mt-1 font-mono font-bold text-white">{formatPredictedFinish(athlete.predictedFinishTime)}</p>
                  </div>
                </div>
              </div>
            </Link>
          ))
        ) : (
          <div className="rounded-3xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-500">
            Leaderboard data is not live yet for this race.
          </div>
        )}
      </div>
    </section>
  )
}
