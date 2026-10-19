import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { FollowButton } from '@/components/public/FollowButton'
import { UserStatsPage } from '@/pages/UserStatsPage'
import type { User } from '@/types'
import '@/i18n'

const authContextMock = vi.hoisted(() => ({
  useAuth: vi.fn(),
  useAuthOptional: vi.fn(),
  getPreferredCapability: (user: User | null | undefined) => user?.preferredCapability ?? null,
}))

const apiMocks = vi.hoisted(() => ({
  favoritesApi: {
    list: vi.fn(),
    follow: vi.fn(),
    unfollow: vi.fn(),
  },
  participantsApi: {
    myRegistrations: vi.fn(),
  },
  resultsApi: {
    my: vi.fn(),
  },
}))

vi.mock('@/contexts/AuthContext', () => authContextMock)
vi.mock('@/lib/api', () => apiMocks)

const athleteUser: User = {
  id: 'athlete-1',
  email: 'athlete@example.com',
  firstName: 'Athlete',
  lastName: 'Runner',
  role: 'ATHLETE',
  capabilities: ['ATHLETE'],
  preferredCapability: 'ATHLETE',
  isActive: true,
  emailVerified: true,
  createdAt: '2026-01-01T00:00:00Z',
}

const spectatorUser: User = {
  ...athleteUser,
  id: 'spectator-1',
  role: 'SPECTATOR',
  capabilities: ['SPECTATOR'],
  preferredCapability: 'SPECTATOR',
}

function setAuth(user: User, generation = 1) {
  authContextMock.useAuth.mockReturnValue({ user, getSessionGeneration: () => generation })
  authContextMock.useAuthOptional.mockReturnValue({
    isAuthenticated: true,
    user,
    getSessionGeneration: () => generation,
    hasCapability: (capability: string) => user.capabilities.includes(capability as User['capabilities'][number]),
  })
}

describe('capability-gated personal actions', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('does not expose or call favorites for an athlete-only account', () => {
    setAuth(athleteUser)

    render(<FollowButton raceId="race-1" participantId="participant-1" />)

    expect(screen.queryByRole('button', { name: /follow/i })).not.toBeInTheDocument()
    expect(apiMocks.favoritesApi.list).not.toHaveBeenCalled()
  })

  it('loads only athlete data in athlete mode', async () => {
    setAuth(athleteUser)
    apiMocks.participantsApi.myRegistrations.mockResolvedValue([])
    apiMocks.resultsApi.my.mockResolvedValue([])

    render(
      <MemoryRouter>
        <UserStatsPage />
      </MemoryRouter>,
    )

    await waitFor(() => expect(apiMocks.participantsApi.myRegistrations).toHaveBeenCalled())
    expect(apiMocks.resultsApi.my).toHaveBeenCalled()
    expect(apiMocks.favoritesApi.list).not.toHaveBeenCalled()
  })

  it('loads only spectator data in spectator mode and exposes follow', async () => {
    setAuth(spectatorUser)
    apiMocks.favoritesApi.list.mockResolvedValue([])

    render(
      <MemoryRouter>
        <FollowButton raceId="race-1" participantId="participant-1" />
        <UserStatsPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('button', { name: /follow/i })).toBeInTheDocument()
    await waitFor(() => expect(apiMocks.favoritesApi.list).toHaveBeenCalled())
    expect(apiMocks.participantsApi.myRegistrations).not.toHaveBeenCalled()
    expect(apiMocks.resultsApi.my).not.toHaveBeenCalled()
  })

  it('clears and ignores a late UserStats response after the account changes', async () => {
    let resolveOldFavorites!: (favorites: Array<{ participantId: string; raceId: string; participantName: string }>) => void
    apiMocks.favoritesApi.list
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOldFavorites = resolve }))
      .mockResolvedValueOnce([])
    setAuth(spectatorUser, 1)
    const { rerender } = render(
      <MemoryRouter>
        <UserStatsPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(apiMocks.favoritesApi.list).toHaveBeenCalledTimes(1))

    const newUser = { ...spectatorUser, id: 'spectator-2', email: 'second@example.com' }
    setAuth(newUser, 2)
    rerender(
      <MemoryRouter>
        <UserStatsPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(apiMocks.favoritesApi.list).toHaveBeenCalledTimes(2))

    await act(async () => {
      resolveOldFavorites([{ participantId: 'participant-1', raceId: 'race-1', participantName: 'Old Account Athlete' }])
      await Promise.resolve()
    })

    expect(screen.queryByText('Old Account Athlete')).not.toBeInTheDocument()
  })

  it('clears and ignores a late FollowButton response after the account changes', async () => {
    let resolveOldFavorites!: (favorites: Array<{ participantId: string }>) => void
    apiMocks.favoritesApi.list
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOldFavorites = resolve }))
      .mockResolvedValueOnce([])
    setAuth(spectatorUser, 1)
    const { rerender } = render(<FollowButton raceId="race-1" participantId="participant-1" />)
    await waitFor(() => expect(apiMocks.favoritesApi.list).toHaveBeenCalledTimes(1))

    const newUser = { ...spectatorUser, id: 'spectator-2', email: 'second@example.com' }
    setAuth(newUser, 2)
    rerender(<FollowButton raceId="race-1" participantId="participant-1" />)
    await waitFor(() => expect(apiMocks.favoritesApi.list).toHaveBeenCalledTimes(2))

    await act(async () => {
      resolveOldFavorites([{ participantId: 'participant-1' }])
      await Promise.resolve()
    })

    expect(screen.getByRole('button', { name: 'Follow' })).toBeInTheDocument()
  })
})
