import { useState } from 'react'
import useSWR from 'swr'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { participantsApi, rfidApi } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import type { RaceWave } from '@/types'
import { toIsoFromLocalInput } from '@/components/rfid/RfidTimingPanel'

export function bibInRange(bib: string | null | undefined, from: string, to: string) {
  if (!bib) return false
  const value = Number(bib)
  const low = Number(from)
  const high = Number(to)
  if ([value, low, high].every(Number.isFinite)) return value >= low && value <= high
  return bib >= from && bib <= to
}

interface WavesPanelProps {
  raceId: string
  canManage: boolean
}

export function WavesPanel({ raceId, canManage }: WavesPanelProps) {
  const { t } = useTranslation()
  const { data: waves, mutate } = useSWR(['race-waves', raceId], () => rfidApi.waves(raceId))
  const [name, setName] = useState('')
  const [scheduled, setScheduled] = useState('')
  const [busy, setBusy] = useState(false)
  const [correcting, setCorrecting] = useState<RaceWave | null>(null)
  const [correctTime, setCorrectTime] = useState('')
  const [correctReason, setCorrectReason] = useState('')
  const [assigning, setAssigning] = useState<RaceWave | null>(null)
  const [bibFrom, setBibFrom] = useState('')
  const [bibTo, setBibTo] = useState('')

  const run = async (action: () => Promise<void>, failure: string) => {
    setBusy(true)
    try {
      await action()
      await mutate()
    } catch {
      toast.error(t(failure))
    } finally {
      setBusy(false)
    }
  }

  const addWave = () => run(async () => {
    await rfidApi.createWave(raceId, {
      name: name.trim(),
      sortOrder: (waves?.length ?? 0) + 1,
      scheduledStart: toIsoFromLocalInput(scheduled),
    })
    setName('')
    setScheduled('')
    toast.success(t('rfid:waves.added'))
  }, 'rfid:waves.addFailed')

  const fireGun = (wave: RaceWave) => {
    if (!window.confirm(t('rfid:waves.startConfirm', { name: wave.name }))) return
    void run(async () => {
      await rfidApi.startWave(raceId, wave.id)
      toast.success(t('rfid:waves.started', { name: wave.name }))
    }, 'rfid:waves.startFailed')
  }

  const correctGun = () => {
    const startedAt = toIsoFromLocalInput(correctTime)
    if (!correcting || !startedAt || correctReason.trim().length < 5) {
      toast.error(t('rfid:timing.gunInvalid'))
      return
    }
    void run(async () => {
      await rfidApi.startWave(raceId, correcting.id, { startedAt, reason: correctReason.trim() })
      setCorrecting(null)
      setCorrectReason('')
      toast.success(t('rfid:waves.corrected'))
    }, 'rfid:waves.startFailed')
  }

  const assignBibs = () => {
    if (!assigning) return
    void run(async () => {
      const participants = await participantsApi.list(raceId)
      const ids = participants.filter((participant) => bibInRange(participant.bibNumber, bibFrom.trim(), bibTo.trim()))
        .map((participant) => participant.id)
      if (ids.length === 0) {
        toast.error(t('rfid:waves.assignNone'))
        return
      }
      const result = await rfidApi.assignWave(raceId, assigning.id, ids)
      toast.success(t('rfid:waves.assigned', { count: result.assigned, name: assigning.name }))
      setAssigning(null)
    }, 'rfid:waves.assignFailed')
  }

  const rows = waves ?? []

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('rfid:waves.title')}</CardTitle>
        <CardDescription>{t('rfid:waves.description')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4" aria-live="polite">
        {rows.length === 0 && <p className="text-sm text-muted-foreground">{t('rfid:waves.empty')}</p>}
        <ul className="space-y-3">
          {rows.map((wave) => (
            <li key={wave.id} className="rounded-md border border-border p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-medium">{wave.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {t('rfid:waves.participants', { count: wave.participants })}
                    {' · '}
                    {wave.startedAt
                      ? t('rfid:waves.startedAt', { time: formatDateTime(wave.startedAt) })
                      : wave.scheduledStart
                        ? t('rfid:waves.scheduledAt', { time: formatDateTime(wave.scheduledStart) })
                        : t('rfid:waves.notStarted')}
                  </p>
                </div>
                {canManage && (
                  <div className="flex flex-wrap gap-2">
                    {!wave.startedAt && (
                      <Button size="sm" onClick={() => fireGun(wave)} disabled={busy}>{t('rfid:waves.startNow')}</Button>
                    )}
                    {wave.startedAt && (
                      <Button size="sm" variant="outline" onClick={() => setCorrecting(wave)} disabled={busy}>{t('rfid:waves.correct')}</Button>
                    )}
                    <Button size="sm" variant="outline" onClick={() => setAssigning(wave)} disabled={busy}>{t('rfid:waves.assign')}</Button>
                    <Button size="sm" variant="ghost" disabled={busy}
                      onClick={() => void run(async () => { await rfidApi.deleteWave(raceId, wave.id) }, 'rfid:waves.deleteFailed')}>
                      {t('rfid:waves.delete')}
                    </Button>
                  </div>
                )}
              </div>
              {correcting?.id === wave.id && (
                <div className="mt-3 grid gap-2 sm:grid-cols-3">
                  <div className="space-y-1">
                    <Label htmlFor={`wave-gun-${wave.id}`}>{t('rfid:waves.correctTime')}</Label>
                    <Input id={`wave-gun-${wave.id}`} type="datetime-local" step={1} value={correctTime}
                      onChange={(event) => setCorrectTime(event.target.value)} />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor={`wave-reason-${wave.id}`}>{t('rfid:waves.correctReason')}</Label>
                    <Input id={`wave-reason-${wave.id}`} value={correctReason} maxLength={500}
                      onChange={(event) => setCorrectReason(event.target.value)} />
                  </div>
                  <Button className="self-end" onClick={correctGun} disabled={busy}>{t('rfid:waves.correct')}</Button>
                </div>
              )}
              {assigning?.id === wave.id && (
                <div className="mt-3 grid gap-2 sm:grid-cols-3">
                  <div className="space-y-1">
                    <Label htmlFor={`wave-from-${wave.id}`}>{t('rfid:waves.bibFrom')}</Label>
                    <Input id={`wave-from-${wave.id}`} value={bibFrom} onChange={(event) => setBibFrom(event.target.value)} />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor={`wave-to-${wave.id}`}>{t('rfid:waves.bibTo')}</Label>
                    <Input id={`wave-to-${wave.id}`} value={bibTo} onChange={(event) => setBibTo(event.target.value)} />
                  </div>
                  <Button className="self-end" onClick={assignBibs} disabled={busy}>{t('rfid:waves.assign')}</Button>
                </div>
              )}
            </li>
          ))}
        </ul>
        {canManage && (
          <div className="grid gap-2 sm:grid-cols-3">
            <div className="space-y-1">
              <Label htmlFor="wave-name">{t('rfid:waves.name')}</Label>
              <Input id="wave-name" value={name} maxLength={100} onChange={(event) => setName(event.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="wave-scheduled">{t('rfid:waves.scheduled')}</Label>
              <Input id="wave-scheduled" type="datetime-local" step={1} value={scheduled}
                onChange={(event) => setScheduled(event.target.value)} />
            </div>
            <Button className="self-end" onClick={() => void addWave()} disabled={busy || !name.trim()}>{t('rfid:waves.add')}</Button>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
