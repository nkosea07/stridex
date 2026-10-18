import { ParticipantsPage } from '@/pages/ParticipantsPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { RACE_OPERATION_ROLES } from '@/config/adminNavigation'

export default function AdminRaceParticipantsRoute() {
  return (
    <RequireRoleRoute allowedRoles={RACE_OPERATION_ROLES}>
      <ParticipantsPage />
    </RequireRoleRoute>
  )
}
