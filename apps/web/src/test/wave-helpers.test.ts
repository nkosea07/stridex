import { describe, expect, it } from 'vitest'
import { bibInRange } from '@/components/rfid/WavesPanel'

describe('wave bib ranges', () => {
  it('compares numeric bibs numerically and others alphabetically', () => {
    expect(bibInRange('150', '100', '199')).toBe(true)
    expect(bibInRange('1500', '100', '199')).toBe(false)
    expect(bibInRange('A12', 'A10', 'A20')).toBe(true)
    expect(bibInRange(null, '1', '9')).toBe(false)
  })
})
