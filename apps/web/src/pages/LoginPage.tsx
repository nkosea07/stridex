import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Flag, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { isOperationsRole, isPersonalUser, useAuth } from '@/contexts/AuthContext'
import { toast } from 'sonner'
import { authApi } from '@/lib/api'
import { LegalFooter } from '@/components/legal/LegalFooter'
import { MfaChallenge } from '@/components/auth/MfaChallenge'
import type { BrowserAuthResponse } from '@/lib/api'
import type { User } from '@/types'

function getSafePublicReturnPath(value: unknown) {
  if (typeof value !== 'string') return undefined
  if (!value.startsWith('/') || value.startsWith('//')) return undefined

  const path = value.split(/[?#]/, 1)[0]
  const isRaceHub = /^\/race\/[^/]+$/.test(path)
  const isAthletePage = /^\/race\/[^/]+\/athlete\/[^/]+$/.test(path)

  return isRaceHub || isAthletePage ? value : undefined
}

export function LoginPage() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [isRecovering, setIsRecovering] = useState(false)
  const { login, completeSession } = useAuth()
  const [mfa, setMfa] = useState<{ challengeToken: string; enrollmentRequired: boolean } | null>(null)
  const navigate = useNavigate()
  const location = useLocation()
  const locationState = location.state as { from?: string; reason?: string } | null
  const unauthorizedReason = locationState?.reason === 'unauthorized'
  const returnTo = getSafePublicReturnPath(locationState?.from)

  const enterApp = (user: User) => {
    const defaultPath = isOperationsRole(user.role)
      ? '/admin'
      : isPersonalUser(user)
        ? '/app'
        : '/unauthorized'
    navigate(returnTo ?? defaultPath)
    toast.success(t('auth:welcomeBack'))
  }

  const handleMfaAuthenticated = (response: BrowserAuthResponse) => {
    setMfa(null)
    enterApp(completeSession(response))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsLoading(true)
    
    try {
      const result = await login(email, password)
      if (result.kind === 'authenticated') {
        enterApp(result.user)
      } else {
        setPassword('')
        setMfa({ challengeToken: result.challengeToken, enrollmentRequired: result.enrollmentRequired })
      }
    } catch (error) {
      toast.error(error instanceof Error && error.message === 'Email verification required'
        ? t('auth:errorVerifyEmail')
        : t('auth:errorSignIn'))
    } finally {
      setIsLoading(false)
    }
  }

  const handleRecovery = async () => {
    if (!email || !password) {
      toast.error(t('auth:recoveryCredentialsRequired'))
      return
    }
    setIsRecovering(true)
    try {
      await authApi.cancelDeletionWithPassword(email, password)
      toast.success(t('auth:recoveryResult'))
    } catch {
      toast.error(t('auth:recoveryError'))
    } finally {
      setIsRecovering(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background p-4">
      <div className="w-full max-w-md space-y-8">
        {/* Logo */}
        <div className="flex flex-col items-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary">
            <Flag className="h-6 w-6 text-primary-foreground" />
          </div>
          <h1 className="mt-4 text-2xl font-bold text-foreground">{t('common:appName')}</h1>
          <p className="mt-1 text-sm text-muted-foreground">{t('auth:appTagline')}</p>
        </div>

        {/* Login Card */}
        <Card className="border-border bg-card">
          <CardHeader className="space-y-1">
            <CardTitle className="text-xl">
              {t('auth:title')}
            </CardTitle>
            <CardDescription>
              {t('auth:subtitle')}
            </CardDescription>
            {unauthorizedReason && (
              <p className="text-sm text-destructive">{t('auth:unauthorized')}</p>
            )}
          </CardHeader>
          <CardContent>
            {mfa ? (
              <MfaChallenge
                challengeToken={mfa.challengeToken}
                enrollmentRequired={mfa.enrollmentRequired}
                onAuthenticated={handleMfaAuthenticated}
                onCancel={() => setMfa(null)}
              />
            ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">{t('auth:emailLabel')}</Label>
                <Input
                  id="email"
                  type="email"
                  placeholder={t('auth:emailPlaceholder')}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  className="bg-muted/50"
                />
              </div>
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label htmlFor="password">{t('auth:passwordLabel')}</Label>
                  <Link to="/forgot-password" className="text-sm text-primary underline-offset-4 hover:underline">
                    {t('auth:forgotPassword.link')}
                  </Link>
                </div>
                <Input
                  id="password"
                  type="password"
                  placeholder={t('auth:passwordPlaceholder')}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  className="bg-muted/50"
                />
              </div>
              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    {t('auth:signingIn')}
                  </>
                ) : (
                  t('auth:signIn')
                )}
              </Button>
            </form>
            )}

            <Button type="button" variant="link" className="mt-2 w-full" disabled={isLoading || isRecovering}
              onClick={handleRecovery}>
              {isRecovering ? t('auth:recoveringAccount') : t('auth:cancelDeletion')}
            </Button>

            <p className="mt-4 text-center text-sm text-muted-foreground">
              {t('auth:newUser')}{' '}
              <Link
                to="/register"
                state={returnTo ? { from: returnTo } : undefined}
                className="text-primary underline-offset-4 hover:underline"
              >
                {t('auth:createAccount')}
              </Link>
            </p>

            {import.meta.env.DEV && (
              <p className="mt-6 text-center text-xs text-muted-foreground">
                {t('auth:devCredentialsNote')}
              </p>
            )}
          </CardContent>
        </Card>

        {/* Footer */}
        <p className="text-center text-xs text-muted-foreground">
          {t('auth:secureLoginNote')}
        </p>
      </div>
      <LegalFooter className="mt-8 w-full max-w-md border-0 bg-transparent" />
    </div>
  )
}
