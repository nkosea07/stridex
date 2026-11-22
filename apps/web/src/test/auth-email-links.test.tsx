import { StrictMode } from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { VerifyEmailPage } from '@/pages/VerifyEmailPage'
import { ResetPasswordPage, validateNewPassword } from '@/pages/ResetPasswordPage'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import '@/i18n'

const mocks = vi.hoisted(() => ({
  authApi: {
    verifyEmail: vi.fn(),
    resendVerification: vi.fn(),
    resetPassword: vi.fn(),
    forgotPassword: vi.fn(),
  },
}))

vi.mock('@/lib/api', () => ({ authApi: mocks.authApi }))

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderAt(path: string, element: React.ReactNode) {
  const pathname = path.split('?')[0]
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path={pathname} element={<>{element}<LocationProbe /></>} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </StrictMode>,
  )
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('email verification link page', () => {
  it('verifies the token exactly once, even under StrictMode, and removes it from the address bar', async () => {
    mocks.authApi.verifyEmail.mockResolvedValue({ message: 'ok' })

    renderAt('/verify-email?token=abc123', <VerifyEmailPage />)

    expect(await screen.findByText('Your email is verified. You can now sign in.')).toBeInTheDocument()
    expect(mocks.authApi.verifyEmail).toHaveBeenCalledTimes(1)
    expect(mocks.authApi.verifyEmail).toHaveBeenCalledWith('abc123')
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/verify-email$/)
    expect(screen.getByRole('link', { name: 'Go to sign in' })).toHaveAttribute('href', '/login')
  })

  it('offers a new verification email when the link is expired or already used', async () => {
    mocks.authApi.verifyEmail.mockRejectedValue(new Error('Invalid or expired token'))
    mocks.authApi.resendVerification.mockResolvedValue({ message: 'ok' })

    renderAt('/verify-email?token=stale', <VerifyEmailPage />)

    fireEvent.change(await screen.findByLabelText('Email'), { target: { value: ' fan@example.com ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send a new verification link' }))

    await waitFor(() => expect(mocks.authApi.resendVerification).toHaveBeenCalledWith('fan@example.com'))
    expect(await screen.findByText(/a new link is on its way/)).toBeInTheDocument()
  })

  it('treats a link without a token as invalid without calling the API', () => {
    renderAt('/verify-email', <VerifyEmailPage />)

    expect(screen.getByText(/invalid, expired or already used/)).toBeInTheDocument()
    expect(mocks.authApi.verifyEmail).not.toHaveBeenCalled()
  })
})

describe('password reset link page', () => {
  it('validates the new password before sending it', () => {
    expect(validateNewPassword('short', 'short')).toBe('tooShort')
    expect(validateNewPassword('x'.repeat(129), 'x'.repeat(129))).toBe('tooLong')
    expect(validateNewPassword('long-enough-1', 'long-enough-2')).toBe('mismatch')
    expect(validateNewPassword('long-enough-1', 'long-enough-1')).toBeNull()
  })

  it('resets the password with the token from the link', async () => {
    mocks.authApi.resetPassword.mockResolvedValue({ message: 'ok' })
    renderAt('/reset-password?token=reset-token', <ResetPasswordPage />)

    expect(screen.getByTestId('location')).toHaveTextContent(/^\/reset-password$/)
    fireEvent.change(screen.getByLabelText('New password'), { target: { value: 'mismatch-one' } })
    fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: 'mismatch-two' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save new password' }))
    expect(await screen.findByRole('alert')).toHaveTextContent("The passwords don't match.")
    expect(mocks.authApi.resetPassword).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: 'mismatch-one' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save new password' }))

    expect(await screen.findByText(/Your password has been changed/)).toBeInTheDocument()
    expect(mocks.authApi.resetPassword).toHaveBeenCalledWith('reset-token', 'mismatch-one')
  })

  it('sends people whose link has expired to request a new one', async () => {
    mocks.authApi.resetPassword.mockRejectedValue(new Error('Invalid or expired token'))
    renderAt('/reset-password?token=expired', <ResetPasswordPage />)

    fireEvent.change(screen.getByLabelText('New password'), { target: { value: 'a-new-password' } })
    fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: 'a-new-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save new password' }))

    expect(await screen.findByRole('link', { name: 'Request a new link' })).toHaveAttribute('href', '/forgot-password')
  })
})

describe('forgot password page', () => {
  it('requests a reset link and shows the same message whether or not the account exists', async () => {
    mocks.authApi.forgotPassword.mockResolvedValue({ message: 'ok' })
    renderAt('/forgot-password', <ForgotPasswordPage />)

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'fan@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByText(/If an account exists for that email/)).toBeInTheDocument()
    expect(mocks.authApi.forgotPassword).toHaveBeenCalledWith('fan@example.com')
  })
})
