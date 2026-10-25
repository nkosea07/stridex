import { ReversalReviewPage } from '@/pages/ReversalReviewPage'
import { RequireRoleRoute } from '@/components/auth/RequireRoleRoute'

export default function AdminFinanceReversalsRoute() {
  return (
    <RequireRoleRoute allowedRoles={['SUPER_ADMIN']}>
      <ReversalReviewPage />
    </RequireRoleRoute>
  )
}
