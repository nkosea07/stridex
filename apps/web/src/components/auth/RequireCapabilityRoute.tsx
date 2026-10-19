import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { getUserCapabilities, useAuth } from '@/contexts/AuthContext'
import type { UserCapability } from '@/types'

interface RequireCapabilityRouteProps {
  capability: UserCapability | readonly UserCapability[]
  children: ReactNode
}

export function RequireCapabilityRoute({ capability, children }: RequireCapabilityRouteProps) {
  const { t } = useTranslation()
  const { isLoading, isAuthenticated, user } = useAuth()
  const location = useLocation()
  const requiredCapabilities = Array.isArray(capability) ? capability : [capability]

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">{t('auth:loading')}</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  const userCapabilities = getUserCapabilities(user)
  if (!requiredCapabilities.some((required) => userCapabilities.includes(required))) {
    return <Navigate to="/unauthorized" replace />
  }

  return <>{children}</>
}
