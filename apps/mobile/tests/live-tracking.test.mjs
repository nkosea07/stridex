/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: { getItem: vi.fn(async () => null), setItem: vi.fn(), removeItem: vi.fn() },
}))
vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(async () => null), setItemAsync: vi.fn(), deleteItemAsync: vi.fn() }))

describe('route geometry', () => {
  it('fits a padded region around valid points and ignores null island', async () => {
    const { regionFor } = await import('../lib/routeGeometry.ts')
    const region = regionFor([
      { latitude: -17.80, longitude: 31.00 },
      { latitude: -17.90, longitude: 31.10 },
      { latitude: 0, longitude: 0 },
    ])
    expect(region.latitude).toBeCloseTo(-17.85)
    expect(region.longitude).toBeCloseTo(31.05)
    expect(region.latitudeDelta).toBeCloseTo(0.125)
    expect(regionFor([{ latitude: 0, longitude: 0 }])).toBeNull()
  })

  it('merges incremental track points without duplicates and keeps time order', async () => {
    const { mergeTrack } = await import('../lib/routeGeometry.ts')
    const a = { latitude: 1, longitude: 1, time: '2026-11-15T08:00:00Z' }
    const b = { latitude: 1.1, longitude: 1.1, time: '2026-11-15T08:00:05Z' }
    const c = { latitude: 1.2, longitude: 1.2, time: '2026-11-15T08:00:10Z' }
    expect(mergeTrack([a, b], [b, c]).map((point) => point.time)).toEqual([a.time, b.time, c.time])
    expect(mergeTrack([a, b, c], [], 2)).toHaveLength(3)
    expect(mergeTrack([a], [c, b], 2).map((point) => point.time)).toEqual([b.time, c.time])
  })

  it('reports minutes since the last fix', async () => {
    const { minutesSince } = await import('../lib/routeGeometry.ts')
    const now = Date.parse('2026-11-15T08:10:30Z')
    expect(minutesSince('2026-11-15T08:00:00Z', now)).toBe(10)
    expect(minutesSince(null, now)).toBeNull()
    expect(minutesSince('not a date', now)).toBeNull()
  })
})

describe('public live tracking API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests only points after the last one already shown', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      points: [{ lat: -17.82, lng: 31.05, time: '2026-11-15T08:00:05Z' }, { lat: 0, lng: 0, time: 'x' }],
    }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const points = await api.getPublicAthleteTrack('race-1', 'p-1', '2026-11-15T08:00:00Z')

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/public\/races\/race-1\/tracking\/athletes\/p-1\/track\?since=2026-11-15T08%3A00%3A00Z$/)
    expect(points).toEqual([{ latitude: -17.82, longitude: 31.05, time: '2026-11-15T08:00:05Z' }])
  })

  it('orders the course route by sequence', async () => {
    const { api } = await import('../lib/api.ts')
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([
      { sequence: 2, latitude: -17.81, longitude: 31.02 },
      { sequence: 1, latitude: -17.80, longitude: 31.01 },
    ]), { status: 200 })))

    expect(await api.getPublicRaceRoute('race-1')).toEqual([
      { latitude: -17.80, longitude: 31.01 },
      { latitude: -17.81, longitude: 31.02 },
    ])
  })
})
