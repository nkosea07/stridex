import { describe, expect, it } from 'vitest'
import { lastFixMillis } from '@/lib/liveTracking'

describe('live tracking freshness', () => {
  const fetchedAt = Date.parse('2026-11-15T09:00:00Z')

  it('uses the time of the athlete\'s last GPS fix, not the time the map refreshed', () => {
    expect(lastFixMillis({ recordedAt: '2026-11-15T08:20:00Z' }, fetchedAt)).toBe(Date.parse('2026-11-15T08:20:00Z'))
  })

  it('never reports a fix newer than now when the device clock runs ahead', () => {
    expect(lastFixMillis({ recordedAt: '2026-11-15T09:01:00Z' }, fetchedAt)).toBe(fetchedAt)
  })

  it('falls back to the fetch time for older servers without a fix time', () => {
    expect(lastFixMillis({}, fetchedAt)).toBe(fetchedAt)
    expect(lastFixMillis({ recordedAt: 'not-a-date' }, fetchedAt)).toBe(fetchedAt)
  })
})
