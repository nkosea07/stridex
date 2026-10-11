import { Search, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'

interface PublicAthleteSearchProps {
  query: string
  onQueryChange: (value: string) => void
  resultCount: number
  isSearching: boolean
}

export function PublicAthleteSearch({
  query,
  onQueryChange,
  resultCount,
  isSearching,
}: PublicAthleteSearchProps) {
  return (
    <section className="rounded-[28px] border border-slate-200/70 bg-white/80 p-4 shadow-[0_16px_50px_rgba(15,23,42,0.08)] backdrop-blur-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-500">Find An Athlete</p>
          <p className="mt-1 text-sm text-slate-600">Search by bib number or athlete name.</p>
        </div>
        <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-mono text-xs font-bold text-slate-700">
          {resultCount} shown
        </div>
      </div>

      <div className="mt-4 flex items-center gap-2 rounded-2xl border border-slate-200 bg-slate-950 px-3 py-2 text-white shadow-inner">
        <Search className="h-4 w-4 shrink-0 text-cyan-300" />
        <Input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Bib 1024 or N. Kosin..."
          className="border-0 bg-transparent px-0 text-base text-white placeholder:text-slate-400 focus-visible:ring-0"
        />
        {query ? (
          <Button
            type="button"
            size="icon"
            variant="ghost"
            className="h-8 w-8 rounded-full text-slate-300 hover:bg-white/10 hover:text-white"
            onClick={() => onQueryChange('')}
          >
            <X className="h-4 w-4" />
          </Button>
        ) : null}
      </div>

      <div className="mt-3 flex items-center justify-between text-xs text-slate-500">
        <span>{query ? 'Search results update from the public index.' : 'Showing current live telemetry feed.'}</span>
        <span className="font-mono uppercase tracking-[0.16em]">{isSearching ? 'Scanning' : 'Ready'}</span>
      </div>
    </section>
  )
}
