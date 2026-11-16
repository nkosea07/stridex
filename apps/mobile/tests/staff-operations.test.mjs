/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@react-native-async-storage/async-storage', () => ({
  default: { getItem: vi.fn(async () => null), setItem: vi.fn(), removeItem: vi.fn() },
}))
vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(async () => null), setItemAsync: vi.fn(), deleteItemAsync: vi.fn() }))

describe('staff operations helpers', () => {
  it('maps stable server codes and statuses to staff messages', async () => {
    const { staffErrorKey } = await import('../lib/operations.ts')
    expect(staffErrorKey({ code: 'CHECK_IN_NOT_ELIGIBLE', status: 409 })).toBe('mobile:staff.errors.CHECK_IN_NOT_ELIGIBLE')
    expect(staffErrorKey({ isNetworkError: true })).toBe('mobile:staff.errors.network')
    expect(staffErrorKey({ status: 404 })).toBe('mobile:staff.errors.notFound')
    expect(staffErrorKey({ status: 403, code: 'AUTHORIZATION_DENIED' })).toBe('mobile:staff.errors.notAllowed')
    expect(staffErrorKey(new Error('raw server text'))).toBe('mobile:staff.errors.generic')
  })

  it('offers undo only for your own non-chip entries inside five minutes', async () => {
    const { canUndoEntry } = await import('../lib/operations.ts')
    const now = Date.parse('2026-11-15T08:10:00Z')
    const entry = { recordedById: 'me', recordMethod: 'MANUAL' }
    expect(canUndoEntry(entry, 'me', now - 60_000, now)).toBe(true)
    expect(canUndoEntry(entry, 'me', now - 6 * 60_000, now)).toBe(false)
    expect(canUndoEntry(entry, 'someone-else', now - 60_000, now)).toBe(false)
    expect(canUndoEntry({ ...entry, recordMethod: 'RFID' }, 'me', now - 60_000, now)).toBe(false)
    expect(canUndoEntry(entry, null, now, now)).toBe(false)
  })
})

describe('staff API', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('searches participants with an encoded query and asks for recent records only', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = vi.fn(async () => new Response('[]', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.searchParticipants('race-1', ' Ada Runner ')
    await api.getRecentStationRecords('race-1', 'cp-1', 20)

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/races\/race-1\/participants\/search\?q=Ada%20Runner$/)
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/races\/race-1\/checkpoints\/cp-1\/records\?limit=20$/)
  })

  it('undoes an entry through the audited void endpoint with a reason', async () => {
    const { api } = await import('../lib/api.ts')
    const fetchMock = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.undoStationRecord('race-1', 'cp-1', 'rec-1', 'Wrong bib entered')

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/races\/race-1\/checkpoints\/cp-1\/records\/rec-1\/void$/)
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ reason: 'Wrong bib entered' })
  })
})
