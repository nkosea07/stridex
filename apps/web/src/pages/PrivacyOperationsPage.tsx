import { useCallback, useEffect, useState } from 'react'
import { ShieldAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { privacyAdminApi } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import type { AdminPrivacyJob } from '@/types'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

export function PrivacyOperationsPage() {
  const { t } = useTranslation()
  const [jobs, setJobs] = useState<AdminPrivacyJob[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [selected, setSelected] = useState<AdminPrivacyJob | null>(null)
  const [scope, setScope] = useState('')
  const [reason, setReason] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      setJobs(await privacyAdminApi.listBlockedJobs())
    } catch {
      setLoadError(true)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const open = (job: AdminPrivacyJob) => {
    setSelected(job)
    setScope('')
    setReason('')
  }

  const submit = async () => {
    if (!selected || !scope.trim() || !reason.trim()) return
    setSaving(true)
    try {
      await privacyAdminApi.verifyScope(selected.id, scope.trim(), reason.trim())
      toast.success(t('common:privacyOps.saved'))
      setSelected(null)
      await load()
    } catch {
      toast.error(t('common:privacyOps.saveFailed'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{t('common:privacyOps.title')}</h1>
        <p className="text-muted-foreground">{t('common:privacyOps.description')}</p>
      </div>

      {loadError && (
        <Card>
          <CardContent className="flex items-center justify-between gap-4 p-4" role="alert">
            <span>{t('common:privacyOps.loadFailed')}</span>
            <Button variant="outline" onClick={() => void load()}>{t('common:privacyOps.retry')}</Button>
          </CardContent>
        </Card>
      )}

      {!loadError && loading && <p role="status">{t('common:privacyOps.loading')}</p>}
      {!loadError && !loading && jobs.length === 0 && (
        <p role="status">{t('common:privacyOps.empty')}</p>
      )}

      <div className="grid gap-4">
        {jobs.map((job) => (
          <Card key={job.id}>
            <CardHeader className="flex-row items-start justify-between gap-4 space-y-0">
              <div>
                <CardTitle className="text-base">{job.storageKey}</CardTitle>
                <p className="mt-1 text-xs text-muted-foreground">{job.id} · {job.userId}</p>
              </div>
              <Badge variant="destructive">{job.status}</Badge>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <p><ShieldAlert className="mr-1 inline h-4 w-4" />{job.storageScope}</p>
              <p className="text-muted-foreground">
                {t('common:privacyOps.attempts', { count: job.attempts })}
                {job.nextAttemptAt ? ` · ${formatDateTime(job.nextAttemptAt)}` : ''}
              </p>
              <Button size="sm" variant="outline" onClick={() => open(job)}>
                {t('common:privacyOps.verifyAction')}
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      <Dialog open={Boolean(selected)} onOpenChange={(openState) => { if (!openState && !saving) setSelected(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common:privacyOps.verifyTitle')}</DialogTitle>
            <DialogDescription>{t('common:privacyOps.verifyDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <Label htmlFor="privacy-scope">{t('common:privacyOps.verifiedScope')}</Label>
              <Input id="privacy-scope" value={scope} onChange={(event) => setScope(event.target.value)}
                placeholder="local|/verified/legacy/path" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="privacy-reason">{t('common:privacyOps.reason')}</Label>
              <Input id="privacy-reason" value={reason} onChange={(event) => setReason(event.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={saving} onClick={() => setSelected(null)}>
              {t('common:actions.cancel')}
            </Button>
            <Button disabled={saving || !scope.trim() || !reason.trim()} onClick={() => void submit()}>
              {t('common:privacyOps.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
