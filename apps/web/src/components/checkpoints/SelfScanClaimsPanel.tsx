import { useState } from 'react'
import useSWR from 'swr'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { selfScanApi } from '@/lib/api'
import { formatDateTime, formatNumber } from '@/lib/format'
import type { SelfScanClaim } from '@/types'

const CLAIMS_POLL_INTERVAL_MS = 30_000

export function claimReasonKey(claim: Pick<SelfScanClaim, 'reason' | 'distanceMeters'>) {
  if (claim.reason === 'OUT_OF_RANGE' && claim.distanceMeters == null) {
    return 'checkpoints:claims.reasons.OUT_OF_RANGE_UNKNOWN'
  }
  return `checkpoints:claims.reasons.${claim.reason}`
}

export function SelfScanClaimsPanel({ raceId }: { raceId: string }) {
  const { t } = useTranslation()
  const [busyId, setBusyId] = useState<string | null>(null)
  const { data: claims, error, mutate } = useSWR<SelfScanClaim[]>(
    ['self-scan-claims', raceId],
    () => selfScanApi.openClaims(raceId),
    { refreshInterval: CLAIMS_POLL_INTERVAL_MS },
  )

  const decide = async (claim: SelfScanClaim, decision: 'APPROVE' | 'REJECT') => {
    setBusyId(claim.id)
    try {
      const result = await selfScanApi.decide(raceId, claim.id, decision)
      const message = result.status === 'SUPERSEDED'
        ? 'checkpoints:claims.superseded'
        : result.status === 'APPROVED' ? 'checkpoints:claims.approved' : 'checkpoints:claims.rejected'
      toast.success(t(message))
      await mutate((current) => current?.filter((item) => item.id !== claim.id), { revalidate: true })
    } catch {
      toast.error(t('checkpoints:claims.decisionFailed'))
    } finally {
      setBusyId(null)
    }
  }

  const rows = claims ?? []

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('checkpoints:claims.title')}</CardTitle>
        <CardDescription>{t('checkpoints:claims.description')}</CardDescription>
      </CardHeader>
      <CardContent className="p-0" aria-live="polite">
        {error ? (
          <p className="px-6 pb-6 text-sm text-destructive">{t('checkpoints:claims.loadFailed')}</p>
        ) : rows.length === 0 ? (
          <p className="px-6 pb-6 text-sm text-muted-foreground">{t('checkpoints:claims.empty')}</p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('checkpoints:claims.athlete')}</TableHead>
                  <TableHead>{t('checkpoints:claims.bib')}</TableHead>
                  <TableHead>{t('checkpoints:claims.checkpoint')}</TableHead>
                  <TableHead>{t('checkpoints:claims.scannedAt')}</TableHead>
                  <TableHead>{t('checkpoints:claims.why')}</TableHead>
                  <TableHead className="text-right">{t('checkpoints:claims.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((claim) => (
                  <TableRow key={claim.id}>
                    <TableCell className="font-medium">{claim.participantName}</TableCell>
                    <TableCell>{claim.bibNumber ?? t('checkpoints:claims.noBib')}</TableCell>
                    <TableCell>{claim.checkpointName}</TableCell>
                    <TableCell>{formatDateTime(claim.claimedAt)}</TableCell>
                    <TableCell>
                      {t(claimReasonKey(claim), {
                        distance: claim.distanceMeters == null ? '' : formatNumber(Math.round(claim.distanceMeters)),
                      })}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="outline" disabled={busyId === claim.id}
                          onClick={() => void decide(claim, 'REJECT')}>
                          {t('checkpoints:claims.reject')}
                        </Button>
                        <Button size="sm" disabled={busyId === claim.id}
                          onClick={() => void decide(claim, 'APPROVE')}>
                          {t('checkpoints:claims.approve')}
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
