import { useEffect, useMemo, useState } from 'react'
import { participantsApi, resultsApi } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { RaceResult } from '@/types'

type MyRegistration = {
  raceId: string
  status: string
}

export function UserStatsPage() {
  const [registrations, setRegistrations] = useState<MyRegistration[]>([])
  const [results, setResults] = useState<RaceResult[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    let mounted = true
    const load = async () => {
      setIsLoading(true)
      try {
        const [myRegistrations, myResults] = await Promise.all([
          participantsApi.myRegistrations(),
          resultsApi.my(),
        ])
        if (!mounted) return
        setRegistrations(myRegistrations)
        setResults(myResults)
      } catch {
        if (!mounted) return
        setRegistrations([])
        setResults([])
      } finally {
        if (mounted) setIsLoading(false)
      }
    }
    load()
    return () => {
      mounted = false
    }
  }, [])

  const finishedCount = useMemo(
    () => results.filter((result) => result.status === 'finished').length,
    [results],
  )
  const bestOverall = useMemo(() => {
    const positions = results
      .map((result) => result.overallPosition)
      .filter((position): position is number => typeof position === 'number' && position > 0)
    return positions.length ? Math.min(...positions) : null
  }, [results])

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">My Stats</h1>
        <p className="text-muted-foreground">Personal race and result statistics.</p>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-muted-foreground">Registrations</CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-semibold">{isLoading ? '--' : registrations.length}</CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-muted-foreground">Results</CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-semibold">{isLoading ? '--' : results.length}</CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-muted-foreground">Finished Races</CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-semibold">{isLoading ? '--' : finishedCount}</CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm text-muted-foreground">Best Overall Position</CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-semibold">{isLoading ? '--' : bestOverall ?? '-'}</CardContent>
        </Card>
      </div>
    </div>
  )
}
