/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { describe, expect, it, vi } from 'vitest'
import { API_CONTRACT } from '@race-tracker/contracts'
import { assignedOptions, createSingleFlight, incidentMessage, isCurrentOperation, receiptCopyKey, validSelection } from '../lib/operations.ts'
import { api, parseAssignedCheckpointPage, parseCheckpointEmergencyReceipt } from '../lib/api.ts'
import contractJson from '../../../packages/contracts/contract.json'

vi.mock('expo-secure-store', () => ({ getItemAsync: vi.fn(async () => 'token') }))
vi.mock('@react-native-async-storage/async-storage', () => ({ default: {} }))

const assigned = { raceId: 'race-1', raceName: 'Race', checkpointId: 'cp-1', checkpointName: 'Start' }
const page = { content: [assigned], number: 0, size: 50, totalElements: 1, totalPages: 1 }

describe('operations contract and response handling', () => {
  it('defines both version 1 endpoints', () => {
    expect(API_CONTRACT.apiVersion).toBe(1)
    expect(API_CONTRACT.endpoints.operationsCheckpoints).toBe('/operations/checkpoints')
    expect(API_CONTRACT.endpoints.checkpointEmergency).toBe('/races/:raceId/checkpoints/:checkpointId/emergency')
    expect(API_CONTRACT.legal.mediaPublicationConsentVersion).toBe(contractJson.legal.mediaPublicationConsentVersion)
  })

  it('parses assignment pages strictly', () => {
    expect(parseAssignedCheckpointPage(page).content).toEqual([assigned])
    expect(parseAssignedCheckpointPage({ content: [], number: 0, size: 50, totalElements: 0, totalPages: 0 }).content).toEqual([])
    expect(() => parseAssignedCheckpointPage({ ...page, content: [{ ...assigned, checkpointName: '' }] })).toThrow()
    expect(() => parseAssignedCheckpointPage({ ...page, totalPages: '1' })).toThrow()
  })

  it.each([
    { content: [assigned], totalElements: 0, totalPages: 0 },
    { content: [], totalElements: 1, totalPages: 1 },
    { number: 1 },
    { number: 2, totalElements: 100, totalPages: 2 },
    { size: 0 },
    { size: 1, totalElements: 2, totalPages: 1 },
    { content: [assigned, assigned] },
    { content: [], number: 1, totalElements: 0, totalPages: 0 },
  ])('rejects inconsistent assignment page metadata %#', (overrides) => {
    expect(() => parseAssignedCheckpointPage({ ...page, ...overrides })).toThrow()
  })

  it('pages through every assignment', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      const number = String(url).includes('page=1') ? 1 : 0
      return { ok: true, status: 200, text: async () => JSON.stringify({
        ...page, number, totalElements: 51, totalPages: 2,
        content: Array.from({ length: number === 0 ? 50 : 1 }, (_, index) => ({
          ...assigned, checkpointId: `cp-${number * 50 + index}`,
        })),
      }) }
    })
    const rows = await api.getAssignedCheckpoints()
    expect(rows).toHaveLength(51)
    expect(rows.at(-1).checkpointId).toBe('cp-50')
    expect(fetchMock).toHaveBeenCalledTimes(2)
    fetchMock.mockRestore()
  })

  it('rejects malformed receipts and maps delivery wording', () => {
    expect(parseCheckpointEmergencyReceipt({ id: '1', deliveryStatus: 'QUEUED' })).toEqual({ id: '1', deliveryStatus: 'QUEUED' })
    expect(() => parseCheckpointEmergencyReceipt({ id: '1', deliveryStatus: 'DELIVERED' })).toThrow()
    expect(receiptCopyKey({ id: '1', deliveryStatus: 'DISPATCHED' })).toBe('incidentDispatchedBody')
    expect(receiptCopyKey({ id: '1', deliveryStatus: 'QUEUED' })).toBe('incidentReceivedBody')
  })

  it('posts a checkpoint incident with its trimmed message', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true, status: 202, text: async () => JSON.stringify({ id: 'alert-1', deliveryStatus: 'QUEUED' }),
    })
    expect(await api.reportCheckpointEmergency('race-1', 'cp-1', 'Help')).toEqual({ id: 'alert-1', deliveryStatus: 'QUEUED' })
    expect(fetchMock.mock.calls[0][0]).toContain('/races/race-1/checkpoints/cp-1/emergency')
    expect(fetchMock.mock.calls[0][1].body).toBe(JSON.stringify({ message: 'Help' }))
    fetchMock.mockRestore()
  })

  it('maps volunteer races and checkpoints and invalidates removed selections', () => {
    const options = assignedOptions([assigned, { ...assigned, checkpointId: 'cp-2', checkpointName: 'Finish' }])
    expect(options.races).toEqual([{ id: 'race-1', name: 'Race' }])
    expect(options.checkpointsByRace['race-1']).toHaveLength(2)
    expect(validSelection('cp-1', options.checkpointsByRace['race-1'])).toBe('cp-1')
    expect(validSelection('removed', options.checkpointsByRace['race-1'])).toBe('')
  })

  it('requires a trimmed, nonblank incident within 2000 characters', () => {
    expect(incidentMessage('  help  ')).toBe('help')
    expect(incidentMessage('   ')).toBeNull()
    expect(incidentMessage('x'.repeat(2001))).toBeNull()
  })

  it('blocks rapid repeat timing submissions synchronously and releases after completion', async () => {
    const flight = createSingleFlight()
    let finish
    const action = vi.fn(() => new Promise((resolve) => { finish = resolve }))
    const first = flight.run(action)
    const second = flight.run(action)
    expect(action).toHaveBeenCalledTimes(1)
    expect(flight.running).toBe(true)
    expect(await second).toBeUndefined()
    finish('recorded')
    expect(await first).toBe('recorded')
    expect(flight.running).toBe(false)
    const third = flight.run(action)
    expect(action).toHaveBeenCalledTimes(2)
    finish('recorded-again')
    expect(await third).toBe('recorded-again')
  })

  it('ignores incident completion after blur, unmount, or session change', () => {
    const session = { user: { id: 'staff-1', role: 'VOLUNTEER' }, token: 'token-1', refreshToken: 'refresh-1' }
    expect(isCurrentOperation(4, 4, true, session, session)).toBe(true)
    expect(isCurrentOperation(4, 5, true, session, session)).toBe(false)
    expect(isCurrentOperation(4, 4, false, session, session)).toBe(false)
    expect(isCurrentOperation(4, 4, true, session, { ...session, token: 'token-2' })).toBe(false)
    expect(isCurrentOperation(4, 4, true, session, { ...session, user: { id: 'staff-2', role: 'VOLUNTEER' } })).toBe(false)
  })
})
