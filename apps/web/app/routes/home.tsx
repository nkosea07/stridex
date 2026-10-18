import { Navigate } from 'react-router-dom'
import { isOperationsRole, isPersonalRole, useAuth } from '@/contexts/AuthContext'
import { LandingPage } from '@/pages/LandingPage'

export default function HomeRoute() {
  const { isLoading, isAuthenticated, user } = useAuth()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">Loading...</div>
  }

  if (!isAuthenticated || !user) {
    return <LandingPage />
  }

  if (isOperationsRole(user.role)) return <Navigate to="/admin" replace />
  if (isPersonalRole(user.role)) return <Navigate to="/app" replace />
  return <Navigate to="/unauthorized" replace />
}
