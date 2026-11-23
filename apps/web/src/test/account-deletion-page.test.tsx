import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { API_CONTRACT } from '@race-tracker/contracts'
import '@/i18n'
import routes from '../../app/routes'
import { AccountDeletionInfoPage } from '@/pages/AccountDeletionInfoPage'
import { LegalFooter } from '@/components/legal/LegalFooter'

describe('public account deletion page', () => {
  afterEach(cleanup)

  it('is registered at the contract path so store listings can link to it', () => {
    const paths = routes.map((entry) => `/${entry.path ?? ''}`)
    expect(paths).toContain(API_CONTRACT.legal.accountDeletionPath)
  })

  it('explains how to delete an account, what is removed, what is kept, and the grace period', () => {
    render(<MemoryRouter><AccountDeletionInfoPage /></MemoryRouter>)

    expect(screen.getByRole('heading', { level: 1, name: 'Delete your account' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '1. Delete your account in the app' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '2. Delete your account on the website' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '4. What we delete' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '5. What we keep' })).toBeInTheDocument()
    const timing = screen.getByRole('heading', { name: '6. When it happens' }).closest('section')
    expect(within(timing as HTMLElement).getByText(
      new RegExp(`anonymized ${API_CONTRACT.legal.accountDeletionGraceDays} days later`),
    )).toBeInTheDocument()
    expect(screen.getAllByText(new RegExp(API_CONTRACT.legal.contactEmail.replace(/[[\]]/g, '\\$&'))).length).toBeGreaterThan(0)
    expect(screen.getByRole('link', { name: 'Read our Privacy Policy' })).toHaveAttribute('href', API_CONTRACT.legal.privacyPolicyPath)
  })

  it('is linked from the legal footer', () => {
    render(<MemoryRouter><LegalFooter /></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Delete your account' }))
      .toHaveAttribute('href', API_CONTRACT.legal.accountDeletionPath)
  })
})
