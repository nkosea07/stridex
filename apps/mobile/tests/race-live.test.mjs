/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: { getItem: vi.fn(async () => null), setItem: vi.fn(), removeItem: vi.fn() },
}))
vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(async () => null), setItemAsync: vi.fn(), deleteItemAsync: vi.fn() }))

describe('race-wide live API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads the ranked leaderboard with a bounded size and keeps the server ranks', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = vi.fn(async () => new Response(JSON.stringify([
      { participantId: 'p2', participantName: 'Leader', rank: 1, distanceRemaining: '12.5' },
      { participantId: 'p1', participantName: 'Chaser', rank: 2, distanceRemaining: null },
      { participantName: 'No id' },
    ]), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const board = await api.getRaceLeaderboard('race-1', 50)

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/public\/races\/race-1\/tracking\/leaderboard\?limit=50$/)
    expect(board.map((row) => [row.participantId, row.rank, row.distanceRemaining])).toEqual([
      ['p2', 1, 12.5],
      ['p1', 2, null],
    ])
  })

  it('searches every athlete in the race by name or bib', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = vi.fn(async () => new Response(JSON.stringify([{ participantId: 'p9', bibNumber: '101' }]), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const results = await api.searchRaceAthletes('race-1', ' Ada 101 ')

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/public\/races\/race-1\/tracking\/search\?q=Ada%20101$/)
    expect(results).toHaveLength(1)
  })
})
