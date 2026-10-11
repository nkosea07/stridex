import { useCallback, useEffect, useMemo, useState, useRef } from 'react'
import { useParams } from 'react-router-dom'
import Map, { Marker, MapRef } from 'react-map-gl'
import { Maximize2, Minimize2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { connectLiveTracking, trackingApi, checkpointsApi, racesApi } from '@/lib/api'
import { getMapAccessToken, resolveMapStyleUrl } from '@/lib/map-provider'
import type { LiveTrackingData, Checkpoint, Race } from '@/types'
import { toast } from 'sonner'
import { DashboardLayout } from '@/components/dashboard/DashboardLayout'
import { CommandBar, TrackingMode } from '@/components/dashboard/CommandBar'
import { Sidebar } from '@/components/dashboard/Sidebar'
import { AnalyticsTray } from '@/components/dashboard/AnalyticsTray'
import { AeroPanel } from '@/components/dashboard/AeroPanel'
import { FreshnessIndicator } from '@/components/dashboard/FreshnessIndicator'
import { SecurityAuditor } from '@/components/dashboard/SecurityAuditor'

const MAPBOX_TOKEN = getMapAccessToken()
type LiveTrackingViewModel = LiveTrackingData & { lastSeen?: number }

export function LiveTrackingPage() {
  const { raceId } = useParams()
  const [race, setRace] = useState<Race | null>(null)
  const [data, setData] = useState<LiveTrackingViewModel[]>([])

  // Security/Config Check
  useEffect(() => {
    if (!MAPBOX_TOKEN) {
      toast.error('Mapbox token is missing. Please check your configuration.')
    }
  }, [])

  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([])
  const [search, setSearch] = useState('')
  const [selectedParticipant, setSelectedParticipant] = useState<LiveTrackingViewModel | null>(null)
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [viewState, setViewState] = useState({ longitude: 18.43, latitude: -34.035, zoom: 11.5 })
  const [mode, setMode] = useState<TrackingMode>('spectator')
  const [now, setNow] = useState(Date.now())
  const mapRef = useRef<MapRef>(null)

  // Isolated ticker for freshness updates
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  const loadLiveData = async () => {
    if (!raceId) return
    try {
      const [rows, cps, raceData] = await Promise.all([
        trackingApi.getLiveData(raceId),
        checkpointsApi.list(raceId),
        racesApi.get(raceId)
      ])
      setData(rows.map(r => ({ ...r, lastSeen: Date.now() })))
      setCheckpoints(cps.sort((a, b) => a.orderIndex - b.orderIndex))
      setRace(raceData)
    } catch {
      toast.error('Failed to load race data')
    }
  }

  useEffect(() => {
    if (!raceId) return
    void loadLiveData()

    const disconnect = connectLiveTracking(raceId, (incoming) => {
      setData((prev) => {
        const index = prev.findIndex((row) => row.participantId === incoming.participantId)
        const updatedParticipant = { ...incoming, lastSeen: Date.now() }
        if (index === -1) return [...prev, updatedParticipant]
        const next = [...prev]
        next[index] = updatedParticipant
        return next
      })
    })

    const interval = setInterval(() => {
      void loadLiveData()
    }, 30000)

    return () => {
      disconnect()
      clearInterval(interval)
    }
  }, [raceId])

  const filteredParticipants = useMemo(
    () =>
      data.filter((p) =>
        p.participantName.toLowerCase().includes(search.toLowerCase()) ||
        p.bibNumber.toLowerCase().includes(search.toLowerCase()),
      ),
    [data, search],
  )

  const handleParticipantClick = useCallback((participant: LiveTrackingViewModel) => {
    setSelectedParticipant(participant)
    mapRef.current?.flyTo({
      center: [participant.longitude, participant.latitude],
      zoom: 14,
      duration: 2000,
      essential: true
    })
  }, [])

  const latestLastSeen = useMemo(
    () => (data.length > 0 ? Math.max(...data.map((participant) => participant.lastSeen || 0)) : undefined),
    [data],
  )

  const DataStrip = (
    <Sidebar
      participants={filteredParticipants}
      search={search}
      onSearchChange={setSearch}
      selectedParticipant={selectedParticipant}
      onParticipantClick={handleParticipantClick}
    />
  )

  const analyticsTrayContent = (
    <AnalyticsTray
      selectedParticipant={selectedParticipant}
      mode={mode}
      checkpoints={checkpoints}
    />
  )

  return (
    <DashboardLayout
      dataStrip={DataStrip}
      analyticsTray={analyticsTrayContent}
      className={cn(isFullscreen && 'fixed inset-0 z-50 h-screen')}
    >
      <CommandBar
        mode={mode}
        onModeChange={setMode}
        raceTitle={race?.name || 'Live Tracking'}
      />

      <div className="flex-1 relative h-full w-full">
        {/* SYNC HUD */}
        <div className="absolute top-4 right-4 z-10 flex items-center gap-2 bg-background/80 backdrop-blur-md px-3 py-1.5 rounded-full border border-white/20 shadow-lg">
          <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse shadow-[0_0_8px_rgba(59,130,246,0.8)]" />
          <FreshnessIndicator lastSeen={latestLastSeen} now={now} />
        </div>

        <Map
          {...viewState}
          ref={mapRef}
          onMove={(evt) => setViewState(evt.viewState)}
          mapStyle={resolveMapStyleUrl()}
          mapboxAccessToken={MAPBOX_TOKEN}
          style={{ width: '100%', height: '100%' }}
        >
          {data.map((participant) => (
            <Marker
              key={participant.participantId}
              longitude={participant.longitude}
              latitude={participant.latitude}
              onClick={(e) => {
                e.originalEvent.stopPropagation()
                handleParticipantClick(participant)
              }}
            >
              <div className="group relative">
                <div className={cn(
                  "h-4 w-4 rounded-full border-2 border-white shadow-[0_0_10px_rgba(239,68,68,0.5)] cursor-pointer transition-all duration-300 hover:scale-125",
                  participant.status === 'FINISHED' ? 'bg-green-500 shadow-[0_0_10px_rgba(34,197,94,0.5)]' : 'bg-live',
                  participant.lastSeen && now - participant.lastSeen > 60000 && "grayscale opacity-50 shadow-none border-muted-foreground"
                )} />
                <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 bg-background border border-border px-1.5 py-0.5 rounded text-[10px] font-bold opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none shadow-xl">
                  #{participant.bibNumber}
                </div>
              </div>
            </Marker>
          ))}
        </Map>

        <AeroPanel
          participant={selectedParticipant}
          onClose={() => setSelectedParticipant(null)}
        />

        <SecurityAuditor />

        <div className="absolute bottom-6 right-6 flex flex-col gap-2">
          <Button size="icon" variant="secondary" className="h-10 w-10 rounded-full shadow-lg bg-background/80 backdrop-blur-md border border-white/20" onClick={() => setIsFullscreen((v) => !v)}>
            {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
          </Button>
        </div>
      </div>
    </DashboardLayout>
  )
}
