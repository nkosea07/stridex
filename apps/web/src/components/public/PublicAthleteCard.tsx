import { Link } from 'react-router-dom'
import { ArrowUpRight, Gauge, MapPinned, Timer } from 'lucide-react'
import type { PublicAthleteSummary } from '@/types'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

interface PublicAthleteCardProps {
  athlete: PublicAthleteSummary
  raceId: string
}

function formatPredictedFinish(value?: string) {
  if (!value) return '---'

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value

  return parsed.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function statusTone(status: string) {
  switch (status) {
    case 'FINISHED':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'STARTED':
      return 'border-cyan-200 bg-cyan-50 text-cyan-700'
    case 'DNF':
    case 'DISQUALIFIED':
      return 'border-rose-200 bg-rose-50 text-rose-700'
    default:
      return 'border-slate-200 bg-slate-50 text-slate-700'
  }
}

export function PublicAthleteCard({ athlete, raceId }: PublicAthleteCardProps) {
  return (
    <Link
      to={`/race/${raceId}/athlete/${athlete.participantId}`}
      className="group block rounded-[26px] border border-slate-200 bg-white p-4 shadow-[0_14px_40px_rgba(15,23,42,0.08)] transition-transform hover:-translate-y-0.5"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <div className="flex h-10 min-w-10 items-center justify-center rounded-2xl bg-slate-950 px-2 font-mono text-sm font-black text-cyan-100">
              {athlete.bibNumber || '--'}
            </div>
            <Badge className={cn('rounded-full border px-2.5 py-1 text-[10px] uppercase tracking-[0.18em]', statusTone(athlete.status))}>
              {athlete.status}
            </Badge>
          </div>
          <h3 className="mt-3 truncate text-lg font-black uppercase tracking-[-0.03em] text-slate-950">
            {athlete.participantName}
          </h3>
        </div>

        <ArrowUpRight className="h-4 w-4 shrink-0 text-slate-400 transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2 text-sm">
        <div className="rounded-2xl bg-slate-100 p-3">
          <p className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">
            <MapPinned className="h-3 w-3 text-cyan-600" />
            Last Checkpoint
          </p>
          <p className="mt-1 truncate font-bold text-slate-900">{athlete.lastCheckpoint || 'Awaiting start'}</p>
        </div>

        <div className="rounded-2xl bg-slate-100 p-3">
          <p className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">
            <Timer className="h-3 w-3 text-amber-600" />
            Elapsed
          </p>
          <p className="mt-1 font-mono font-bold text-slate-900">{athlete.elapsedTime || '--:--:--'}</p>
        </div>

        <div className="rounded-2xl bg-slate-100 p-3">
          <p className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">
            <Gauge className="h-3 w-3 text-emerald-600" />
            Avg Pace
          </p>
          <p className="mt-1 font-mono font-bold text-slate-900">{athlete.averagePace || '--'}</p>
        </div>

        <div className="rounded-2xl bg-slate-100 p-3">
          <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">Est Finish</p>
          <p className="mt-1 font-mono font-bold text-slate-900">{formatPredictedFinish(athlete.predictedFinishTime)}</p>
        </div>
      </div>
    </Link>
  )
}
