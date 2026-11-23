import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { PRODUCT } from '@/config/product'
import { cn } from '@/lib/utils'
import { LEGAL_PATHS } from '@/lib/legal'

export function LegalFooter({ className }: { className?: string }) {
  const { t } = useTranslation()
  return (
    <footer className={cn('border-t border-border bg-background px-4 py-4 text-xs text-muted-foreground', className)}>
      <nav aria-label={t('legal:common.footerLegal')}
        className="mx-auto flex max-w-5xl flex-wrap items-center justify-center gap-x-4 gap-y-1 sm:justify-between">
        <span>{PRODUCT.name}</span>
        <span className="flex flex-wrap justify-center gap-x-4 gap-y-1">
          <Link to={LEGAL_PATHS.terms} className="hover:text-foreground hover:underline">{t('legal:common.terms')}</Link>
          <Link to={LEGAL_PATHS.privacy} className="hover:text-foreground hover:underline">{t('legal:common.privacyPolicy')}</Link>
          <Link to={LEGAL_PATHS.accountDeletion} className="hover:text-foreground hover:underline">{t('legal:common.accountDeletion')}</Link>
        </span>
      </nav>
    </footer>
  )
}
