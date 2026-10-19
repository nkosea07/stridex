import { Navigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { isOperationsRole, isPersonalUser, useAuth } from '@/contexts/AuthContext'
import { LandingPage } from '@/pages/LandingPage'

export default function HomeRoute() {
  const { isLoading, isAuthenticated, user } = useAuth()
  const { t } = useTranslation()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">{t('auth:loading')}</div>
  }

  if (!isAuthenticated || !user) {
    return <LandingPage />
  }

  if (isOperationsRole(user.role)) return <Navigate to="/admin" replace />
  if (isPersonalUser(user)) return <Navigate to="/app" replace />
  return <Navigate to="/unauthorized" replace />
}
