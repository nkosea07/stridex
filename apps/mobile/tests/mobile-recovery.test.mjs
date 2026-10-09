/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  secureValues: new Map(),
  legacyValues: new Map(),
  secureGet: vi.fn(),
  secureSet: vi.fn(),
  secureDelete: vi.fn(),
  legacyGet: vi.fn(),
  legacySet: vi.fn(),
  legacyRemove: vi.fn(),
    openDatabase: vi.fn(),
    sendLocationsBatch: vi.fn(),
  database: {
    execAsync: vi.fn(),
    getAllAsync: vi.fn(),
    runAsync: vi.fn(),
  },
}))

vi.mock('expo-secure-store', () => ({
  getItemAsync: mocks.secureGet,
  setItemAsync: mocks.secureSet,
  deleteItemAsync: mocks.secureDelete,
}))

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: {
    getItem: mocks.legacyGet,
    setItem: mocks.legacySet,
    removeItem: mocks.legacyRemove,
  },
}))

vi.mock('expo-sqlite', () => ({ openDatabaseAsync: mocks.openDatabase }))
vi.mock('expo-location', () => ({
  Accuracy: { BestForNavigation: 1, Balanced: 2 },
  watchPositionAsync: vi.fn(),
  requestForegroundPermissionsAsync: vi.fn(),
  requestBackgroundPermissionsAsync: vi.fn(),
  hasStartedLocationUpdatesAsync: vi.fn(),
  startLocationUpdatesAsync: vi.fn(),
  stopLocationUpdatesAsync: vi.fn(),
}))
vi.mock('expo-background-task', () => ({
  BackgroundTaskStatus: { Available: 1 },
  getStatusAsync: vi.fn(),
  registerTaskAsync: vi.fn(),
  unregisterTaskAsync: vi.fn(),
}))
vi.mock('expo-task-manager', () => ({
  defineTask: vi.fn(),
  isTaskRegisteredAsync: vi.fn(),
}))
function createLocation(id, raceId, timestamp) {
  return {
    id,
    raceId,
    clientPointId: `point-${id}`,
    latitude: -33.9 + id / 1000,
    longitude: 18.4,
    altitude: null,
    accuracy: 5,
    speed: 4,
    heading: null,
    timestamp,
    synced: 0,
    syncAttempts: 0,
    lastSyncError: null,
    updatedAt: null,
  }
}

function connectPersistentQueue(rows) {
  mocks.database.execAsync.mockResolvedValue(undefined)
  mocks.database.getAllAsync.mockImplementation(async (_query, parameters) =>
    rows.filter((row) => row.raceId === parameters[0] && row.synced === 0).slice(0, 100),
  )
  mocks.database.runAsync.mockImplementation(async (query, parameters) => {
    if (query.includes('SET synced = 1')) {
      for (const id of parameters.slice(1)) {
        const row = rows.find((candidate) => candidate.id === id)
        if (row) row.synced = 1
      }
    }
    if (query.includes('syncAttempts = syncAttempts + 1')) {
      for (const id of parameters.slice(2)) {
        const row = rows.find((candidate) => candidate.id === id)
        if (row) {
          row.syncAttempts += 1
          row.lastSyncError = parameters[0]
        }
      }
    }
    return { changes: parameters.length }
  })
  mocks.openDatabase.mockResolvedValue(mocks.database)
}

beforeEach(() => {
  vi.resetModules()
  vi.unstubAllGlobals()
  for (const mock of [
    mocks.secureGet, mocks.secureSet, mocks.secureDelete,
    mocks.legacyGet, mocks.legacySet, mocks.legacyRemove,
    mocks.openDatabase, mocks.sendLocationsBatch,
    mocks.database.execAsync, mocks.database.getAllAsync, mocks.database.runAsync,
  ]) mock.mockReset()
  mocks.secureValues.clear()
  mocks.legacyValues.clear()
  mocks.secureGet.mockImplementation(async (key) => mocks.secureValues.get(key) ?? null)
  mocks.secureSet.mockImplementation(async (key, value) => { mocks.secureValues.set(key, value) })
  mocks.secureDelete.mockImplementation(async (key) => { mocks.secureValues.delete(key) })
  mocks.legacyGet.mockImplementation(async (key) => mocks.legacyValues.get(key) ?? null)
  mocks.legacySet.mockImplementation(async (key, value) => { mocks.legacyValues.set(key, value) })
  mocks.legacyRemove.mockImplementation(async (key) => { mocks.legacyValues.delete(key) })
})

describe('mobile session recovery', () => {
  it('moves legacy access credentials to SecureStore before an authenticated request', async () => {
    mocks.legacyValues.set('auth_token', 'legacy-access-token')
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'user-1' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await api.getCurrentUser()

    expect(mocks.secureValues.get('auth_token')).toBe('legacy-access-token')
    expect(mocks.legacyValues.has('auth_token')).toBe(false)
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer legacy-access-token')
  })

  it('shares one refresh across concurrent 401s and persists the rotated tokens', async () => {
    mocks.secureValues.set('auth_token', 'expired-access')
    mocks.secureValues.set('refresh_token', 'old-refresh')
    const fetchMock = vi.fn(async (input, init) => {
      if (String(input).endsWith('/auth/refresh')) {
        return new Response(JSON.stringify({ token: 'new-access', refreshToken: 'new-refresh' }), { status: 200 })
      }
      if (init.headers.Authorization === 'Bearer expired-access') {
        return new Response(JSON.stringify({ message: 'expired' }), { status: 401 })
      }
      return new Response(JSON.stringify({ content: [] }), { status: 200 })
    })
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await Promise.all([api.getRaces(), api.getRaces()])

    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/auth/refresh'))).toHaveLength(1)
    expect(mocks.secureValues.get('auth_token')).toBe('new-access')
    expect(mocks.secureValues.get('refresh_token')).toBe('new-refresh')
  })

  it('clears both secure and legacy credentials when refresh is expired', async () => {
    mocks.secureValues.set('auth_token', 'expired-access')
    mocks.secureValues.set('refresh_token', 'expired-refresh')
    mocks.legacyValues.set('auth_token', 'stale-legacy-access')
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'expired' }), { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await expect(api.getRaces()).rejects.toThrow()

    expect(mocks.secureValues.has('auth_token')).toBe(false)
    expect(mocks.secureValues.has('refresh_token')).toBe(false)
    expect(mocks.legacyValues.has('auth_token')).toBe(false)
  })
})

describe('offline location queue', () => {
  it('retains race-scoped locations and records retry state when the network is down', async () => {
    const rows = [createLocation(11, 'race-a', '2026-10-05T10:00:00.000Z')]
    connectPersistentQueue(rows)
    mocks.sendLocationsBatch.mockRejectedValue(new Error('offline'))
    const { api } = await import('../lib/api.ts')
    vi.spyOn(api, 'sendLocationsBatch').mockImplementation(mocks.sendLocationsBatch)
    const { syncPendingLocations } = await import('../lib/tracking-service.ts')

    await syncPendingLocations('race-a')

    expect(mocks.database.getAllAsync).toHaveBeenCalledWith(expect.stringContaining('raceId = ?'), ['race-a'])
    expect(rows[0].synced).toBe(0)
    expect(rows[0].syncAttempts).toBe(1)
    expect(rows[0].lastSyncError).toBe('offline')
  })

  it('recovers a persisted row after module restart and does not resend it once acknowledged', async () => {
    const rows = [createLocation(21, 'race-b', '2026-10-05T10:00:00.000Z')]
    connectPersistentQueue(rows)
    mocks.sendLocationsBatch.mockResolvedValue(undefined)
    const { api } = await import('../lib/api.ts')
    vi.spyOn(api, 'sendLocationsBatch').mockImplementation(mocks.sendLocationsBatch)

    let tracking = await import('../lib/tracking-service.ts')
    await tracking.syncPendingLocations('race-b')
    expect(rows[0].synced).toBe(1)

    vi.resetModules()
    const restartedApi = await import('../lib/api.ts')
    vi.spyOn(restartedApi.api, 'sendLocationsBatch').mockImplementation(mocks.sendLocationsBatch)
    tracking = await import('../lib/tracking-service.ts')
    await tracking.syncPendingLocations('race-b')

    expect(mocks.sendLocationsBatch).toHaveBeenCalledTimes(1)
    expect(mocks.database.getAllAsync).toHaveBeenCalledWith(expect.stringContaining('raceId = ?'), ['race-b'])
  })
})

describe('race registration', () => {
  it('submits waiver, custom answers, and team grouping', async () => {
    mocks.secureValues.set('auth_token', 'access-token')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: 'registration-1', raceId: 'race-a', status: 'pending_review' }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await api.registerForRace('race-a', {
      categoryId: 'cat-1',
      emergencyContact: { name: 'Ada Runner', phone: '0710000000', relationship: 'Self' },
      waiverAccepted: true,
      shirtSize: 'M',
      teamName: 'Falcons',
      answers: [{ fieldId: 'field-1', value: 'yes' }],
    })

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toContain('/races/race-a/participants/register')
    const body = JSON.parse(String(init.body))
    expect(body.waiverAccepted).toBe(true)
    expect(body.shirtSize).toBe('M')
    expect(body.teamName).toBe('Falcons')
    expect(body.answers).toEqual([{ fieldId: 'field-1', value: 'yes' }])
  })

  it('loads registration fields from the public endpoint', async () => {
    mocks.secureValues.set('auth_token', 'access-token')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ id: 'field-1', label: 'Shirt size' }]), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    const fields = await api.getRegistrationFields('race-a')

    expect(String(fetchMock.mock.calls[0][0])).toContain('/public/races/race-a/registration-fields')
    expect(fields[0].label).toBe('Shirt size')
  })
})
