import { describe, expect, it } from 'vitest'
import { formatClock, resolvePlaybackPosition, trackDurationMs } from '@/lib/playback'
import type { TrackPoint } from '@/types'

const points: TrackPoint[] = [
  { lat: -33.9, lng: 18.4, time: '2026-01-01T00:00:00Z' },
  { lat: -33.8, lng: 18.5, time: '2026-01-01T00:10:00Z' },
  { lat: -33.7, lng: 18.6, time: '2026-01-01T00:20:00Z' },
]

describe('playback helpers', () => {
  it('resolves interpolated positions across the track', () => {
    expect(resolvePlaybackPosition(points, 0)).toMatchObject({ lat: -33.9, lng: 18.4, index: 0 })
    expect(resolvePlaybackPosition(points, 1)).toMatchObject({ lat: -33.7, lng: 18.6, index: 2 })

    const middle = resolvePlaybackPosition(points, 0.5)
    expect(middle?.lat).toBeCloseTo(-33.8, 5)
    expect(middle?.lng).toBeCloseTo(18.5, 5)
  })

  it('returns null for an empty track and the single point otherwise', () => {
    expect(resolvePlaybackPosition([], 0.5)).toBeNull()
    expect(resolvePlaybackPosition([points[0]], 0.9)).toMatchObject({ lat: -33.9, lng: 18.4 })
  })

  it('derives duration from timestamps', () => {
    expect(trackDurationMs(points)).toBe(20 * 60 * 1000)
    expect(trackDurationMs([points[0]])).toBe(0)
  })

  it('formats clock values', () => {
    expect(formatClock(0)).toBe('00:00:00')
    expect(formatClock(3661000)).toBe('01:01:01')
  })
})
