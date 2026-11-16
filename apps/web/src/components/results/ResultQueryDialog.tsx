import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { ApiError, resultsApi } from '@/lib/api'

interface ResultQueryDialogProps {
  raceId: string | null
  raceName?: string
  onOpenChange: (open: boolean) => void
}

export function ResultQueryDialog({ raceId, raceName, onOpenChange }: ResultQueryDialogProps) {
  const { t } = useTranslation()
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)

  const send = async () => {
    if (!raceId) return
    if (message.trim().length < 10) {
      toast.error(t('results:queries.tooShort'))
      return
    }
    setSending(true)
    try {
      await resultsApi.submitQuery(raceId, message.trim())
      toast.success(t('results:queries.sent'))
      setMessage('')
      onOpenChange(false)
    } catch (error) {
      const code = error instanceof ApiError ? error.code : undefined
      toast.error(t(code === 'RESULT_QUERY_LIMIT' ? 'results:queries.limit'
        : code === 'RESULTS_CERTIFIED' ? 'results:queries.locked' : 'results:queries.sendFailed'))
    } finally {
      setSending(false)
    }
  }

  return (
    <Dialog open={raceId !== null} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('results:queries.askTitle')}{raceName ? ` · ${raceName}` : ''}</DialogTitle>
          <DialogDescription>{t('results:queries.askDescription')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="result-query-message">{t('results:queries.message')}</Label>
          <Textarea id="result-query-message" value={message} maxLength={1000} rows={4}
            onChange={(event) => setMessage(event.target.value)} />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={sending}>{t('results:queries.cancel')}</Button>
          <Button onClick={() => void send()} disabled={sending}>
            {sending ? t('results:queries.sending') : t('results:queries.send')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
