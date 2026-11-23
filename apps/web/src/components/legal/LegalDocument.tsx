import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, FileText, ShieldCheck, UserX } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { LegalFooter } from '@/components/legal/LegalFooter'
import { LEGAL_PATHS, LEGAL_VERSIONS, asList, legalInterpolation, type LegalDocumentKind, type LegalSection } from '@/lib/legal'

const DOCUMENT_ICONS = { privacy: ShieldCheck, terms: FileText, accountDeletion: UserX } satisfies Record<LegalDocumentKind, unknown>

export function LegalDocument({ kind }: { kind: LegalDocumentKind }) {
  const { t } = useTranslation()
  const values = legalInterpolation()
  const sections = asList<LegalSection>(t(`legal:${kind}.sections`, { returnObjects: true, ...values }))
  const Icon = DOCUMENT_ICONS[kind]
  const other: LegalDocumentKind = kind === 'privacy' ? 'terms' : 'privacy'
  const headingId = `legal-${kind}-title`

  return (
    <div className="flex min-h-screen flex-col bg-background">
      <div className="mx-auto w-full max-w-3xl flex-1 px-4 py-8 sm:px-6 sm:py-12">
        <Button asChild variant="ghost" className="mb-6 -ml-2 text-muted-foreground">
          <Link to="/">
            <ArrowLeft className="mr-2 h-4 w-4" aria-hidden="true" />
            {t('legal:common.back')}
          </Link>
        </Button>

        <header className="mb-8 flex items-start gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <Icon className="h-6 w-6" aria-hidden="true" />
          </span>
          <div>
            <h1 id={headingId} className="text-2xl font-bold text-foreground sm:text-3xl">{t(`legal:${kind}.title`)}</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              {t('legal:common.version', { version: LEGAL_VERSIONS[kind] })}
            </p>
            <p className="mt-3 text-sm leading-6 text-muted-foreground">{t(`legal:${kind}.summary`, values)}</p>
          </div>
        </header>

        <nav aria-label={t('legal:common.contents')} className="mb-10 rounded-lg border border-border bg-card p-4">
          <h2 className="mb-2 text-sm font-semibold text-foreground">{t('legal:common.contents')}</h2>
          <ol className="grid gap-1 text-sm sm:grid-cols-2">
            {sections.map((section) => (
              <li key={section.id}>
                <a href={`#${section.id}`} className="text-primary underline-offset-4 hover:underline">{section.title}</a>
              </li>
            ))}
          </ol>
        </nav>

        <article aria-labelledby={headingId} className="space-y-8">
          {sections.map((section) => (
            <section key={section.id} id={section.id} className="scroll-mt-6 space-y-3">
              <h2 className="text-lg font-semibold text-foreground">{section.title}</h2>
              {section.paragraphs?.map((paragraph) => (
                <p key={paragraph} className="text-sm leading-6 text-muted-foreground">{paragraph}</p>
              ))}
              {section.items && (
                <ul className="list-disc space-y-2 pl-5 text-sm leading-6 text-muted-foreground">
                  {section.items.map((item) => <li key={item}>{item}</li>)}
                </ul>
              )}
              {section.closing && <p className="text-sm leading-6 text-muted-foreground">{section.closing}</p>}
            </section>
          ))}
        </article>

        <div className="mt-10 flex flex-col gap-2 border-t border-border pt-6 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
          <p>{t('legal:common.contactLine', values)}</p>
          <Link to={LEGAL_PATHS[other]} className="font-medium text-primary underline-offset-4 hover:underline">
            {t(other === 'privacy' ? 'legal:common.relatedPrivacy' : 'legal:common.relatedTerms')}
          </Link>
        </div>
      </div>
      <LegalFooter />
    </div>
  )
}
