import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { BadgeCheck, ClipboardList, Loader2, MailCheck, ShieldAlert } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import { organizerApplicationsApi } from '@/lib/api'
import type { OrganizerApplicationReview, OrganizerApplicationStatus } from '@/types'
import { toast } from 'sonner'
import { formatDateTime } from '@/lib/format'

const statusFilters: Array<'PENDING' | 'APPROVED' | 'REJECTED'> = ['PENDING', 'APPROVED', 'REJECTED']

export function OrganizerApplicationsPage() {
  const { t } = useTranslation()
  const [status, setStatus] = useState<OrganizerApplicationStatus>('PENDING')
  const [items, setItems] = useState<OrganizerApplicationReview[]>([])
  const [reviewNotes, setReviewNotes] = useState<Record<string, string>>({})
  const [isLoading, setIsLoading] = useState(true)
  const [pendingId, setPendingId] = useState<string | null>(null)
  const [loadError, setLoadError] = useState(false)

  const loadApplications = useCallback(async () => {
    setIsLoading(true)
    setLoadError(false)
    try {
      const page = await organizerApplicationsApi.list(status, 0, 50)
      setItems(page.items)
    } catch {
      setLoadError(true)
      toast.error(t('admin:organizers.loadFailed'))
    } finally {
      setIsLoading(false)
    }
  }, [status])

  useEffect(() => {
    void loadApplications()
  }, [loadApplications])

  const decide = async (application: OrganizerApplicationReview, decision: 'APPROVE' | 'REJECT') => {
    setPendingId(application.id)
    try {
      await organizerApplicationsApi.decide(application.id, decision, reviewNotes[application.id]?.trim())
      toast.success(decision === 'APPROVE' ? t('admin:organizers.approved') : t('admin:organizers.rejected'))
      await loadApplications()
    } catch {
      toast.error(t('admin:organizers.decisionFailed'))
    } finally {
      setPendingId(null)
    }
  }

  return (
    <div className="space-y-6 p-4 md:p-8">
      <div>
        <div className="flex items-center gap-2 text-primary"><ClipboardList className="h-5 w-5" /><span className="text-sm font-medium">{t('admin:organizers.eyebrow')}</span></div>
        <h1 className="mt-2 text-3xl font-semibold">{t('nav:organizerApplications')}</h1>
        <p className="mt-2 text-muted-foreground">{t('admin:organizers.description')}</p>
      </div>

      <div className="flex flex-wrap gap-2" role="tablist" aria-label={t('admin:organizers.statusFilter')}>
        {statusFilters.map((filter) => (
          <Button key={filter} variant={status === filter ? 'default' : 'outline'} onClick={() => setStatus(filter)} role="tab" aria-selected={status === filter}>
            {t(`admin:organizers.status.${filter}`)}
          </Button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 py-12 text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />{t('admin:organizers.loading')}</div>
      ) : loadError ? (
        <Card><CardContent className="flex items-center justify-between gap-4 p-6"><span>{t('admin:organizers.loadError')}</span><Button variant="outline" onClick={() => void loadApplications()}>{t('admin:organizers.retry')}</Button></CardContent></Card>
      ) : items.length === 0 ? (
        <Card><CardContent className="p-10 text-center text-muted-foreground">{t(`admin:organizers.empty.${status}`)}</CardContent></Card>
      ) : (
        <div className="grid gap-4">
          {items.map((application) => (
            <Card key={application.id}>
              <CardHeader className="flex flex-row items-start justify-between gap-4 space-y-0">
                <div>
                  <CardTitle>{application.organizationName}</CardTitle>
                  <CardDescription className="mt-1">{application.applicantFirstName} {application.applicantLastName} · {application.applicantEmail}</CardDescription>
                </div>
                <Badge variant={application.status === 'PENDING' ? 'secondary' : 'outline'}>{application.status.toLowerCase()}</Badge>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                  <span className="inline-flex items-center gap-1">{application.emailVerified ? <MailCheck className="h-4 w-4 text-emerald-600" /> : <ShieldAlert className="h-4 w-4 text-amber-600" />}{application.emailVerified ? t('admin:organizers.emailVerified') : t('admin:organizers.emailUnverified')}</span>
                  <span>{t('admin:organizers.submitted', { date: formatDateTime(application.createdAt) })}</span>
                  {application.reviewedByEmail && <span>{t('admin:organizers.reviewedBy', { email: application.reviewedByEmail })}</span>}
                </div>
                {application.reviewNotes && <p className="rounded-md bg-muted p-3 text-sm">{t('admin:organizers.reviewNote', { note: application.reviewNotes })}</p>}
                {application.status === 'PENDING' && (
                  <>
                    <Textarea
                      value={reviewNotes[application.id] ?? ''}
                      onChange={(event) => setReviewNotes((current) => ({ ...current, [application.id]: event.target.value }))}
                      maxLength={2000}
                      placeholder={t('admin:organizers.notePlaceholder')}
                      aria-label={t('admin:organizers.noteFor', { organization: application.organizationName })}
                    />
                    <div className="flex flex-wrap justify-end gap-2">
                      <Button variant="outline" disabled={pendingId === application.id} onClick={() => void decide(application, 'REJECT')}>
                        {t('admin:organizers.reject')}
                      </Button>
                      <Button disabled={!application.emailVerified || pendingId === application.id} onClick={() => void decide(application, 'APPROVE')}>
                        {pendingId === application.id ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <BadgeCheck className="mr-2 h-4 w-4" />}
                        {t('admin:organizers.approve')}
                      </Button>
                    </div>
                    {!application.emailVerified && <p className="text-right text-xs text-muted-foreground">{t('admin:organizers.approvalDisabled')}</p>}
                  </>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
