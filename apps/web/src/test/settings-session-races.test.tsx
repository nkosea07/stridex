import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SettingsPage } from '@/pages/SettingsPage'
import type { User } from '@/types'
import '@/i18n'

const mocks = vi.hoisted(() => ({
  authApi: {
    updateProfile: vi.fn(),
    forgotPassword: vi.fn(),
    deleteAccount: vi.fn(),
    logout: vi.fn(),
    consent: vi.fn(),
    deletionStatus: vi.fn(),
    exportData: vi.fn(),
    updateConsent: vi.fn(),
    cancelDeletion: vi.fn(),
  },
  notificationsApi: {
    preferences: vi.fn(),
    updatePreferences: vi.fn(),
  },
  logout: vi.fn(),
  updateUser: vi.fn(),
  useAuth: vi.fn(),
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/lib/api', () => ({
  authApi: mocks.authApi,
  notificationsApi: mocks.notificationsApi,
}))

vi.mock('@/contexts/AuthContext', () => ({
  getUserCapabilities: (user: User | null | undefined) => user?.capabilities ?? [],
  useAuth: mocks.useAuth,
}))

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: () => ({
    theme: 'system',
    resolvedTheme: 'light',
    setTheme: vi.fn(),
  }),
}))

vi.mock('@/lib/push', () => ({
  enableWebPush: vi.fn(),
  isWebPushConfigured: () => false,
}))

vi.mock('sonner', () => ({ toast: mocks.toast }))

const user: User = {
  id: 'user-1',
  email: 'user@example.com',
  firstName: 'Current',
  lastName: 'User',
  phone: '+263 77 000 0000',
  role: 'ATHLETE',
  capabilities: ['ATHLETE'],
  preferredCapability: 'ATHLETE',
  isActive: true,
  emailVerified: true,
  createdAt: '2026-01-01T00:00:00Z',
}

const updatedUser: User = {
  ...user,
  firstName: 'Stale',
  phone: '+263 77 999 9999',
}

describe('SettingsPage session generations', () => {
  let generation = 1

  beforeEach(() => {
    vi.clearAllMocks()
    generation = 1
    mocks.useAuth.mockReturnValue({
      user,
      logout: mocks.logout,
      updateUser: mocks.updateUser,
      getSessionGeneration: () => generation,
    })
    mocks.authApi.consent.mockResolvedValue(null)
    mocks.authApi.deletionStatus.mockResolvedValue(null)
    mocks.notificationsApi.preferences.mockResolvedValue({
      emailEnabled: true,
      pushEnabled: true,
      smsEnabled: true,
      whatsappEnabled: true,
    })
  })

  afterEach(() => cleanup())

  it('ignores a late profile response after the session generation changes', async () => {
    let resolveProfile!: (resolvedUser: User) => void
    mocks.authApi.updateProfile.mockReturnValueOnce(new Promise<User>((resolve) => { resolveProfile = resolve }))

    render(
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('First Name'), { target: { value: 'Updated' } })
    fireEvent.click(screen.getByRole('button', { name: /save changes/i }))
    await waitFor(() => expect(mocks.authApi.updateProfile).toHaveBeenCalled())

    generation = 2
    await act(async () => {
      resolveProfile(updatedUser)
      await Promise.resolve()
    })

    expect(mocks.updateUser).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Phone')).toHaveValue(user.phone)
    expect(mocks.toast.success).not.toHaveBeenCalledWith('Profile updated successfully!')
  })

  it('clears old account settings and ignores late loader responses after an account switch', async () => {
    let resolveOldPreferences!: (preferences: { emailEnabled: boolean; pushEnabled: boolean; smsEnabled: boolean; whatsappEnabled: boolean }) => void
    let resolveOldConsent!: (consent: { dataProcessingConsentAt: string; marketingConsent: boolean }) => void
    let resolveOldDeletion!: (deletion: { deletionRequested: boolean; scheduledFor: string }) => void
    mocks.notificationsApi.preferences
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOldPreferences = resolve }))
      .mockResolvedValueOnce({ emailEnabled: false, pushEnabled: false, smsEnabled: false, whatsappEnabled: false })
    mocks.authApi.consent
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOldConsent = resolve }))
      .mockResolvedValueOnce({ dataProcessingConsentAt: null, marketingConsent: false })
    mocks.authApi.deletionStatus
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOldDeletion = resolve }))
      .mockResolvedValueOnce({ deletionRequested: false, scheduledFor: null })

    const view = render(
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>,
    )
    await waitFor(() => {
      expect(mocks.notificationsApi.preferences).toHaveBeenCalledTimes(1)
      expect(mocks.authApi.consent).toHaveBeenCalledTimes(1)
      expect(mocks.authApi.deletionStatus).toHaveBeenCalledTimes(1)
    })

    const newUser = { ...user, id: 'user-2', email: 'second@example.com', phone: '+263 77 111 1111' }
    generation = 2
    mocks.useAuth.mockReturnValue({
      user: newUser,
      logout: mocks.logout,
      updateUser: mocks.updateUser,
      getSessionGeneration: () => generation,
    })
    view.rerender(
      <MemoryRouter>
        <SettingsPage />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(mocks.notificationsApi.preferences).toHaveBeenCalledTimes(2)
      expect(mocks.authApi.consent).toHaveBeenCalledTimes(2)
      expect(mocks.authApi.deletionStatus).toHaveBeenCalledTimes(2)
    })
    expect(screen.getByLabelText('Phone')).toHaveValue(newUser.phone)
    fireEvent.mouseDown(screen.getByRole('tab', { name: 'Notifications' }), { button: 0 })
    expect(screen.getByRole('switch', { name: 'Email' })).not.toBeChecked()

    await act(async () => {
      resolveOldPreferences({ emailEnabled: true, pushEnabled: true, smsEnabled: true, whatsappEnabled: true })
      resolveOldConsent({ dataProcessingConsentAt: '2026-01-01T00:00:00Z', marketingConsent: true })
      resolveOldDeletion({ deletionRequested: true, scheduledFor: '2026-02-01T00:00:00Z' })
      await Promise.resolve()
    })

    expect(screen.getByRole('switch', { name: 'Email' })).not.toBeChecked()
  })
})
