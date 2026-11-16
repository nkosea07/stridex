import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { SWRConfig } from 'swr'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import '@/i18n'

const mocks = vi.hoisted(() => ({
  openClaims: vi.fn(),
  decide: vi.fn(),
}))

vi.mock('@/lib/api', () => ({
  selfScanApi: { openClaims: mocks.openClaims, decide: mocks.decide },
}))
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))

import { SelfScanClaimsPanel, claimReasonKey } from '@/components/checkpoints/SelfScanClaimsPanel'
import { acceptsSelfScan } from '@/components/checkpoints/SelfScanQrDialog'

const claim = {
  id: 'c1',
  participantId: 'p1',
  participantName: 'Tari Moyo',
  bibNumber: null,
  checkpointId: 'cp1',
  checkpointName: 'Water 2',
  claimedAt: '2026-11-15T08:30:00Z',
  reason: 'OUT_OF_RANGE' as const,
  distanceMeters: 1840.4,
  fixAgeSeconds: 12,
  status: 'PENDING' as const,
}

function renderPanel() {
  return render(
    <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
      <SelfScanClaimsPanel raceId="r1" />
    </SWRConfig>,
  )
}

describe('self-scan review', () => {
  afterEach(cleanup)
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.openClaims.mockResolvedValue([claim])
    mocks.decide.mockResolvedValue({ ...claim, status: 'APPROVED' })
  })

  it('lists provisional scans with the reason and approves them', async () => {
    renderPanel()

    expect(await screen.findByText('Tari Moyo')).toBeTruthy()
    expect(screen.getByText('1,840 m from checkpoint')).toBeTruthy()
    mocks.openClaims.mockResolvedValue([])
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }))

    await waitFor(() => expect(mocks.decide).toHaveBeenCalledWith('r1', 'c1', 'APPROVE'))
    expect(await screen.findByText('No self-scans need review.')).toBeTruthy()
  })

  it('only offers self-scan codes for intermediate checkpoints', () => {
    expect(acceptsSelfScan({ checkpointType: 'INTERMEDIATE' })).toBe(true)
    expect(acceptsSelfScan({ checkpointType: 'FINISH' })).toBe(false)
    expect(acceptsSelfScan({ checkpointType: 'START' })).toBe(false)
  })

  it('explains out-of-range claims even without a measured distance', () => {
    expect(claimReasonKey({ reason: 'OUT_OF_RANGE', distanceMeters: null }))
      .toBe('checkpoints:claims.reasons.OUT_OF_RANGE_UNKNOWN')
  })
})
