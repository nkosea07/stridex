import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { NotificationsPage } from '@/pages/NotificationsPage'
import type { AppNotification, User } from '@/types'

const mocks = vi.hoisted(() => ({
  notificationsApi: {
    list: vi.fn(),
    markRead: vi.fn(),
  },
  useAuth: vi.fn(),
  toast: {
    error: vi.fn(),
  },
}))

vi.mock('@/lib/api', () => ({ notificationsApi: mocks.notificationsApi }))
vi.mock('@/contexts/AuthContext', () => ({ useAuth: mocks.useAuth }))
vi.mock('sonner', () => ({ toast: mocks.toast }))

const user: User = {
  id: 'user-1',
  email: 'user@example.com',
  firstName: 'Current',
  lastName: 'User',
  role: 'ATHLETE',
  capabilities: ['ATHLETE'],
  preferredCapability: 'ATHLETE',
  isActive: true,
  emailVerified: true,
  createdAt: '2026-01-01T00:00:00Z',
}

const notification = (id: string, title: string): AppNotification => ({
  id,
  title,
  message: `${title} message`,
  type: 'SYSTEM',
  read: false,
  timestamp: '2026-01-01T00:00:00Z',
})

describe('NotificationsPage session generations', () => {
  let generation = 1

  beforeEach(() => {
    vi.clearAllMocks()
    generation = 1
    mocks.useAuth.mockReturnValue({
      user,
      getSessionGeneration: () => generation,
    })
  })

  afterEach(() => cleanup())

  it('does not apply a late notification response after an account switch', async () => {
    let resolveOldNotifications!: (value: AppNotification[]) => void
    mocks.notificationsApi.list.mockImplementationOnce(
      () => new Promise<AppNotification[]>((resolve) => { resolveOldNotifications = resolve }),
    )

    const view = render(
      <MemoryRouter>
        <NotificationsPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(mocks.notificationsApi.list).toHaveBeenCalledTimes(1))

    generation = 2
    mocks.useAuth.mockReturnValue({
      user: { ...user, id: 'user-2' },
      getSessionGeneration: () => generation,
    })
    mocks.notificationsApi.list.mockResolvedValueOnce([notification('new', 'New notification')])
    view.rerender(
      <MemoryRouter>
        <NotificationsPage />
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByText('New notification')).toBeInTheDocument())

    await act(async () => {
      resolveOldNotifications([notification('old', 'Old notification')])
      await Promise.resolve()
    })

    expect(screen.queryByText('Old notification')).not.toBeInTheDocument()
  })
})
