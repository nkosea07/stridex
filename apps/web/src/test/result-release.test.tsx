import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { SWRConfig } from 'swr'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import '@/i18n'

const mocks = vi.hoisted(() => ({ integrity: vi.fn(), versions: vi.fn(), certify: vi.fn(), reopen: vi.fn() }))
vi.mock('@/lib/api', () => ({ resultsApi: mocks }))
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))

import { ResultReleasePanel } from '@/components/results/ResultReleasePanel'

const intact = {
  latestVersion: 2, status: 'PROVISIONAL', publishedAt: '2026-11-15T12:00:00Z', certifiedAt: null,
  contentSha256: 'a'.repeat(64), chainSha256: 'b'.repeat(64), chainValid: true, publishedResultsIntact: true,
  changedSincePublish: false,
}

function renderPanel() {
  return render(
    <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
      <ResultReleasePanel raceId="r1" canManage />
    </SWRConfig>,
  )
}

describe('results release', () => {
  afterEach(cleanup)
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.versions.mockResolvedValue([])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('certifies an intact provisional version', async () => {
    mocks.integrity.mockResolvedValue(intact)
    renderPanel()

    fireEvent.click(await screen.findByRole('button', { name: 'Certify as official' }))

    await waitFor(() => expect(mocks.certify).toHaveBeenCalledWith('r1'))
  })

  it('refuses to offer certification when results were altered outside the platform', async () => {
    mocks.integrity.mockResolvedValue({ ...intact, publishedResultsIntact: false })
    renderPanel()

    expect(await screen.findByText(/changed them outside the platform/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Certify as official' })).toBeNull()
  })

  it('requires a reason to reopen certified results', async () => {
    mocks.integrity.mockResolvedValue({ ...intact, status: 'OFFICIAL', certifiedAt: '2026-11-15T13:00:00Z' })
    renderPanel()

    fireEvent.click(await screen.findByRole('button', { name: 'Reopen results' }))
    expect(mocks.reopen).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText(/Reason for reopening/), { target: { value: 'Protest upheld for bib 101' } })
    fireEvent.click(screen.getByRole('button', { name: 'Reopen results' }))
    await waitFor(() => expect(mocks.reopen).toHaveBeenCalledWith('r1', 'Protest upheld for bib 101'))
  })
})
