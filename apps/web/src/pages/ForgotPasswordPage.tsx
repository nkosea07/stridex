import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AuthPageShell } from '@/components/auth/AuthPageShell'
import { authApi } from '@/lib/api'

export function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)
  const [failed, setFailed] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setFailed(false)
    try {
      await authApi.forgotPassword(email.trim())
      setSent(true)
    } catch {
      setFailed(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthPageShell
      title={t('auth:forgotPassword.title')}
      description={sent ? undefined : t('auth:forgotPassword.description')}
    >
      <div aria-live="polite" className="space-y-4">
        {sent ? (
          <p role="status" className="text-sm text-foreground">{t('auth:forgotPassword.sent')}</p>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="forgot-email">{t('auth:emailLabel')}</Label>
              <Input
                id="forgot-email"
                type="email"
                autoComplete="email"
                placeholder={t('auth:emailPlaceholder')}
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
                className="bg-muted/50"
              />
            </div>
            {failed && <p role="alert" className="text-sm text-destructive">{t('auth:forgotPassword.failed')}</p>}
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
                  {t('auth:forgotPassword.sending')}
                </>
              ) : (
                t('auth:forgotPassword.submit')
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
