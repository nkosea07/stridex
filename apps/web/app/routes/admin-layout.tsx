import { RequireAdminRoute } from '@/components/auth/RequireAdminRoute'
import { DashboardLayout } from '@/components/layout/DashboardLayout'

export default function AdminLayoutRoute() {
  return (
    <RequireAdminRoute>
      <DashboardLayout />
    </RequireAdminRoute>
  )
}
