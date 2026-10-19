/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { describe, expect, it } from 'vitest'

import {
  getPreferredCapability,
  getUserCapabilities,
  hasActiveCapability,
  hasAnyCapability,
  isOperationsRole,
  normalizeUser,
} from '../lib/capabilities.ts'

function user(overrides = {}) {
  return {
    id: 'user-1',
    email: 'runner@example.com',
    firstName: 'Ada',
    lastName: 'Runner',
    role: 'ATHLETE',
    capabilities: ['ATHLETE'],
    preferredCapability: 'ATHLETE',
    ...overrides,
  }
}

describe('mobile capability helpers', () => {
  it('normalizes dual capability accounts and preserves a valid preferred mode', () => {
    const account = normalizeUser(user({
      capabilities: ['ATHLETE', 'SPECTATOR', 'ATHLETE', 'UNKNOWN'],
      preferredCapability: 'SPECTATOR',
    }))

    expect(account.capabilities).toEqual(['ATHLETE', 'SPECTATOR'])
    expect(getPreferredCapability(account)).toBe('SPECTATOR')
    expect(hasAnyCapability(account, ['SPECTATOR'])).toBe(true)
  })

  it('falls back to the legacy personal role when capability data is absent', () => {
    const account = normalizeUser(user({ role: 'SPECTATOR', capabilities: [], preferredCapability: null }))

    expect(getUserCapabilities(account)).toEqual(['SPECTATOR'])
    expect(getPreferredCapability(account)).toBe('SPECTATOR')
  })

  it('does not treat operations roles as mobile personal capabilities', () => {
    const account = user({ role: 'RACE_ADMIN', capabilities: [], preferredCapability: null })

    expect(getUserCapabilities(account)).toEqual([])
    expect(isOperationsRole(account.role)).toBe(true)
    expect(hasAnyCapability(account, ['ATHLETE', 'SPECTATOR'])).toBe(false)
  })

  it('uses the preferred capability as the active direct-navigation mode', () => {
    const account = user({
      capabilities: ['ATHLETE', 'SPECTATOR'],
      preferredCapability: 'SPECTATOR',
    })

    expect(hasActiveCapability(account, 'SPECTATOR')).toBe(true)
    expect(hasActiveCapability(account, 'ATHLETE')).toBe(false)
  })
})
