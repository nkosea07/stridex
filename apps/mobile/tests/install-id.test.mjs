/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { afterEach, describe, expect, it, vi } from 'vitest'

const storage = new Map()
vi.mock('@react-native-async-storage/async-storage', () => ({
  default: {
    getItem: vi.fn(async (key) => storage.get(key) ?? null),
    setItem: vi.fn(async (key, value) => { storage.set(key, value) }),
    removeItem: vi.fn(async (key) => { storage.delete(key) }),
  },
}))
vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(async () => null), setItemAsync: vi.fn(), deleteItemAsync: vi.fn() }))

describe('anonymous install id for public rate limiting', () => {
  afterEach(async () => {
    vi.unstubAllGlobals()
    storage.clear()
    const { resetInstallIdCacheForTests } = await import('../lib/installId.ts')
    resetInstallIdCacheForTests()
  })

  it('creates RFC 4122 version 4 identifiers', async () => {
    const { createInstallId, isInstallId } = await import('../lib/installId.ts')

    const ids = Array.from({ length: 50 }, () => createInstallId())

    expect(ids.every(isInstallId)).toBe(true)
    expect(new Set(ids).size).toBe(50)
    expect(isInstallId(createInstallId(() => 0.999999))).toBe(true)
    expect(isInstallId('not-a-uuid')).toBe(false)
  })

  it('keeps one id per install and replaces a corrupted stored value', async () => {
    storage.set('race_tracker_install_id', 'garbage')
    const { getInstallId, isInstallId, resetInstallIdCacheForTests } = await import('../lib/installId.ts')

    const first = await getInstallId()
    resetInstallIdCacheForTests()
    const second = await getInstallId()

    expect(isInstallId(first)).toBe(true)
    expect(second).toBe(first)
    expect(storage.get('race_tracker_install_id')).toBe(first)
  })

  it('sends the contract header on public tracking requests', async () => {
    const { api } = await import('../lib/api.ts')
    const { API_CONTRACT } = await import('@race-tracker/contracts')
    const fetchMock = vi.fn(async () => new Response('[]', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.getRaceLeaderboard('race-1', 10)

    const headers = fetchMock.mock.calls[0][1].headers
    expect(headers[API_CONTRACT.headers.publicClientId]).toMatch(/^[0-9a-f-]{36}$/)
    expect(headers.Authorization).toBeUndefined()
  })
})

describe('email link screens helpers', () => {
  it('validates new passwords against the backend limits', async () => {
    const { validateNewPassword, readLinkToken } = await import('../lib/passwordRules.ts')

    expect(validateNewPassword('short', 'short')).toBe('tooShort')
    expect(validateNewPassword('x'.repeat(129), 'x'.repeat(129))).toBe('tooLong')
    expect(validateNewPassword('long-enough', 'long-enougH')).toBe('mismatch')
    expect(validateNewPassword('long-enough', 'long-enough')).toBeNull()
    expect(readLinkToken([' abc ', 'def'])).toBe('abc')
    expect(readLinkToken('   ')).toBeNull()
    expect(readLinkToken(undefined)).toBeNull()
  })

  it('verifies the email and resets the password through the contract endpoints without auth refresh', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ message: 'ok' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.verifyEmail('a b&c')
    await api.resetPassword('reset-token', 'new-password')

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/auth\/verify-email\?token=a%20b%26c$/)
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/auth\/reset-password$/)
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ token: 'reset-token', newPassword: 'new-password' })
  })
})
