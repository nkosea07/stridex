import { RfidReadersPage } from '@/pages/RfidReadersPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'

export default function AdminRaceRfidRoute() {
  return (
    <RequireRoleRoute allowedRoles={['SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL']}>
      <RfidReadersPage />
    </RequireRoleRoute>
  )
}
