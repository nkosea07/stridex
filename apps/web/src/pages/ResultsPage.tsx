import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Search, Trophy, Download, Send, Filter, Clock, TrendingUp, Medal } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ApiError, resultsApi } from '@/lib/api'
import type { RaceResult, RankingEntry } from '@/types'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { ResultReleasePanel } from '@/components/results/ResultReleasePanel'
import { TimingAnomaliesPanel } from '@/components/results/TimingAnomaliesPanel'
import { ResultQueriesPanel } from '@/components/results/ResultQueriesPanel'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { formatNumber } from '@/lib/format'

export function ResultsPage() {
  const { t } = useTranslation()
  const { raceId } = useParams()
  const [results, setResults] = useState<RaceResult[]>([])
  const [resultTotal, setResultTotal] = useState(0)
  const [resultPage, setResultPage] = useState(0)
  const [resultRevision, setResultRevision] = useState<number | null>(null)
  const [hasMoreResults, setHasMoreResults] = useState(false)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [search, setSearch] = useState('')
  const [genderFilter, setGenderFilter] = useState<string>('all')
  const [activeTab, setActiveTab] = useState('overall')
  const [teamRankings, setTeamRankings] = useState<RankingEntry[]>([])
  const [countryRankings, setCountryRankings] = useState<RankingEntry[]>([])
  const [rankingsLoading, setRankingsLoading] = useState(false)

  useEffect(() => {
    if (!raceId || (activeTab !== 'teams' && activeTab !== 'countries')) return
    let cancelled = false
    setRankingsLoading(true)
    const load = async () => {
      try {
        if (activeTab === 'teams') {
          const data = await resultsApi.teamRankings(raceId)
          if (!cancelled) setTeamRankings(data)
        } else {
          const data = await resultsApi.countryRankings(raceId)
          if (!cancelled) setCountryRankings(data)
        }
      } catch {
        toast.error(t('results:ui.failedToLoadRankings'))
      } finally {
        if (!cancelled) setRankingsLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [activeTab, raceId])

  const loadResults = async (page = 0, append = false) => {
    if (!raceId) return
    try {
      if (append) setIsLoadingMore(true)
      const data = await resultsApi.page(raceId, page, 50, append ? resultRevision ?? undefined : undefined)
      setResults((current) => append ? [...current, ...data.items] : data.items)
      setResultTotal(data.totalElements)
      setResultPage(data.page)
      setResultRevision(data.revision)
      setHasMoreResults(data.page + 1 < data.totalPages)
    } catch (error) {
      if (append && error instanceof Error && error.message.toLowerCase().includes('results changed')) {
        toast.error(t('results:ui.resultsChangedReloadingFromTheFirst'))
        setResultRevision(null)
        await loadResults(0, false)
        return
      }
      toast.error(t('results:ui.failedToLoadResults'))
    } finally {
      if (append) setIsLoadingMore(false)
    }
  }

  useEffect(() => {
    void loadResults()
  }, [raceId])

  const filteredResults = useMemo(
    () =>
      results.filter((result) => {
        const fullName = `${result.participant?.firstName || ''} ${result.participant?.lastName || ''}`.toLowerCase()
        const matchesSearch =
          fullName.includes(search.toLowerCase()) ||
          result.participant?.bibNumber?.toLowerCase().includes(search.toLowerCase())
        const matchesGender = genderFilter === 'all' || result.participant?.gender === genderFilter
        return Boolean(matchesSearch && matchesGender)
      }),
    [results, search, genderFilter],
  )

  const maleResults = filteredResults.filter((r) => r.participant?.gender === 'MALE')
  const femaleResults = filteredResults.filter((r) => r.participant?.gender === 'FEMALE')

  const { user } = useAuth()
  const canManageResults = isAdminRole(user?.role)
  const [releaseRefresh, setReleaseRefresh] = useState(0)

  const handlePublishResults = async () => {
    if (!raceId) return
    try {
      await resultsApi.publish(raceId)
      toast.success(t('results:ui.resultsPublished'))
      setReleaseRefresh((value) => value + 1)
      await loadResults()
    } catch (error) {
      toast.error(error instanceof ApiError && error.code === 'RESULTS_UNPAID_FINISHERS'
        ? t('results:ui.publishBlockedUnpaidFinishers')
        : t('results:ui.failedToPublishResults'))
    }
  }

  const handleExportResults = async (format: 'csv' | 'pdf' | 'xlsx' = 'csv') => {
    if (!raceId) return
    try {
      const blob = await resultsApi.exportResults(raceId, format)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `results-${raceId}.${format}`)
      link.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error(t('results:ui.failedToExportResults'))
    }
  }

  const ResultsTable = ({ list, showGenderPosition = false }: { list: RaceResult[]; showGenderPosition?: boolean }) => (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('results:ui.pos')}</TableHead>
          <TableHead>{t('results:ui.athlete')}</TableHead>
          <TableHead>{t('results:ui.bib')}</TableHead>
          <TableHead>{t('results:ui.chipTime')}</TableHead>
          <TableHead>{t('results:ui.ageGroup')}</TableHead>
          <TableHead>{t('results:ui.pace')}</TableHead>
          <TableHead>{t('results:ui.speed')}</TableHead>
          {showGenderPosition && <TableHead>{t('results:ui.genderPos')}</TableHead>}
          <TableHead>{t('results:ui.status')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {list.map((result) => (
          <TableRow key={result.id}>
            <TableCell>
              <div className="flex items-center gap-1">
                {result.medal && <Medal className="h-3.5 w-3.5 text-amber-400" />}
                {showGenderPosition ? result.genderPosition : result.overallPosition}
              </div>
            </TableCell>
            <TableCell>
              <div>
                <p className="font-medium">{result.participant?.firstName} {result.participant?.lastName}</p>
                <p className="text-xs text-muted-foreground">{result.participant?.nationality} - {result.participant?.clubName}</p>
              </div>
            </TableCell>
            <TableCell className="font-mono">{result.participant?.bibNumber}</TableCell>
            <TableCell><div className="flex items-center gap-1"><Clock className="h-3 w-3 text-muted-foreground" />{result.chipTime || '--:--:--'}</div></TableCell>
            <TableCell>{result.ageGroup || '-'}{result.ageGroupPosition ? ` (#${result.ageGroupPosition})` : ''}</TableCell>
            <TableCell>{result.averagePace || '-'}</TableCell>
            <TableCell><div className="flex items-center gap-1"><TrendingUp className="h-3 w-3 text-muted-foreground" />{result.averageSpeed != null ? t('results:speedKmh', { value: formatNumber(result.averageSpeed, { maximumFractionDigits: 1 }) }) : '-'}</div></TableCell>
            {showGenderPosition && <TableCell>{result.genderPosition}</TableCell>}
            <TableCell>{result.isOfficial ? <Badge className="bg-success/20 text-success">{t('results:ui.official')}</Badge> : <Badge variant="outline">{t('results:ui.provisional')}</Badge>}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )

  const RankingTable = ({ list, label }: { list: RankingEntry[]; label: string }) => (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('results:ui.pos')}</TableHead>
          <TableHead>{label}</TableHead>
          <TableHead>{t('results:ui.finishers')}</TableHead>
          <TableHead>{t('results:ui.totalTime')}</TableHead>
          <TableHead>{t('results:ui.average')}</TableHead>
          <TableHead>{t('results:ui.best')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {list.map((entry, index) => (
          <TableRow key={entry.name}>
            <TableCell>{index + 1}</TableCell>
            <TableCell className="font-medium">{entry.name}</TableCell>
            <TableCell>{entry.finishers}</TableCell>
            <TableCell className="font-mono">{entry.totalTime || '-'}</TableCell>
            <TableCell className="font-mono">{entry.averageTime || '-'}</TableCell>
            <TableCell className="font-mono">{entry.bestTime || '-'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{t('results:ui.results')}</h1>
          <p className="text-muted-foreground">{t('results:ui.raceResultsAndRankings')}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => void handleExportResults('csv')}><Download className="mr-2 h-4 w-4" />{t('results:ui.export')}</Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="icon" aria-label={t('results:exportFormat')}>
                <Download className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => void handleExportResults('csv')}>{t('results:ui.csv')}</DropdownMenuItem>
              <DropdownMenuItem onClick={() => void handleExportResults('xlsx')}>{t('results:ui.excelXlsx')}</DropdownMenuItem>
              <DropdownMenuItem onClick={() => void handleExportResults('pdf')}>{t('results:ui.pdf')}</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button onClick={() => void handlePublishResults()}><Send className="mr-2 h-4 w-4" />{t('results:ui.publishResults')}</Button>
        </div>
      </div>

      {raceId && <ResultReleasePanel raceId={raceId} canManage={canManageResults} refreshKey={releaseRefresh} />}
      {raceId && canManageResults && <TimingAnomaliesPanel raceId={raceId} refreshKey={releaseRefresh} />}
      {raceId && canManageResults && <ResultQueriesPanel raceId={raceId} onAnswered={() => setReleaseRefresh((value) => value + 1)} />}

      <div className="grid gap-4 sm:grid-cols-4">
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('results:ui.results')}</CardTitle></CardHeader><CardContent><div className="flex items-center gap-2"><Trophy className="h-5 w-5 text-chart-3" /><span className="text-2xl font-bold">{resultTotal}</span></div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('results:ui.maleFinishers')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{maleResults.length}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('results:ui.femaleFinishers')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{femaleResults.length}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{t('results:ui.bestTime')}</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold font-mono">{filteredResults[0]?.chipTime || '--:--:--'}</div></CardContent></Card>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input placeholder={t('results:ui.searchByNameOrBib')} className="pl-10" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <Select value={genderFilter} onValueChange={setGenderFilter}>
          <SelectTrigger className="w-[150px]"><Filter className="mr-2 h-4 w-4" /><SelectValue placeholder={t('results:ui.gender')} /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('results:ui.all')}</SelectItem>
            <SelectItem value="MALE">{t('results:ui.male')}</SelectItem>
            <SelectItem value="FEMALE">{t('results:ui.female')}</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="overall">{t('results:ui.overall')}</TabsTrigger>
          <TabsTrigger value="men">{t('results:ui.men')}</TabsTrigger>
          <TabsTrigger value="women">{t('results:ui.women')}</TabsTrigger>
          <TabsTrigger value="teams">{t('results:ui.teams')}</TabsTrigger>
          <TabsTrigger value="countries">{t('results:ui.countries')}</TabsTrigger>
        </TabsList>
        <TabsContent value="overall"><ResultsTable list={filteredResults} /></TabsContent>
        <TabsContent value="men"><ResultsTable list={maleResults} showGenderPosition /></TabsContent>
        <TabsContent value="women"><ResultsTable list={femaleResults} showGenderPosition /></TabsContent>
        <TabsContent value="teams">
          {rankingsLoading ? (
            <p className="py-6 text-sm text-muted-foreground">{t('results:ui.loadingTeamRankings')}</p>
          ) : teamRankings.length === 0 ? (
            <p className="py-6 text-sm text-muted-foreground">{t('results:ui.noTeamResultsAvailable')}</p>
          ) : (
            <RankingTable list={teamRankings} label={t('results:team')} />
          )}
        </TabsContent>
        <TabsContent value="countries">
          {rankingsLoading ? (
            <p className="py-6 text-sm text-muted-foreground">{t('results:ui.loadingCountryRankings')}</p>
          ) : countryRankings.length === 0 ? (
            <p className="py-6 text-sm text-muted-foreground">{t('results:ui.noCountryResultsAvailable')}</p>
          ) : (
            <RankingTable list={countryRankings} label={t('results:country')} />
          )}
        </TabsContent>
      </Tabs>
      {hasMoreResults && (
        <div className="flex justify-center">
          <Button variant="outline" disabled={isLoadingMore} onClick={() => void loadResults(resultPage + 1, true)}>
            {isLoadingMore ? t('results:loadingMore') : t('results:loadMore')}
          </Button>
        </div>
      )}
    </div>
  )
}
