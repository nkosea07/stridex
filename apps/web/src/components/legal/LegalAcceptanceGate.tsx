import { useState, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { FileText } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/contexts/AuthContext'
import { ApiError, authApi } from '@/lib/api'
import { LEGAL_PATHS, LEGAL_VERSION_MISMATCH_CODES, isLegalPath, needsLegalAcceptance } from '@/lib/legal'

export function LegalAcceptanceGate({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const { user, isAuthenticated, updateUser, logout } = useAuth()
  const [agreed, setAgreed] = useState(false)
  const [saving, setSaving] = useState(false)

  if (!isAuthenticated || !user || !needsLegalAcceptance(user) || isLegalPath(pathname)) {
    return <>{children}</>
  }

  const previouslyAccepted = Boolean(user.termsVersion || user.privacyPolicyVersion)

  const accept = async () => {
    if (!agreed || saving) return
    setSaving(true)
    try {
      const accepted = await authApi.acceptLegalDocuments()
      updateUser({
        ...user,
        privacyPolicyVersion: accepted.privacyPolicyVersion,
        termsVersion: accepted.termsVersion,
        legalAcceptanceRequired: false,
      })
    } catch (error) {
      const outdated = error instanceof ApiError && error.code !== undefined && LEGAL_VERSION_MISMATCH_CODES.has(error.code)
      toast.error(t(outdated ? 'legal:acceptance.outdated' : 'legal:acceptance.failed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-4 py-10">
      <section role="dialog" aria-modal="true" aria-labelledby="legal-acceptance-title"
        className="w-full max-w-lg rounded-xl border border-border bg-card p-6 shadow-sm">
        <div className="mb-4 flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <FileText className="h-5 w-5" aria-hidden="true" />
          </span>
          <h1 id="legal-acceptance-title" className="text-lg font-semibold text-foreground">{t('legal:acceptance.title')}</h1>
        </div>
        <p className="text-sm leading-6 text-muted-foreground">
          {previouslyAccepted ? t('legal:acceptance.updatedBody') : t('legal:acceptance.body')}
        </p>
        <ul className="mt-4 space-y-1 text-sm">
          <li>
            <Link to={LEGAL_PATHS.terms} target="_blank" rel="noreferrer" className="font-medium text-primary underline-offset-4 hover:underline">
              {t('legal:common.terms')}
            </Link>
          </li>
          <li>
            <Link to={LEGAL_PATHS.privacy} target="_blank" rel="noreferrer" className="font-medium text-primary underline-offset-4 hover:underline">
              {t('legal:common.privacyPolicy')}
            </Link>
          </li>
        </ul>
        <label className="mt-5 flex items-start gap-2 text-sm text-foreground">
          <input type="checkbox" className="mt-1 h-4 w-4 rounded border-border" checked={agreed}
            onChange={(event) => setAgreed(event.target.checked)} />
          <span>{t('legal:acceptance.checkbox')}</span>
        </label>
        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={logout} disabled={saving}>{t('legal:acceptance.signOut')}</Button>
          <Button onClick={() => void accept()} disabled={!agreed || saving}>
            {saving ? t('legal:acceptance.accepting') : t('legal:acceptance.accept')}
          </Button>
        </div>
      </section>
    </main>
  )
}
