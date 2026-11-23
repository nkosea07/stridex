import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { UserStatsPage } from '@/pages/UserStatsPage'
import type { User } from '@/types'
import '@/i18n'

const mocks = vi.hoisted(() => ({
  useAuth: vi.fn(),
  participantsApi: {
    myRegistrations: vi.fn(),
    withdraw: vi.fn(),
    updateMediaConsent: vi.fn(),
    updateLocationSharing: vi.fn(),
  },
  resultsApi: {
    my: vi.fn(),
    myHistory: vi.fn(),
    myPersonalBests: vi.fn(),
  },
  favoritesApi: { list: vi.fn() },
  toast: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: mocks.useAuth,
  getPreferredCapability: (user: User | null | undefined) => user?.preferredCapability ?? null,
}))
vi.mock('@/lib/api', () => ({
  participantsApi: mocks.participantsApi,
  resultsApi: mocks.resultsApi,
  favoritesApi: mocks.favoritesApi,
}))
vi.mock('sonner', () => ({ toast: mocks.toast }))

const athlete: User = {
  id: 'athlete-1',
  email: 'athlete@example.com',
  firstName: 'Ada',
  lastName: 'Runner',
  role: 'ATHLETE',
  capabilities: ['ATHLETE'],
  preferredCapability: 'ATHLETE',
  isActive: true,
  emailVerified: true,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('athlete race entries', () => {
  beforeEach(() => {
    mocks.useAuth.mockReturnValue({ user: athlete, getSessionGeneration: () => 1 })
    mocks.participantsApi.myRegistrations.mockResolvedValue([
      { id: 'entry-1', raceId: 'race-1', status: 'confirmed', canWithdraw: true },
      { id: 'entry-2', raceId: 'race-2', status: 'finished', canWithdraw: false },
    ])
    mocks.resultsApi.my.mockResolvedValue([])
    mocks.resultsApi.myHistory.mockResolvedValue([])
    mocks.resultsApi.myPersonalBests.mockResolvedValue([])
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('offers withdrawal only for entries that can still be withdrawn and confirms first', async () => {
    mocks.participantsApi.withdraw.mockResolvedValue(undefined)
    render(<MemoryRouter><UserStatsPage /></MemoryRouter>)

    const withdrawButtons = await screen.findAllByRole('button', { name: 'Withdraw' })
    expect(withdrawButtons).toHaveLength(1)
    expect(screen.getByText('Finished')).toBeInTheDocument()

    fireEvent.click(withdrawButtons[0])
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('Withdraw from this race?')).toBeInTheDocument()
    expect(mocks.participantsApi.withdraw).not.toHaveBeenCalled()

    fireEvent.click(within(dialog).getByRole('button', { name: 'Withdraw' }))

    await waitFor(() => expect(mocks.participantsApi.withdraw).toHaveBeenCalledWith('entry-1'))
    await waitFor(() => expect(screen.getByText('Withdrawn')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Withdraw' })).toBeNull()
    expect(mocks.toast.success).toHaveBeenCalledWith('You have withdrawn from the race.')
  })

  it('keeps the entry when the athlete cancels the confirmation', async () => {
    render(<MemoryRouter><UserStatsPage /></MemoryRouter>)

    fireEvent.click(await screen.findByRole('button', { name: 'Withdraw' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Keep my entry' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(mocks.participantsApi.withdraw).not.toHaveBeenCalled()
    expect(screen.getByText('Confirmed')).toBeInTheDocument()
  })
})
