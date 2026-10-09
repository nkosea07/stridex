import { Activity, ArrowRight, CheckCircle2, Radio, ShieldCheck, Timer, UsersRound } from 'lucide-react'
import { Link } from 'react-router-dom'
import { PRODUCT } from '@/config/product'
import { Button } from '@/components/ui/button'

const capabilities = [
  ['Plan with certainty', 'Registrations, race setup, participant operations, and bibs in one operational workspace.', UsersRound],
  ['See the race unfold', 'Live GPS, checkpoints, public tracking, and official results stay connected from start to finish.', Radio],
  ['Operate with confidence', 'Role-aware access, verified accounts, reader health, and a pilot-first reliability model.', ShieldCheck],
]

export function LandingPage() {
  return (
    <main className="min-h-screen overflow-hidden bg-[#06111f] text-slate-100">
      <div className="absolute inset-x-0 top-0 h-[38rem] bg-[radial-gradient(circle_at_18%_0%,rgba(37,99,235,0.42),transparent_35%),radial-gradient(circle_at_82%_18%,rgba(249,115,22,0.25),transparent_30%)]" />
      <div className="relative mx-auto max-w-7xl px-6 pb-16 pt-6 lg:px-8">
        <header className="flex items-center justify-between border-b border-white/10 pb-5">
          <Link to="/" className="flex items-center gap-3 font-mono text-sm font-semibold tracking-[0.16em] text-white">
            <span className="grid h-9 w-9 place-items-center border border-orange-300/70 bg-orange-400 text-slate-950">
              <Activity className="h-5 w-5" aria-hidden="true" />
            </span>
            {PRODUCT.name.toUpperCase()}
          </Link>
          <div className="flex items-center gap-3">
            <Link to="/race/demo" className="hidden text-sm text-slate-300 transition hover:text-white sm:inline">
              View public tracking
            </Link>
            <Button asChild variant="outline" className="border-white/25 bg-transparent text-white hover:bg-white hover:text-slate-950">
              <Link to="/login">Sign in</Link>
            </Button>
          </div>
        </header>

        <section className="grid items-end gap-14 pb-20 pt-20 lg:grid-cols-[1.16fr_0.84fr] lg:pt-28">
          <div>
            <p className="mb-6 font-mono text-xs font-semibold tracking-[0.22em] text-orange-300">BUILT FOR THE MOMENT THE STARTER FIRES</p>
            <h1 className="max-w-4xl text-5xl font-semibold leading-[0.93] tracking-[-0.05em] text-white sm:text-7xl">
              {PRODUCT.tagline}
            </h1>
            <p className="mt-8 max-w-2xl text-lg leading-8 text-slate-300">
              {PRODUCT.description} Set up the event, move participants through the course, and give every follower a clear view of race day.
            </p>
            <div className="mt-10 flex flex-col gap-3 sm:flex-row">
              <Button asChild size="lg" className="bg-orange-400 px-6 text-slate-950 hover:bg-orange-300">
                <Link to="/register?intent=organizer">
                  Create organizer account <ArrowRight className="ml-2 h-4 w-4" aria-hidden="true" />
                </Link>
              </Button>
              <Button asChild size="lg" variant="outline" className="border-white/25 bg-white/5 px-6 text-white hover:bg-white/10 hover:text-white">
                <Link to="/race/demo">Explore race tracking</Link>
              </Button>
            </div>
            <p className="mt-4 text-xs text-slate-400">Organizer permissions are reviewed before race controls are enabled.</p>
          </div>

          <div className="border border-white/15 bg-[#0b1b2f]/85 p-5 shadow-2xl shadow-black/30 backdrop-blur sm:p-7">
            <div className="flex items-center justify-between border-b border-white/10 pb-4 font-mono text-xs tracking-[0.16em] text-slate-400">
              <span>RACE DAY SIGNAL</span>
              <span className="flex items-center gap-2 text-emerald-300"><span className="h-2 w-2 animate-live-pulse rounded-full bg-emerald-300" /> LIVE</span>
            </div>
            <div className="grid gap-px bg-white/10 sm:grid-cols-2">
              <div className="bg-[#0b1b2f] p-5">
                <Timer className="mb-6 h-5 w-5 text-orange-300" aria-hidden="true" />
                <p className="font-mono text-3xl text-white">00:42:18</p>
                <p className="mt-2 text-sm text-slate-400">Official timing signal</p>
              </div>
              <div className="bg-[#0b1b2f] p-5">
                <Radio className="mb-6 h-5 w-5 text-sky-300" aria-hidden="true" />
                <p className="font-mono text-3xl text-white">248</p>
                <p className="mt-2 text-sm text-slate-400">Active course updates</p>
              </div>
            </div>
            <div className="mt-5 flex items-center gap-3 border-l-2 border-orange-300 bg-white/5 px-4 py-3 text-sm text-slate-300">
              <CheckCircle2 className="h-5 w-5 shrink-0 text-orange-300" aria-hidden="true" />
              One operating picture for organizers, officials, athletes, and spectators.
            </div>
          </div>
        </section>
      </div>

      <section className="border-y border-slate-800 bg-slate-950">
        <div className="mx-auto grid max-w-7xl gap-px bg-slate-800 px-6 lg:grid-cols-3 lg:px-8">
          {capabilities.map(([title, description, Icon]) => (
            <article key={title as string} className="bg-slate-950 px-6 py-10 sm:px-8">
              <Icon className="h-6 w-6 text-orange-300" aria-hidden="true" />
              <h2 className="mt-6 text-2xl font-semibold tracking-tight text-white">{title as string}</h2>
              <p className="mt-3 max-w-sm leading-7 text-slate-400">{description as string}</p>
            </article>
          ))}
        </div>
      </section>
    </main>
  )
}
