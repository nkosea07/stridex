import { RaceDetailPage } from '@/pages/RaceDetailsPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { RACE_OPERATION_ROLES } from '@/config/adminNavigation'

export default function AdminRaceDetailRoute() {
  return (
    <RequireRoleRoute allowedRoles={RACE_OPERATION_ROLES}>
      <RaceDetailPage />
    </RequireRoleRoute>
  )
}
