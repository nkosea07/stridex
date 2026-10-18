import { AnalyticsPage } from '@/pages/AnalyticsPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { RACE_OPERATION_ROLES } from '@/config/adminNavigation'

export default function AdminRaceAnalyticsRoute() {
  return (
    <RequireRoleRoute allowedRoles={RACE_OPERATION_ROLES}>
      <AnalyticsPage />
    </RequireRoleRoute>
  )
}
