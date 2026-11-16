import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import '@/i18n'

const apiMocks = vi.hoisted(() => ({
  list: vi.fn(),
  updateStatus: vi.fn(),
}))

vi.mock('@/lib/api', () => ({
  ApiError: class ApiError extends Error {
    constructor(public status: number, message: string, public code?: string) {
      super(message)
    }
  },
  emergencyApi: { list: apiMocks.list, updateStatus: apiMocks.updateStatus },
}))

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({ user: { role: 'RACE_ADMIN' } }),
}))

import { EmergencyAlertsPanel } from '@/components/dashboard/EmergencyAlertsPanel'

describe('EmergencyAlertsPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.list.mockResolvedValue([{
      id: 'alert-1', raceId: 'race-1', participantName: 'Ada Runner', latitude: -17.82912, longitude: 31.05337,
      status: 'ACTIVE', deliveryStatus: 'ESCALATION_REQUIRED', createdAt: '2026-03-07T08:00:00Z',
    }])
    apiMocks.updateStatus.mockResolvedValue({})
  })

  it('shows the shared location with a map link', async () => {
    render(<EmergencyAlertsPanel raceId="race-1" />)

    const link = await screen.findByRole('link', { name: 'Open in map' })
    expect(link.getAttribute('href')).toContain('mlat=-17.82912')
    expect(screen.getByText(/-17\.82912/)).toBeTruthy()
  })

  it('requires confirmation before resolving an alert', async () => {
    render(<EmergencyAlertsPanel raceId="race-1" />)

    fireEvent.click(await screen.findByRole('button', { name: /Resolve/ }))
    expect(apiMocks.updateStatus).not.toHaveBeenCalled()

    const dialog = await screen.findByRole('dialog')
    fireEvent.click(Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === 'Resolve')!)

    await waitFor(() => expect(apiMocks.updateStatus).toHaveBeenCalledWith('race-1', 'alert-1', 'RESOLVED'))
  })
})
