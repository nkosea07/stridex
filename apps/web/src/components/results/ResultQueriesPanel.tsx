import { useState } from 'react'
import useSWR from 'swr'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { resultsApi } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import type { ResultQuery } from '@/types'

export function ResultQueriesPanel({ raceId, onAnswered }: { raceId: string; onAnswered?: () => void }) {
  const { t } = useTranslation()
  const { data, mutate } = useSWR(['result-queries', raceId], () => resultsApi.openQueries(raceId), { refreshInterval: 30_000 })
  const [notes, setNotes] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState<string | null>(null)

  const answer = async (query: ResultQuery, outcome: 'RESOLVED' | 'REJECTED') => {
    const note = (notes[query.id] ?? '').trim()
    if (note.length < 5) {
      toast.error(t('results:queries.noteRequired'))
      return
    }
    setBusy(query.id)
    try {
      await resultsApi.resolveQuery(raceId, query.id, outcome, note)
      toast.success(t('results:queries.answered'))
      await mutate()
      onAnswered?.()
    } catch {
      toast.error(t('results:queries.answerFailed'))
    } finally {
      setBusy(null)
    }
  }

  const rows = data ?? []
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('results:queries.panelTitle')}</CardTitle>
        <CardDescription>{t('results:queries.panelDescription')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4" aria-live="polite">
        {rows.length === 0 && <p className="text-sm text-muted-foreground">{t('results:queries.empty')}</p>}
        {rows.map((query) => (
          <div key={query.id} className="space-y-2 rounded-md border border-border p-3">
            <p className="text-sm font-medium">
              {query.participantName} · {query.bibNumber ?? '—'}
              {query.checkpointName ? ` · ${query.checkpointName}` : ''}
            </p>
            {query.createdAt && <p className="text-xs text-muted-foreground">{formatDateTime(query.createdAt)}</p>}
            <p className="whitespace-pre-wrap text-sm">{query.message}</p>
            <Label htmlFor={`query-note-${query.id}`}>{t('results:queries.note')}</Label>
            <Textarea id={`query-note-${query.id}`} rows={2} maxLength={1000} value={notes[query.id] ?? ''}
              onChange={(event) => setNotes((current) => ({ ...current, [query.id]: event.target.value }))} />
            <div className="flex gap-2">
              <Button size="sm" onClick={() => void answer(query, 'RESOLVED')} disabled={busy === query.id}>{t('results:queries.resolve')}</Button>
              <Button size="sm" variant="outline" onClick={() => void answer(query, 'REJECTED')} disabled={busy === query.id}>{t('results:queries.reject')}</Button>
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}
