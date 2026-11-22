import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Plus, Trash2, Power } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { promoApi } from '@/lib/api'
import type { PromoCode } from '@/types'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

export function PromoCodesPage() {
  const { t } = useTranslation()
  const { raceId } = useParams()
  const { user } = useAuth()
  const canManagePromos = isAdminRole(user?.role)
  const [codes, setCodes] = useState<PromoCode[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [form, setForm] = useState({ code: '', discountType: 'PERCENT' as 'PERCENT' | 'FIXED', discountValue: '', maxUses: '' })

  const load = useCallback(async () => {
    if (!raceId) return
    setIsLoading(true)
    try {
      setCodes(await promoApi.list(raceId))
    } catch {
      toast.error(t('admin:promos.loadFailed'))
    } finally {
      setIsLoading(false)
    }
  }, [raceId])

  useEffect(() => {
    void load()
  }, [load])

  const handleCreate = async () => {
    if (!raceId || !form.code.trim() || !form.discountValue) return
    try {
      await promoApi.create(raceId, {
        code: form.code.trim(),
        discountType: form.discountType,
        discountValue: Number(form.discountValue),
        maxUses: form.maxUses ? Number(form.maxUses) : undefined,
      })
      toast.success(t('admin:promos.created'))
      setForm({ code: '', discountType: 'PERCENT', discountValue: '', maxUses: '' })
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('admin:promos.createFailed'))
    }
  }

  const handleToggle = async (promo: PromoCode) => {
    if (!raceId) return
    try {
      await promoApi.setActive(raceId, promo.id, !promo.active)
      await load()
    } catch {
      toast.error(t('admin:promos.updateFailed'))
    }
  }

  const handleDelete = async (id: string) => {
    if (!raceId) return
    try {
      await promoApi.remove(raceId, id)
      toast.success(t('admin:promos.deleted'))
      await load()
    } catch {
      toast.error(t('admin:promos.deleteFailed'))
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{t('admin:promos.title')}</h1>
        <p className="text-muted-foreground">{t('admin:promos.description')}</p>
      </div>

      {canManagePromos && <Card>
        <CardHeader><CardTitle>{t('admin:promos.createTitle')}</CardTitle></CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-4">
          <Input placeholder="CODE" value={form.code} onChange={(e) => setForm((v) => ({ ...v, code: e.target.value.toUpperCase() }))} />
          <Select value={form.discountType} onValueChange={(value) => setForm((v) => ({ ...v, discountType: value as 'PERCENT' | 'FIXED' }))}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="PERCENT">{t('admin:promos.percent')}</SelectItem>
              <SelectItem value="FIXED">{t('admin:promos.fixed')}</SelectItem>
            </SelectContent>
          </Select>
          <Input type="number" placeholder={t('admin:promos.value')} aria-label={t('admin:promos.value')} value={form.discountValue} onChange={(e) => setForm((v) => ({ ...v, discountValue: e.target.value }))} />
          <Input type="number" placeholder={t('admin:promos.maxUses')} aria-label={t('admin:promos.maxUses')} value={form.maxUses} onChange={(e) => setForm((v) => ({ ...v, maxUses: e.target.value }))} />
          <div className="sm:col-span-4">
            <Button onClick={() => void handleCreate()} disabled={!form.code.trim() || !form.discountValue}>
              <Plus className="mr-2 h-4 w-4" /> {t('admin:promos.create')}
            </Button>
          </div>
        </CardContent>
      </Card>}

      <Card>
        <CardHeader><CardTitle>{t('admin:promos.codes')}</CardTitle></CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">{t('admin:promos.loading')}</p>
          ) : codes.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('admin:promos.empty')}</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('admin:promos.code')}</TableHead>
                  <TableHead>{t('admin:promos.discount')}</TableHead>
                  <TableHead>{t('admin:promos.uses')}</TableHead>
                  <TableHead>{t('admin:promos.status')}</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {codes.map((promo) => (
                  <TableRow key={promo.id}>
                    <TableCell className="font-mono font-medium">{promo.code}</TableCell>
                    <TableCell>{promo.discountType === 'PERCENT' ? `${promo.discountValue}%` : promo.discountValue}</TableCell>
                    <TableCell>{promo.usedCount}{promo.maxUses ? ` / ${promo.maxUses}` : ''}</TableCell>
                    <TableCell>
                      <Badge className={promo.active ? 'bg-success/20 text-success' : 'bg-muted text-muted-foreground'}>
                        {promo.active ? 'ACTIVE' : 'INACTIVE'}
                      </Badge>
                    </TableCell>
                    <TableCell className="flex gap-1">
                      {canManagePromos && (
                        <>
                          <Button size="sm" variant="ghost" onClick={() => void handleToggle(promo)}>
                            <Power className="h-3.5 w-3.5" />
                          </Button>
                          <Button size="sm" variant="ghost" className="text-destructive" onClick={() => void handleDelete(promo.id)}>
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
