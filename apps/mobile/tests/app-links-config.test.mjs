/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { createRequire } from 'node:module'
import { describe, expect, it } from 'vitest'

const require = createRequire(import.meta.url)
const appConfig = require('../app.config.js')
const base = { name: 'Race Tracker', scheme: 'racetracker', ios: { bundleIdentifier: 'com.racetracker.app' }, android: { package: 'com.racetracker.app' } }

describe('universal and app links config', () => {
  it('declares verified links for the email paths on the configured HTTPS web host', () => {
    const config = appConfig.withAppLinks(base, 'https://race.example.com/')

    expect(config.ios.associatedDomains).toEqual(['applinks:race.example.com'])
    expect(config.ios.bundleIdentifier).toBe('com.racetracker.app')
    const [filter] = config.android.intentFilters
    expect(filter.autoVerify).toBe(true)
    expect(filter.data).toEqual([
      { scheme: 'https', host: 'race.example.com', pathPrefix: '/verify-email' },
      { scheme: 'https', host: 'race.example.com', pathPrefix: '/reset-password' },
    ])
  })

  it('leaves the config untouched without an HTTPS web URL', () => {
    expect(appConfig.withAppLinks(base, undefined)).toBe(base)
    expect(appConfig.withAppLinks(base, 'http://localhost:5173')).toBe(base)
  })

  it('fails the build on a malformed web URL instead of shipping broken links', () => {
    expect(() => appConfig.withAppLinks(base, 'race.example.com')).toThrow(/EXPO_PUBLIC_WEB_URL/)
  })

  it('routes the email link paths to in-app screens', () => {
    expect(appConfig.APP_LINK_PATHS).toEqual(['/verify-email', '/reset-password'])
  })
})
