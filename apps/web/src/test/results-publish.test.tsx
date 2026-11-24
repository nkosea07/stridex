import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ResultsPage } from '@/pages/ResultsPage'
import '@/i18n'

const mocks = vi.hoisted(() => {
  class MockApiError extends Error {
    constructor(public status: number, message: string, public code?: string) {
      super(message)
    }
  }
  return {
    ApiError: MockApiError,
    role: 'RACE_ADMIN',
    racesApi: { get: vi.fn() },
    resultsApi: {
      page: vi.fn(),
      teamRankings: vi.fn(),
      countryRankings: vi.fn(),
      publish: vi.fn(),
      exportResults: vi.fn(),
      anomalies: vi.fn(),
      openQueries: vi.fn(),
      integrity: vi.fn(),
      versions: vi.fn(),
      certify: vi.fn(),
      reopen: vi.fn(),
      resolveQuery: vi.fn(),
    },
    toast: { success: vi.fn(), error: vi.fn() },
  }
})

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'u1', role: mocks.role } }),
  isAdminRole: (role?: string) => role === 'SUPER_ADMIN' || role === 'RACE_ADMIN',
}))
vi.mock('@/lib/api', () => ({ ApiError: mocks.ApiError, racesApi: mocks.racesApi, resultsApi: mocks.resultsApi }))
vi.mock('sonner', () => ({ toast: mocks.toast }))

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/races/race-1/results']}>
      <Routes>
        <Route path="/admin/races/:raceId/results" element={<ResultsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('results publishing', () => {
  beforeEach(() => {
    mocks.role = 'RACE_ADMIN'
    mocks.resultsApi.page.mockResolvedValue({ items: [], page: 0, size: 50, totalElements: 0, totalPages: 0, revision: 1 })
    mocks.resultsApi.integrity.mockResolvedValue(null)
    mocks.resultsApi.versions.mockResolvedValue([])
    mocks.resultsApi.anomalies.mockResolvedValue([])
    mocks.resultsApi.openQueries.mockResolvedValue([])
  })

  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('offers publishing to organizers once the race is completed', async () => {
    mocks.racesApi.get.mockResolvedValue({ id: 'race-1', status: 'COMPLETED' })
    mocks.resultsApi.publish.mockResolvedValue({ message: 'ok' })
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: /publish results/i }))

    await waitFor(() => expect(mocks.resultsApi.publish).toHaveBeenCalledWith('race-1'))
  })

  it('hides publishing while the race is still running', async () => {
    mocks.racesApi.get.mockResolvedValue({ id: 'race-1', status: 'IN_PROGRESS' })
    renderPage()

    await waitFor(() => expect(mocks.racesApi.get).toHaveBeenCalledWith('race-1'))
    expect(screen.queryByRole('button', { name: /publish results/i })).toBeNull()
  })

  it('hides publishing from staff who cannot publish', async () => {
    mocks.role = 'VOLUNTEER'
    renderPage()

    await waitFor(() => expect(mocks.resultsApi.page).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: /publish results/i })).toBeNull()
    expect(mocks.racesApi.get).not.toHaveBeenCalled()
  })

  it('explains when unpaid finishers block publishing', async () => {
    mocks.racesApi.get.mockResolvedValue({ id: 'race-1', status: 'COMPLETED' })
    mocks.resultsApi.publish.mockRejectedValue(new mocks.ApiError(409, 'detail', 'RESULTS_UNPAID_FINISHERS'))
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: /publish results/i }))

    await waitFor(() => expect(mocks.toast.error).toHaveBeenCalledWith(
      'Some finishers have not paid the entry fee. Verify or waive their payment, or change their status, then publish again.'))
  })
})
