import { defineConfig } from 'cypress'

export default defineConfig({
  e2e: {
    baseUrl: process.env.CYPRESS_BASE_URL || 'http://localhost:4173',
    specPattern: 'specs/**/*.cy.ts',
    fixturesFolder: 'fixtures',
    supportFile: false,
    video: false,
  },
})
