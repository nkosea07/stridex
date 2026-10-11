import { useEffect, useMemo, useRef, useState } from 'react'
import Map, { Layer, MapRef, Marker, Source } from 'react-map-gl'
import { Pause, Play, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { publicTrackingApi } from '@/lib/api'
import { getMapAccessToken, resolveMapStyleUrl } from '@/lib/map-provider'
import { formatClock, resolvePlaybackPosition, trackDurationMs } from '@/lib/playback'
import type { TrackPoint } from '@/types'

const MAPBOX_TOKEN = getMapAccessToken()

interface AthleteTrackPlaybackProps {
  raceId: string
  participantId: string
}

const SPEEDS = [1, 2, 4]

export function AthleteTrackPlayback({ raceId, participantId }: AthleteTrackPlaybackProps) {
  const [points, setPoints] = useState<TrackPoint[]>([])
  const [progress, setProgress] = useState(1)
  const [isPlaying, setIsPlaying] = useState(false)
  const [speed, setSpeed] = useState(1)
  const mapRef = useRef<MapRef>(null)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const track = await publicTrackingApi.getAthleteTrack(raceId, participantId)
        if (!cancelled) {
          setPoints(track.points ?? [])
          setProgress(1)
        }
      } catch {
        if (!cancelled) setPoints([])
      }
    })()
    return () => {
      cancelled = true
    }
  }, [participantId, raceId])

  const duration = useMemo(() => trackDurationMs(points), [points])
  const position = useMemo(() => resolvePlaybackPosition(points, progress), [points, progress])

  useEffect(() => {
    if (!isPlaying || points.length < 2) return
    const interval = window.setInterval(() => {
      setProgress((current) => {
        const step = (200 * speed) / Math.max(1, duration)
        const next = current + step
        if (next >= 1) {
          setIsPlaying(false)
          return 1
        }
        return next
      })
    }, 200)
    return () => window.clearInterval(interval)
  }, [duration, isPlaying, points.length, speed])

  const geojson = useMemo<GeoJSON.Feature<GeoJSON.LineString>>(() => ({
    type: 'Feature',
    properties: {},
    geometry: {
      type: 'LineString',
      coordinates: points.map((point) => [point.lng, point.lat]),
    },
  }), [points])

  if (points.length < 2 || !MAPBOX_TOKEN) {
    return null
  }

  const start = points[0]
  const elapsed = duration * progress

  return (
    <section className="rounded-[28px] border border-white/10 bg-white/6 p-4 text-white shadow-[0_20px_60px_rgba(2,6,23,0.35)] backdrop-blur-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">Route Playback</p>
          <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em] text-white">Movement Replay</h2>
        </div>
        <span className="font-mono text-xs text-cyan-100">{formatClock(elapsed)} / {formatClock(duration)}</span>
      </div>

      <div className="mt-4 h-80 overflow-hidden rounded-3xl border border-white/10">
        <Map
          ref={mapRef}
          initialViewState={{ longitude: start.lng, latitude: start.lat, zoom: 13 }}
          mapStyle={resolveMapStyleUrl()}
          mapboxAccessToken={MAPBOX_TOKEN}
          style={{ width: '100%', height: '100%' }}
        >
          <Source id="athlete-track" type="geojson" data={geojson}>
            <Layer
              id="athlete-track-line"
              type="line"
              paint={{ 'line-color': '#22d3ee', 'line-width': 3, 'line-opacity': 0.85 }}
            />
          </Source>
          {position && (
            <Marker longitude={position.lng} latitude={position.lat}>
              <div className="h-4 w-4 rounded-full border-2 border-white bg-cyan-400 shadow-[0_0_10px_rgba(34,211,238,0.8)]" />
            </Marker>
          )}
        </Map>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Button
          type="button"
          variant="secondary"
          className="rounded-full border border-white/10 bg-white/5 text-slate-100 hover:bg-white/10"
          onClick={() => {
            if (progress >= 1) setProgress(0)
            setIsPlaying((value) => !value)
          }}
        >
          {isPlaying ? <Pause className="mr-2 h-4 w-4" /> : <Play className="mr-2 h-4 w-4" />}
          {isPlaying ? 'Pause' : 'Play'}
        </Button>
        <Button
          type="button"
          variant="secondary"
          className="rounded-full border border-white/10 bg-white/5 text-slate-100 hover:bg-white/10"
          onClick={() => {
            setIsPlaying(false)
            setProgress(0)
          }}
        >
          <RotateCcw className="mr-2 h-4 w-4" />
          Restart
        </Button>

        <input
          type="range"
          min={0}
          max={1000}
          value={Math.round(progress * 1000)}
          onChange={(event) => {
            setIsPlaying(false)
            setProgress(Number(event.target.value) / 1000)
          }}
          className="h-1 flex-1 min-w-[160px] cursor-pointer appearance-none rounded-full bg-white/20 accent-cyan-400"
          aria-label="Playback position"
        />

        <div className="flex items-center gap-1">
          {SPEEDS.map((value) => (
            <Button
              key={value}
              type="button"
              size="sm"
              variant={speed === value ? 'default' : 'secondary'}
              className="rounded-full text-xs"
              onClick={() => setSpeed(value)}
            >
              {value}x
            </Button>
          ))}
        </div>
      </div>
    </section>
  )
}
