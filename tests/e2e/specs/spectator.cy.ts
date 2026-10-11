// Cypress e2e: public spectator flow. Not part of the default CI job; see tests/e2e/README.md.
// Run with a seeded race whose id is provided via CYPRESS_RACE_ID.

describe('public spectator flow', () => {
  const raceId = Cypress.env('RACE_ID') || 'demo'

  it('loads the race hub and follows an athlete', () => {
    cy.visit(`/race/${raceId}`)
    cy.contains(/public race hub/i, { timeout: 15000 }).should('be.visible')

    cy.request(`/api/v1/public/races/${raceId}/tracking/live`).its('status').should('eq', 200)

    cy.get('a[href*="/athlete/"]').first().click({ force: true })
    cy.contains(/public athlete tracking/i, { timeout: 15000 }).should('be.visible')
  })

  it('exposes a PII-free public payload', () => {
    cy.request(`/api/v1/public/races/${raceId}/tracking/live`).then((response) => {
      const body = JSON.stringify(response.body)
      expect(body).to.not.match(/@/)
      expect(body).to.not.contain('phone')
      expect(body).to.not.contain('medical')
    })
  })
})
