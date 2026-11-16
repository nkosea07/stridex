import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { SWRConfig } from 'swr'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import '@/i18n'

const mocks = vi.hoisted(() => ({ list: vi.fn(), update: vi.fn() }))
vi.mock('@/lib/api', () => {
  class ApiError extends Error {
    constructor(public status: number, message: string, public code?: string) { super(message) }
  }
  return { ApiError, participantsApi: { list: mocks.list, update: mocks.update } }
})

import { TagAssignmentPage } from '@/pages/TagAssignmentPage'
import { ApiError } from '@/lib/api'

function renderPage() {
  return render(
    <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
      <MemoryRouter initialEntries={['/admin/races/r1/tags']}>
        <Routes><Route path="/admin/races/:raceId/tags" element={<TagAssignmentPage />} /></Routes>
      </MemoryRouter>
    </SWRConfig>,
  )
}

describe('packet pickup chip assignment', () => {
  afterEach(cleanup)
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.list.mockResolvedValue([{ id: 'p1', firstName: 'Tari', lastName: 'Moyo', bibNumber: '101', rfidTag: null }])
  })

  it('assigns a chip by bib and gets ready for the next athlete', async () => {
    mocks.update.mockResolvedValue({ id: 'p1', firstName: 'Tari', lastName: 'Moyo', bibNumber: '101', rfidTag: 'E2003411' })
    renderPage()
    await waitFor(() => expect(mocks.list).toHaveBeenCalled())

    fireEvent.change(screen.getByLabelText('Bib number'), { target: { value: '101' } })
    fireEvent.click(screen.getByRole('button', { name: 'Find' }))
    fireEvent.change(await screen.findByLabelText('Chip (RFID tag)'), { target: { value: 'e200 3411' } })
    fireEvent.click(screen.getByRole('button', { name: 'Assign chip' }))

    await waitFor(() => expect(mocks.update).toHaveBeenCalledWith('r1', 'p1', { rfidTag: 'e200 3411' }))
    expect((await screen.findByRole('status')).textContent).toContain('Chip assigned to Tari Moyo')
    expect((screen.getByLabelText('Bib number') as HTMLInputElement).value).toBe('')
  })

  it('explains when a chip already belongs to someone else', async () => {
    mocks.update.mockRejectedValue(new ApiError(409, 'in use', 'RFID_TAG_IN_USE'))
    renderPage()
    await waitFor(() => expect(mocks.list).toHaveBeenCalled())

    fireEvent.change(screen.getByLabelText('Bib number'), { target: { value: '101' } })
    fireEvent.click(screen.getByRole('button', { name: 'Find' }))
    fireEvent.change(await screen.findByLabelText('Chip (RFID tag)'), { target: { value: 'E200' } })
    fireEvent.click(screen.getByRole('button', { name: 'Assign chip' }))

    expect((await screen.findByRole('status')).textContent).toContain('already assigned')
  })
})
