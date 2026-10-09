import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Search, Trophy, Download, Send, Filter, Clock, TrendingUp } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { resultsApi } from '@/lib/api'
import type { RaceResult } from '@/types'
import { toast } from 'sonner'

export function ResultsPage() {
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
        toast.error('Results changed. Reloading from the first page.')
        setResultRevision(null)
        await loadResults(0, false)
        return
      }
      toast.error('Failed to load results')
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

  const handlePublishResults = async () => {
    if (!raceId) return
    try {
      await resultsApi.publish(raceId)
      toast.success('Results published')
      await loadResults()
    } catch {
      toast.error('Failed to publish results')
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
      toast.error('Failed to export results')
    }
  }

  const ResultsTable = ({ list, showGenderPosition = false }: { list: RaceResult[]; showGenderPosition?: boolean }) => (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Pos</TableHead>
          <TableHead>Athlete</TableHead>
          <TableHead>Bib</TableHead>
          <TableHead>Chip Time</TableHead>
          <TableHead>Pace</TableHead>
          <TableHead>Speed</TableHead>
          {showGenderPosition && <TableHead>Gender Pos</TableHead>}
          <TableHead>Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {list.map((result) => (
          <TableRow key={result.id}>
            <TableCell>{showGenderPosition ? result.genderPosition : result.overallPosition}</TableCell>
            <TableCell>
              <div>
                <p className="font-medium">{result.participant?.firstName} {result.participant?.lastName}</p>
                <p className="text-xs text-muted-foreground">{result.participant?.nationality} - {result.participant?.clubName}</p>
              </div>
            </TableCell>
            <TableCell className="font-mono">{result.participant?.bibNumber}</TableCell>
            <TableCell><div className="flex items-center gap-1"><Clock className="h-3 w-3 text-muted-foreground" />{result.chipTime || '--:--:--'}</div></TableCell>
            <TableCell>{result.averagePace || '-'}</TableCell>
            <TableCell><div className="flex items-center gap-1"><TrendingUp className="h-3 w-3 text-muted-foreground" />{result.averageSpeed ?? '-'} km/h</div></TableCell>
            {showGenderPosition && <TableCell>{result.genderPosition}</TableCell>}
            <TableCell>{result.isOfficial ? <Badge className="bg-success/20 text-success">Official</Badge> : <Badge variant="outline">Provisional</Badge>}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Results</h1>
          <p className="text-muted-foreground">Race results and rankings</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => void handleExportResults('csv')}><Download className="mr-2 h-4 w-4" />Export</Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="icon" aria-label="Export format">
                <Download className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => void handleExportResults('csv')}>CSV</DropdownMenuItem>
              <DropdownMenuItem onClick={() => void handleExportResults('xlsx')}>Excel (XLSX)</DropdownMenuItem>
              <DropdownMenuItem onClick={() => void handleExportResults('pdf')}>PDF</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button onClick={() => void handlePublishResults()}><Send className="mr-2 h-4 w-4" />Publish Results</Button>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-4">
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Results</CardTitle></CardHeader><CardContent><div className="flex items-center gap-2"><Trophy className="h-5 w-5 text-chart-3" /><span className="text-2xl font-bold">{resultTotal}</span></div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Male Finishers</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{maleResults.length}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Female Finishers</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold">{femaleResults.length}</div></CardContent></Card>
        <Card><CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">Best Time</CardTitle></CardHeader><CardContent><div className="text-2xl font-bold font-mono">{filteredResults[0]?.chipTime || '--:--:--'}</div></CardContent></Card>
      </div>

      <div className="flex flex-col gap-4 sm:flex-row">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input placeholder="Search by name or bib..." className="pl-10" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <Select value={genderFilter} onValueChange={setGenderFilter}>
          <SelectTrigger className="w-[150px]"><Filter className="mr-2 h-4 w-4" /><SelectValue placeholder="Gender" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All</SelectItem>
            <SelectItem value="MALE">Male</SelectItem>
            <SelectItem value="FEMALE">Female</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="overall">Overall</TabsTrigger>
          <TabsTrigger value="men">Men</TabsTrigger>
          <TabsTrigger value="women">Women</TabsTrigger>
        </TabsList>
        <TabsContent value="overall"><ResultsTable list={filteredResults} /></TabsContent>
        <TabsContent value="men"><ResultsTable list={maleResults} showGenderPosition /></TabsContent>
        <TabsContent value="women"><ResultsTable list={femaleResults} showGenderPosition /></TabsContent>
      </Tabs>
      {hasMoreResults && (
        <div className="flex justify-center">
          <Button variant="outline" disabled={isLoadingMore} onClick={() => void loadResults(resultPage + 1, true)}>
            {isLoadingMore ? 'Loading…' : 'Load more results'}
          </Button>
        </div>
      )}
    </div>
  )
}
