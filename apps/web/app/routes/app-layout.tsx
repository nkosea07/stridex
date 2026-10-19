import { RequireCapabilityRoute } from '@/components/auth/RequireCapabilityRoute'
import { UserLayout } from '@/components/layout/UserLayout'
import { PERSONAL_CAPABILITIES } from '@/contexts/AuthContext'

export default function AppLayoutRoute() {
  return (
    <RequireCapabilityRoute capability={PERSONAL_CAPABILITIES}>
      <UserLayout />
    </RequireCapabilityRoute>
  )
}
