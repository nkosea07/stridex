/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: { getItem: vi.fn(async () => null), setItem: vi.fn(), removeItem: vi.fn() },
}))
vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(async () => null), setItemAsync: vi.fn(), deleteItemAsync: vi.fn() }))

const RACE = '6f1c2a40-1111-4222-8333-944455556666'
const ATHLETE = '7a2b3c40-1111-4222-8333-944455556666'

describe('notification routing', () => {
  it('opens the live view for followed-athlete alerts', async () => {
    const { routeForNotification } = await import('../lib/notificationRoutes.ts')
    expect(routeForNotification({ kind: 'FOLLOWED_ATHLETE', raceId: RACE, participantId: ATHLETE }))
      .toEqual({ pathname: '/athlete/[raceId]/[participantId]', params: { raceId: RACE, participantId: ATHLETE } })
  })

  it('opens results for result and query notifications and the race for registration or payment', async () => {
    const { routeForNotification } = await import('../lib/notificationRoutes.ts')
    expect(routeForNotification({ kind: 'RESULTS', raceId: RACE })).toEqual({ pathname: '/(tabs)/results' })
    expect(routeForNotification({ kind: 'RESULT_QUERY', raceId: RACE })).toEqual({ pathname: '/(tabs)/results' })
    expect(routeForNotification({ kind: 'PAYMENT', raceId: RACE, participantId: ATHLETE }))
      .toEqual({ pathname: '/race/[id]', params: { id: RACE } })
  })

  it('falls back to the notifications list for unknown or unsafe data', async () => {
    const { routeForNotification } = await import('../lib/notificationRoutes.ts')
    expect(routeForNotification(null)).toEqual({ pathname: '/notifications' })
    expect(routeForNotification({ kind: 'FOLLOWED_ATHLETE', raceId: '../admin', participantId: ATHLETE }))
      .toEqual({ pathname: '/notifications' })
  })
})

describe('settings API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('saves notification channels and only the marketing consent flag', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ emailEnabled: false, pushEnabled: true, smsEnabled: true, whatsappEnabled: false, marketingConsent: true }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const prefs = await api.updateNotificationPreferences({ emailEnabled: false, pushEnabled: true, smsEnabled: true, whatsappEnabled: false })
    await api.updateMarketingConsent(true)

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/notifications\/preferences$/)
    expect(fetchMock.mock.calls[0][1].method).toBe('PUT')
    expect(prefs.emailEnabled).toBe(false)
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/me\/consent$/)
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ marketingConsent: true })
  })

  it('drops personal bests without a usable distance', async () => {
    const { api } = await import('../lib/api.ts')
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([
      { distanceKm: 42.195, chipTime: '03:41:10', raceName: 'Harare Marathon', resultCount: 2 },
      { distanceKm: null, chipTime: '00:20:00' },
    ]), { status: 200 })))

    expect(await api.getMyPersonalBests()).toEqual([
      { distanceKm: 42.195, chipTime: '03:41:10', raceId: null, raceName: 'Harare Marathon', resultCount: 2 },
    ])
  })
})
