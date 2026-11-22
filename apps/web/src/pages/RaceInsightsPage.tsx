import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Activity, AlertTriangle, Clock, Users } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { insightsApi } from '@/lib/api'
import type { ParticipantAnomaly, RaceInsightsSummary } from '@/types'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { formatTime, formatNumber } from '@/lib/format'

function formatPace(secondsPerKm: number) {
  if (!secondsPerKm || secondsPerKm <= 0) return '—'
  const minutes = Math.floor(secondsPerKm / 60)
  const seconds = Math.round(secondsPerKm % 60)
  return `${minutes}:${seconds.toString().padStart(2, '0')} /km`
}

export function RaceInsightsPage() {
  const { t } = useTranslation()
  const { raceId } = useParams()
  const [summary, setSummary] = useState<RaceInsightsSummary | null>(null)
  const [anomalies, setAnomalies] = useState<ParticipantAnomaly[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const load = useCallback(async () => {
    if (!raceId) return
    setIsLoading(true)
    try {
      const [s, a] = await Promise.all([insightsApi.summary(raceId), insightsApi.anomalies(raceId)])
      setSummary(s)
      setAnomalies(a)
    } catch {
      toast.error(t('admin:insights.loadFailed'))
    } finally {
      setIsLoading(false)
    }
  }, [raceId])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{t('admin:insights.title')}</h1>
        <p className="text-muted-foreground">{t('admin:insights.description')}</p>
      </div>

      {isLoading && !summary ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="h-28 animate-pulse rounded-xl bg-muted" />
          ))}
        </div>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('admin:insights.participants')}</CardTitle>
                <Users className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{summary?.totalParticipants ?? 0}</div>
                <p className="text-xs text-muted-foreground">{t('admin:insights.live', { count: summary?.liveParticipants ?? 0 })}</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('admin:insights.averagePace')}</CardTitle>
                <Activity className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{formatPace(summary?.averagePaceSecondsPerKm ?? 0)}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('admin:insights.leaderFinish')}</CardTitle>
                <Clock className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {summary?.predictedLeaderFinishTime
                    ? formatTime(summary.predictedLeaderFinishTime)
                    : '—'}
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('admin:insights.anomalies')}</CardTitle>
                <AlertTriangle className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{summary?.anomalyCount ?? 0}</div>
                <p className="text-xs text-muted-foreground">{t('admin:insights.atRisk', { value: formatNumber(summary?.dropoutRiskPercent ?? 0, { maximumFractionDigits: 1 }) })}</p>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>{t('admin:insights.signals')}</CardTitle>
            </CardHeader>
            <CardContent>
              {anomalies.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">{t('admin:insights.none')}</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b text-left text-muted-foreground">
                        <th className="py-2 pr-4 font-medium">{t('admin:insights.athlete')}</th>
                        <th className="py-2 pr-4 font-medium">{t('admin:insights.bib')}</th>
                        <th className="py-2 pr-4 font-medium">{t('admin:insights.status')}</th>
                        <th className="py-2 pr-4 font-medium">{t('admin:insights.filteredGps')}</th>
                        <th className="py-2 pr-4 font-medium">{t('admin:insights.missedCutoffs')}</th>
                        <th className="py-2 font-medium">{t('admin:insights.score')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {anomalies.map((anomaly) => (
                        <tr key={anomaly.participantId} className="border-b last:border-0">
                          <td className="py-2 pr-4 font-medium">{anomaly.participantName ?? '—'}</td>
                          <td className="py-2 pr-4">{anomaly.bibNumber ?? '—'}</td>
                          <td className="py-2 pr-4">
                            <Badge variant="outline">{anomaly.status ?? '—'}</Badge>
                          </td>
                          <td className="py-2 pr-4">{anomaly.filteredGpsPoints}</td>
                          <td className="py-2 pr-4">{anomaly.missedCutoffs}</td>
                          <td className="py-2 font-mono">{anomaly.anomalyScore}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
