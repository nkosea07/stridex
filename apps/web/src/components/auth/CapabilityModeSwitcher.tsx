import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { getPreferredCapability, getUserCapabilities, useAuth } from '@/contexts/AuthContext'
import type { UserCapability } from '@/types'
import { cn } from '@/lib/utils'

interface CapabilityModeSwitcherProps {
  compact?: boolean
  className?: string
}

export function CapabilityModeSwitcher({ compact = false, className }: CapabilityModeSwitcherProps) {
  const { t } = useTranslation()
  const { user, setPreferredCapability } = useAuth()
  const capabilities = getUserCapabilities(user)
  const preferredCapability = getPreferredCapability(user)
  const [isSaving, setIsSaving] = useState(false)

  if (capabilities.length < 2) return null

  const selectCapability = async (capability: UserCapability) => {
    if (capability === preferredCapability || isSaving) return
    setIsSaving(true)
    try {
      await setPreferredCapability(capability)
    } catch {
      toast.error(t('nav:capabilityModeUpdateFailed'))
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className={cn('space-y-2', className)}>
      <p className={cn('text-xs font-medium text-muted-foreground', compact && 'sr-only')}>
        {t('nav:capabilityMode')}
      </p>
      <div className={cn('flex gap-2', compact ? 'flex-wrap' : 'flex-col sm:flex-row')} role="group" aria-label={t('nav:capabilityMode')}>
        <Button
          type="button"
          size="sm"
          variant={preferredCapability === 'ATHLETE' ? 'default' : 'outline'}
          aria-pressed={preferredCapability === 'ATHLETE'}
          disabled={isSaving}
          onClick={() => void selectCapability('ATHLETE')}
        >
          {t('nav:athleteMode')}
        </Button>
        <Button
          type="button"
          size="sm"
          variant={preferredCapability === 'SPECTATOR' ? 'default' : 'outline'}
          aria-pressed={preferredCapability === 'SPECTATOR'}
          disabled={isSaving}
          onClick={() => void selectCapability('SPECTATOR')}
        >
          {t('nav:spectatorMode')}
        </Button>
      </div>
    </div>
  )
}
