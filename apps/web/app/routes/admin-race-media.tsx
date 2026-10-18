import { MediaPage } from '@/pages/MediaPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { RACE_OPERATION_ROLES } from '@/config/adminNavigation'

export default function AdminRaceMediaRoute() {
  return (
    <RequireRoleRoute allowedRoles={RACE_OPERATION_ROLES}>
      <MediaPage />
    </RequireRoleRoute>
  )
}
