import { Navigate } from 'react-router-dom'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'

export default function HomeRoute() {
  const { isLoading, isAuthenticated, user } = useAuth()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">Loading...</div>
  }

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />
  }

  return <Navigate to={isAdminRole(user.role) ? '/admin' : '/app'} replace />
}
