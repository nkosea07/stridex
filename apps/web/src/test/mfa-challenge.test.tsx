import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import '@/i18n'

const mocks = vi.hoisted(() => ({ mfaEnroll: vi.fn(), mfaConfirmEnrollment: vi.fn(), mfaVerifyLogin: vi.fn() }))
vi.mock('@/lib/api', () => ({ authApi: mocks }))

import { MfaChallenge } from '@/components/auth/MfaChallenge'

describe('admin two-factor sign-in', () => {
  afterEach(cleanup)
  beforeEach(() => vi.clearAllMocks())

  it('enrolls an admin, shows recovery codes once, then signs them in', async () => {
    mocks.mfaEnroll.mockResolvedValue({ secret: 'JBSWY3DPEHPK3PXP', provisioningUri: 'otpauth://totp/x', qrCodeDataUri: 'data:image/png;base64,AA' })
    const session = { token: 't', user: { id: 'u1' }, recoveryCodes: ['aaaa-bbbb', 'cccc-dddd'] }
    mocks.mfaConfirmEnrollment.mockResolvedValue(session)
    const onAuthenticated = vi.fn()
    render(<MfaChallenge challengeToken="c1" enrollmentRequired onAuthenticated={onAuthenticated} onCancel={vi.fn()} />)

    expect(await screen.findByText('JBSWY3DPEHPK3PXP')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Verification code'), { target: { value: '123 456' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verify' }))

    expect(await screen.findByText('aaaa-bbbb')).toBeTruthy()
    expect(mocks.mfaConfirmEnrollment).toHaveBeenCalledWith('c1', '123456')
    expect(onAuthenticated).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'I have saved these codes' }))
    expect(onAuthenticated).toHaveBeenCalledWith(session)
  })

  it('verifies an enrolled admin and reports a wrong code', async () => {
    mocks.mfaVerifyLogin.mockRejectedValueOnce(new Error('bad')).mockResolvedValueOnce({ token: 't', user: { id: 'u1' } })
    const onAuthenticated = vi.fn()
    render(<MfaChallenge challengeToken="c2" enrollmentRequired={false} onAuthenticated={onAuthenticated} onCancel={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Verification code'), { target: { value: '000000' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verify' }))
    expect((await screen.findByRole('alert')).textContent).toContain('not valid')

    fireEvent.click(screen.getByRole('button', { name: 'Verify' }))
    await waitFor(() => expect(onAuthenticated).toHaveBeenCalled())
    expect(mocks.mfaEnroll).not.toHaveBeenCalled()
  })
})
