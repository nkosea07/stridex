import { OrganizerApplicationsPage } from '@/pages/OrganizerApplicationsPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'

export default function AdminOrganizerApplicationsRoute() {
  return (
    <RequireRoleRoute allowedRoles={['SUPER_ADMIN']}>
      <OrganizerApplicationsPage />
    </RequireRoleRoute>
  )
}
