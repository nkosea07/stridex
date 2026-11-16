import { useState } from 'react'
import useSWR from 'swr'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { rfidApi } from '@/lib/api'
import { formatDateTime, formatNumber } from '@/lib/format'
import type { RfidReprocessScope } from '@/types'

const UNMATCHED_POLL_INTERVAL_MS = 15_000
const CLOCK_WARNING_MS = 2000

export function clockOffsetWarning(offsetMs: number | null | undefined) {
  return offsetMs != null && Math.abs(offsetMs) > CLOCK_WARNING_MS
}

export function toIsoFromLocalInput(value: string): string | null {
  if (!value) return null
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString()
}

interface RfidTimingPanelProps {
  raceId: string
  canManage: boolean
}

export function RfidTimingPanel({ raceId, canManage }: RfidTimingPanelProps) {
  const { t } = useTranslation()
  const { data: gunTime, mutate: refreshGunTime } = useSWR(['gun-time', raceId], () => rfidApi.gunTime(raceId))
  const { data: unmatched, error: unmatchedError, mutate: refreshUnmatched } = useSWR(
    ['rfid-unmatched', raceId],
    () => rfidApi.unmatchedTags(raceId),
    { refreshInterval: UNMATCHED_POLL_INTERVAL_MS },
  )
  const [gunInput, setGunInput] = useState('')
  const [gunReason, setGunReason] = useState('')
  const [savingGun, setSavingGun] = useState(false)
  const [scopes, setScopes] = useState<RfidReprocessScope[]>(['UNKNOWN_TAG'])
  const [reprocessing, setReprocessing] = useState(false)

  const saveGunTime = async () => {
    const startedAt = toIsoFromLocalInput(gunInput)
    if (!startedAt || !gunReason.trim()) {
      toast.error(t('rfid:timing.gunInvalid'))
      return
    }
    setSavingGun(true)
    try {
      await rfidApi.correctGunTime(raceId, startedAt, gunReason.trim())
      toast.success(t('rfid:timing.gunSaved'))
      setGunReason('')
      await refreshGunTime()
    } catch {
      toast.error(t('rfid:timing.gunFailed'))
    } finally {
      setSavingGun(false)
    }
  }

  const toggleScope = (scope: RfidReprocessScope) => {
    setScopes((current) => current.includes(scope) ? current.filter((item) => item !== scope) : [...current, scope])
  }

  const reprocess = async () => {
    if (scopes.length === 0) {
      toast.error(t('rfid:timing.reprocessNone'))
      return
    }
    if (!window.confirm(t('rfid:timing.reprocessConfirm'))) return
    setReprocessing(true)
    try {
      const result = await rfidApi.reprocess(raceId, scopes)
      toast.success(t('rfid:timing.reprocessed', { count: result.requeued }))
      await refreshUnmatched()
    } catch {
      toast.error(t('rfid:timing.reprocessFailed'))
    } finally {
      setReprocessing(false)
    }
  }

  const rows = unmatched ?? []
  const scopeOptions: { value: RfidReprocessScope; label: string }[] = [
    { value: 'UNKNOWN_TAG', label: t('rfid:timing.reprocessUnknown') },
    { value: 'IGNORED', label: t('rfid:timing.reprocessIgnored') },
    { value: 'ERROR', label: t('rfid:timing.reprocessError') },
  ]

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>{t('rfid:timing.gunTitle')}</CardTitle>
          <CardDescription>{t('rfid:timing.gunDescription')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm" aria-live="polite">
            {gunTime?.startedAt
              ? t('rfid:timing.gunRecorded', { time: formatDateTime(gunTime.startedAt) })
              : gunTime?.scheduledStart
                ? t('rfid:timing.gunNotRecorded', { time: formatDateTime(gunTime.scheduledStart) })
                : t('rfid:timing.gunNoSchedule')}
          </p>
          {canManage && (
            <div className="space-y-3">
              <div className="space-y-1">
                <Label htmlFor="gun-time">{t('rfid:timing.gunInput')}</Label>
                <Input id="gun-time" type="datetime-local" step={1} value={gunInput}
                  onChange={(event) => setGunInput(event.target.value)} />
              </div>
              <div className="space-y-1">
                <Label htmlFor="gun-reason">{t('rfid:timing.gunReason')}</Label>
                <Input id="gun-reason" value={gunReason} maxLength={500}
                  onChange={(event) => setGunReason(event.target.value)} />
              </div>
              <Button onClick={() => void saveGunTime()} disabled={savingGun}>
                {savingGun ? t('rfid:timing.gunSaving') : t('rfid:timing.gunSave')}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      {canManage && (
        <Card>
          <CardHeader>
            <CardTitle>{t('rfid:timing.reprocessTitle')}</CardTitle>
            <CardDescription>{t('rfid:timing.reprocessDescription')}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <fieldset className="space-y-2">
              {scopeOptions.map((option) => (
                <label key={option.value} className="flex items-center gap-2 text-sm">
                  <input type="checkbox" className="h-4 w-4" checked={scopes.includes(option.value)}
                    onChange={() => toggleScope(option.value)} />
                  {option.label}
                </label>
              ))}
            </fieldset>
            <Button variant="outline" onClick={() => void reprocess()} disabled={reprocessing}>
              {reprocessing ? t('rfid:timing.reprocessing') : t('rfid:timing.reprocess')}
            </Button>
          </CardContent>
        </Card>
      )}

      <Card className="lg:col-span-2">
        <CardHeader>
          <CardTitle>{t('rfid:timing.unmatchedTitle')}</CardTitle>
          <CardDescription>{t('rfid:timing.unmatchedDescription')}</CardDescription>
        </CardHeader>
        <CardContent className="p-0" aria-live="polite">
          {unmatchedError ? (
            <p className="px-6 pb-6 text-sm text-destructive">{t('rfid:timing.unmatchedFailed')}</p>
          ) : rows.length === 0 ? (
            <p className="px-6 pb-6 text-sm text-muted-foreground">{t('rfid:timing.unmatchedEmpty')}</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('rfid:timing.tag')}</TableHead>
                    <TableHead className="text-right">{t('rfid:timing.reads')}</TableHead>
                    <TableHead>{t('rfid:timing.firstRead')}</TableHead>
                    <TableHead>{t('rfid:timing.lastRead')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.rfidTag}>
                      <TableCell className="font-mono text-xs">{row.rfidTag}</TableCell>
                      <TableCell className="text-right">{formatNumber(row.reads)}</TableCell>
                      <TableCell>{row.firstReadAt ? formatDateTime(row.firstReadAt) : '—'}</TableCell>
                      <TableCell>{row.lastReadAt ? formatDateTime(row.lastReadAt) : '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
