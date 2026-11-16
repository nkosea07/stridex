import useSWR from 'swr'
import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { resultsApi } from '@/lib/api'
import { formatNumber } from '@/lib/format'

export function TimingAnomaliesPanel({ raceId, refreshKey = 0 }: { raceId: string; refreshKey?: number }) {
  const { t } = useTranslation()
  const { data, error } = useSWR(['timing-anomalies', raceId, refreshKey], () => resultsApi.anomalies(raceId))
  const rows = data ?? []

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('results:anomalies.title')}</CardTitle>
        <CardDescription>{t('results:anomalies.description')}</CardDescription>
      </CardHeader>
      <CardContent className="p-0" aria-live="polite">
        {error ? (
          <p className="px-6 pb-6 text-sm text-destructive">{t('results:anomalies.loadFailed')}</p>
        ) : rows.length === 0 ? (
          <p className="px-6 pb-6 text-sm text-muted-foreground">{t('results:anomalies.empty')}</p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('results:anomalies.athlete')}</TableHead>
                  <TableHead>{t('results:anomalies.bib')}</TableHead>
                  <TableHead>{t('results:anomalies.checkpoint')}</TableHead>
                  <TableHead>{t('results:anomalies.issue')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={`${row.participantId}-${row.checkpointId}-${row.type}`}>
                    <TableCell className="font-medium">{row.participantName}</TableCell>
                    <TableCell>{row.bibNumber ?? '—'}</TableCell>
                    <TableCell>{row.checkpointName}</TableCell>
                    <TableCell>
                      {t(`results:anomalies.types.${row.type}`, {
                        speed: row.speedKmh == null ? '' : formatNumber(row.speedKmh, { maximumFractionDigits: 1 }),
                      })}
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
