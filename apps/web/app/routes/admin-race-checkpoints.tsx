import { CheckpointsPage } from '@/pages/CheckpointsPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { RACE_OPERATION_ROLES } from '@/config/adminNavigation'

export default function AdminRaceCheckpointsRoute() {
  return (
    <RequireRoleRoute allowedRoles={RACE_OPERATION_ROLES}>
      <CheckpointsPage />
    </RequireRoleRoute>
  )
}
