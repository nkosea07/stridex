import { Activity, CalendarDays, MapPin, Timer } from 'lucide-react'
import type { PublicRaceHub } from '@/types'
import { Badge } from '@/components/ui/badge'

interface PublicRaceHeroProps {
  hub: PublicRaceHub
  liveCount: number
}

function formatRaceDate(value?: string) {
  if (!value) return 'Date pending'

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value

  return parsed.toLocaleDateString([], {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

function formatDistance(distanceKm?: number) {
  if (distanceKm === undefined || distanceKm === null) return 'Distance TBC'
  return `${distanceKm} km`
}

export function PublicRaceHero({ hub, liveCount }: PublicRaceHeroProps) {
  const location = [hub.city, hub.country].filter(Boolean).join(', ')

  return (
    <section className="relative overflow-hidden rounded-[28px] border border-white/10 bg-slate-950 text-slate-50 shadow-[0_30px_80px_rgba(15,23,42,0.45)]">
      {hub.bannerImageUrl ? (
        <img
          src={hub.bannerImageUrl}
          alt={hub.raceName}
          className="absolute inset-0 h-full w-full object-cover opacity-25"
        />
      ) : null}
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(56,189,248,0.25),_transparent_35%),linear-gradient(135deg,_rgba(15,23,42,0.92),_rgba(2,6,23,0.98))]" />
      <div className="absolute inset-0 opacity-[0.08]" style={{ backgroundImage: 'linear-gradient(rgba(148,163,184,0.4) 1px, transparent 1px), linear-gradient(90deg, rgba(148,163,184,0.4) 1px, transparent 1px)', backgroundSize: '22px 22px' }} />

      <div className="relative flex flex-col gap-6 p-5 sm:p-7">
        <div className="flex items-start justify-between gap-4">
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <Badge className="rounded-full border border-cyan-400/30 bg-cyan-400/10 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.24em] text-cyan-100">
                Public Race Hub
              </Badge>
              <Badge variant="outline" className="rounded-full border-white/15 bg-white/5 font-mono text-[10px] uppercase tracking-[0.18em] text-slate-200">
                {hub.status}
              </Badge>
            </div>

            <div className="flex items-center gap-3">
              {hub.logoUrl ? (
                <img src={hub.logoUrl} alt="" className="h-12 w-12 rounded-2xl border border-white/10 object-cover" />
              ) : (
                <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-white/10 bg-white/5 font-mono text-sm font-bold text-cyan-100">
                  {hub.raceName.slice(0, 3).toUpperCase()}
                </div>
              )}
              <div>
                <h1 className="max-w-3xl text-3xl font-black uppercase tracking-[-0.04em] text-white sm:text-4xl">
                  {hub.raceName}
                </h1>
                <p className="mt-1 text-sm text-slate-300">
                  Spectator view with live standings and athlete search.
                </p>
              </div>
            </div>
          </div>

          <div className="hidden rounded-3xl border border-white/10 bg-white/5 px-4 py-3 text-right backdrop-blur-sm sm:block">
            <p className="text-[10px] uppercase tracking-[0.22em] text-slate-400">Live Field</p>
            <p className="mt-1 font-mono text-3xl font-black text-white">{liveCount}</p>
            <p className="text-xs text-slate-400">tracked athletes</p>
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-3xl border border-white/10 bg-white/6 p-4 backdrop-blur-sm">
            <div className="flex items-center gap-2 text-[11px] uppercase tracking-[0.2em] text-slate-400">
              <CalendarDays className="h-3.5 w-3.5 text-cyan-300" />
              Race Day
            </div>
            <p className="mt-2 text-lg font-bold text-white">{formatRaceDate(hub.raceDate)}</p>
          </div>

          <div className="rounded-3xl border border-white/10 bg-white/6 p-4 backdrop-blur-sm">
            <div className="flex items-center gap-2 text-[11px] uppercase tracking-[0.2em] text-slate-400">
              <MapPin className="h-3.5 w-3.5 text-amber-300" />
              Host City
            </div>
            <p className="mt-2 text-lg font-bold text-white">{location || 'Location pending'}</p>
          </div>

          <div className="rounded-3xl border border-white/10 bg-white/6 p-4 backdrop-blur-sm">
            <div className="flex items-center gap-2 text-[11px] uppercase tracking-[0.2em] text-slate-400">
              <Timer className="h-3.5 w-3.5 text-emerald-300" />
              Course
            </div>
            <p className="mt-2 text-lg font-bold text-white">{formatDistance(hub.distanceKm)}</p>
          </div>
        </div>

        <div className="grid gap-3 sm:hidden">
          <div className="rounded-3xl border border-cyan-400/15 bg-cyan-400/10 p-4">
            <div className="flex items-center gap-2 text-[11px] uppercase tracking-[0.2em] text-cyan-100/80">
              <Activity className="h-3.5 w-3.5" />
              Live Field
            </div>
            <div className="mt-2 flex items-end justify-between">
              <p className="font-mono text-3xl font-black text-white">{liveCount}</p>
              <p className="text-xs text-cyan-100/75">tracked athletes</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
