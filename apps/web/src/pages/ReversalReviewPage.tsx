import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Banknote } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { paymentReversalApi } from '@/lib/api'
import { formatCurrency, formatDateTime } from '@/lib/format'
import type { ReversalCase } from '@/types'
import { useTranslation } from 'react-i18next'

export function ReversalReviewPage() {
  const { t } = useTranslation()
  const [cases, setCases] = useState<ReversalCase[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      setCases(await paymentReversalApi.listOpen())
    } catch {
      setLoadError(true)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{t('common:reversalReview.title')}</h1>
        <p className="text-muted-foreground">{t('common:reversalReview.description')}</p>
      </div>

      {loadError && (
        <Card>
          <CardContent className="flex items-center justify-between gap-4 p-4" role="alert">
            <span>{t('common:reversalReview.loadFailed')}</span>
            <Button variant="outline" onClick={() => void load()}>{t('common:reversalReview.retry')}</Button>
          </CardContent>
        </Card>
      )}

      {!loadError && loading && <p role="status">{t('common:reversalReview.loading')}</p>}
      {!loadError && !loading && cases.length === 0 && (
        <p role="status">{t('common:reversalReview.empty')}</p>
      )}

      <div className="grid gap-4">
        {cases.map((item) => (
          <Card key={item.paymentId}>
            <CardHeader className="flex-row items-start justify-between gap-4 space-y-0">
              <div>
                <CardTitle className="text-base">
                  {item.participantName || t('common:reversalReview.unknownParticipant')}
                </CardTitle>
                <p className="mt-1 text-xs text-muted-foreground">
                  {item.raceName || item.raceId} · {item.paymentId}
                </p>
              </div>
              <Badge variant="secondary">{item.paymentStatus}</Badge>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <p className="flex items-center gap-2">
                <Banknote className="h-4 w-4" />
                {formatCurrency(item.amount ?? 0, item.currency ?? undefined)} · {item.provider} · {item.providerReference}
              </p>
              {item.observedAt && (
                <p className="text-muted-foreground">
                  {t('common:reversalReview.observedAt', { time: formatDateTime(item.observedAt) })}
                </p>
              )}
              <p className="text-muted-foreground">{t('common:reversalReview.instructions')}</p>
              {item.raceId && (
                <Button asChild size="sm" variant="outline">
                  <Link to={`/admin/races/${item.raceId}/participants`}>
                    {t('common:reversalReview.openParticipant')}
                  </Link>
                </Button>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
