import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'
import { Users, DollarSign, CheckCircle2, TrendingUp } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { analyticsApi } from '@/lib/api'
import type { Demographics, RaceAnalyticsSummary, RegistrationPoint } from '@/types'
import { toast } from 'sonner'

export function AnalyticsPage() {
  const { raceId } = useParams()
  const [summary, setSummary] = useState<RaceAnalyticsSummary | null>(null)
  const [demographics, setDemographics] = useState<Demographics | null>(null)
  const [series, setSeries] = useState<RegistrationPoint[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const load = useCallback(async () => {
    if (!raceId) return
    setIsLoading(true)
    try {
      const [s, d, r] = await Promise.all([
        analyticsApi.summary(raceId),
        analyticsApi.demographics(raceId),
        analyticsApi.registrations(raceId, 30),
      ])
      setSummary(s)
      setDemographics(d)
      setSeries(r)
    } catch {
      toast.error('Failed to load analytics')
    } finally {
      setIsLoading(false)
    }
  }, [raceId])

  useEffect(() => {
    void load()
  }, [load])

  if (isLoading || !summary) {
    return <div className="p-6 text-sm text-muted-foreground">Loading analytics…</div>
  }

  const toRows = (record: Record<string, number> | undefined) =>
    Object.entries(record ?? {}).map(([name, value]) => ({ name, value }))

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Race Analytics</h1>
        <p className="text-muted-foreground">Registrations, revenue, and demographics</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm text-muted-foreground">Registrations</CardTitle></CardHeader><CardContent><div className="flex items-center gap-2"><Users className="h-5 w-5 text-primary" /><span className="text-2xl font-bold">{summary.totalRegistrations}</span></div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm text-muted-foreground">Revenue</CardTitle></CardHeader><CardContent><div className="flex items-center gap-2"><DollarSign className="h-5 w-5 text-success" /><span className="text-2xl font-bold">{summary.revenue}</span></div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm text-muted-foreground">Paid</CardTitle></CardHeader><CardContent><div className="flex items-center gap-2"><CheckCircle2 className="h-5 w-5 text-primary" /><span className="text-2xl font-bold">{summary.paidCount}</span></div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm text-muted-foreground">Completion</CardTitle></CardHeader><CardContent><div className="flex items-center gap-2"><TrendingUp className="h-5 w-5 text-chart-3" /><span className="text-2xl font-bold">{summary.completionRate}%</span></div></CardContent></Card>
      </div>

      <Card>
        <CardHeader><CardTitle>Registrations (last 30 days)</CardTitle></CardHeader>
        <CardContent>
          {series.length === 0 ? (
            <p className="text-sm text-muted-foreground">No registrations in this window.</p>
          ) : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={series}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" fontSize={11} />
                  <YAxis allowDecimals={false} fontSize={11} />
                  <Tooltip />
                  <Bar dataKey="count" fill="#22d3ee" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader><CardTitle>Status breakdown</CardTitle></CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            {Object.entries(summary.statusCounts).map(([status, count]) => (
              <Badge key={status} variant="outline">{status}: {count}</Badge>
            ))}
            <Badge variant="outline">UNPAID: {summary.unpaidCount}</Badge>
            <Badge variant="outline">REFUNDED: {summary.refundedCount}</Badge>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>Demographics</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            {(['gender', 'ageGroups', 'countries', 'shirtSizes'] as const).map((dimension) => (
              <div key={dimension}>
                <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{dimension}</p>
                <div className="flex flex-wrap gap-2">
                  {toRows(demographics?.[dimension]).map((row) => (
                    <Badge key={row.name} variant="outline">{row.name}: {row.value}</Badge>
                  ))}
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
