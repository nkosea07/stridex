import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import '@/i18n'

const apiMocks = vi.hoisted(() => ({
  list: vi.fn(),
  revokeNonPerson: vi.fn(),
}))

vi.mock('@/lib/api', () => ({
  mediaApi: {
    list: apiMocks.list,
    revokeNonPerson: apiMocks.revokeNonPerson,
    attestNonPerson: vi.fn(),
    upload: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({ user: { role: 'SUPER_ADMIN' } }),
  isAdminRole: () => true,
}))

vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useParams: () => ({ raceId: 'race-1' }),
}))

import { MediaPage } from '@/pages/MediaPage'

describe('MediaPage publication state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('explains approved media hidden by missing consent and revokes attestations', async () => {
    apiMocks.list.mockResolvedValue([
      {
        id: 'media-1', raceId: 'race-1', participantId: 'participant-1', mediaType: 'IMAGE',
        url: '/content', status: 'APPROVED', participantConsent: false, nonPersonAttested: false,
        publiclyVisible: false,
      },
      {
        id: 'media-2', raceId: 'race-1', participantId: null, mediaType: 'IMAGE',
        url: '/content', status: 'APPROVED', participantConsent: false, nonPersonAttested: true,
        publiclyVisible: true,
      },
    ])
    apiMocks.revokeNonPerson.mockResolvedValue({})

    render(<MediaPage />)

    expect(await screen.findByText(/has not granted media consent/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /revoke attestation/i }))
    await waitFor(() => expect(apiMocks.revokeNonPerson).toHaveBeenCalledWith('race-1', 'media-2'))
  })
})
