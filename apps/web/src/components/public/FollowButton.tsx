import { useEffect, useState } from 'react'
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

export function FollowButton({ raceId, participantId, className }: FollowButtonProps) {
  const auth = useAuthOptional()
  const isAuthenticated = Boolean(auth?.isAuthenticated)
  const [isFollowing, setIsFollowing] = useState(false)
  const [isBusy, setIsBusy] = useState(false)

  useEffect(() => {
    if (!isAuthenticated) return
    let cancelled = false
    void (async () => {
      try {
        const favorites = await favoritesApi.list()
        if (!cancelled) {
          setIsFollowing(favorites.some((favorite) => favorite.participantId === participantId))
        }
      } catch {
        // Favorites are optional; ignore failures.
      }
    })()
    return () => {
      cancelled = true
    }
  }, [isAuthenticated, participantId])

  if (!isAuthenticated) {
    return null
  }

  const toggle = async () => {
    setIsBusy(true)
    try {
      if (isFollowing) {
        await favoritesApi.unfollow(raceId, participantId)
        setIsFollowing(false)
        toast.success('Removed from favorites')
      } else {
        await favoritesApi.follow(raceId, participantId)
        setIsFollowing(true)
        toast.success('Added to favorites')
      }
    } catch {
      toast.error('Failed to update favorites')
    } finally {
      setIsBusy(false)
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
      {isFollowing ? 'Following' : 'Follow'}
    </Button>
  )
}
