import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Flag } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader } from '@/components/ui/card'
import { LegalFooter } from '@/components/legal/LegalFooter'

interface AuthPageShellProps {
  title: string
  description?: string
  children: ReactNode
}

export function AuthPageShell({ title, description, children }: AuthPageShellProps) {
  const { t } = useTranslation()

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background p-4">
      <main className="w-full max-w-md space-y-8">
        <div className="flex flex-col items-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary">
            <Flag className="h-6 w-6 text-primary-foreground" aria-hidden="true" />
          </div>
          <p className="mt-4 text-2xl font-bold text-foreground">{t('common:appName')}</p>
          <p className="mt-1 text-sm text-muted-foreground">{t('auth:appTagline')}</p>
        </div>
        <Card className="border-border bg-card">
          <CardHeader className="space-y-1">
            <h1 className="text-xl font-semibold leading-none tracking-tight">{title}</h1>
            {description && <CardDescription>{description}</CardDescription>}
          </CardHeader>
          <CardContent>{children}</CardContent>
        </Card>
      </main>
      <LegalFooter className="mt-8 w-full max-w-md border-0 bg-transparent" />
    </div>
  )
}
