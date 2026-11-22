import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AuthPageShell } from '@/components/auth/AuthPageShell'
import { authApi } from '@/lib/api'

type Status = 'verifying' | 'verified' | 'invalid'

export function VerifyEmailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [token] = useState(() => searchParams.get('token')?.trim() ?? '')
  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'invalid')
  const [email, setEmail] = useState('')
  const [resending, setResending] = useState(false)
  const [resendMessage, setResendMessage] = useState<string | null>(null)
  const attempted = useRef(false)

  useEffect(() => {
    if (searchParams.has('token')) navigate('/verify-email', { replace: true })
  }, [navigate, searchParams])

  useEffect(() => {
    if (!token || attempted.current) return
    attempted.current = true
    authApi.verifyEmail(token)
      .then(() => setStatus('verified'))
      .catch(() => setStatus('invalid'))
  }, [token])

  const handleResend = async (event: React.FormEvent) => {
    event.preventDefault()
    setResending(true)
    try {
      await authApi.resendVerification(email.trim())
      setResendMessage(t('auth:verifyEmail.resendSent'))
    } catch {
      setResendMessage(t('auth:verifyEmail.resendFailed'))
    } finally {
      setResending(false)
    }
  }

  return (
    <AuthPageShell title={t('auth:verifyEmail.title')}>
      <div aria-live="polite" className="space-y-4">
        {status === 'verifying' && (
          <p role="status" className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            {t('auth:verifyEmail.verifying')}
          </p>
        )}

        {status === 'verified' && (
          <>
            <p className="flex items-start gap-2 text-sm text-foreground">
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
              {t('auth:verifyEmail.verified')}
            </p>
            <Button asChild className="w-full">
              <Link to="/login">{t('auth:linkPages.goToSignIn')}</Link>
            </Button>
          </>
        )}

        {status === 'invalid' && (
          <>
            <p className="flex items-start gap-2 text-sm text-destructive">
              <XCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              {t('auth:verifyEmail.invalid')}
            </p>
            <form onSubmit={handleResend} className="space-y-3">
              <div className="space-y-2">
                <Label htmlFor="verify-email-address">{t('auth:emailLabel')}</Label>
                <Input
                  id="verify-email-address"
                  type="email"
                  autoComplete="email"
                  placeholder={t('auth:emailPlaceholder')}
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  required
                  className="bg-muted/50"
                />
              </div>
              <Button type="submit" variant="outline" className="w-full" disabled={resending}>
                {resending ? t('auth:verifyEmail.resending') : t('auth:verifyEmail.resend')}
              </Button>
              {resendMessage && <p role="status" className="text-sm text-muted-foreground">{resendMessage}</p>}
            </form>
            <p className="text-center text-sm">
              <Link to="/login" className="text-primary underline-offset-4 hover:underline">
                {t('auth:linkPages.backToSignIn')}
              </Link>
            </p>
          </>
        )}
      </div>
    </AuthPageShell>
  )
}
