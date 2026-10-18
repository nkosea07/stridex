import { RequireOperationsRoute } from '@/components/auth/RequireOperationsRoute'
import { DashboardLayout } from '@/components/layout/DashboardLayout'

export default function AdminLayoutRoute() {
  return (
    <RequireOperationsRoute>
      <DashboardLayout />
    </RequireOperationsRoute>
  )
}
