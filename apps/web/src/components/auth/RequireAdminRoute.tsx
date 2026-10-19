import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'

interface RequireAdminRouteProps {
  children: ReactNode
}

export function RequireAdminRoute({ children }: RequireAdminRouteProps) {
  const { isLoading, isAuthenticated, user } = useAuth()
  const { t } = useTranslation()
  const location = useLocation()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">{t('auth:loading')}</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (!user || !isAdminRole(user.role)) {
    return <Navigate to="/unauthorized" replace />
  }

  return <>{children}</>
}
