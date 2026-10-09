import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PublicRaceResultsPanel } from '@/components/public/PublicRaceResultsPanel'

function page(pageNumber: number, name: string) {
  return {
    items: [{ participantId: `runner-${pageNumber}`, participantName: name, bibNumber: String(pageNumber), status: 'FINISHED', totalTime: '03:42:10' }],
    page: pageNumber,
    size: 25,
    totalElements: 2,
    totalPages: 2,
    revision: 8,
  }
}

describe('PublicRaceResultsPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('loads bounded pages and carries the result revision forward', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(page(0, 'Ava Runner')), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(page(1, 'Noah Runner')), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    render(<PublicRaceResultsPanel raceId="race-123" />)
    expect(await screen.findByText(/Ava Runner/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Load more finishers' }))
    expect(await screen.findByText(/Noah Runner/)).toBeTruthy()

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(String(fetchMock.mock.calls[0][0])).toContain('/public/races/race-123/results?page=0&size=25')
    expect(String(fetchMock.mock.calls[1][0])).toContain('/public/races/race-123/results?page=1&size=25&revision=8')
  })
})
