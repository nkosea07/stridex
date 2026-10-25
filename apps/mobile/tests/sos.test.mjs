/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { describe, expect, it } from 'vitest'
import { freshSosFix, SOS_FIX_MAX_AGE_MS } from '../lib/sos.ts'

const now = new Date('2026-10-25T08:00:00.000Z').getTime()

function fix(overrides = {}) {
  return {
    latitude: -33.924912345,
    longitude: 18.424112345,
    timestamp: new Date(now - 30_000).toISOString(),
    ...overrides,
  }
}

describe('freshSosFix', () => {
  it('returns a rounded recent fix', () => {
    expect(freshSosFix([fix()], now)).toEqual({ latitude: -33.92491, longitude: 18.42411 })
  })

  it('rejects a stale fix', () => {
    expect(freshSosFix([fix({ timestamp: new Date(now - SOS_FIX_MAX_AGE_MS - 1).toISOString() })], now)).toBeNull()
  })

  it('rejects missing, non-finite, and out-of-range coordinates', () => {
    expect(freshSosFix([], now)).toBeNull()
    expect(freshSosFix([fix({ latitude: Number.NaN })], now)).toBeNull()
    expect(freshSosFix([fix({ latitude: 91 })], now)).toBeNull()
    expect(freshSosFix([fix({ longitude: -181 })], now)).toBeNull()
  })

  it('rejects an invalid timestamp', () => {
    expect(freshSosFix([fix({ timestamp: 'not-a-date' })], now)).toBeNull()
  })
})
