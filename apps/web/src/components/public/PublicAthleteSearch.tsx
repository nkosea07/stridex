import { Search, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useTranslation } from 'react-i18next'

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
  const { t } = useTranslation()
  return (
    <section className="rounded-[28px] border border-slate-200/70 bg-white/80 p-4 shadow-[0_16px_50px_rgba(15,23,42,0.08)] backdrop-blur-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-500">{t('publicRace:search.eyebrow')}</p>
          <p className="mt-1 text-sm text-slate-600">{t('publicRace:search.description')}</p>
        </div>
        <div className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 font-mono text-xs font-bold text-slate-700">
          {t('publicRace:search.shown', { count: resultCount })}
        </div>
      </div>

      <div className="mt-4 flex items-center gap-2 rounded-2xl border border-slate-200 bg-slate-950 px-3 py-2 text-white shadow-inner">
        <Search className="h-4 w-4 shrink-0 text-cyan-300" />
        <Input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder={t('publicRace:search.placeholder')}
            aria-label={t('publicRace:search.label')}
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
        <span>{query ? t('publicRace:search.searchingHint') : t('publicRace:search.liveHint')}</span>
        <span className="font-mono uppercase tracking-[0.16em]">{isSearching ? t('publicRace:search.scanning') : t('publicRace:search.ready')}</span>
      </div>
    </section>
  )
}
