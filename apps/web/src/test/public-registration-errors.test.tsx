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

  it.each([
    ['API_ERROR', 'Registration is not currently available for this race.'],
    ['CONFLICT', 'You are already registered, or this race cannot accept another registration.'],
  ])('maps %s without exposing the API message', async (code, expectedMessage) => {
    mocks.publicRegistrationApi.register.mockRejectedValueOnce(
      new mocks.ApiError(400, 'sensitive backend detail', code),
    )

    render(
      <MemoryRouter>
        <PublicRaceRegistrationPanel raceId="race-1" hub={hub} returnTo="/race/race-1" />
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByRole('combobox')).toHaveValue('category-1'))
    fireEvent.submit(screen.getByRole('button', { name: /submit registration/i }).closest('form')!)

    await waitFor(() => expect(mocks.toast.error).toHaveBeenCalledWith(expectedMessage))
    expect(mocks.toast.error).not.toHaveBeenCalledWith('sensitive backend detail')
  })
})
