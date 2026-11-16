import type { LiveTrackingData } from '@/types'

export const STALE_FIX_MS = 5 * 60_000

export function lastFixMillis(row: Pick<LiveTrackingData, 'recordedAt'>, fallback: number): number {
  if (!row.recordedAt) return fallback
  const parsed = Date.parse(row.recordedAt)
  return Number.isNaN(parsed) ? fallback : Math.min(parsed, fallback)
}
