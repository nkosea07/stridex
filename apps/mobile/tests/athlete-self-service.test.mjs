/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: { getItem: vi.fn(async () => null), setItem: vi.fn(), removeItem: vi.fn() },
}))
vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(async () => null), setItemAsync: vi.fn(), deleteItemAsync: vi.fn() }))

const respond = (body, status = 200) => vi.fn(async () => new Response(JSON.stringify(body), { status }))

describe('athlete self-service API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('requests a password reset and a new verification email without auth refresh', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = respond({ message: 'ok' })
    vi.stubGlobal('fetch', fetchMock)

    await api.forgotPassword('runner@example.com')
    await api.resendVerification('runner@example.com')

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/auth\/forgot-password$/)
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ email: 'runner@example.com' })
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/auth\/resend-verification$/)
  })

  it('starts checkout for the athlete entry and passes an encoded promo code', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = respond({ status: 'PENDING', checkoutUrl: 'https://pay.example/checkout/1' })
    vi.stubGlobal('fetch', fetchMock)

    const checkout = await api.startPaymentCheckout('race-1', 'entry-1', ' EARLY 10 ')

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/races\/race-1\/participants\/entry-1\/payment\/checkout\?promoCode=EARLY%2010$/)
    expect(fetchMock.mock.calls[0][1].method).toBe('POST')
    expect(checkout.checkoutUrl).toBe('https://pay.example/checkout/1')
  })

  it('lets the athlete stop and resume public location sharing for an entry', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = respond({ participantId: 'entry-1', enabled: false })
    vi.stubGlobal('fetch', fetchMock)

    const result = await api.updateLocationSharing('entry-1', false)

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/participants\/my-registrations\/entry-1\/location-sharing$/)
    expect(fetchMock.mock.calls[0][1].method).toBe('PUT')
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ enabled: false })
    expect(result.enabled).toBe(false)
  })

  it('loads the check-in QR for the athlete entry', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = respond({ participantId: 'entry-1', content: 'AMS-PARTICIPANT-entry-1', imagePngBase64: 'iVBORw0KGgo=' })
    vi.stubGlobal('fetch', fetchMock)

    const qr = await api.getCheckInQr('entry-1')

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/participants\/my-registrations\/entry-1\/check-in-qr$/)
    expect(qr).toEqual({ participantId: 'entry-1', content: 'AMS-PARTICIPANT-entry-1', imagePngBase64: 'iVBORw0KGgo=' })
  })

  it('rejects a check-in QR response without an image', async () => {
    const { api } = await import('../lib/api.ts')
    vi.stubGlobal('fetch', respond({ participantId: 'entry-1', content: 'AMS-PARTICIPANT-entry-1' }))

    await expect(api.getCheckInQr('entry-1')).rejects.toThrow(/missing required fields/)
  })

  it('submits a result query and surfaces the open-query limit code', async () => {
    const { api } = await import('../lib/api.ts')
    const ok = respond({ id: 'q1', raceId: 'race-1', participantId: 'entry-1', message: 'Missing 10 km split', status: 'OPEN' })
    vi.stubGlobal('fetch', ok)
    const query = await api.submitResultQuery('race-1', 'Missing 10 km split')
    expect(ok.mock.calls[0][0]).toMatch(/\/results\/my\/race-1\/queries$/)
    expect(JSON.parse(ok.mock.calls[0][1].body)).toEqual({ message: 'Missing 10 km split' })
    expect(query.status).toBe('OPEN')

    vi.stubGlobal('fetch', respond({ code: 'RESULT_QUERY_LIMIT', message: 'limit' }, 409))
    await expect(api.submitResultQuery('race-1', 'Another question here')).rejects.toMatchObject({ code: 'RESULT_QUERY_LIMIT' })
  })

  it('builds a certificate link only when the result carries a token', async () => {
    const { certificateUrl } = await import('../lib/api.ts')
    expect(certificateUrl({ raceId: 'race-1', participantId: 'entry-1', certificateToken: null })).toBeNull()
    expect(certificateUrl({ raceId: 'race-1', participantId: 'entry-1', certificateToken: 'a.b' }))
      .toMatch(/\/public\/races\/race-1\/results\/entry-1\/certificate\?token=a\.b$/)
  })
})
