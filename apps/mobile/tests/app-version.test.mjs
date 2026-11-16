/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { describe, expect, it, vi } from 'vitest'

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: { getItem: vi.fn(async () => null), setItem: vi.fn(), removeItem: vi.fn() },
}))
vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(async () => null), setItemAsync: vi.fn(), deleteItemAsync: vi.fn() }))

describe('minimum app version', () => {
  it('compares versions numerically and never blocks on unreadable values', async () => {
    const { isVersionBelow } = await import('../lib/appVersion.ts')
    expect(isVersionBelow('1.9.0', '1.10.0')).toBe(true)
    expect(isVersionBelow('1.10.0', '1.9.9')).toBe(false)
    expect(isVersionBelow('1.4', '1.4.0')).toBe(false)
    expect(isVersionBelow('1.4.0-beta.1', '1.4.1')).toBe(true)
    expect(isVersionBelow('dev', '9.0.0')).toBe(false)
    expect(isVersionBelow('1.0.0', null)).toBe(false)
  })

  it('picks the store link for the running platform', async () => {
    const { storeUrlFor } = await import('../lib/appVersion.ts')
    const config = { minimumVersion: '2.0.0', latestVersion: null, iosStoreUrl: 'https://apps.example/ios', androidStoreUrl: 'https://apps.example/android' }
    expect(storeUrlFor(config, 'ios')).toBe('https://apps.example/ios')
    expect(storeUrlFor(config, 'android')).toBe('https://apps.example/android')
    expect(storeUrlFor(config, 'web')).toBeNull()
  })

  it('sends the app version and reports a 426 update-required response', async () => {
    const api = await import('../lib/api.ts')
    const onUpdate = vi.fn()
    api.setAppIdentity({ version: '1.0.0', platform: 'android' })
    api.setUpdateRequiredHandler(onUpdate)
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ code: 'APP_UPDATE_REQUIRED', message: 'Update' }), { status: 426 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.api.getAppConfig()).rejects.toMatchObject({ status: 426, code: 'APP_UPDATE_REQUIRED' })
    expect(onUpdate).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][1].headers['X-App-Version']).toBe('1.0.0')
    expect(fetchMock.mock.calls[0][1].headers['X-App-Platform']).toBe('android')
    vi.unstubAllGlobals()
  })
})
