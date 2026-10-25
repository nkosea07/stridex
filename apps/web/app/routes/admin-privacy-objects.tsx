import { PrivacyOperationsPage } from '@/pages/PrivacyOperationsPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'

export default function AdminPrivacyObjectsRoute() {
  return (
    <RequireRoleRoute allowedRoles={['SUPER_ADMIN']}>
      <PrivacyOperationsPage />
    </RequireRoleRoute>
  )
}
