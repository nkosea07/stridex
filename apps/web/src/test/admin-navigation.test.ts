import { describe, expect, it } from 'vitest'
import { getRaceNavigationForRole } from '@/config/adminNavigation'

describe('race navigation capabilities', () => {
  it('shows administrator-only setup and diagnostic capabilities to administrators', () => {
    const paths = getRaceNavigationForRole('SUPER_ADMIN').map((item) => item.to)

    expect(paths).toContain('registration')
    expect(paths).toContain('rfid')
    expect(paths).toContain('insights')
  })

  it('keeps volunteer navigation operational without privileged setup controls', () => {
    const paths = getRaceNavigationForRole('VOLUNTEER').map((item) => item.to)

    expect(paths).toContain('participants')
    expect(paths).toContain('checkpoints')
    expect(paths).not.toContain('registration')
    expect(paths).not.toContain('rfid')
    expect(paths).not.toContain('insights')
  })

  it('does not expose race operations to non-operations roles', () => {
    expect(getRaceNavigationForRole('ATHLETE')).toHaveLength(0)
    expect(getRaceNavigationForRole('SPECTATOR')).toHaveLength(0)
  })
})
