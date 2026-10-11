import { Navigate } from 'react-router-dom'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { LandingPage } from '@/pages/LandingPage'

export default function HomeRoute() {
  const { isLoading, isAuthenticated, user } = useAuth()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">Loading...</div>
  }

  if (!isAuthenticated || !user) {
    return <LandingPage />
  }

  return <Navigate to={isAdminRole(user.role) ? '/admin' : '/app'} replace />
}
