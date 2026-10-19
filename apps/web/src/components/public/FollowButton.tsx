import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Heart, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuthOptional } from '@/contexts/AuthContext'
import { favoritesApi } from '@/lib/api'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'

interface FollowButtonProps {
  raceId: string
  participantId: string
  className?: string
}

type SessionSnapshot = {
  userId: string | null
  generation: number
  canFollow: boolean
}

export function FollowButton({ raceId, participantId, className }: FollowButtonProps) {
  const auth = useAuthOptional()
  const isAuthenticated = Boolean(auth?.isAuthenticated)
  const canFollow = isAuthenticated && Boolean(auth?.hasCapability('SPECTATOR'))
  const userId = auth?.user?.id ?? null
  const sessionGeneration = auth?.getSessionGeneration() ?? 0
  const sessionRef = useRef<SessionSnapshot>({ userId, generation: sessionGeneration, canFollow })
  sessionRef.current = { userId, generation: sessionGeneration, canFollow }
  const { t } = useTranslation()
  const [isFollowing, setIsFollowing] = useState(false)
  const [isBusy, setIsBusy] = useState(false)

  useEffect(() => {
    const requestSession = sessionRef.current
    let cancelled = false
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId
        && auth?.getSessionGeneration() === requestSession.generation
        && currentSession.canFollow === requestSession.canFollow
    }
    setIsFollowing(false)
    setIsBusy(false)
    if (!canFollow) {
      return () => {
        cancelled = true
      }
    }
    void (async () => {
      try {
        const favorites = await favoritesApi.list()
        if (!cancelled && isCurrentSession()) {
          setIsFollowing(favorites.some((favorite) => favorite.participantId === participantId))
        }
      } catch {
        // Favorites are optional; ignore failures.
      }
    })()
    return () => {
      cancelled = true
    }
  }, [canFollow, participantId, sessionGeneration, userId])

  if (!canFollow) {
    return null
  }

  const toggle = async () => {
    const requestSession = sessionRef.current
    if (!requestSession.canFollow) return
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId
        && auth?.getSessionGeneration() === requestSession.generation
        && currentSession.canFollow === requestSession.canFollow
    }
    const wasFollowing = isFollowing
    setIsBusy(true)
    try {
      if (wasFollowing) {
        await favoritesApi.unfollow(raceId, participantId)
        if (!isCurrentSession()) return
        setIsFollowing(false)
        toast.success(t('nav:removedFromFavorites'))
      } else {
        await favoritesApi.follow(raceId, participantId)
        if (!isCurrentSession()) return
        setIsFollowing(true)
        toast.success(t('nav:addedToFavorites'))
      }
    } catch {
      if (isCurrentSession()) toast.error(t('nav:favoritesUpdateFailed'))
    } finally {
      if (isCurrentSession()) setIsBusy(false)
    }
  }

  return (
    <Button
      type="button"
      variant={isFollowing ? 'default' : 'secondary'}
      onClick={() => void toggle()}
      disabled={isBusy}
      className={cn('rounded-full border border-white/10 text-xs font-bold uppercase tracking-[0.18em]', className)}
    >
      {isBusy ? <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" /> : <Heart className={cn('mr-2 h-3.5 w-3.5', isFollowing && 'fill-current')} />}
      {isFollowing ? t('nav:following') : t('nav:follow')}
    </Button>
  )
}
