import { LiveTrackingPage } from '@/pages/LiveTrackingPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { RACE_OPERATION_ROLES } from '@/config/adminNavigation'

export default function AdminRaceLiveRoute() {
  return (
    <RequireRoleRoute allowedRoles={RACE_OPERATION_ROLES}>
      <LiveTrackingPage />
    </RequireRoleRoute>
  )
}
