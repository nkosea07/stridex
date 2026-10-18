import { CategoriesPage } from '@/pages/CategoriesPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { RACE_OPERATION_ROLES } from '@/config/adminNavigation'

export default function AdminRaceCategoriesRoute() {
  return (
    <RequireRoleRoute allowedRoles={RACE_OPERATION_ROLES}>
      <CategoriesPage />
    </RequireRoleRoute>
  )
}
