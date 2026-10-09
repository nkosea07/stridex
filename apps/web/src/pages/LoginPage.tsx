import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
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
        toast.success('Welcome back!')
      } else {
        setChallenge({ token: result.challengeToken, enrollmentRequired: result.enrollmentRequired })
        if (result.enrollmentRequired) {
          setEnrollment(await authApi.beginMfaEnrollment(result.challengeToken))
        }
      }
    } catch (error) {
      toast.error(error instanceof Error && error.message === 'Email verification required'
        ? 'Verify your email before signing in.'
        : 'Sign-in could not be completed. Check your credentials and try again.')
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
        toast.success('Welcome back!')
      }
    } catch {
      toast.error('The authentication code was rejected or the challenge expired.')
    } finally {
      setIsLoading(false)
    }
  }

  const continueAfterRecoveryCodes = () => {
    setRecoveryCodes([])
    navigate(returnTo ?? (isAdminRole(user?.role) ? '/admin' : '/app'))
    toast.success('MFA enabled. Store your recovery codes somewhere safe.')
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
          <h1 className="mt-4 text-2xl font-bold text-foreground">Race Tracker</h1>
          <p className="mt-1 text-sm text-muted-foreground">Professional Race Management Platform</p>
        </div>

        {/* Login Card */}
        <Card className="border-border bg-card">
          <CardHeader className="space-y-1">
            <CardTitle className="text-xl">
              {recoveryCodes.length ? 'Save your recovery codes' : challenge ? 'Verify your identity' : 'Sign in'}
            </CardTitle>
            <CardDescription>
              {recoveryCodes.length
                ? 'Each code can be used once if you lose access to your authenticator. These codes are shown only once.'
                : challenge?.enrollmentRequired
                  ? 'Privileged accounts must enroll an authenticator before accessing protected operations.'
                  : challenge
                    ? 'Enter a six-digit authenticator code or an unused recovery code.'
                    : 'Enter your credentials to access Race Tracker.'}
            </CardDescription>
            {unauthorizedReason && (
              <p className="text-sm text-destructive">Unauthorized account. Please sign in with an admin user.</p>
            )}
          </CardHeader>
          <CardContent>
            {recoveryCodes.length ? (
              <div className="space-y-4">
                <ul className="grid grid-cols-2 gap-2 rounded-md border bg-muted/40 p-4 font-mono text-sm" aria-label="MFA recovery codes">
                  {recoveryCodes.map((code) => <li key={code}>{code}</li>)}
                </ul>
                <Button type="button" className="w-full" onClick={continueAfterRecoveryCodes}>Continue</Button>
              </div>
            ) : challenge ? (
              <form onSubmit={handleMfaSubmit} className="space-y-4">
                {enrollment && (
                  <div className="space-y-2 rounded-md border bg-muted/40 p-4 text-sm">
                    <p>In your authenticator app, add Race Tracker using this setup key:</p>
                    <p className="break-all font-mono font-semibold" aria-label="Authenticator setup key">{enrollment.secret}</p>
                    <details>
                      <summary className="cursor-pointer text-primary">Show provisioning URI</summary>
                      <p className="mt-2 break-all font-mono text-xs">{enrollment.provisioningUri}</p>
                    </details>
                  </div>
                )}
                <div className="space-y-2">
                  <Label htmlFor="mfa-code">Authenticator or recovery code</Label>
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
                    ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" />Verifying…</>
                    : challenge.enrollmentRequired ? 'Enable MFA and continue' : 'Verify and continue'}
                </Button>
                <Button type="button" variant="ghost" className="w-full" onClick={cancelChallenge} disabled={isLoading}>
                  Back to sign in
                </Button>
              </form>
            ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  placeholder="you@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  className="bg-muted/50"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  type="password"
                  placeholder="Enter your password"
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
                    Signing in...
                  </>
                ) : (
                  'Sign in'
                )}
              </Button>
            </form>
            )}

            {!challenge && !recoveryCodes.length && <p className="mt-4 text-center text-sm text-muted-foreground">
              New user?{' '}
              <Link
                to="/register"
                state={returnTo ? { from: returnTo } : undefined}
                className="text-primary underline-offset-4 hover:underline"
              >
                Create account
              </Link>
            </p>}

            {import.meta.env.DEV && (
              <p className="mt-6 text-center text-xs text-muted-foreground">
                Development credentials are supplied through the local environment only.
              </p>
            )}
          </CardContent>
        </Card>

        {/* Footer */}
        <p className="text-center text-xs text-muted-foreground">
          Secure login powered by JWT authentication
        </p>
      </div>
    </div>
  )
}
