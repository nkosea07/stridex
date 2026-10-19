import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, Bell, RefreshCcw } from 'lucide-react'
import { notificationsApi } from '@/lib/api'
import type { AppNotification } from '@/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { toast } from 'sonner'
import { useAuth } from '@/contexts/AuthContext'

type SessionSnapshot = {
  userId: string | null
  generation: number
}

export function NotificationsPage() {
  const { user, getSessionGeneration } = useAuth()
  const userId = user?.id ?? null
  const sessionGeneration = getSessionGeneration()
  const sessionRef = useRef<SessionSnapshot>({ userId, generation: sessionGeneration })
  sessionRef.current = { userId, generation: sessionGeneration }
  const [notifications, setNotifications] = useState<AppNotification[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  const load = useCallback(async (mode: 'initial' | 'refresh' = 'initial') => {
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    if (mode === 'initial') setLoading(true)
    else setRefreshing(true)
    if (!requestSession.userId) {
      setNotifications([])
      setLoading(false)
      setRefreshing(false)
      return
    }
    try {
      const nextNotifications = await notificationsApi.list()
      if (isCurrentSession()) setNotifications(nextNotifications)
    } catch {
      if (isCurrentSession()) toast.error('Failed to load notifications')
    } finally {
      if (isCurrentSession()) {
        setLoading(false)
        setRefreshing(false)
      }
    }
  }, [getSessionGeneration, sessionGeneration, userId])

  useEffect(() => {
    setNotifications([])
    void load('initial')
  }, [load])

  const markRead = async (notification: AppNotification) => {
    if (notification.read) return
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    try {
      const updated = await notificationsApi.markRead(notification.id)
      if (isCurrentSession()) {
        setNotifications((current) => current.map((item) => (item.id === updated.id ? updated : item)))
      }
    } catch {
      if (isCurrentSession()) toast.error('Failed to mark notification as read')
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6">
        <div className="mb-6 flex items-center justify-between">
          <Button asChild variant="ghost" className="-ml-2 text-muted-foreground">
            <Link to="/">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back
            </Link>
          </Button>
          <Button variant="outline" size="sm" onClick={() => void load('refresh')} disabled={refreshing}>
            <RefreshCcw className={cn('mr-2 h-4 w-4', refreshing && 'animate-spin')} />
            Refresh
          </Button>
        </div>

        <div className="mb-6 flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-foreground">
            <Bell className="h-6 w-6" />
          </span>
          <div>
            <h1 className="text-2xl font-bold text-foreground">Notifications</h1>
            <p className="text-sm text-muted-foreground">Your race alerts and account updates</p>
          </div>
        </div>

        {loading ? (
          <div className="space-y-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="h-20 animate-pulse rounded-2xl bg-muted" />
            ))}
          </div>
        ) : notifications.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-border bg-card px-4 py-16 text-center text-sm text-muted-foreground">
            No notifications yet.
          </div>
        ) : (
          <ul className="space-y-3">
            {notifications.map((notification) => (
              <li key={notification.id}>
                <button
                  type="button"
                  onClick={() => void markRead(notification)}
                  className={cn(
                    'flex w-full items-start gap-3 rounded-2xl border p-4 text-left transition-colors',
                    notification.read
                      ? 'border-border bg-card'
                      : 'border-primary/40 bg-primary/5 hover:bg-primary/10',
                  )}
                >
                  <span className={cn('mt-1 h-2.5 w-2.5 flex-none rounded-full', notification.read ? 'bg-muted-foreground/40' : 'bg-primary')} />
                  <span className="flex-1">
                    <span className="flex items-center gap-2">
                      <span className="font-semibold text-foreground">{notification.title}</span>
                      {notification.type && (
                        <Badge variant="outline" className="text-[10px] uppercase tracking-wide">
                          {notification.type.replace('_', ' ')}
                        </Badge>
                      )}
                    </span>
                    <span className="mt-1 block text-sm text-muted-foreground">{notification.message}</span>
                    {notification.timestamp && (
                      <span className="mt-1 block text-xs text-muted-foreground">
                        {new Date(notification.timestamp).toLocaleString()}
                      </span>
                    )}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
