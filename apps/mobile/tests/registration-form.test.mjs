/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { describe, expect, it } from 'vitest'
import { isPastIsoDate } from '../lib/registrationForm.ts'

const NOW = Date.UTC(2026, 10, 23)

describe('registration date of birth', () => {
  it('accepts a real past date in YYYY-MM-DD form', () => {
    expect(isPastIsoDate('1990-05-17', NOW)).toBe(true)
    expect(isPastIsoDate(' 2000-02-29 ', NOW)).toBe(true)
  })

  it('rejects malformed, impossible, future or implausibly old dates', () => {
    expect(isPastIsoDate('17/05/1990', NOW)).toBe(false)
    expect(isPastIsoDate('1990-5-17', NOW)).toBe(false)
    expect(isPastIsoDate('2001-02-29', NOW)).toBe(false)
    expect(isPastIsoDate('1990-13-01', NOW)).toBe(false)
    expect(isPastIsoDate('2026-11-24', NOW)).toBe(false)
    expect(isPastIsoDate('1899-12-31', NOW)).toBe(false)
    expect(isPastIsoDate('', NOW)).toBe(false)
  })
})
