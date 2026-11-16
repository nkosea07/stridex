import { useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import useSWR from 'swr'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, participantsApi } from '@/lib/api'
import type { Participant } from '@/types'

type Message = { tone: 'success' | 'error'; text: string }

export function TagAssignmentPage() {
  const { raceId } = useParams()
  const { t } = useTranslation()
  const { data: participants, mutate, isLoading } = useSWR(raceId ? ['tag-participants', raceId] : null,
    () => participantsApi.list(raceId as string))
  const [bib, setBib] = useState('')
  const [tag, setTag] = useState('')
  const [selected, setSelected] = useState<Participant | null>(null)
  const [message, setMessage] = useState<Message | null>(null)
  const [saving, setSaving] = useState(false)
  const [recent, setRecent] = useState<{ bib: string; tag: string; name: string }[]>([])
  const bibRef = useRef<HTMLInputElement>(null)
  const tagRef = useRef<HTMLInputElement>(null)

  const byBib = useMemo(() => {
    const map = new Map<string, Participant>()
    for (const participant of participants ?? []) {
      if (participant.bibNumber) map.set(participant.bibNumber.trim().toUpperCase(), participant)
    }
    return map
  }, [participants])

  const findParticipant = (event: React.FormEvent) => {
    event.preventDefault()
    const found = byBib.get(bib.trim().toUpperCase()) ?? null
    setSelected(found)
    setTag('')
    if (!found) {
      setMessage({ tone: 'error', text: t('tags:notFound', { bib: bib.trim() }) })
      bibRef.current?.select()
      return
    }
    setMessage(null)
    requestAnimationFrame(() => tagRef.current?.focus())
  }

  const assign = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!raceId || !selected || !tag.trim()) return
    setSaving(true)
    try {
      const previous = selected.rfidTag
      const updated = await participantsApi.update(raceId, selected.id, { rfidTag: tag.trim() })
      const name = `${updated.firstName ?? ''} ${updated.lastName ?? ''}`.trim()
      const text = t('tags:assigned', { name, bib: updated.bibNumber })
        + (previous && previous !== updated.rfidTag ? ` ${t('tags:replaced', { previous })}` : '')
      setMessage({ tone: 'success', text })
      setRecent((items) => [{ bib: updated.bibNumber ?? '', tag: updated.rfidTag ?? tag.trim(), name }, ...items].slice(0, 20))
      setSelected(null)
      setBib('')
      setTag('')
      await mutate()
      requestAnimationFrame(() => bibRef.current?.focus())
    } catch (error) {
      const code = error instanceof ApiError ? error.code : undefined
      setMessage({
        tone: 'error',
        text: code === 'RFID_TAG_IN_USE' ? t('tags:inUse') : code === 'RESULTS_CERTIFIED' ? t('tags:locked') : t('tags:failed'),
      })
      tagRef.current?.select()
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{t('tags:title')}</h1>
        <p className="text-sm text-muted-foreground">{t('tags:description')}</p>
      </div>
      <Card className="max-w-xl">
        <CardHeader>
          <CardTitle className="text-base">{t('tags:title')}</CardTitle>
          <CardDescription>{isLoading ? t('tags:loading') : t('tags:tagHelp')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <form className="flex items-end gap-2" onSubmit={findParticipant}>
            <div className="flex-1 space-y-1">
              <Label htmlFor="tag-bib">{t('tags:bib')}</Label>
              <Input id="tag-bib" ref={bibRef} autoFocus autoComplete="off" value={bib}
                onChange={(event) => setBib(event.target.value)} />
            </div>
            <Button type="submit" variant="outline" disabled={!bib.trim() || isLoading}>{t('tags:find')}</Button>
          </form>
          {selected && (
            <form className="space-y-3" onSubmit={(event) => void assign(event)}>
              <div className="rounded-md border border-border p-3 text-sm">
                <p className="font-medium">{selected.firstName} {selected.lastName} · {selected.bibNumber}</p>
                <p className="text-muted-foreground">
                  {selected.rfidTag ? t('tags:current', { tag: selected.rfidTag }) : t('tags:noCurrent')}
                </p>
              </div>
              <div className="space-y-1">
                <Label htmlFor="tag-value">{t('tags:tag')}</Label>
                <Input id="tag-value" ref={tagRef} autoComplete="off" className="font-mono" value={tag}
                  onChange={(event) => setTag(event.target.value)} />
              </div>
              <Button type="submit" disabled={saving || !tag.trim()}>{saving ? t('tags:saving') : t('tags:save')}</Button>
            </form>
          )}
          <p aria-live="polite" role="status"
            className={message?.tone === 'error' ? 'text-sm text-destructive' : 'text-sm text-success'}>
            {message?.text}
          </p>
        </CardContent>
      </Card>
      {recent.length > 0 && (
        <Card className="max-w-xl">
          <CardHeader><CardTitle className="text-base">{t('tags:recent')}</CardTitle></CardHeader>
          <CardContent>
            <ul className="space-y-1 text-sm">
              {recent.map((item) => (
                <li key={`${item.bib}-${item.tag}`} className="flex justify-between gap-2">
                  <span>{item.bib} · {item.name}</span>
                  <span className="font-mono text-xs text-muted-foreground">{item.tag}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
