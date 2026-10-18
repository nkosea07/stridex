import { describe, expect, it } from 'vitest'
import { isAdminRole, isOperationsRole, isPersonalRole } from '@/contexts/AuthContext'

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
})
