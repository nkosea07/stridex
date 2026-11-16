import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { API_CONTRACT } from '@race-tracker/contracts'
import '@/i18n'
import { TermsPage } from '@/pages/TermsPage'
import { PrivacyPolicyPage } from '@/pages/PrivacyPolicyPage'

describe('legal documents', () => {
  afterEach(cleanup)

  it('renders the terms with the current version, contents, and operator details', () => {
    render(<MemoryRouter><TermsPage /></MemoryRouter>)

    expect(screen.getByRole('heading', { level: 1, name: 'Terms and Conditions' })).toBeTruthy()
    expect(screen.getByText(`Version ${API_CONTRACT.legal.termsVersion}`)).toBeTruthy()
    expect(screen.getByRole('navigation', { name: 'Contents' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 2, name: '8. Emergency SOS' })).toBeTruthy()
    expect(screen.getAllByText(new RegExp(API_CONTRACT.legal.operatorName.replace(/[[\]]/g, '\\$&'))).length).toBeGreaterThan(0)
  })

  it('states the configured retention periods in the privacy policy', () => {
    render(<MemoryRouter><PrivacyPolicyPage /></MemoryRouter>)

    expect(screen.getByText(new RegExp(`deleted ${API_CONTRACT.legal.gpsRetentionDays} days after`))).toBeTruthy()
    expect(screen.getByText(new RegExp(`after ${API_CONTRACT.legal.accountDeletionGraceDays} days`))).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Read our Terms and Conditions' }).getAttribute('href')).toBe(API_CONTRACT.legal.termsPath)
  })
})
