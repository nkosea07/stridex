import { API_CONTRACT } from '@race-tracker/contracts'
import { ApiError } from '@/lib/api'

export const DELETION_CONFIRMATION_TOKEN = 'DELETE'
export const DELETION_GRACE_DAYS = API_CONTRACT.legal.accountDeletionGraceDays

const ERROR_CODES = new Set([
  'ACCOUNT_DELETION_PASSWORD_REQUIRED',
  'ACCOUNT_DELETION_PASSWORD_INVALID',
  'ACCOUNT_DELETION_CONFIRMATION_REQUIRED',
  'ACCOUNT_ALREADY_DELETED',
  'TOO_MANY_REQUESTS',
])

const BLOCKER_CODES = new Set([
  'ACCOUNT_DELETION_LAST_SUPER_ADMIN',
  'ACCOUNT_DELETION_ACTIVE_RACES',
])

export function isDeletionBlocker(code: string | undefined): code is string {
  return code !== undefined && BLOCKER_CODES.has(code)
}

export function deletionBlockerKey(code: string) {
  return BLOCKER_CODES.has(code) ? `account:deletion.blockers.${code}` : 'account:deletion.errors.generic'
}

export function deletionErrorKey(error: unknown) {
  const code = error instanceof ApiError ? error.code : undefined
  if (isDeletionBlocker(code)) return deletionBlockerKey(code)
  return code && ERROR_CODES.has(code) ? `account:deletion.errors.${code}` : 'account:deletion.errors.generic'
}

export function knownBlockers(blockers: readonly string[] | undefined) {
  return (blockers ?? []).filter((code) => BLOCKER_CODES.has(code))
}
