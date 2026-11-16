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
    hasStartedLocationUpdates: vi.fn(),
    stopLocationUpdates: vi.fn(),
    isTaskRegistered: vi.fn(),
    unregisterBackgroundTask: vi.fn(),
  database: {
    execAsync: vi.fn(),
    getFirstAsync: vi.fn(),
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
  hasStartedLocationUpdatesAsync: mocks.hasStartedLocationUpdates,
  startLocationUpdatesAsync: vi.fn(),
  stopLocationUpdatesAsync: mocks.stopLocationUpdates,
}))
vi.mock('expo-background-task', () => ({
  BackgroundTaskStatus: { Available: 1 },
  getStatusAsync: vi.fn(),
  registerTaskAsync: vi.fn(),
  unregisterTaskAsync: mocks.unregisterBackgroundTask,
}))
vi.mock('expo-task-manager', () => ({
  defineTask: vi.fn(),
  isTaskRegisteredAsync: mocks.isTaskRegistered,
}))
vi.mock('expo-notifications', () => ({
  AndroidImportance: { HIGH: 4 },
  setNotificationHandler: vi.fn(),
  setNotificationChannelAsync: vi.fn(),
  getPermissionsAsync: vi.fn(),
  requestPermissionsAsync: vi.fn(),
  getDevicePushTokenAsync: vi.fn(),
  addNotificationResponseReceivedListener: vi.fn(),
  addNotificationReceivedListener: vi.fn(),
}))
vi.mock('react-native', () => ({ Platform: { OS: 'ios' } }))
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
    rows.filter((row) => row.raceId === parameters[0] && row.synced === (parameters[1] ?? 0)).slice(0, 100),
  )
  mocks.database.runAsync.mockImplementation(async (query, parameters) => {
    const match = /^UPDATE locations SET ([\s\S]*) WHERE id IN/.exec(query.trim())
    if (match) {
      let parameterIndex = 0
      const assignments = match[1].split(',').map((assignment) => {
        const [field, expression] = assignment.split('=').map((part) => part.trim())
        if (expression === '?') return { field, value: parameters[parameterIndex++] }
        return { field, expression }
      })
      for (const id of parameters.slice(parameterIndex)) {
        const row = rows.find((candidate) => candidate.id === id)
        if (!row) continue
        for (const { field, value, expression } of assignments) {
          if (field === 'syncAttempts' && expression === 'syncAttempts + 1') row.syncAttempts += 1
          else if (expression === 'NULL') row[field] = null
          else if (expression === undefined) row[field] = value
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
    mocks.hasStartedLocationUpdates, mocks.stopLocationUpdates,
    mocks.isTaskRegistered, mocks.unregisterBackgroundTask,
    mocks.database.execAsync, mocks.database.getFirstAsync, mocks.database.getAllAsync, mocks.database.runAsync,
  ]) mock.mockReset()
  mocks.secureValues.clear()
  mocks.legacyValues.clear()
  mocks.secureGet.mockImplementation(async (key) => mocks.secureValues.get(key) ?? null)
  mocks.secureSet.mockImplementation(async (key, value) => { mocks.secureValues.set(key, value) })
  mocks.secureDelete.mockImplementation(async (key) => { mocks.secureValues.delete(key) })
  mocks.legacyGet.mockImplementation(async (key) => mocks.legacyValues.get(key) ?? null)
  mocks.legacySet.mockImplementation(async (key, value) => { mocks.legacyValues.set(key, value) })
  mocks.legacyRemove.mockImplementation(async (key) => { mocks.legacyValues.delete(key) })
  mocks.hasStartedLocationUpdates.mockResolvedValue(false)
  mocks.stopLocationUpdates.mockResolvedValue(undefined)
  mocks.isTaskRegistered.mockResolvedValue(false)
  mocks.unregisterBackgroundTask.mockResolvedValue(undefined)
  mocks.database.getFirstAsync.mockResolvedValue({ quarantined: 0 })
})

describe('mobile session recovery', () => {
  it('cancels pending deletion without installing or sending a session', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'If eligible' }), {
      status: 200,
    }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await api.cancelDeletionWithPassword('runner@example.com', 'password')

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toContain('/auth/deletion/cancel')
    expect(init.headers.Authorization).toBeUndefined()
    expect(JSON.parse(init.body)).toEqual({ email: 'runner@example.com', password: 'password' })
    expect(mocks.secureValues.size).toBe(0)
  })
  it('moves legacy access credentials to SecureStore before an authenticated request', async () => {
    mocks.legacyValues.set('auth_token', 'legacy-access-token')
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 'user-1',
      email: 'runner@example.com',
      firstName: 'Ada',
      lastName: 'Runner',
      role: 'ATHLETE',
      capabilities: ['ATHLETE'],
      preferredCapability: 'ATHLETE',
    }), { status: 200 }))
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
        return new Response(JSON.stringify({
          token: 'new-access',
          refreshToken: 'new-refresh',
          user: {
            id: 'user-1',
            email: 'runner@example.com',
            firstName: 'Ada',
            lastName: 'Runner',
            role: 'ATHLETE',
            capabilities: ['ATHLETE'],
            preferredCapability: 'ATHLETE',
          },
        }), { status: 200 })
      }
      if (init.headers.Authorization === 'Bearer expired-access') {
        return new Response(JSON.stringify({ message: 'expired' }), { status: 401 })
      }
      return new Response(JSON.stringify({
        content: [], number: 0, size: 50, totalElements: 0, totalPages: 0,
      }), { status: 200 })
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
    mocks.legacyValues.set('user', JSON.stringify({ id: 'user-1' }))
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'expired' }), { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await expect(api.getRaces()).rejects.toThrow()

    expect(mocks.secureValues.has('auth_token')).toBe(false)
    expect(mocks.secureValues.has('refresh_token')).toBe(false)
    expect(mocks.legacyValues.has('auth_token')).toBe(false)
    expect(mocks.legacyValues.has('user')).toBe(false)
  })

  it('notifies the auth store when refresh fails so Zustand state can be cleared', async () => {
    mocks.secureValues.set('auth_token', 'expired-access')
    mocks.secureValues.set('refresh_token', 'expired-refresh')
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'expired' }), { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api, setSessionInvalidatedHandler } = await import('../lib/api.ts')
    const resetAuthState = vi.fn(async () => {
      expect(mocks.secureValues.has('auth_token')).toBe(true)
    })
    setSessionInvalidatedHandler(resetAuthState)

    await expect(api.getRaces()).rejects.toThrow()

    expect(resetAuthState).toHaveBeenCalledOnce()
  })
})

describe('terminal session cleanup', () => {
  it('runs tracking cleanup in the finally path and coalesces concurrent invalidations', async () => {
    const events = []
    const pushCleanup = vi.fn(async () => {
      events.push('push')
      throw new Error('push unregister failed')
    })
    const trackingCleanup = vi.fn(async () => {
      events.push('tracking')
    })
    const errors = []
    const { createSessionCleanup } = await import('../lib/session-cleanup.ts')
    const cleanup = createSessionCleanup({
      unregisterPushDevice: pushCleanup,
      clearTracking: trackingCleanup,
      onError: (scope) => errors.push(scope),
    })

    await Promise.all([cleanup(), cleanup()])

    expect(pushCleanup).toHaveBeenCalledOnce()
    expect(trackingCleanup).toHaveBeenCalledOnce()
    expect(events).toEqual(['push', 'tracking'])
    expect(errors).toEqual(['push'])
  })

  it('clears the local push token even when the backend unregister request fails', async () => {
    mocks.legacyValues.set('push_device_token', 'device-token')
    mocks.secureValues.set('auth_token', 'access-token')
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'rejected' }), { status: 500 }))
    vi.stubGlobal('fetch', fetchMock)
    const { unregisterCurrentPushDevice } = await import('../lib/notifications.ts')

    await unregisterCurrentPushDevice()

    expect(mocks.legacyValues.has('push_device_token')).toBe(false)
    expect(String(fetchMock.mock.calls[0][0])).toContain('/notifications/devices')
  })

  it('stops native tracking and transactionally clears the queue and sessions', async () => {
    mocks.openDatabase.mockResolvedValue(mocks.database)
    mocks.database.execAsync.mockResolvedValue(undefined)
    mocks.hasStartedLocationUpdates.mockResolvedValue(true)
    mocks.isTaskRegistered.mockResolvedValue(true)
    const { clearLocalTrackingData, isTrackingQuarantined } = await import('../lib/tracking-service.ts')

    await clearLocalTrackingData()

    expect(isTrackingQuarantined()).toBe(false)
    expect(mocks.stopLocationUpdates).toHaveBeenCalledOnce()
    expect(mocks.unregisterBackgroundTask).toHaveBeenCalledOnce()
    expect(mocks.database.execAsync.mock.calls.some(([sql]) => (
      String(sql).includes('DELETE FROM locations') && String(sql).includes('DELETE FROM tracking_sessions')
    ))).toBe(true)
    expect(mocks.database.execAsync.mock.calls.some(([sql]) => String(sql).includes('UPDATE tracking_cleanup_state'))).toBe(true)
  })

  it('persists quarantine when the purge fails and blocks tracking after reload', async () => {
    mocks.openDatabase.mockResolvedValue(mocks.database)
    mocks.database.getFirstAsync.mockResolvedValue({ quarantined: 0 })
    mocks.database.execAsync.mockImplementation(async (sql) => {
      if (String(sql).includes('BEGIN TRANSACTION')) throw new Error('purge failed')
    })
    const { clearLocalTrackingData, isTrackingQuarantined } = await import('../lib/tracking-service.ts')

    await expect(clearLocalTrackingData()).rejects.toThrow('purge failed')
    expect(isTrackingQuarantined()).toBe(true)
    expect(mocks.database.runAsync.mock.calls.some(([sql, params]) => (
      String(sql).includes('tracking_cleanup_state') && String(sql).includes('quarantined = 1')
        && typeof params[0] === 'string'
    ))).toBe(true)

    vi.resetModules()
    mocks.database.execAsync.mockResolvedValue(undefined)
    mocks.database.getFirstAsync.mockResolvedValue({ quarantined: 1 })
    const restartedTracking = await import('../lib/tracking-service.ts')

    await expect(restartedTracking.setActiveRaceId('new-account-race')).rejects.toThrow(/quarantined/)
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

    expect(mocks.database.getAllAsync).toHaveBeenCalledWith(expect.stringContaining('raceId = ?'), ['race-a', 0])
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
    expect(mocks.database.getAllAsync).toHaveBeenCalledWith(expect.stringContaining('raceId = ?'), ['race-b', 0])
  })
})

describe('location upload failures', () => {
  async function loadTracking(rows, error) {
    connectPersistentQueue(rows)
    const { api, ApiRequestError } = await import('../lib/api.ts')
    mocks.sendLocationsBatch.mockRejectedValue(error(ApiRequestError))
    vi.spyOn(api, 'sendLocationsBatch').mockImplementation(mocks.sendLocationsBatch)
    return import('../lib/tracking-service.ts')
  }

  it('sets aside a batch the server permanently rejects so newer points can upload', async () => {
    const rows = [
      createLocation(31, 'race-c', '2026-10-05T10:00:00.000Z'),
      createLocation(32, 'race-c', '2026-10-05T10:05:00.000Z'),
    ]
    const tracking = await loadTracking(rows, (ApiRequestError) => new ApiRequestError('Invalid timestamp format', 400))

    await tracking.syncPendingLocations('race-c')

    expect(rows.map((row) => row.synced)).toEqual([2, 2])
    expect(rows[0].lastSyncError).toBe('Invalid timestamp format')

    rows.push(createLocation(33, 'race-c', '2026-10-05T10:10:00.000Z'))
    mocks.sendLocationsBatch.mockReset()
    mocks.sendLocationsBatch.mockResolvedValue(undefined)
    await tracking.syncPendingLocations('race-c')

    expect(mocks.sendLocationsBatch).toHaveBeenCalledTimes(1)
    expect(mocks.sendLocationsBatch.mock.calls[0][1]).toHaveLength(1)
    expect(rows[2].synced).toBe(1)
  })

  it('keeps points queued for retry on server errors and rate limits', async () => {
    const rows = [createLocation(41, 'race-d', '2026-10-05T10:00:00.000Z')]
    const tracking = await loadTracking(rows, (ApiRequestError) => new ApiRequestError('busy', 503))

    await tracking.syncPendingLocations('race-d')

    expect(rows[0].synced).toBe(0)
    expect(rows[0].syncAttempts).toBe(1)
    expect(tracking.classifySyncFailure(new (await import('../lib/api.ts')).ApiRequestError('slow down', 429))).toBe('retry')
  })

  it('stops tracking and records why when the athlete is no longer registered', async () => {
    const rows = [createLocation(51, 'race-e', '2026-10-05T10:00:00.000Z')]
    mocks.hasStartedLocationUpdates.mockResolvedValue(true)
    mocks.isTaskRegistered.mockResolvedValue(false)
    const tracking = await loadTracking(rows, (ApiRequestError) => new ApiRequestError('You are not registered for this race', 404))
    mocks.database.getFirstAsync.mockResolvedValue({ raceId: 'race-e', status: 'active', stopReason: null, quarantined: 0 })
    const notices = []
    tracking.subscribeTrackingStopped((notice) => notices.push(notice))

    await tracking.syncPendingLocations('race-e')

    expect(rows[0].synced).toBe(2)
    expect(mocks.stopLocationUpdates).toHaveBeenCalledOnce()
    expect(notices).toEqual([{ raceId: 'race-e', reason: 'not_registered' }])
    expect(mocks.database.runAsync.mock.calls.some(([sql, params]) => (
      String(sql).includes("status = 'rejected'") && params[0] === 'not_registered' && params[2] === 'race-e'
    ))).toBe(true)
  })

  it('stops tracking when the server says the race entry is closed', async () => {
    const rows = [createLocation(61, 'race-f', '2026-10-05T10:00:00.000Z')]
    mocks.hasStartedLocationUpdates.mockResolvedValue(true)
    mocks.isTaskRegistered.mockResolvedValue(false)
    const tracking = await loadTracking(rows, (ApiRequestError) => new ApiRequestError('Live tracking has ended', 409, false, 'TRACKING_CLOSED'))
    mocks.database.getFirstAsync.mockResolvedValue({ raceId: 'race-f', status: 'active', stopReason: null, quarantined: 0 })
    const notices = []
    tracking.subscribeTrackingStopped((notice) => notices.push(notice))

    await tracking.syncPendingLocations('race-f')

    expect(rows[0].synced).toBe(2)
    expect(mocks.stopLocationUpdates).toHaveBeenCalledOnce()
    expect(notices).toEqual([{ raceId: 'race-f', reason: 'closed' }])
    const { ApiRequestError } = await import('../lib/api.ts')
    expect(tracking.classifySyncFailure(new ApiRequestError('conflict', 409))).toBe('retry')
  })
})

describe('checkpoint self-scan', () => {
  const checkpointId = '6f1c0b0e-1111-4c4c-8888-123456789abc'
  const raceId = '7a2d1c0f-2222-4d4d-9999-abcdef012345'
  const token = 'ab'.repeat(32)

  it('reads the secret self-scan code and flags printed codes from before the change', async () => {
    const { parseSelfScanCode } = await import('../lib/selfScan.ts')

    expect(parseSelfScanCode(`${checkpointId}:${raceId}:${token}`)).toEqual({ kind: 'ok', code: { checkpointId, raceId, token } })
    expect(parseSelfScanCode(`${checkpointId}:${raceId}`)).toEqual({ kind: 'outdated' })
    expect(parseSelfScanCode(`${checkpointId}:${raceId}:not-hex`)).toEqual({ kind: 'unreadable' })
    expect(parseSelfScanCode(42)).toEqual({ kind: 'unreadable' })
  })

  it('sends the scan code and current fix, and reports provisional scans', async () => {
    mocks.secureValues.set('auth_token', 'access-token')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ status: 'PROVISIONAL', reason: 'OUT_OF_RANGE' }), { status: 202 }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')
    const fix = { latitude: -17.83, longitude: 31.05, accuracy: 8, timestamp: '2026-11-15T09:00:00.000Z' }

    await expect(api.recordCheckpoint(raceId, checkpointId, token, fix)).resolves.toEqual({
      status: 'PROVISIONAL',
      reason: 'OUT_OF_RANGE',
    })
    expect(JSON.parse(String(fetchMock.mock.calls[0][1].body))).toEqual({
      checkpointId, method: 'qr_code', scanToken: token, location: fix,
    })
  })

  it('maps refusal codes to localized messages and hides raw server text', async () => {
    const { selfScanErrorKey } = await import('../lib/selfScan.ts')
    const { ApiRequestError } = await import('../lib/api.ts')

    expect(selfScanErrorKey(new ApiRequestError('x', 409, false, 'SELF_SCAN_NOT_ALLOWED'))).toBe('mobile:scanner.errors.SELF_SCAN_NOT_ALLOWED')
    expect(selfScanErrorKey(new ApiRequestError('stack trace', 500, false, 'INTERNAL_ERROR'))).toBe('mobile:scanner.failedBody')
  })
})

describe('local location history retention', () => {
  it('prunes only uploaded or rejected points older than a week outside active sessions', async () => {
    connectPersistentQueue([])
    mocks.database.getFirstAsync.mockResolvedValue({ quarantined: 0 })
    const { initDb } = await import('../lib/tracking-service.ts')

    await initDb()

    const prune = mocks.database.runAsync.mock.calls.find(([sql]) => String(sql).includes('DELETE FROM locations'))
    expect(prune).toBeDefined()
    const [sql, params] = prune
    expect(sql).toContain('synced IN (?, ?)')
    expect(sql).toContain("status = 'active'")
    expect(params.slice(0, 2)).toEqual([1, 2])
    const cutoffAgeMs = Date.now() - Date.parse(params[2])
    expect(cutoffAgeMs).toBeGreaterThan(6.9 * 24 * 60 * 60 * 1000)
    expect(cutoffAgeMs).toBeLessThan(7.1 * 24 * 60 * 60 * 1000)
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

describe('account capabilities', () => {
  it('sends selected registration experiences to the additive registration endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        token: 'access-token',
        refreshToken: 'refresh-token',
        user: {
          id: 'user-1',
          email: 'runner@example.com',
          firstName: 'Ada',
          lastName: 'Runner',
          role: 'SPECTATOR',
          capabilities: ['SPECTATOR'],
          preferredCapability: 'SPECTATOR',
        },
      }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await api.register({
      firstName: ' Ada ',
      lastName: 'Runner',
      email: ' runner@example.com ',
      password: 'password-123',
      privacyPolicyAccepted: true,
      privacyPolicyVersion: '2026-01',
      termsAccepted: true,
      termsVersion: '2026-02',
      experiences: ['SPECTATOR'],
    })

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toContain('/auth/register')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toMatchObject({
      email: 'runner@example.com',
      experiences: ['SPECTATOR'],
      privacyPolicyAccepted: true,
      termsAccepted: true,
      termsVersion: '2026-02',
    })
  })

  it('records acceptance of the current legal documents', async () => {
    mocks.secureValues.set('auth_token', 'access-token')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        privacyPolicyVersion: '2026-11-10',
        privacyPolicyAcceptedAt: '2026-11-10T08:00:00Z',
        termsVersion: '2026-11-10',
        termsAcceptedAt: '2026-11-10T08:00:00Z',
      }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')
    const { API_CONTRACT } = await import('@race-tracker/contracts')

    await expect(api.acceptLegalDocuments()).resolves.toEqual({
      privacyPolicyVersion: '2026-11-10',
      termsVersion: '2026-11-10',
    })

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toContain(API_CONTRACT.endpoints.myLegalAcceptance)
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({
      privacyPolicyVersion: API_CONTRACT.legal.privacyPolicyVersion,
      termsVersion: API_CONTRACT.legal.termsVersion,
    })
  })

  it('surfaces stable deletion error codes and parses blockers', async () => {
    mocks.secureValues.set('auth_token', 'access-token')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        deletionRequested: false,
        requestedAt: null,
        scheduledFor: null,
        blockers: ['ACCOUNT_DELETION_ACTIVE_RACES', 7],
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        message: 'Password is incorrect',
        code: 'ACCOUNT_DELETION_PASSWORD_INVALID',
      }), { status: 400 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')
    const { deletionErrorKey, knownBlockers } = await import('../lib/accountDeletion.ts')

    const status = await api.deletionStatus()
    expect(knownBlockers(status.blockers)).toEqual(['ACCOUNT_DELETION_ACTIVE_RACES'])

    const failure = await api.deleteAccount('wrong', 'DELETE').catch((error) => error)
    expect(failure.code).toBe('ACCOUNT_DELETION_PASSWORD_INVALID')
    expect(deletionErrorKey(failure)).toBe('account:deletion.errors.ACCOUNT_DELETION_PASSWORD_INVALID')
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(mocks.secureValues.get('auth_token')).toBe('access-token')
  })

  it('requires acceptance until both stored versions match the contract', async () => {
    const { needsLegalAcceptance, LEGAL_VERSIONS } = await import('../lib/legal.ts')

    expect(needsLegalAcceptance(null)).toBe(false)
    expect(needsLegalAcceptance({ privacyPolicyVersion: LEGAL_VERSIONS.privacy, termsVersion: null })).toBe(true)
    expect(needsLegalAcceptance({ privacyPolicyVersion: 'old', termsVersion: LEGAL_VERSIONS.terms })).toBe(true)
    expect(needsLegalAcceptance({ privacyPolicyVersion: LEGAL_VERSIONS.privacy, termsVersion: LEGAL_VERSIONS.terms })).toBe(false)
    expect(needsLegalAcceptance({ privacyPolicyVersion: '2099-01-01', termsVersion: '2099-01-01', legalAcceptanceRequired: false })).toBe(false)
    expect(needsLegalAcceptance({ privacyPolicyVersion: LEGAL_VERSIONS.privacy, termsVersion: LEGAL_VERSIONS.terms, legalAcceptanceRequired: true })).toBe(true)
  })

  it('uses the additive profile capabilities endpoint and preserves the full account capability set', async () => {
    mocks.secureValues.set('auth_token', 'access-token')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        id: 'user-1',
        email: 'runner@example.com',
        firstName: 'Ada',
        lastName: 'Runner',
        role: 'ATHLETE',
        capabilities: ['ATHLETE', 'SPECTATOR'],
        preferredCapability: 'SPECTATOR',
      }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await api.updateCapabilities({
      capabilities: ['ATHLETE', 'SPECTATOR'],
      preferredCapability: 'SPECTATOR',
    })

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toContain('/auth/profile/capabilities')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(String(init.body))).toEqual({
      capabilities: ['ATHLETE', 'SPECTATOR'],
      preferredCapability: 'SPECTATOR',
    })
  })
})

describe('spectator favorites', () => {
  it('rejects empty race and participant ids before making API requests', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const { api, getNonEmptyRouteId } = await import('../lib/api.ts')

    await expect(api.getRace('')).rejects.toThrow(/required/)
    await expect(api.getPublicRaceResults('')).rejects.toThrow(/required/)
    await expect(api.followAthlete('', 'participant-1')).rejects.toThrow(/raceId is required/)
    await expect(api.unfollowAthlete('race-1', '')).rejects.toThrow(/participantId is required/)
    await expect(api.unfollowAthlete('', 'participant-1')).rejects.toThrow(/raceId is required/)
    await expect(api.markNotificationRead('')).rejects.toThrow(/id is required/)
    await expect(api.registerDevice('')).rejects.toThrow(/token is required/)
    await expect(api.cancelRegistration('')).rejects.toThrow(/registrationId is required/)
    expect(getNonEmptyRouteId('  ')).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('lists, follows, and unfollows athletes through the contract favorites paths', async () => {
    mocks.secureValues.set('auth_token', 'spectator-token')
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([
        { raceId: 'race-1', raceName: 'City Run', participantId: 'participant-1', participantName: 'Ada Runner' },
      ]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        raceId: 'race-2', raceName: 'Harbor Run', participantId: 'participant-2', participantName: 'Sam Runner',
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: 'Athlete removed from favorites' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    const favorites = await api.listFavorites()
    await api.followAthlete('race-2', 'participant-2')
    await api.unfollowAthlete('race-2', 'participant-2')

    expect(favorites[0].participantName).toBe('Ada Runner')
    expect(String(fetchMock.mock.calls[0][0])).toContain('/favorites')
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer spectator-token')

    const [followUrl, followInit] = fetchMock.mock.calls[1]
    expect(String(followUrl)).toContain('/favorites')
    expect(followInit.method).toBe('POST')
    expect(JSON.parse(String(followInit.body))).toEqual({ raceId: 'race-2', participantId: 'participant-2' })

    const [unfollowUrl, unfollowInit] = fetchMock.mock.calls[2]
    expect(String(unfollowUrl)).toContain('/favorites/participant-2?raceId=race-2')
    expect(unfollowInit.method).toBe('DELETE')
  })

  it('rejects malformed favorite records instead of treating them as an empty list', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([
      { raceId: ' ', participantId: 'participant-1', participantName: 'Missing race id' },
    ]), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await expect(api.listFavorites()).rejects.toThrow(/required race or athlete identifiers/)
  })

  it('classifies network and not-found requests for retry UI', async () => {
    const notFoundFetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Missing race' }), { status: 404 }))
    vi.stubGlobal('fetch', notFoundFetch)
    const { api, getApiErrorKind } = await import('../lib/api.ts')

    try {
      await api.getRace('missing-race')
      throw new Error('Expected missing race request to fail')
    } catch (error) {
      expect(error).toMatchObject({ status: 404 })
      expect(getApiErrorKind(error)).toBe('not_found')
    }

    const networkFetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    vi.stubGlobal('fetch', networkFetch)
    vi.resetModules()
    const restartedApi = await import('../lib/api.ts')
    try {
      await restartedApi.api.getRace('offline-race')
      throw new Error('Expected network request to fail')
    } catch (error) {
      expect(restartedApi.getApiErrorKind(error)).toBe('network')
    }
  })

  it('requests later public result pages for participant load-more', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      items: [],
      page: 1,
      size: 50,
      totalElements: 51,
      totalPages: 2,
      revision: 4,
    }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    const response = await api.getPublicRaceResults('race-a', 1, 50, 4)

    expect(response.page).toBe(1)
    expect(String(fetchMock.mock.calls[0][0])).toContain('/public/races/race-a/results?page=1&size=50&revision=4')
  })

  it('restarts public result pagination from page zero after a revision conflict', async () => {
    const { ApiRequestError } = await import('../lib/api.ts')
    const { createPublicResultsPaginationState, loadNextPublicResultsPage } = await import('../lib/public-results-pagination.ts')
    const pageZero = {
      items: [{ participantId: 'participant-1' }],
      page: 0,
      size: 50,
      totalElements: 2,
      totalPages: 2,
      revision: 7,
    }
    const restartedPageZero = { ...pageZero, items: [{ participantId: 'participant-1' }, { participantId: 'participant-2' }], revision: 8 }
    const loadPage = vi.fn()
      .mockRejectedValueOnce(new ApiRequestError('conflict', 409))
      .mockResolvedValueOnce(restartedPageZero)

    const result = await loadNextPublicResultsPage(createPublicResultsPaginationState(pageZero), loadPage)

    expect(loadPage).toHaveBeenNthCalledWith(1, 1, 50, 7)
    expect(loadPage).toHaveBeenNthCalledWith(2, 0, 50)
    expect(result.items).toHaveLength(2)
    expect(result.revision).toBe(8)
    expect(result.page).toBe(0)
  })

  it('rejects malformed public result pages instead of rendering an empty participant list', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ items: [] }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await expect(api.getPublicRaceResults('race-a')).rejects.toThrow(/Public race results .* malformed/)
  })

  it('rejects public result metadata and participant records with empty identifiers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      items: [{ participantId: ' ' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
      revision: 1,
    }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await expect(api.getPublicRaceResults('race-a')).rejects.toThrow(/participant identifiers/)
  })

  it('rejects malformed authentication, profile, notification, and deletion responses', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        token: 'access-token',
        refreshToken: 'refresh-token',
        user: {
          id: ' ',
          email: 'runner@example.com',
          firstName: 'Ada',
          lastName: 'Runner',
          role: 'ATHLETE',
        },
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: 'user-1',
        email: ' ',
        firstName: 'Ada',
        lastName: 'Runner',
        role: 'ATHLETE',
        capabilities: ['ATHLETE'],
        preferredCapability: 'ATHLETE',
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{
        id: '',
        title: 'Race update',
        message: 'Your race changed',
        type: 'race_update',
        timestamp: '2026-10-19T10:00:00Z',
        read: false,
      }]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ deletionRequested: 'false' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await expect(api.login('runner@example.com', 'password')).rejects.toThrow(/missing an id/)
    await expect(api.updateProfile({ firstName: 'Ada', lastName: 'Runner' })).rejects.toThrow(/missing an email/)
    await expect(api.getNotifications()).rejects.toThrow(/missing an id/)
    await expect(api.deletionStatus()).rejects.toThrow(/request state/)
  })

  it('paginates race discovery and aggregates all metadata pages for callers that need a complete list', async () => {
    const fetchMock = vi.fn(async (input) => {
      const url = String(input)
      const page = new URL(url).searchParams.get('page')
      const response = page === '1'
        ? { content: [{ id: 'race-2', name: 'Second race' }], number: 1, size: 1, totalElements: 2, totalPages: 2 }
        : { content: [{ id: 'race-1', name: 'First race' }], number: 0, size: 1, totalElements: 2, totalPages: 2 }
      return new Response(JSON.stringify(response), { status: 200 })
    })
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    const page = await api.getRacesPage(1, 1)
    const races = await api.getRaces()

    expect(page.content[0].id).toBe('race-2')
    expect(String(fetchMock.mock.calls[0][0])).toContain('/races?page=1&size=1')
    expect(races.map((race) => race.id)).toEqual(['race-1', 'race-2'])
    expect(String(fetchMock.mock.calls[2][0])).toContain('/races?page=1&size=1')
  })

  it('rejects malformed race, checkpoint, registration, and result payloads', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'race-1' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{}]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{ id: 'field-1' }]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'registration-1' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ items: [] }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const { api } = await import('../lib/api.ts')

    await expect(api.getRace('race-1')).rejects.toThrow(/Race response was missing required identifiers/)
    await expect(api.getRaceCheckpoints('race-1')).rejects.toThrow(/Checkpoints response was missing required identifiers/)
    await expect(api.getRegistrationFields('race-1')).rejects.toThrow(/Registration fields response was missing required fields/)
    await expect(api.registerForRace('race-1', { categoryId: 'category-1', emergencyContact: { name: 'Ada', phone: '1', relationship: 'Self' } })).rejects.toThrow(/Registration response was missing required fields/)
    await expect(api.getMyResultsPage()).rejects.toThrow(/Race results page number was malformed/)
  })
})
