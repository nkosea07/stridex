/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const store = vi.hoisted(() => new Map())

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: {
    getItem: vi.fn(async (key) => (store.has(key) ? store.get(key) : null)),
    setItem: vi.fn(async (key, value) => { store.set(key, value) }),
    removeItem: vi.fn(async (key) => { store.delete(key) }),
  },
}))
vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(), setItemAsync: vi.fn(), deleteItemAsync: vi.fn() }))

const capture = (bib, minute) => ({
  raceId: 'r1', checkpointId: 'c1', bib, capturedAt: `2026-11-15T08:${String(minute).padStart(2, '0')}:00.000Z`,
})

describe('offline staff captures', () => {
  beforeEach(() => store.clear())

  it('keeps captures through a network outage and sends them in order once online', async () => {
    const queue = await import('../lib/offlineCaptures.ts')
    const { ApiRequestError } = await import('../lib/api.ts')
    await queue.enqueueOfflineCapture(capture('101', 1))
    await queue.enqueueOfflineCapture(capture('102', 2))

    const offline = await queue.flushOfflineCaptures(async () => { throw new ApiRequestError('Network request failed', undefined, true) })
    expect(offline).toEqual({ sent: 0, rejected: [], remaining: 2 })

    const sent = []
    const online = await queue.flushOfflineCaptures(async (item) => { sent.push(item.bib) })
    expect(online.sent).toBe(2)
    expect(sent).toEqual(['101', '102'])
    expect(await queue.loadOfflineCaptures()).toEqual([])
  })

  it('treats already-recorded captures as sent and reports refused ones', async () => {
    const queue = await import('../lib/offlineCaptures.ts')
    const { ApiRequestError } = await import('../lib/api.ts')
    await queue.enqueueOfflineCapture(capture('101', 1))
    await queue.enqueueOfflineCapture(capture('999', 2))

    const result = await queue.flushOfflineCaptures(async (item) => {
      if (item.bib === '101') throw new ApiRequestError('Checkpoint already recorded', 409, false, 'CHECKPOINT_ALREADY_RECORDED')
      throw new ApiRequestError('Participant not found', 404, false, 'NOT_FOUND')
    })

    expect(result.sent).toBe(1)
    expect(result.rejected.map((item) => item.capture.bib)).toEqual(['999'])
    expect(result.remaining).toBe(0)
  })

  it('is cleared when the staff member signs out', async () => {
    const queue = await import('../lib/offlineCaptures.ts')
    await queue.enqueueOfflineCapture(capture('101', 1))
    await queue.clearOfflineCaptures()
    expect(await queue.loadOfflineCaptures()).toEqual([])
  })
})
