import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ShieldAlert } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/contexts/AuthContext'

export function UnauthorizedPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { logout } = useAuth()
  const didLogout = useRef(false)

  useEffect(() => {
    if (didLogout.current) return
    didLogout.current = true
    logout()
    const timer = window.setTimeout(() => {
      navigate('/login', { replace: true, state: { reason: 'unauthorized' } })
    }, 1500)

    return () => window.clearTimeout(timer)
  }, [navigate, logout])

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-destructive/10">
            <ShieldAlert className="h-5 w-5 text-destructive" />
          </div>
          <CardTitle>{t('auth:accessDeniedTitle')}</CardTitle>
          <CardDescription>
            {t('auth:accessDeniedDescription')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button onClick={() => navigate('/login', { replace: true, state: { reason: 'unauthorized' } })}>
            {t('auth:goToLogin')}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
