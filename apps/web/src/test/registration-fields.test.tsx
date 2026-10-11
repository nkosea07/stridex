import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RegistrationFieldsPage } from '@/pages/RegistrationFieldsPage'
import { registrationApi } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  registrationApi: {
    listFields: vi.fn(),
    createField: vi.fn(),
    updateField: vi.fn(),
    deleteField: vi.fn(),
  },
}))

const listFieldsMock = vi.mocked(registrationApi.listFields)
const createFieldMock = vi.mocked(registrationApi.createField)

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/races/race-1/registration']}>
      <Routes>
        <Route path="/admin/races/:raceId/registration" element={<RegistrationFieldsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RegistrationFieldsPage', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
  })

  it('renders configured registration fields', async () => {
    listFieldsMock.mockResolvedValue([
      {
        id: 'field-1',
        raceId: 'race-1',
        fieldKey: 'shirt_size',
        label: 'Shirt size',
        fieldType: 'SELECT',
        required: true,
        options: ['S', 'M', 'L'],
        displayOrder: 0,
        active: true,
      },
    ])

    renderPage()

    expect(await screen.findByText('Shirt size')).toBeInTheDocument()
    expect(screen.getByText('S, M, L')).toBeInTheDocument()
  })

  it('creates a new field from the dialog', async () => {
    listFieldsMock.mockResolvedValue([])
    createFieldMock.mockResolvedValue({
      id: 'field-2',
      raceId: 'race-1',
      fieldKey: 'club',
      label: 'Club',
      fieldType: 'TEXT',
      required: false,
      options: [],
      displayOrder: 0,
      active: true,
    })

    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: /add field/i }))
    fireEvent.change(screen.getByLabelText(/label/i), { target: { value: 'Club' } })
    fireEvent.click(screen.getByRole('button', { name: /^add$/i }))

    await waitFor(() => {
      expect(createFieldMock).toHaveBeenCalledWith('race-1', expect.objectContaining({ label: 'Club', fieldType: 'TEXT' }))
    })
  })
})
