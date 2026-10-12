import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Flag, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { authApi } from '@/lib/api'
import { toast } from 'sonner'

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
  const [mfaCode, setMfaCode] = useState('')
  const [challenge, setChallenge] = useState<{ token: string; enrollmentRequired: boolean } | null>(null)
  const [enrollment, setEnrollment] = useState<{ secret: string; provisioningUri: string } | null>(null)
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const { login, completeSession, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const locationState = location.state as { from?: string; reason?: string } | null
  const unauthorizedReason = locationState?.reason === 'unauthorized'
  const returnTo = getSafePublicReturnPath(locationState?.from)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsLoading(true)
    
    try {
      const result = await login(email, password)
      if (result.kind === 'authenticated') {
        navigate(returnTo ?? (isAdminRole(result.user.role) ? '/admin' : '/app'))
        toast.success(t('auth:welcomeBack'))
      } else {
        setChallenge({ token: result.challengeToken, enrollmentRequired: result.enrollmentRequired })
        if (result.enrollmentRequired) {
          setEnrollment(await authApi.beginMfaEnrollment(result.challengeToken))
        }
      }
    } catch (error) {
      toast.error(error instanceof Error && error.message === 'Email verification required'
        ? t('auth:errorVerifyEmail')
        : t('auth:errorSignIn'))
    } finally {
      setIsLoading(false)
    }
  }

  const handleMfaSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!challenge) return
    setIsLoading(true)
    try {
      const response = challenge.enrollmentRequired
        ? await authApi.confirmMfaEnrollment(challenge.token, mfaCode.trim())
        : await authApi.verifyMfaLogin(challenge.token, mfaCode.trim())
      const authenticatedUser = completeSession(response)
      if (response.recoveryCodes?.length) {
        setRecoveryCodes(response.recoveryCodes)
      } else {
        navigate(returnTo ?? (isAdminRole(authenticatedUser.role) ? '/admin' : '/app'))
        toast.success(t('auth:welcomeBack'))
      }
    } catch {
      toast.error(t('auth:mfa.rejected'))
    } finally {
      setIsLoading(false)
    }
  }

  const continueAfterRecoveryCodes = () => {
    setRecoveryCodes([])
    navigate(returnTo ?? (isAdminRole(user?.role) ? '/admin' : '/app'))
    toast.success(t('auth:recovery.enabled'))
  }

  const cancelChallenge = () => {
    setChallenge(null)
    setEnrollment(null)
    setMfaCode('')
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
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
              {recoveryCodes.length
                ? t('auth:recovery.title')
                : challenge ? t('auth:mfa.title') : t('auth:title')}
            </CardTitle>
            <CardDescription>
              {recoveryCodes.length
                ? t('auth:recovery.description')
                : challenge?.enrollmentRequired
                  ? t('auth:mfa.enrollDescription')
                  : challenge
                    ? t('auth:mfa.codeDescription')
                    : t('auth:subtitle')}
            </CardDescription>
            {unauthorizedReason && (
              <p className="text-sm text-destructive">{t('auth:unauthorized')}</p>
            )}
          </CardHeader>
          <CardContent>
            {recoveryCodes.length ? (
              <div className="space-y-4">
                <ul className="grid grid-cols-2 gap-2 rounded-md border bg-muted/40 p-4 font-mono text-sm" aria-label={t('auth:recovery.listLabel')}>
                  {recoveryCodes.map((code) => <li key={code}>{code}</li>)}
                </ul>
                <Button type="button" className="w-full" onClick={continueAfterRecoveryCodes}>{t('auth:recovery.continue')}</Button>
              </div>
            ) : challenge ? (
              <form onSubmit={handleMfaSubmit} className="space-y-4">
                {enrollment && (
                  <div className="space-y-2 rounded-md border bg-muted/40 p-4 text-sm">
                    <p>{t('auth:mfa.setupIntro')}</p>
                    <p className="break-all font-mono font-semibold" aria-label={t('auth:mfa.setupKeyLabel')}>{enrollment.secret}</p>
                    <details>
                      <summary className="cursor-pointer text-primary">{t('auth:mfa.showProvisioningUri')}</summary>
                      <p className="mt-2 break-all font-mono text-xs">{enrollment.provisioningUri}</p>
                    </details>
                  </div>
                )}
                <div className="space-y-2">
                  <Label htmlFor="mfa-code">{t('auth:mfa.codeLabel')}</Label>
                  <Input
                    id="mfa-code"
                    autoComplete="one-time-code"
                    value={mfaCode}
                    onChange={(event) => setMfaCode(event.target.value)}
                    required
                    maxLength={64}
                  />
                </div>
                <Button type="submit" className="w-full" disabled={isLoading}>
                  {isLoading
                    ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />{t('auth:mfa.verifying')}</>
                    : challenge.enrollmentRequired ? t('auth:mfa.enableAndContinue') : t('auth:mfa.verifyAndContinue')}
                </Button>
                <Button type="button" variant="ghost" className="w-full" onClick={cancelChallenge} disabled={isLoading}>
                  {t('auth:mfa.backToSignIn')}
                </Button>
              </form>
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
                <Label htmlFor="password">{t('auth:passwordLabel')}</Label>
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

            {!challenge && !recoveryCodes.length && <p className="mt-4 text-center text-sm text-muted-foreground">
              {t('auth:newUser')}{' '}
              <Link
                to="/register"
                state={returnTo ? { from: returnTo } : undefined}
                className="text-primary underline-offset-4 hover:underline"
              >
                {t('auth:createAccount')}
              </Link>
            </p>}

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
    </div>
  )
}
