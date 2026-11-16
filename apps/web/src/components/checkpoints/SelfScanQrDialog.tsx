import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Download, RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { selfScanApi } from '@/lib/api'
import type { Checkpoint } from '@/types'

export function acceptsSelfScan(checkpoint: Pick<Checkpoint, 'checkpointType'>) {
  return checkpoint.checkpointType !== 'START' && checkpoint.checkpointType !== 'FINISH'
}

interface SelfScanQrDialogProps {
  raceId: string
  checkpoint: Checkpoint | null
  canRotate: boolean
  onOpenChange: (open: boolean) => void
}

export function SelfScanQrDialog({ raceId, checkpoint, canRotate, onOpenChange }: SelfScanQrDialogProps) {
  const { t } = useTranslation()
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  const [rotating, setRotating] = useState(false)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    if (!checkpoint) return
    let active = true
    let objectUrl: string | null = null
    setImageUrl(null)
    setFailed(false)
    selfScanApi.qrCode(raceId, checkpoint.id)
      .then((blob) => {
        if (!active) return
        objectUrl = URL.createObjectURL(blob)
        setImageUrl(objectUrl)
      })
      .catch(() => {
        if (active) setFailed(true)
      })
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [raceId, checkpoint, version])

  const rotate = async () => {
    if (!checkpoint || rotating || !window.confirm(t('checkpoints:selfScan.rotateConfirm'))) return
    setRotating(true)
    try {
      await selfScanApi.rotate(raceId, checkpoint.id)
      toast.success(t('checkpoints:selfScan.rotated'))
      setVersion((value) => value + 1)
    } catch {
      toast.error(t('checkpoints:selfScan.rotateFailed'))
    } finally {
      setRotating(false)
    }
  }

  const name = checkpoint?.name ?? ''

  return (
    <Dialog open={checkpoint !== null} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('checkpoints:selfScan.dialogTitle', { name })}</DialogTitle>
          <DialogDescription>{t('checkpoints:selfScan.dialogDescription')}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col items-center gap-3" aria-live="polite">
          {imageUrl ? (
            <img src={imageUrl} alt={t('checkpoints:selfScan.qrAlt', { name })}
              className="h-64 w-64 rounded-md border border-border bg-white p-2" />
          ) : (
            <p className="py-16 text-sm text-muted-foreground">
              {failed ? t('checkpoints:selfScan.loadFailed') : t('checkpoints:selfScan.loading')}
            </p>
          )}
          <p className="text-xs text-muted-foreground">{t('checkpoints:selfScan.secretWarning')}</p>
        </div>
        <DialogFooter className="gap-2 sm:gap-0">
          {canRotate && (
            <Button variant="outline" onClick={() => void rotate()} disabled={rotating}>
              <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
              {rotating ? t('checkpoints:selfScan.rotating') : t('checkpoints:selfScan.rotate')}
            </Button>
          )}
          <Button asChild disabled={!imageUrl}>
            <a href={imageUrl ?? undefined} download={`self-scan-${name || 'checkpoint'}.png`}
              aria-disabled={!imageUrl}>
              <Download className="mr-2 h-4 w-4" aria-hidden="true" />
              {t('checkpoints:selfScan.download')}
            </a>
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
