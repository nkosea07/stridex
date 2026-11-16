import { describe, expect, it } from 'vitest'
import { ApiError } from '@/lib/api'
import { deletionErrorKey, knownBlockers } from '@/lib/accountDeletion'

describe('account deletion error mapping', () => {
  it('maps stable server codes to localized keys', () => {
    expect(deletionErrorKey(new ApiError(400, 'Password is incorrect', 'ACCOUNT_DELETION_PASSWORD_INVALID')))
      .toBe('account:deletion.errors.ACCOUNT_DELETION_PASSWORD_INVALID')
    expect(deletionErrorKey(new ApiError(429, 'Too many requests', 'TOO_MANY_REQUESTS')))
      .toBe('account:deletion.errors.TOO_MANY_REQUESTS')
    expect(deletionErrorKey(new ApiError(409, 'blocked', 'ACCOUNT_DELETION_ACTIVE_RACES')))
      .toBe('account:deletion.blockers.ACCOUNT_DELETION_ACTIVE_RACES')
  })

  it('never surfaces raw server messages for unknown failures', () => {
    expect(deletionErrorKey(new ApiError(500, 'NullPointerException at ...', 'INTERNAL_ERROR')))
      .toBe('account:deletion.errors.generic')
    expect(deletionErrorKey(new Error('offline'))).toBe('account:deletion.errors.generic')
  })

  it('ignores blocker codes the client does not know', () => {
    expect(knownBlockers(['ACCOUNT_DELETION_LAST_SUPER_ADMIN', 'SOMETHING_NEW'])).toEqual(['ACCOUNT_DELETION_LAST_SUPER_ADMIN'])
    expect(knownBlockers(undefined)).toEqual([])
  })
})
