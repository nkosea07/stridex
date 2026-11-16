import { TagAssignmentPage } from '@/pages/TagAssignmentPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'

export default function AdminRaceTagsRoute() {
  return (
    <RequireRoleRoute allowedRoles={['SUPER_ADMIN', 'RACE_ADMIN']}>
      <TagAssignmentPage />
    </RequireRoleRoute>
  )
}
