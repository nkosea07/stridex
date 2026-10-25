import { RaceSetupPage } from '@/pages/RaceSetupPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { RACE_ADMIN_ROLES } from '@/config/adminNavigation'

export default function AdminRaceSetupRoute() {
  return (
    <RequireRoleRoute allowedRoles={RACE_ADMIN_ROLES}>
      <RaceSetupPage />
    </RequireRoleRoute>
  )
}
