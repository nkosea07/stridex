import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RegisterPage } from '@/pages/RegisterPage'
import { authApi } from '@/lib/api'
import '@/i18n'

vi.mock('@/lib/api', () => ({
  ApiError: class MockApiError extends Error {},
  authApi: {
    register: vi.fn(),
    registerOrganizer: vi.fn(),
    logout: vi.fn(),
  },
}))

const registerMock = vi.mocked(authApi.register)
const registerOrganizerMock = vi.mocked(authApi.registerOrganizer)

function renderRegister(path = '/register') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<div>Login</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

function fillRequiredFields() {
  fireEvent.change(screen.getByLabelText('First Name'), { target: { value: 'Ada' } })
  fireEvent.change(screen.getByLabelText('Last Name'), { target: { value: 'Runner' } })
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ada@example.com' } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
  fireEvent.change(screen.getByLabelText('Confirm Password'), { target: { value: 'password123' } })
  fireEvent.click(screen.getByRole('checkbox'))
}

describe('RegisterPage capability onboarding', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('offers join, follow, and both choices and submits both capabilities together', async () => {
    registerMock.mockResolvedValue({ user: {} as never })
    renderRegister()

    expect(screen.getByRole('radio', { name: /join a race/i })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /follow races/i })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /^both/i })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('radio', { name: /^both/i }))
    fillRequiredFields()
    fireEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(registerMock).toHaveBeenCalledWith(expect.objectContaining({
        experiences: ['ATHLETE', 'SPECTATOR'],
      }))
    })
  })

  it('keeps capability selection out of the organizer flow', async () => {
    registerOrganizerMock.mockResolvedValue({ user: {} as never })
    renderRegister('/register?intent=organizer')

    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    fillRequiredFields()
    fireEvent.change(screen.getByLabelText('Organization / race brand'), { target: { value: 'Race Co' } })
    fireEvent.click(screen.getByRole('button', { name: /create organizer account/i }))

    await waitFor(() => {
      expect(registerOrganizerMock).toHaveBeenCalledWith(expect.not.objectContaining({ experiences: expect.anything() }))
    })
    expect(registerMock).not.toHaveBeenCalled()
  })
})
