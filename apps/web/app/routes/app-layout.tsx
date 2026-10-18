import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { UserLayout } from '@/components/layout/UserLayout'
import { PERSONAL_ROLES } from '@/contexts/AuthContext'

export default function AppLayoutRoute() {
  return (
    <RequireRoleRoute allowedRoles={PERSONAL_ROLES}>
      <UserLayout />
    </RequireRoleRoute>
  )
}
