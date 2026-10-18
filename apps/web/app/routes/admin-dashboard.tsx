import { DashboardPage } from '@/pages/DashboardPage'
import { RacesPage } from '@/pages/RacesPage'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'

export default function AdminDashboardRoute() {
  const { user } = useAuth()
  return isAdminRole(user?.role) ? <DashboardPage /> : <RacesPage />
}
