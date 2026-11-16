import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Loader2, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { authApi, type BrowserAuthResponse, type MfaEnrollment } from '@/lib/api'

interface MfaChallengeProps {
  challengeToken: string
  enrollmentRequired: boolean
  onAuthenticated: (response: BrowserAuthResponse) => void
  onCancel: () => void
}

export function MfaChallenge({ challengeToken, enrollmentRequired, onAuthenticated, onCancel }: MfaChallengeProps) {
  const { t } = useTranslation()
  const [enrollment, setEnrollment] = useState<MfaEnrollment | null>(null)
  const [enrollmentFailed, setEnrollmentFailed] = useState(false)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState<BrowserAuthResponse | null>(null)

  useEffect(() => {
    if (!enrollmentRequired) return
    let active = true
    authApi.mfaEnroll(challengeToken)
      .then((value) => { if (active) setEnrollment(value) })
      .catch(() => { if (active) setEnrollmentFailed(true) })
    return () => { active = false }
  }, [challengeToken, enrollmentRequired])

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    const value = code.replace(/\s+/g, '')
    if (!value) return
    setBusy(true)
    setError(null)
    try {
      const response = enrollmentRequired
        ? await authApi.mfaConfirmEnrollment(challengeToken, value)
        : await authApi.mfaVerifyLogin(challengeToken, value)
      if (response.recoveryCodes && response.recoveryCodes.length > 0) {
        setPending(response)
      } else {
        onAuthenticated(response)
      }
    } catch {
      setError(t('auth:mfa.invalidCode'))
    } finally {
      setBusy(false)
    }
  }

  if (pending) {
    return (
      <div className="space-y-4">
        <h2 className="text-lg font-semibold">{t('auth:mfa.recoveryTitle')}</h2>
        <p className="text-sm text-muted-foreground">{t('auth:mfa.recoveryDescription')}</p>
        <ul className="grid grid-cols-2 gap-2 rounded-md border border-border bg-muted/40 p-3 font-mono text-sm">
          {pending.recoveryCodes?.map((recoveryCode) => <li key={recoveryCode}>{recoveryCode}</li>)}
        </ul>
        <Button className="w-full" onClick={() => onAuthenticated(pending)}>{t('auth:mfa.recoverySaved')}</Button>
      </div>
    )
  }

  return (
    <form className="space-y-4" onSubmit={(event) => void submit(event)}>
      <div className="flex items-center gap-2">
        <ShieldCheck className="h-5 w-5 text-primary" aria-hidden="true" />
        <h2 className="text-lg font-semibold">
          {enrollmentRequired ? t('auth:mfa.enrollTitle') : t('auth:mfa.title')}
        </h2>
      </div>
      <p className="text-sm text-muted-foreground">
        {enrollmentRequired ? t('auth:mfa.enrollDescription') : t('auth:mfa.verifyDescription')}
      </p>
      {enrollmentRequired && (
        <div className="flex flex-col items-center gap-2" aria-live="polite">
          {enrollment ? (
            <>
              {enrollment.qrCodeDataUri && (
                <img src={enrollment.qrCodeDataUri} alt={t('auth:mfa.qrAlt')} className="h-48 w-48 rounded-md bg-white p-2" />
              )}
              <p className="text-xs text-muted-foreground">{t('auth:mfa.manualKey')}</p>
              <code className="break-all rounded bg-muted px-2 py-1 text-xs">{enrollment.secret}</code>
            </>
          ) : enrollmentFailed ? (
            <p className="text-sm text-destructive">{t('auth:mfa.enrollFailed')}</p>
          ) : (
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />{t('auth:mfa.loadingEnrollment')}
            </p>
          )}
        </div>
      )}
      <div className="space-y-2">
        <Label htmlFor="mfa-code">{t('auth:mfa.code')}</Label>
        <Input id="mfa-code" inputMode="numeric" autoComplete="one-time-code" autoFocus value={code}
          onChange={(event) => setCode(event.target.value)} aria-invalid={error ? true : undefined}
          aria-describedby={error ? 'mfa-error' : undefined} />
        {error && <p id="mfa-error" role="alert" className="text-sm text-destructive">{error}</p>}
      </div>
      <Button type="submit" className="w-full" disabled={busy || (enrollmentRequired && !enrollment)}>
        {busy ? t('auth:mfa.verifying') : t('auth:mfa.verify')}
      </Button>
      <Button type="button" variant="ghost" className="w-full" onClick={onCancel}>{t('auth:mfa.back')}</Button>
    </form>
  )
}
