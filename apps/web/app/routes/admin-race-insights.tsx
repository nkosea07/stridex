import { RaceInsightsPage } from '@/pages/RaceInsightsPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'

export default function AdminRaceInsightsRoute() {
  return (
    <RequireRoleRoute allowedRoles={['SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL']}>
      <RaceInsightsPage />
    </RequireRoleRoute>
  )
}
