import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { isOperationsRole, useAuth } from '@/contexts/AuthContext'

interface RequireOperationsRouteProps {
  children: ReactNode
}

export function RequireOperationsRoute({ children }: RequireOperationsRouteProps) {
  const { isLoading, isAuthenticated, user } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">Loading...</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (!user || !isOperationsRole(user.role)) {
    return <Navigate to="/unauthorized" replace />
  }

  return <>{children}</>
}
