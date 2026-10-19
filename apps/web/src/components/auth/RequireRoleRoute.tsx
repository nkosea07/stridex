import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/contexts/AuthContext'
import type { UserRole } from '@/types'

interface RequireRoleRouteProps {
  allowedRoles: readonly UserRole[]
  children: ReactNode
}

export function RequireRoleRoute({ allowedRoles, children }: RequireRoleRouteProps) {
  const { isLoading, isAuthenticated, user } = useAuth()
  const { t } = useTranslation()
  const location = useLocation()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">{t('auth:loading')}</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (!user || !allowedRoles.includes(user.role)) {
    return <Navigate to="/unauthorized" replace />
  }

  return <>{children}</>
}
