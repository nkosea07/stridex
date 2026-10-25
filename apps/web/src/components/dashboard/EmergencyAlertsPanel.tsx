import { useCallback, useEffect, useState } from 'react'
import { AlertTriangle, CheckCheck, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { emergencyApi } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import type { EmergencyAlert } from '@/types'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/contexts/AuthContext'

const POLL_INTERVAL_MS = 10000

export function EmergencyAlertsPanel({ raceId }: { raceId: string }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const canView = Boolean(user?.role && ['SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL'].includes(user.role))
  const [alerts, setAlerts] = useState<EmergencyAlert[]>([])
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!canView) return
    try {
      const rows = await emergencyApi.list(raceId)
      setAlerts(rows.filter((alert) => alert.status !== 'RESOLVED'))
    } catch {
      // Keep the previous list; the next poll retries.
    }
  }, [raceId, canView])

  useEffect(() => {
    void load()
    const timer = setInterval(() => void load(), POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [load])

  const updateStatus = async (alert: EmergencyAlert, status: 'ACKNOWLEDGED' | 'RESOLVED') => {
    setBusyId(alert.id)
    try {
      await emergencyApi.updateStatus(raceId, alert.id, status)
      await load()
      toast.success(status === 'RESOLVED'
        ? t('common:emergency.resolvedToast')
        : t('common:emergency.acknowledgedToast'))
    } catch {
      toast.error(t('common:emergency.updateFailed'))
    } finally {
      setBusyId(null)
    }
  }

  if (!canView || alerts.length === 0) return null

  return (
    <section aria-live="polite" aria-label={t('common:emergency.title')}
      className="absolute left-4 top-4 z-20 w-[min(24rem,calc(100%-2rem))] space-y-2">
      {alerts.map((alert) => {
        const escalated = alert.deliveryStatus === 'ESCALATION_REQUIRED'
        const legacy = alert.deliveryStatus === 'LEGACY_UNVERIFIED'
        return (
          <div key={alert.id} className="rounded-xl border border-destructive/50 bg-background/95 p-3 shadow-xl backdrop-blur">
            <div className="flex items-start justify-between gap-2">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-4 w-4 text-destructive" />
                <p className="text-sm font-semibold">{t('common:emergency.title')}</p>
              </div>
              <Badge variant={escalated ? 'destructive' : 'secondary'}>
                {escalated ? t('common:emergency.escalated') : legacy ? t('common:emergency.legacy') : alert.status}
              </Badge>
            </div>
            <p className="mt-2 text-sm">
              {alert.participantName || t('common:emergency.unknownAthlete')}
              {alert.message ? ` — ${alert.message}` : ''}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              {alert.latitude != null && alert.longitude != null
                ? t('common:emergency.locationShared')
                : t('common:emergency.noLocation')}
            </p>
            {alert.createdAt && (
              <p className="text-xs text-muted-foreground">
                {t('common:emergency.raisedAt', { time: formatDateTime(alert.createdAt) })}
              </p>
            )}
            <div className="mt-2 flex flex-wrap gap-2">
              {alert.status === 'ACTIVE' && (
                <Button size="sm" variant="outline" disabled={busyId === alert.id}
                  onClick={() => void updateStatus(alert, 'ACKNOWLEDGED')}>
                  <ShieldCheck className="mr-1 h-3 w-3" /> {t('common:emergency.acknowledge')}
                </Button>
              )}
              <Button size="sm" disabled={busyId === alert.id}
                onClick={() => void updateStatus(alert, 'RESOLVED')}>
                <CheckCheck className="mr-1 h-3 w-3" /> {t('common:emergency.resolve')}
              </Button>
            </div>
          </div>
        )
      })}
    </section>
  )
}
