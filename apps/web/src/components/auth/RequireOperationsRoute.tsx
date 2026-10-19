import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { isOperationsRole, useAuth } from '@/contexts/AuthContext'

interface RequireOperationsRouteProps {
  children: ReactNode
}

export function RequireOperationsRoute({ children }: RequireOperationsRouteProps) {
  const { isLoading, isAuthenticated, user } = useAuth()
  const { t } = useTranslation()
  const location = useLocation()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">{t('auth:loading')}</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (!user || !isOperationsRole(user.role)) {
    return <Navigate to="/unauthorized" replace />
  }

  return <>{children}</>
}
