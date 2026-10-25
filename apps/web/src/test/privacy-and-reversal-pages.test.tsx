import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import '@/i18n'

const apiMocks = vi.hoisted(() => ({
  listBlockedJobs: vi.fn(),
  verifyScope: vi.fn(),
  listOpen: vi.fn(),
}))

vi.mock('@/lib/api', () => ({
  privacyAdminApi: {
    listBlockedJobs: apiMocks.listBlockedJobs,
    verifyScope: apiMocks.verifyScope,
  },
  paymentReversalApi: {
    listOpen: apiMocks.listOpen,
  },
}))

import { PrivacyOperationsPage } from '@/pages/PrivacyOperationsPage'
import { ReversalReviewPage } from '@/pages/ReversalReviewPage'

describe('privacy and reversal operations pages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requires a verified scope and reason before updating a blocked deletion job', async () => {
    apiMocks.listBlockedJobs.mockResolvedValue([{
      id: 'job-1', userId: 'user-1', storageKey: 'original.jpg',
      storageScope: 'UNVERIFIED_LEGACY', status: 'PENDING', attempts: 2,
    }])
    apiMocks.verifyScope.mockResolvedValue({
      id: 'job-1', userId: 'user-1', storageKey: 'original.jpg',
      storageScope: 'local|/verified/legacy', status: 'PENDING', attempts: 0,
    })

    render(<MemoryRouter><PrivacyOperationsPage /></MemoryRouter>)

    expect(await screen.findByText('original.jpg')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /verify scope/i }))
    fireEvent.change(screen.getByLabelText(/verified scope/i), { target: { value: 'local|/verified/legacy' } })
    fireEvent.change(screen.getByLabelText(/verification reason/i), { target: { value: 'Deployment record' } })
    fireEvent.click(screen.getByRole('button', { name: /record verification/i }))

    await waitFor(() => expect(apiMocks.verifyScope).toHaveBeenCalledWith(
      'job-1', 'local|/verified/legacy', 'Deployment record'))
  })

  it('renders open reversal cases with the payable amount and participant link', async () => {
    apiMocks.listOpen.mockResolvedValue([{
      paymentId: 'payment-1', raceId: 'race-1', raceName: 'City Marathon',
      participantId: 'participant-1', participantName: 'Ada Runner', provider: 'PESEPAY',
      providerReference: 'PROV-1', amount: 80, currency: 'USD', paymentStatus: 'COMPLETED',
      observedAt: '2026-10-25T08:00:00Z', pendingRefundAttemptId: null,
    }])

    render(<MemoryRouter><ReversalReviewPage /></MemoryRouter>)

    expect(await screen.findByText('Ada Runner')).toBeInTheDocument()
    expect(screen.getByText(/80/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /open participants/i }))
      .toHaveAttribute('href', '/admin/races/race-1/participants')
  })
})
