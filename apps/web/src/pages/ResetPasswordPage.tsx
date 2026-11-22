import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AuthPageShell } from '@/components/auth/AuthPageShell'
import { authApi } from '@/lib/api'

export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 128

export function validateNewPassword(password: string, confirmation: string): 'tooShort' | 'tooLong' | 'mismatch' | null {
  if (password.length < PASSWORD_MIN_LENGTH) return 'tooShort'
  if (password.length > PASSWORD_MAX_LENGTH) return 'tooLong'
  if (password !== confirmation) return 'mismatch'
  return null
}

export function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [token] = useState(() => searchParams.get('token')?.trim() ?? '')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [linkInvalid, setLinkInvalid] = useState(!token)
  const [done, setDone] = useState(false)

  useEffect(() => {
    if (searchParams.has('token')) navigate('/reset-password', { replace: true })
  }, [navigate, searchParams])

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    const problem = validateNewPassword(password, confirmation)
    if (problem) {
      setError(t(`auth:resetPassword.errors.${problem}`, { min: PASSWORD_MIN_LENGTH, max: PASSWORD_MAX_LENGTH }))
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      await authApi.resetPassword(token, password)
      setPassword('')
      setConfirmation('')
      setDone(true)
    } catch {
      setLinkInvalid(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthPageShell
      title={t('auth:resetPassword.title')}
      description={done || linkInvalid ? undefined : t('auth:resetPassword.description', { min: PASSWORD_MIN_LENGTH })}
    >
      <div aria-live="polite" className="space-y-4">
        {done ? (
          <>
            <p className="flex items-start gap-2 text-sm text-foreground">
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
              {t('auth:resetPassword.done')}
            </p>
            <Button asChild className="w-full">
              <Link to="/login">{t('auth:linkPages.goToSignIn')}</Link>
            </Button>
          </>
        ) : linkInvalid ? (
          <>
            <p className="text-sm text-destructive">{t('auth:resetPassword.invalid')}</p>
            <Button asChild className="w-full">
              <Link to="/forgot-password">{t('auth:resetPassword.requestNewLink')}</Link>
            </Button>
          </>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="new-password">{t('auth:resetPassword.newPassword')}</Label>
              <Input
                id="new-password"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                minLength={PASSWORD_MIN_LENGTH}
                maxLength={PASSWORD_MAX_LENGTH}
                required
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? 'reset-password-error' : undefined}
                className="bg-muted/50"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirm-password">{t('auth:resetPassword.confirmPassword')}</Label>
              <Input
                id="confirm-password"
                type="password"
                autoComplete="new-password"
                value={confirmation}
                onChange={(event) => setConfirmation(event.target.value)}
                maxLength={PASSWORD_MAX_LENGTH}
                required
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? 'reset-password-error' : undefined}
                className="bg-muted/50"
              />
            </div>
            {error && <p id="reset-password-error" role="alert" className="text-sm text-destructive">{error}</p>}
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
                  {t('auth:resetPassword.saving')}
                </>
              ) : (
                t('auth:resetPassword.submit')
              )}
            </Button>
          </form>
        )}
        <p className="text-center text-sm">
          <Link to="/login" className="text-primary underline-offset-4 hover:underline">
            {t('auth:linkPages.backToSignIn')}
          </Link>
        </p>
      </div>
    </AuthPageShell>
  )
}
