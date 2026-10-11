import { useEffect, useMemo, useState } from 'react'
import Map, { Layer, Source } from 'react-map-gl'
import { Flame } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { publicTrackingApi } from '@/lib/api'
import type { HeatCell } from '@/types'

const MAPBOX_TOKEN = import.meta.env.VITE_MAPBOX_TOKEN

interface RaceHeatmapProps {
  raceId: string
}

export function RaceHeatmap({ raceId }: RaceHeatmapProps) {
  const [cells, setCells] = useState<HeatCell[]>([])
  const [visible, setVisible] = useState(true)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const data = await publicTrackingApi.getHeatmap(raceId)
        if (!cancelled) setCells(data)
      } catch {
        if (!cancelled) setCells([])
      }
    })()
    return () => {
      cancelled = true
    }
  }, [raceId])

  const geojson = useMemo<GeoJSON.FeatureCollection<GeoJSON.Point>>(() => ({
    type: 'FeatureCollection',
    features: cells.map((cell) => ({
      type: 'Feature',
      properties: { weight: cell.weight },
      geometry: { type: 'Point', coordinates: [cell.longitude, cell.latitude] },
    })),
  }), [cells])

  if (cells.length === 0 || !MAPBOX_TOKEN) {
    return null
  }

  const center = cells[0]

  return (
    <section className="rounded-[28px] border border-white/10 bg-white/6 p-4 text-white shadow-[0_20px_60px_rgba(2,6,23,0.35)] backdrop-blur-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-400">Course Heat</p>
          <h2 className="mt-1 text-xl font-black uppercase tracking-[-0.04em] text-white">Where the field runs</h2>
        </div>
        <Button
          type="button"
          variant="secondary"
          className="rounded-full border border-white/10 bg-white/5 text-xs font-bold uppercase tracking-[0.18em] text-slate-100 hover:bg-white/10"
          onClick={() => setVisible((value) => !value)}
        >
          <Flame className="mr-2 h-3.5 w-3.5" />
          {visible ? 'Hide' : 'Show'}
        </Button>
      </div>

      {visible && (
        <div className="mt-4 h-72 overflow-hidden rounded-3xl border border-white/10">
          <Map
            initialViewState={{ longitude: center.longitude, latitude: center.latitude, zoom: 11 }}
            mapStyle="mapbox://styles/mapbox/dark-v11"
            mapboxAccessToken={MAPBOX_TOKEN}
            style={{ width: '100%', height: '100%' }}
          >
            <Source id="race-heat" type="geojson" data={geojson}>
              <Layer
                id="race-heat-layer"
                type="heatmap"
                paint={{
                  'heatmap-weight': ['interpolate', ['linear'], ['get', 'weight'], 0, 0, 20, 1],
                  'heatmap-intensity': 1,
                  'heatmap-radius': 14,
                  'heatmap-opacity': 0.85,
                  'heatmap-color': [
                    'interpolate',
                    ['linear'],
                    ['heatmap-density'],
                    0, 'rgba(0,0,0,0)',
                    0.2, '#22d3ee',
                    0.6, '#a78bfa',
                    1, '#f43f5e',
                  ],
                }}
              />
            </Source>
          </Map>
        </div>
      )}
    </section>
  )
}
