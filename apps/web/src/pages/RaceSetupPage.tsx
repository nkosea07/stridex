import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { sponsorsApi, stagesApi } from '@/lib/api'
import type { RaceSponsor, RaceStage } from '@/types'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

export function RaceSetupPage() {
  const { raceId } = useParams()
  const { t } = useTranslation()
  const [stages, setStages] = useState<RaceStage[]>([])
  const [sponsors, setSponsors] = useState<RaceSponsor[]>([])
  const [stageForm, setStageForm] = useState({ name: '', sequence: '1', distanceKm: '', stageDate: '' })
  const [sponsorForm, setSponsorForm] = useState({ name: '', tier: '', websiteUrl: '' })

  const load = useCallback(async () => {
    if (!raceId) return
    try {
      const [stageRows, sponsorRows] = await Promise.all([stagesApi.list(raceId), sponsorsApi.list(raceId)])
      setStages(stageRows)
      setSponsors(sponsorRows)
    } catch {
      toast.error(t('common:setupLoadFailed'))
    }
  }, [raceId, t])

  useEffect(() => {
    void load()
  }, [load])

  const addStage = async () => {
    if (!raceId || !stageForm.name.trim()) return
    try {
      await stagesApi.create(raceId, {
        name: stageForm.name.trim(),
        sequence: Number(stageForm.sequence),
        distanceKm: stageForm.distanceKm ? Number(stageForm.distanceKm) : undefined,
        stageDate: stageForm.stageDate || undefined,
      })
      setStageForm({ name: '', sequence: String(stages.length + 2), distanceKm: '', stageDate: '' })
      await load()
    } catch {
      toast.error(t('common:setupSaveFailed'))
    }
  }

  const addSponsor = async () => {
    if (!raceId || !sponsorForm.name.trim()) return
    try {
      await sponsorsApi.create(raceId, {
        name: sponsorForm.name.trim(), tier: sponsorForm.tier || undefined,
        websiteUrl: sponsorForm.websiteUrl || undefined, displayOrder: sponsors.length,
      })
      setSponsorForm({ name: '', tier: '', websiteUrl: '' })
      await load()
    } catch {
      toast.error(t('common:setupSaveFailed'))
    }
  }

  const remove = async (kind: 'stage' | 'sponsor', id: string) => {
    if (!raceId) return
    try {
      if (kind === 'stage') await stagesApi.remove(raceId, id)
      else await sponsorsApi.remove(raceId, id)
      await load()
    } catch {
      toast.error(t('common:setupDeleteFailed'))
    }
  }

  return (
    <div className="grid gap-6 p-6 lg:grid-cols-2">
      <Card>
        <CardHeader><CardTitle>{t('common:raceStages')}</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          {stages.map((stage) => (
            <div key={stage.id} className="flex items-center justify-between rounded-lg border border-border p-3">
              <div><p className="font-medium">{stage.sequence}. {stage.name}</p><p className="text-xs text-muted-foreground">{stage.stageDate ?? '—'} · {stage.distanceKm ?? '—'} km</p></div>
              <Button size="icon" variant="ghost" onClick={() => void remove('stage', stage.id)}><Trash2 className="h-4 w-4 text-destructive" /></Button>
            </div>
          ))}
          <div className="grid gap-2 sm:grid-cols-2">
            <div><Label>{t('common:stageName')}</Label><Input value={stageForm.name} onChange={(e) => setStageForm((v) => ({ ...v, name: e.target.value }))} /></div>
            <div><Label>{t('common:stageSequence')}</Label><Input type="number" value={stageForm.sequence} onChange={(e) => setStageForm((v) => ({ ...v, sequence: e.target.value }))} /></div>
            <div><Label>{t('common:stageDate')}</Label><Input type="date" value={stageForm.stageDate} onChange={(e) => setStageForm((v) => ({ ...v, stageDate: e.target.value }))} /></div>
            <div><Label>{t('common:distanceKm')}</Label><Input type="number" value={stageForm.distanceKm} onChange={(e) => setStageForm((v) => ({ ...v, distanceKm: e.target.value }))} /></div>
          </div>
          <Button onClick={() => void addStage()} disabled={!stageForm.name.trim()}><Plus className="mr-2 h-4 w-4" />{t('common:addStage')}</Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t('common:raceSponsors')}</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          {sponsors.map((sponsor) => (
            <div key={sponsor.id} className="flex items-center justify-between rounded-lg border border-border p-3">
              <div><p className="font-medium">{sponsor.name}</p><p className="text-xs text-muted-foreground">{sponsor.tier ?? '—'} · {sponsor.websiteUrl ?? '—'}</p></div>
              <Button size="icon" variant="ghost" onClick={() => void remove('sponsor', sponsor.id)}><Trash2 className="h-4 w-4 text-destructive" /></Button>
            </div>
          ))}
          <div className="grid gap-2 sm:grid-cols-2">
            <div><Label>{t('common:sponsorName')}</Label><Input value={sponsorForm.name} onChange={(e) => setSponsorForm((v) => ({ ...v, name: e.target.value }))} /></div>
            <div><Label>{t('common:sponsorTier')}</Label><Input value={sponsorForm.tier} onChange={(e) => setSponsorForm((v) => ({ ...v, tier: e.target.value }))} /></div>
            <div className="sm:col-span-2"><Label>{t('common:websiteUrl')}</Label><Input value={sponsorForm.websiteUrl} onChange={(e) => setSponsorForm((v) => ({ ...v, websiteUrl: e.target.value }))} /></div>
          </div>
          <Button onClick={() => void addSponsor()} disabled={!sponsorForm.name.trim()}><Plus className="mr-2 h-4 w-4" />{t('common:addSponsor')}</Button>
        </CardContent>
      </Card>
    </div>
  )
}
