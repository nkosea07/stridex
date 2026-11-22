/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: { getItem: vi.fn(async () => null), setItem: vi.fn(), removeItem: vi.fn() },
}))
vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(async () => null), setItemAsync: vi.fn(), deleteItemAsync: vi.fn() }))

describe('race-wide map', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('asks only for recent density and keeps the busiest valid cells', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = vi.fn(async () => new Response(JSON.stringify([
      { latitude: -17.83, longitude: 31.05, weight: 4 },
      { latitude: -17.84, longitude: 31.06, weight: 40 },
      { latitude: 0, longitude: 0, weight: 99 },
      { latitude: 'x', longitude: 31.07, weight: 2 },
      { latitude: -17.85, longitude: 31.08, weight: 0 },
      { latitude: -17.86, longitude: 31.09, weight: 7 },
    ]), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const since = new Date('2026-11-22T10:00:00.000Z')

    const cells = await api.getRaceHeatmap('race-1', since, 2)

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/public\/races\/race-1\/tracking\/heatmap\?from=2026-11-22T10%3A00%3A00.000Z$/)
    expect(cells.map((cell) => cell.weight)).toEqual([40, 7])
  })

  it('rejects a malformed heatmap response', async () => {
    const { parseHeatCells } = await import('../lib/api.ts')

    expect(() => parseHeatCells({ cells: [] }, 10)).toThrow('Race heatmap response was malformed.')
  })

  it('shades denser cells more strongly within a readable range', async () => {
    const { heatFill } = await import('../lib/raceMap.ts')

    expect(heatFill(0, 10)).toBe('rgba(234, 88, 12, 0.15)')
    expect(heatFill(10, 10)).toBe('rgba(234, 88, 12, 0.60)')
    expect(heatFill(50, 10)).toBe('rgba(234, 88, 12, 0.60)')
    expect(heatFill(3, 0)).toBe('rgba(234, 88, 12, 0.15)')
  })

  it('places a followed athlete at their latest valid point', async () => {
    const { latestPosition } = await import('../lib/raceMap.ts')

    expect(latestPosition([
      { latitude: -17.8, longitude: 31.0, time: 't1' },
      { latitude: -17.9, longitude: 31.1, time: 't2' },
      { latitude: 0, longitude: 0, time: 't3' },
    ])).toEqual({ latitude: -17.9, longitude: 31.1 })
    expect(latestPosition([])).toBeNull()
  })

  it('maps only followed athletes in this race, once each, up to the limit', async () => {
    const { followedInRace } = await import('../lib/raceMap.ts')
    const rows = [
      { raceId: 'race-1', participantId: 'a' },
      { raceId: 'race-2', participantId: 'b' },
      { raceId: 'race-1', participantId: 'a' },
      { raceId: 'race-1', participantId: 'c' },
      { raceId: 'race-1', participantId: 'd' },
    ]

    expect(followedInRace(rows, 'race-1', 2).map((row) => row.participantId)).toEqual(['a', 'c'])
  })

  it('looks back the documented window for density', async () => {
    const { HEAT_WINDOW_MINUTES, heatWindowStart } = await import('../lib/raceMap.ts')
    const now = Date.parse('2026-11-22T10:30:00.000Z')

    expect(heatWindowStart(now).toISOString()).toBe(new Date(now - HEAT_WINDOW_MINUTES * 60000).toISOString())
  })
})
