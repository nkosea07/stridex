import { useState } from 'react'
import useSWR from 'swr'
import { useTranslation } from 'react-i18next'
import { AlertTriangle, BadgeCheck, LockOpen } from 'lucide-react'
import { toast } from 'sonner'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { resultsApi } from '@/lib/api'
import { formatDateTime } from '@/lib/format'

export function shortHash(hash: string | null | undefined) {
  return hash ? `${hash.slice(0, 12)}…` : ''
}

interface ResultReleasePanelProps {
  raceId: string
  canManage: boolean
  refreshKey?: number
}

export function ResultReleasePanel({ raceId, canManage, refreshKey = 0 }: ResultReleasePanelProps) {
  const { t } = useTranslation()
  const { data: integrity, mutate } = useSWR(['result-integrity', raceId, refreshKey], () => resultsApi.integrity(raceId))
  const { data: versions, mutate: refreshVersions } = useSWR(['result-versions', raceId, refreshKey], () => resultsApi.versions(raceId))
  const [busy, setBusy] = useState(false)
  const [reason, setReason] = useState('')

  const refresh = async () => {
    await Promise.all([mutate(), refreshVersions()])
  }

  const certify = async () => {
    if (!integrity?.latestVersion) return
    if (!window.confirm(t('results:release.certifyConfirm', { version: integrity.latestVersion }))) return
    setBusy(true)
    try {
      await resultsApi.certify(raceId)
      toast.success(t('results:release.certified'))
      await refresh()
    } catch {
      toast.error(t('results:release.certifyFailed'))
    } finally {
      setBusy(false)
    }
  }

  const reopen = async () => {
    if (reason.trim().length < 5) {
      toast.error(t('results:release.reopenReasonRequired'))
      return
    }
    setBusy(true)
    try {
      await resultsApi.reopen(raceId, reason.trim())
      toast.success(t('results:release.reopened'))
      setReason('')
      await refresh()
    } catch {
      toast.error(t('results:release.reopenFailed'))
    } finally {
      setBusy(false)
    }
  }

  const certified = Boolean(integrity?.certifiedAt)
  const canCertify = canManage && !certified && integrity?.status === 'PROVISIONAL'
    && integrity.publishedResultsIntact && integrity.chainValid && !integrity.changedSincePublish

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('results:release.title')}</CardTitle>
        <CardDescription>{t('results:release.description')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4" aria-live="polite">
        {!integrity?.latestVersion ? (
          <p className="text-sm text-muted-foreground">{t('results:release.none')}</p>
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-medium">{t('results:release.version', { version: integrity.latestVersion })}</span>
              {integrity.status && (
                <Badge variant={integrity.status === 'OFFICIAL' ? 'default' : 'secondary'}>
                  {integrity.status === 'OFFICIAL' && <BadgeCheck className="mr-1 h-3 w-3" aria-hidden="true" />}
                  {t(`results:release.status.${integrity.status}`)}
                </Badge>
              )}
              <span className="font-mono text-xs text-muted-foreground" title={integrity.contentSha256 ?? undefined}>
                {t('results:release.fingerprint', { hash: shortHash(integrity.contentSha256) })}
              </span>
            </div>
            <div className="text-sm text-muted-foreground">
              {integrity.publishedAt && <div>{t('results:release.publishedAt', { time: formatDateTime(integrity.publishedAt) })}</div>}
              {integrity.certifiedAt && <div>{t('results:release.certifiedAt', { time: formatDateTime(integrity.certifiedAt) })}</div>}
            </div>
            {(!integrity.publishedResultsIntact || !integrity.chainValid) ? (
              <Alert variant="destructive">
                <AlertTriangle className="h-4 w-4" aria-hidden="true" />
                <AlertDescription>
                  {!integrity.publishedResultsIntact ? t('results:release.notIntact') : t('results:release.chainBroken')}
                </AlertDescription>
              </Alert>
            ) : (
              <p className="text-sm text-muted-foreground">{t('results:release.intact')} {t('results:release.chainValid')}</p>
            )}
            {integrity.changedSincePublish && !certified && (
              <Alert>
                <AlertTriangle className="h-4 w-4" aria-hidden="true" />
                <AlertDescription>{t('results:release.changed')}</AlertDescription>
              </Alert>
            )}
          </>
        )}

        {canCertify && (
          <Button onClick={() => void certify()} disabled={busy}>
            <BadgeCheck className="mr-2 h-4 w-4" aria-hidden="true" />
            {busy ? t('results:release.certifying') : t('results:release.certify')}
          </Button>
        )}

        {canManage && certified && (
          <div className="space-y-2">
            <Label htmlFor="reopen-reason">{t('results:release.reopenReason')}</Label>
            <Input id="reopen-reason" value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} />
            <Button variant="outline" onClick={() => void reopen()} disabled={busy}>
              <LockOpen className="mr-2 h-4 w-4" aria-hidden="true" />
              {busy ? t('results:release.reopening') : t('results:release.reopen')}
            </Button>
          </div>
        )}

        {versions && versions.length > 0 && (
          <div className="space-y-1">
            <p className="text-sm font-medium">{t('results:release.historyTitle')}</p>
            <ul className="space-y-1 text-xs text-muted-foreground">
              {[...versions].reverse().map((version) => (
                <li key={version.version} className="flex flex-wrap gap-2">
                  <span>{t('results:release.version', { version: version.version })}</span>
                  <span>{t(`results:release.status.${version.status}`)}</span>
                  <span>{formatDateTime(version.publishedAt)}</span>
                  <span>{t('results:release.results', { count: version.resultCount })}</span>
                  <span className="font-mono" title={version.chainSha256}>{shortHash(version.chainSha256)}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
