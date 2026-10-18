import { PromoCodesPage } from '@/pages/PromoCodesPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'
import { RACE_OPERATION_ROLES } from '@/config/adminNavigation'

export default function AdminRacePromosRoute() {
  return (
    <RequireRoleRoute allowedRoles={RACE_OPERATION_ROLES}>
      <PromoCodesPage />
    </RequireRoleRoute>
  )
}
