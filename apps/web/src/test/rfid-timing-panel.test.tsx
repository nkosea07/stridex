import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { SWRConfig } from 'swr'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import '@/i18n'

const mocks = vi.hoisted(() => ({
  gunTime: vi.fn(),
  unmatchedTags: vi.fn(),
  reprocess: vi.fn(),
  correctGunTime: vi.fn(),
}))

vi.mock('@/lib/api', () => ({ rfidApi: mocks }))
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))

import { RfidTimingPanel, clockOffsetWarning, toIsoFromLocalInput } from '@/components/rfid/RfidTimingPanel'

function renderPanel(canManage = true) {
  return render(
    <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
      <RfidTimingPanel raceId="r1" canManage={canManage} />
    </SWRConfig>,
  )
}

describe('RFID timing tools', () => {
  afterEach(cleanup)
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.gunTime.mockResolvedValue({ startedAt: null, scheduledStart: '2026-11-15T06:00:00Z' })
    mocks.unmatchedTags.mockResolvedValue([
      { rfidTag: 'E2003411B802', reads: 3, firstReadAt: '2026-11-15T06:31:00Z', lastReadAt: '2026-11-15T06:31:02Z' },
    ])
    mocks.reprocess.mockResolvedValue({ requeued: 3 })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('lists unmatched tags and reprocesses them after a tag is assigned', async () => {
    renderPanel()

    expect(await screen.findByText('E2003411B802')).toBeTruthy()
    expect(screen.getByText(/gated by the scheduled start/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Reprocess selected' }))

    await waitFor(() => expect(mocks.reprocess).toHaveBeenCalledWith('r1', ['UNKNOWN_TAG']))
  })

  it('hides timing changes from staff who cannot manage the race', async () => {
    renderPanel(false)

    expect(await screen.findByText('E2003411B802')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Reprocess selected' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Save gun time' })).toBeNull()
  })

  it('flags gateway clocks that are off and converts the gun time input', () => {
    expect(clockOffsetWarning(3500)).toBe(true)
    expect(clockOffsetWarning(-150)).toBe(false)
    expect(clockOffsetWarning(null)).toBe(false)
    expect(toIsoFromLocalInput('')).toBeNull()
    expect(toIsoFromLocalInput('2026-11-15T06:07:12')).toMatch(/^2026-11-1[45]T\d{2}:07:12\.000Z$/)
  })
})
