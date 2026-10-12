import { RequireAuthenticatedRoute } from '@/components/auth/RequireAuthenticatedRoute'
import { NotificationsPage } from '@/pages/NotificationsPage'

export default function NotificationsRoute() {
  return (
    <RequireAuthenticatedRoute>
      <NotificationsPage />
    </RequireAuthenticatedRoute>
  )
}
