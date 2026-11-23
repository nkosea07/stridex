/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { createRequire } from 'node:module'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { API_CONTRACT } from '@race-tracker/contracts'

const mocks = vi.hoisted(() => ({
  requestForeground: vi.fn(),
  requestBackground: vi.fn(),
  getForeground: vi.fn(),
  startUpdates: vi.fn(),
  database: {
    execAsync: vi.fn(),
    getFirstAsync: vi.fn(),
    getAllAsync: vi.fn(),
    runAsync: vi.fn(),
  },
}))

vi.mock('expo-sqlite', () => ({ openDatabaseAsync: vi.fn(async () => mocks.database) }))
vi.mock('expo-location', () => ({
  Accuracy: { BestForNavigation: 1, Balanced: 2 },
  requestForegroundPermissionsAsync: mocks.requestForeground,
  requestBackgroundPermissionsAsync: mocks.requestBackground,
  getForegroundPermissionsAsync: mocks.getForeground,
  startLocationUpdatesAsync: mocks.startUpdates,
  hasStartedLocationUpdatesAsync: vi.fn(async () => false),
  stopLocationUpdatesAsync: vi.fn(),
}))
vi.mock('expo-background-task', () => ({
  BackgroundTaskStatus: { Available: 1 },
  BackgroundTaskResult: { Success: 1, Failed: 2 },
  getStatusAsync: vi.fn(async () => 2),
  registerTaskAsync: vi.fn(),
  unregisterTaskAsync: vi.fn(),
}))
vi.mock('expo-task-manager', () => ({ defineTask: vi.fn(), isTaskRegisteredAsync: vi.fn(async () => false) }))
vi.mock('../lib/api.ts', () => ({ api: { sendLocationsBatch: vi.fn() }, ApiRequestError: class extends Error {} }))

const require = createRequire(import.meta.url)
const appConfig = require('../app.config.js')
const expo = require('../app.json').expo
const pluginOptions = (name) => expo.plugins.find((plugin) => Array.isArray(plugin) && plugin[0] === name)?.[1]

describe('store review configuration', () => {
  it('tracks with while-in-use location through a location foreground service and iOS background mode', () => {
    const location = pluginOptions('expo-location')
    expect(location.isIosBackgroundLocationEnabled).toBe(true)
    expect(location.isAndroidForegroundServiceEnabled).toBe(true)
    expect(location.isAndroidBackgroundLocationEnabled).toBe(false)
    expect(expo.android.permissions).toEqual(expect.arrayContaining(['FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_LOCATION']))
    expect(expo.android.permissions).not.toContain('ACCESS_BACKGROUND_LOCATION')
    for (const key of ['locationWhenInUsePermission', 'locationAlwaysAndWhenInUsePermission', 'locationAlwaysPermission']) {
      expect(location[key]).toMatch(/organizers/)
      expect(location[key]).toMatch(/spectators/)
    }
  })

  it('does not request permissions the app never uses', () => {
    const camera = pluginOptions('expo-camera')
    expect(camera.microphonePermission).toBe(false)
    expect(camera.recordAudioAndroid).toBe(false)
    expect(expo.android.blockedPermissions).toEqual(expect.arrayContaining([
      'android.permission.ACCESS_BACKGROUND_LOCATION',
      'android.permission.RECORD_AUDIO',
      'android.permission.READ_EXTERNAL_STORAGE',
      'android.permission.WRITE_EXTERNAL_STORAGE',
      'android.permission.SYSTEM_ALERT_WINDOW',
    ]))
  })

  it('declares iOS export compliance, HTTPS-only transport and a privacy manifest without tracking', () => {
    expect(expo.ios.config.usesNonExemptEncryption).toBe(false)
    expect(expo.ios.infoPlist.NSAppTransportSecurity.NSAllowsArbitraryLoads).toBe(false)
    expect(expo.ios.supportsTablet).toBe(false)
    const manifest = expo.ios.privacyManifests
    expect(manifest.NSPrivacyTracking).toBe(false)
    expect(manifest.NSPrivacyTrackingDomains).toEqual([])
    expect(manifest.NSPrivacyAccessedAPITypes.map((entry) => entry.NSPrivacyAccessedAPIType)).toEqual(expect.arrayContaining([
      'NSPrivacyAccessedAPICategoryUserDefaults',
      'NSPrivacyAccessedAPICategoryFileTimestamp',
      'NSPrivacyAccessedAPICategorySystemBootTime',
      'NSPrivacyAccessedAPICategoryDiskSpace',
    ]))
    for (const entry of manifest.NSPrivacyCollectedDataTypes) {
      expect(entry.NSPrivacyCollectedDataTypeTracking).toBe(false)
    }
  })

  it('translates every iOS permission prompt into each supported language', () => {
    const languages = API_CONTRACT.i18n.supportedLocales.filter((locale) => locale !== API_CONTRACT.i18n.defaultLocale)
    expect(Object.keys(expo.locales).sort()).toEqual([...languages].sort())
    for (const language of languages) {
      const strings = require(`../${expo.locales[language]}`).ios
      for (const key of ['NSLocationWhenInUseUsageDescription', 'NSLocationAlwaysAndWhenInUseUsageDescription',
        'NSLocationAlwaysUsageDescription', 'NSCameraUsageDescription']) {
        expect(strings[key], `${language} ${key}`).toMatch(/\S/)
        expect(strings[key], `${language} ${key}`).not.toMatch(/["\\]/)
      }
    }
  })
})

describe('release build settings', () => {
  const config = { plugins: ['expo-router', 'expo-notifications', ['expo-camera', { cameraPermission: 'x' }]] }
  const urls = { EXPO_PUBLIC_API_URL: 'https://api.race.example/api', EXPO_PUBLIC_WEB_URL: 'https://race.example' }

  it('fails store builds that would point at a local or insecure API', () => {
    expect(() => appConfig.withReleaseSettings(config, { EAS_BUILD: 'true', EAS_BUILD_PROFILE: 'production' }))
      .toThrow(/EXPO_PUBLIC_API_URL and EXPO_PUBLIC_WEB_URL must be set to https URLs/)
    expect(() => appConfig.withReleaseSettings(config, {
      EAS_BUILD: 'true', EAS_BUILD_PROFILE: 'production', ...urls, EXPO_PUBLIC_API_URL: 'http://10.0.2.2:8081/api',
    })).toThrow(/EXPO_PUBLIC_API_URL must be set/)
    expect(() => appConfig.withReleaseSettings(config, { EAS_BUILD_PROFILE: 'production' })).not.toThrow()
  })

  it('uses the production push environment for release profiles only', () => {
    const production = appConfig.withReleaseSettings(config, { EAS_BUILD: 'true', EAS_BUILD_PROFILE: 'production', ...urls })
    expect(production.plugins).toContainEqual(['expo-notifications', { mode: 'production' }])
    expect(production.plugins).toContainEqual(['expo-camera', { cameraPermission: 'x' }])
    expect(appConfig.withReleaseSettings(config, { EAS_BUILD_PROFILE: 'preview' }).plugins)
      .toContainEqual(['expo-notifications', { mode: 'production' }])
    expect(appConfig.withReleaseSettings(config, { EAS_BUILD_PROFILE: 'development' })).toBe(config)
    expect(appConfig.withReleaseSettings(config, {})).toBe(config)
  })
})

describe('push and build numbering', () => {
  const config = { android: { package: 'com.racetracker.app' } }
  const store = { EAS_BUILD: 'true', EAS_BUILD_PROFILE: 'production' }

  it('builds Android with the Firebase config from the EAS file variable or a local file', () => {
    expect(appConfig.withAndroidPushCredentials(config, { ...store, GOOGLE_SERVICES_JSON: '/eas/secrets/google-services.json' }, () => false)
      .android).toEqual({ package: 'com.racetracker.app', googleServicesFile: '/eas/secrets/google-services.json' })
    expect(appConfig.withAndroidPushCredentials(config, {}, () => true).android.googleServicesFile).toBe('./google-services.json')
  })

  it('fails store builds that could not receive Android push notifications', () => {
    expect(() => appConfig.withAndroidPushCredentials(config, store, () => false)).toThrow(/GOOGLE_SERVICES_JSON/)
    expect(appConfig.withAndroidPushCredentials(config, { EAS_BUILD_PROFILE: 'production' }, () => false)).toBe(config)
    expect(appConfig.withAndroidPushCredentials(config, { EAS_BUILD: 'true', EAS_BUILD_PROFILE: 'preview' }, () => false)).toBe(config)
  })

  it('lets EAS assign increasing build numbers to every store build', () => {
    const eas = require('../eas.json')
    expect(eas.cli.appVersionSource).toBe('remote')
    expect(eas.build.production.autoIncrement).toBe(true)
  })
})

describe('tracking permission flow', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    mocks.database.execAsync.mockResolvedValue(undefined)
    mocks.database.getFirstAsync.mockResolvedValue({ quarantined: 0 })
    mocks.database.runAsync.mockResolvedValue(undefined)
  })

  it('asks only for while-in-use location and shows the localized tracking notification', async () => {
    mocks.requestForeground.mockResolvedValue({ status: 'granted', granted: true, canAskAgain: true })
    const tracking = await import('../lib/tracking-service.ts')

    await tracking.startBackgroundTracking('race-1', { title: 'Kutevera kwakabatidzwa', body: 'Nzvimbo yako iri kugovaniswa.' })

    expect(mocks.requestBackground).not.toHaveBeenCalled()
    expect(mocks.startUpdates).toHaveBeenCalledWith(tracking.LOCATION_TRACKING_TASK, expect.objectContaining({
      showsBackgroundLocationIndicator: true,
      foregroundService: expect.objectContaining({
        notificationTitle: 'Kutevera kwakabatidzwa',
        notificationBody: 'Nzvimbo yako iri kugovaniswa.',
      }),
    }))
  })

  it('reports a denied permission without starting location updates', async () => {
    mocks.requestForeground.mockResolvedValue({ status: 'denied', granted: false, canAskAgain: false })
    const tracking = await import('../lib/tracking-service.ts')

    const failure = await tracking.startBackgroundTracking('race-1', { title: 't', body: 'b' }).catch((error) => error)

    expect(failure).toBeInstanceOf(tracking.LocationPermissionError)
    expect(failure.canAskAgain).toBe(false)
    expect(mocks.startUpdates).not.toHaveBeenCalled()
  })

  it('shows the location disclosure until location access has been granted', async () => {
    const tracking = await import('../lib/tracking-service.ts')
    mocks.getForeground.mockResolvedValueOnce({ granted: false })
    await expect(tracking.needsLocationDisclosure()).resolves.toBe(true)
    mocks.getForeground.mockResolvedValueOnce({ granted: true })
    await expect(tracking.needsLocationDisclosure()).resolves.toBe(false)
  })
})
