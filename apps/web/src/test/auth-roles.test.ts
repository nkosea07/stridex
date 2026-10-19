import { describe, expect, it } from 'vitest'
import {
  getPreferredCapability,
  getUserCapabilities,
  hasUserCapability,
  isAdminRole,
  isOperationsRole,
  isPersonalRole,
} from '@/contexts/AuthContext'
import type { UserCapability } from '@/types'

describe('role access helpers', () => {
  it.each(['SUPER_ADMIN', 'RACE_ADMIN'] as const)('%s is an administrator', (role) => {
    expect(isAdminRole(role)).toBe(true)
    expect(isOperationsRole(role)).toBe(true)
  })

  it.each(['OFFICIAL', 'VOLUNTEER'] as const)('%s is race operations staff', (role) => {
    expect(isAdminRole(role)).toBe(false)
    expect(isOperationsRole(role)).toBe(true)
  })

  it.each(['ATHLETE', 'SPECTATOR'] as const)('%s stays outside the admin operations shell', (role) => {
    expect(isAdminRole(role)).toBe(false)
    expect(isOperationsRole(role)).toBe(false)
    expect(isPersonalRole(role)).toBe(true)
  })

  it.each(['SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL', 'VOLUNTEER'] as const)('%s stays outside the personal app shell', (role) => {
    expect(isPersonalRole(role)).toBe(false)
  })

  it('keeps athlete and spectator capabilities independent for a dual-capability account', () => {
    const user = {
      role: 'ATHLETE' as const,
      capabilities: ['ATHLETE', 'SPECTATOR'] as UserCapability[],
      preferredCapability: 'SPECTATOR' as const,
    }

    expect(getUserCapabilities(user)).toEqual(['ATHLETE', 'SPECTATOR'])
    expect(hasUserCapability(user, 'ATHLETE')).toBe(true)
    expect(hasUserCapability(user, 'SPECTATOR')).toBe(true)
    expect(getPreferredCapability(user)).toBe('SPECTATOR')
  })

  it('does not infer a capability from role when the capability list is explicitly empty', () => {
    const user = {
      role: 'ATHLETE' as const,
      capabilities: [] as UserCapability[],
      preferredCapability: null,
    }

    expect(getUserCapabilities(user)).toEqual([])
    expect(hasUserCapability(user, 'ATHLETE')).toBe(false)
    expect(getPreferredCapability(user)).toBeNull()
  })
})
