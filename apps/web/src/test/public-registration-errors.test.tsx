import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PublicRaceRegistrationPanel } from '@/components/public/PublicRaceRegistrationPanel'
import type { PublicRaceHub } from '@/types'
import '@/i18n'

const mocks = vi.hoisted(() => {
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string,
      public code?: string,
    ) {
      super(message)
      this.name = 'ApiError'
    }
  }

  return {
    ApiError: MockApiError,
    publicRegistrationApi: {
      listFields: vi.fn().mockResolvedValue([]),
      register: vi.fn(),
    },
    toast: {
      error: vi.fn(),
      success: vi.fn(),
    },
  }
})

vi.mock('@/contexts/AuthContext', () => ({
  useAuthOptional: () => ({
    isAuthenticated: true,
    hasCapability: () => true,
  }),
}))

vi.mock('@/lib/api', () => ({
  ApiError: mocks.ApiError,
  publicRegistrationApi: mocks.publicRegistrationApi,
}))

vi.mock('sonner', () => ({ toast: mocks.toast }))

const hub = {
  raceId: 'race-1',
  raceName: 'Test Race',
  status: 'REGISTRATION_OPEN',
  raceDate: '2026-12-01T06:00:00Z',
  isPublicRegistration: true,
  requireWaiver: false,
  categories: [{ id: 'category-1', name: '10K' }],
} as unknown as PublicRaceHub

describe('PublicRaceRegistrationPanel safe errors', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  function renderPanel(raceHub: PublicRaceHub = hub) {
    return render(
      <MemoryRouter>
        <PublicRaceRegistrationPanel raceId="race-1" hub={raceHub} returnTo="/race/race-1" />
      </MemoryRouter>,
    )
  }

  async function fillRequiredDetails() {
    await waitFor(() => expect(document.getElementById('reg-category')).toHaveValue('category-1'))
    fireEvent.change(screen.getByLabelText('Gender'), { target: { value: 'FEMALE' } })
    fireEvent.change(screen.getByLabelText('Date of birth'), { target: { value: '1990-05-17' } })
  }

  function submit() {
    fireEvent.submit(screen.getByRole('button', { name: /submit registration/i }).closest('form')!)
  }

  it.each([
    ['API_ERROR', 'Registration is not currently available for this race.'],
    ['CONFLICT', 'You are already registered, or this race cannot accept another registration.'],
    ['CATEGORY_FULL', 'This category is full. Choose another category.'],
    ['CATEGORY_AGE_INELIGIBLE', 'Your age on race day is outside this category\u2019s age range. Choose another category.'],
    ['CATEGORY_GENDER_INELIGIBLE', 'This category is not open to the gender you selected. Choose another category.'],
    ['REGISTRATION_NOT_OPEN', 'Registration for this race is not open.'],
  ])('maps %s without exposing the API message', async (code, expectedMessage) => {
    mocks.publicRegistrationApi.register.mockRejectedValueOnce(
      new mocks.ApiError(400, 'sensitive backend detail', code),
    )

    renderPanel()
    await fillRequiredDetails()
    submit()

    await waitFor(() => expect(mocks.toast.error).toHaveBeenCalledWith(expectedMessage))
    expect(mocks.toast.error).not.toHaveBeenCalledWith('sensitive backend detail')
  })

  it('sends gender and date of birth with the registration', async () => {
    mocks.publicRegistrationApi.register.mockResolvedValueOnce({ id: 'entry-1', raceId: 'race-1', status: 'pending' })

    renderPanel()
    await fillRequiredDetails()
    submit()

    await waitFor(() => expect(mocks.publicRegistrationApi.register).toHaveBeenCalledWith('race-1',
      expect.objectContaining({ categoryId: 'category-1', gender: 'FEMALE', dateOfBirth: '1990-05-17' })))
  })

  it('asks for gender before submitting', async () => {
    renderPanel()
    await waitFor(() => expect(document.getElementById('reg-category')).toHaveValue('category-1'))
    submit()

    await waitFor(() => expect(mocks.toast.error).toHaveBeenCalledWith(
      'Select your gender. It is used for gender categories and results.'))
    expect(mocks.publicRegistrationApi.register).not.toHaveBeenCalled()
  })

  it('does not offer the form unless the race is open for registration', () => {
    renderPanel({ ...hub, status: 'REGISTRATION_CLOSED' } as unknown as PublicRaceHub)

    expect(screen.queryByRole('button', { name: /submit registration/i })).toBeNull()
  })
})
