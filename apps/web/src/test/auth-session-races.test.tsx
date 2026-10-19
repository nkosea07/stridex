import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth } from '@/contexts/AuthContext'
import type { User } from '@/types'

const mocks = vi.hoisted(() => ({
  restoreBrowserSession: vi.fn(),
  me: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  setAccessToken: vi.fn(),
  updateCapabilities: vi.fn(),
}))

vi.mock('@/lib/api', () => ({ authApi: mocks }))

const restoredUser: User = {
  id: 'restored-user',
  email: 'restored@example.com',
  firstName: 'Restored',
  lastName: 'User',
  role: 'ATHLETE',
  capabilities: ['ATHLETE'],
  preferredCapability: 'ATHLETE',
  isActive: true,
  emailVerified: true,
  createdAt: '2026-01-01T00:00:00Z',
}

const loggedInUser: User = {
  ...restoredUser,
  id: 'logged-in-user',
  email: 'logged-in@example.com',
  firstName: 'Logged in',
}

const dualCapabilityUser: User = {
  ...restoredUser,
  capabilities: ['ATHLETE', 'SPECTATOR'],
}

function AuthProbe() {
  const { isLoading, user, login, logout, setPreferredCapability } = useAuth()
  return (
    <>
      <output data-testid="loading">{String(isLoading)}</output>
      <output data-testid="user">{user?.id ?? 'none'}</output>
      <button type="button" onClick={() => void login('user@example.com', 'password').catch(() => undefined)}>
        Login
      </button>
      <button type="button" onClick={() => void setPreferredCapability('SPECTATOR').catch(() => undefined)}>
        Set spectator
      </button>
      <button type="button" onClick={logout}>Logout</button>
    </>
  )
}

function renderAuth() {
  return render(
    <AuthProvider>
      <AuthProbe />
    </AuthProvider>,
  )
}

describe('AuthContext session generations', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.restoreBrowserSession.mockResolvedValue(false)
    mocks.logout.mockResolvedValue(undefined)
  })

  afterEach(() => cleanup())

  it('does not let an initial restore overwrite a newer login', async () => {
    let resolveRestore!: (restored: boolean) => void
    let resolveMe!: (user: User) => void
    mocks.restoreBrowserSession.mockReturnValueOnce(new Promise<boolean>((resolve) => { resolveRestore = resolve }))
    mocks.me.mockReturnValueOnce(new Promise<User>((resolve) => { resolveMe = resolve }))
    mocks.login.mockResolvedValueOnce({ token: 'login-token', user: loggedInUser })

    renderAuth()
    await waitFor(() => expect(mocks.restoreBrowserSession).toHaveBeenCalled())

    fireEvent.click(screen.getByRole('button', { name: 'Login' }))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('logged-in-user'))
    expect(screen.getByTestId('loading')).toHaveTextContent('false')

    await act(async () => {
      resolveRestore(true)
      resolveMe(restoredUser)
      await Promise.resolve()
    })

    expect(screen.getByTestId('user')).toHaveTextContent('logged-in-user')
  })

  it('does not let a late login re-authenticate after logout', async () => {
    let resolveLogin!: (response: { token: string; user: User }) => void
    mocks.login.mockReturnValueOnce(new Promise<{ token: string; user: User }>((resolve) => { resolveLogin = resolve }))

    renderAuth()
    await waitFor(() => expect(mocks.restoreBrowserSession).toHaveBeenCalled())
    fireEvent.click(screen.getByRole('button', { name: 'Login' }))
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }))

    await act(async () => {
      resolveLogin({ token: 'late-token', user: loggedInUser })
      await Promise.resolve()
    })

    expect(screen.getByTestId('user')).toHaveTextContent('none')
    expect(mocks.setAccessToken).not.toHaveBeenCalled()
  })

  it('does not let an initial restore re-authenticate after logout', async () => {
    let resolveRestore!: (restored: boolean) => void
    mocks.restoreBrowserSession.mockReturnValueOnce(new Promise<boolean>((resolve) => { resolveRestore = resolve }))
    mocks.me.mockResolvedValueOnce(restoredUser)

    renderAuth()
    await waitFor(() => expect(mocks.restoreBrowserSession).toHaveBeenCalled())
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }))

    await act(async () => {
      resolveRestore(true)
      await Promise.resolve()
    })

    expect(screen.getByTestId('user')).toHaveTextContent('none')
    expect(mocks.me).toHaveBeenCalledTimes(1)
  })

  it('does not let a late capability response overwrite a newer account session', async () => {
    mocks.restoreBrowserSession.mockResolvedValueOnce(true)
    mocks.me.mockResolvedValueOnce(dualCapabilityUser)
    let resolveCapabilities!: (user: User) => void
    mocks.updateCapabilities.mockReturnValueOnce(new Promise<User>((resolve) => { resolveCapabilities = resolve }))
    mocks.login.mockResolvedValueOnce({ token: 'new-session-token', user: loggedInUser })

    renderAuth()
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('restored-user'))

    fireEvent.click(screen.getByRole('button', { name: 'Set spectator' }))
    await waitFor(() => expect(mocks.updateCapabilities).toHaveBeenCalled())
    fireEvent.click(screen.getByRole('button', { name: 'Login' }))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('logged-in-user'))

    await act(async () => {
      resolveCapabilities({ ...dualCapabilityUser, preferredCapability: 'SPECTATOR' })
      await Promise.resolve()
    })

    expect(screen.getByTestId('user')).toHaveTextContent('logged-in-user')
  })

  it('does not let a late capability response restore a session after logout', async () => {
    mocks.restoreBrowserSession.mockResolvedValueOnce(true)
    mocks.me.mockResolvedValueOnce(dualCapabilityUser)
    let resolveCapabilities!: (user: User) => void
    mocks.updateCapabilities.mockReturnValueOnce(new Promise<User>((resolve) => { resolveCapabilities = resolve }))

    renderAuth()
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('restored-user'))
    fireEvent.click(screen.getByRole('button', { name: 'Set spectator' }))
    await waitFor(() => expect(mocks.updateCapabilities).toHaveBeenCalled())
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }))

    await act(async () => {
      resolveCapabilities({ ...dualCapabilityUser, preferredCapability: 'SPECTATOR' })
      await Promise.resolve()
    })

    expect(screen.getByTestId('user')).toHaveTextContent('none')
  })
})
