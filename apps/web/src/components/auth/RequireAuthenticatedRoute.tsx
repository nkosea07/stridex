import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/contexts/AuthContext'

interface RequireAuthenticatedRouteProps {
  children: ReactNode
}

export function RequireAuthenticatedRoute({ children }: RequireAuthenticatedRouteProps) {
  const { isLoading, isAuthenticated } = useAuth()
  const { t } = useTranslation()
  const location = useLocation()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">{t('auth:loading')}</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}
