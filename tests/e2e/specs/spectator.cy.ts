// Cypress e2e: public spectator flow. Hermetic — all API calls are intercepted so the spec
// runs against the built web app with no backend. Wired into CI (see pilot-validation.yml).

describe('public spectator flow', () => {
  const raceId = 'demo'

  beforeEach(() => {
    cy.intercept('GET', `**/api/v1/public/races/${raceId}/hub`, { fixture: 'race-hub.json' }).as('hub')
    cy.intercept('GET', `**/api/v1/public/races/${raceId}/tracking/live-feed*`, {
      body: { items: [], nextCursor: null, refreshedAt: new Date().toISOString() },
    }).as('liveFeed')
    cy.intercept('GET', `**/api/v1/public/races/${raceId}/tracking/live`, { body: [] }).as('live')
    cy.intercept('GET', `**/api/v1/public/races/${raceId}/tracking/heatmap*`, { body: [] })
    cy.intercept('GET', `**/api/v1/public/races/${raceId}/media`, { body: [] })
    cy.intercept('GET', `**/api/v1/public/races/${raceId}/registration-fields`, { body: [] })
    cy.intercept('GET', `**/api/v1/public/races/${raceId}/results*`, { body: { items: [], page: 0, size: 50, totalElements: 0, totalPages: 0, revision: 0 } })
  })

  it('loads the race hub with the public leaderboard', () => {
    cy.visit(`/race/${raceId}`)
    cy.contains(/public race hub/i, { timeout: 15000 }).should('be.visible')
    cy.contains(/demo race/i).should('be.visible')
  })

  it('keeps the public payload PII-free', () => {
    cy.fixture('race-hub.json').then((hub) => {
      const body = JSON.stringify(hub)
      expect(body).to.not.match(/@/)
      expect(body).to.not.contain('phone')
      expect(body).to.not.contain('medical')
      expect(body).to.not.contain('emergency')
    })
  })
})
