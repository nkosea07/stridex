import { RequireAuthenticatedRoute } from '@/components/auth/RequireAuthenticatedRoute'
import { UserLayout } from '@/components/layout/UserLayout'

export default function AppLayoutRoute() {
  return (
    <RequireAuthenticatedRoute>
      <UserLayout />
    </RequireAuthenticatedRoute>
  )
}
