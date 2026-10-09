import { useCallback, useEffect, useState } from 'react'
import { Trophy } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { resultsApi } from '@/lib/api'
import type { PublicRaceResult, PublicRaceResultPage } from '@/types'

interface PublicRaceResultsPanelProps {
  raceId: string
}

export function PublicRaceResultsPanel({ raceId }: PublicRaceResultsPanelProps) {
  const [results, setResults] = useState<PublicRaceResult[]>([])
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState<number | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [loadError, setLoadError] = useState(false)

  const applyPage = useCallback((data: PublicRaceResultPage, append: boolean) => {
    setResults((current) => append ? [...current, ...data.items] : data.items)
    setPage(data.page)
    setRevision(data.revision)
    setHasMore(data.page + 1 < data.totalPages)
    setLoadError(false)
  }, [])

  const loadFirstPage = useCallback(async () => {
    setIsLoading(true)
    try {
      applyPage(await resultsApi.publicPage(raceId), false)
    } catch {
      setLoadError(true)
    } finally {
      setIsLoading(false)
    }
  }, [applyPage, raceId])

  useEffect(() => {
    let cancelled = false
    setResults([])
    setRevision(null)
    setHasMore(false)
    setLoadError(false)
    setIsLoading(true)
    void resultsApi.publicPage(raceId).then((data) => {
      if (!cancelled) applyPage(data, false)
    }).catch(() => {
      if (!cancelled) setLoadError(true)
    }).finally(() => {
      if (!cancelled) setIsLoading(false)
    })
    return () => { cancelled = true }
  }, [applyPage, raceId])

  const loadMore = async () => {
    if (revision === null || isLoadingMore) return
    setIsLoadingMore(true)
    try {
      applyPage(await resultsApi.publicPage(raceId, page + 1, 25, revision), true)
    } catch (error) {
      if (error instanceof Error && error.message.toLowerCase().includes('results changed')) {
        await loadFirstPage()
      } else {
        setLoadError(true)
      }
    } finally {
      setIsLoadingMore(false)
    }
  }

  return (
    <section className="rounded-[28px] border border-white/10 bg-slate-950 p-5 text-slate-100 shadow-[0_20px_60px_rgba(2,6,23,0.18)]">
      <div className="flex items-center gap-3">
        <div className="rounded-2xl bg-cyan-300/10 p-3 text-cyan-200"><Trophy className="h-5 w-5" /></div>
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-cyan-200">Official race results</p>
          <h2 className="mt-1 text-xl font-black uppercase tracking-[-0.04em]">Finishers</h2>
        </div>
      </div>

      {isLoading ? (
        <p className="mt-5 text-sm text-slate-400" role="status">Loading published results…</p>
      ) : loadError && results.length === 0 ? (
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm text-slate-300">
          <span>Published results could not be loaded.</span>
          <Button variant="outline" onClick={() => void loadFirstPage()}>Retry</Button>
        </div>
      ) : results.length === 0 ? (
        <p className="mt-5 text-sm text-slate-400">No published finishers yet.</p>
      ) : (
        <>
          <ol className="mt-5 divide-y divide-white/10">
            {results.map((result) => (
              <li key={result.participantId} className="grid grid-cols-[3rem,1fr,auto] items-center gap-3 py-3 first:pt-0 last:pb-0">
                <span className="font-mono text-sm font-bold text-cyan-200">{result.overallPosition ?? '—'}</span>
                <span className="min-w-0 truncate text-sm font-semibold">{result.participantName || 'Participant'}
                  <span className="ml-2 font-mono text-xs font-normal text-slate-400">#{result.bibNumber || '—'}</span>
                </span>
                <span className="font-mono text-sm text-slate-300">{result.totalTime || result.finishTime || '—'}</span>
              </li>
            ))}
          </ol>
          {loadError && <p className="mt-3 text-sm text-amber-200" role="status">Could not load the next page. Try again.</p>}
          {hasMore && (
            <div className="mt-5 flex justify-center">
              <Button variant="secondary" onClick={() => void loadMore()} disabled={isLoadingMore}>
                {isLoadingMore ? 'Loading…' : 'Load more finishers'}
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  )
}
