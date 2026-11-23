import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_CONTRACT } from '@race-tracker/contracts'
import '@/i18n'

const mocks = vi.hoisted(() => ({
  user: null as Record<string, unknown> | null,
  updateUser: vi.fn(),
  logout: vi.fn(),
  accept: vi.fn(),
}))

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({ user: mocks.user, isAuthenticated: mocks.user !== null, updateUser: mocks.updateUser, logout: mocks.logout }),
}))

vi.mock('@/lib/api', () => {
  class ApiError extends Error {
    constructor(public status: number, message: string, public code?: string) {
      super(message)
    }
  }
  return { ApiError, authApi: { acceptLegalDocuments: mocks.accept } }
})

import { LegalAcceptanceGate } from '@/components/legal/LegalAcceptanceGate'
import { ApiError } from '@/lib/api'
import { toast } from 'sonner'

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <LegalAcceptanceGate><p>App content</p></LegalAcceptanceGate>
    </MemoryRouter>,
  )
}

describe('LegalAcceptanceGate', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.user = { id: 'u1', termsVersion: null, privacyPolicyVersion: '2026-10-11' }
    mocks.accept.mockResolvedValue({
      privacyPolicyVersion: API_CONTRACT.legal.privacyPolicyVersion,
      termsVersion: API_CONTRACT.legal.termsVersion,
    })
  })

  it('blocks the app until the current documents are accepted', async () => {
    renderAt('/app')

    expect(screen.queryByText('App content')).toBeNull()
    const accept = screen.getByRole('button', { name: 'Accept and continue' })
    expect((accept as HTMLButtonElement).disabled).toBe(true)

    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(accept)

    await waitFor(() => expect(mocks.updateUser).toHaveBeenCalledWith(expect.objectContaining({
      termsVersion: API_CONTRACT.legal.termsVersion,
      privacyPolicyVersion: API_CONTRACT.legal.privacyPolicyVersion,
    })))
  })

  it('lets users read the documents while acceptance is pending', () => {
    renderAt(API_CONTRACT.legal.termsPath)
    expect(screen.getByText('App content')).toBeTruthy()
    cleanup()
    renderAt(API_CONTRACT.legal.accountDeletionPath)
    expect(screen.getByText('App content')).toBeTruthy()
  })

  it('does not block signed-out visitors', () => {
    mocks.user = null
    renderAt('/')
    expect(screen.getByText('App content')).toBeTruthy()
  })

  it('does not block users who accepted the current versions', () => {
    mocks.user = { id: 'u1', termsVersion: API_CONTRACT.legal.termsVersion, privacyPolicyVersion: API_CONTRACT.legal.privacyPolicyVersion }
    renderAt('/app')
    expect(screen.getByText('App content')).toBeTruthy()
  })

  it('trusts the server when this build carries an older document version', () => {
    mocks.user = { id: 'u1', termsVersion: '2099-01-01', privacyPolicyVersion: '2099-01-01', legalAcceptanceRequired: false }
    renderAt('/app')
    expect(screen.getByText('App content')).toBeTruthy()
  })

  it('blocks when the server says acceptance is required even if versions look current', () => {
    mocks.user = {
      id: 'u1',
      termsVersion: API_CONTRACT.legal.termsVersion,
      privacyPolicyVersion: API_CONTRACT.legal.privacyPolicyVersion,
      legalAcceptanceRequired: true,
    }
    renderAt('/app')
    expect(screen.queryByText('App content')).toBeNull()
  })

  it('tells the user to reload when the server has newer documents', async () => {
    mocks.accept.mockRejectedValue(new ApiError(400, 'mismatch', 'TERMS_VERSION_MISMATCH'))
    renderAt('/app')

    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(screen.getByRole('button', { name: 'Accept and continue' }))

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(
      expect.stringContaining('Reload the page or update the app'),
    ))
    expect(mocks.updateUser).not.toHaveBeenCalled()
  })
})
