import type { TrackPoint } from '@/types'

export interface PlaybackPosition {
  lat: number
  lng: number
  index: number
}

export function trackDurationMs(points: TrackPoint[]): number {
  if (points.length < 2) return 0
  const first = points[0]?.time ? Date.parse(points[0].time) : NaN
  const last = points[points.length - 1]?.time ? Date.parse(points[points.length - 1].time as string) : NaN
  if (Number.isNaN(first) || Number.isNaN(last) || last <= first) {
    return points.length * 1000
  }
  return last - first
}

export function resolvePlaybackPosition(points: TrackPoint[], progress: number): PlaybackPosition | null {
  if (points.length === 0) return null
  if (points.length === 1) return { lat: points[0].lat, lng: points[0].lng, index: 0 }

  const clamped = Math.max(0, Math.min(1, progress))
  const scaled = clamped * (points.length - 1)
  const lower = Math.floor(scaled)
  const upper = Math.min(points.length - 1, lower + 1)
  const t = scaled - lower

  const a = points[lower]
  const b = points[upper]

  return {
    lat: a.lat + (b.lat - a.lat) * t,
    lng: a.lng + (b.lng - a.lng) * t,
    index: lower,
  }
}

export function formatClock(milliseconds: number): string {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
}
