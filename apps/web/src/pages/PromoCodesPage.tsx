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
import { toast } from 'sonner'

export function PromoCodesPage() {
  const { raceId } = useParams()
  const [codes, setCodes] = useState<PromoCode[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [form, setForm] = useState({ code: '', discountType: 'PERCENT' as 'PERCENT' | 'FIXED', discountValue: '', maxUses: '' })

  const load = useCallback(async () => {
    if (!raceId) return
    setIsLoading(true)
    try {
      setCodes(await promoApi.list(raceId))
    } catch {
      toast.error('Failed to load promo codes')
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
      toast.success('Promo code created')
      setForm({ code: '', discountType: 'PERCENT', discountValue: '', maxUses: '' })
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to create promo code')
    }
  }

  const handleToggle = async (promo: PromoCode) => {
    if (!raceId) return
    try {
      await promoApi.setActive(raceId, promo.id, !promo.active)
      await load()
    } catch {
      toast.error('Failed to update promo code')
    }
  }

  const handleDelete = async (id: string) => {
    if (!raceId) return
    try {
      await promoApi.remove(raceId, id)
      toast.success('Promo code deleted')
      await load()
    } catch {
      toast.error('Failed to delete promo code')
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Promo Codes</h1>
        <p className="text-muted-foreground">Discount codes for race registration</p>
      </div>

      <Card>
        <CardHeader><CardTitle>Create Promo Code</CardTitle></CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-4">
          <Input placeholder="CODE" value={form.code} onChange={(e) => setForm((v) => ({ ...v, code: e.target.value.toUpperCase() }))} />
          <Select value={form.discountType} onValueChange={(value) => setForm((v) => ({ ...v, discountType: value as 'PERCENT' | 'FIXED' }))}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="PERCENT">Percent %</SelectItem>
              <SelectItem value="FIXED">Fixed amount</SelectItem>
            </SelectContent>
          </Select>
          <Input type="number" placeholder="Value" value={form.discountValue} onChange={(e) => setForm((v) => ({ ...v, discountValue: e.target.value }))} />
          <Input type="number" placeholder="Max uses (optional)" value={form.maxUses} onChange={(e) => setForm((v) => ({ ...v, maxUses: e.target.value }))} />
          <div className="sm:col-span-4">
            <Button onClick={() => void handleCreate()} disabled={!form.code.trim() || !form.discountValue}>
              <Plus className="mr-2 h-4 w-4" /> Create
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>Codes</CardTitle></CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Loading…</p>
          ) : codes.length === 0 ? (
            <p className="text-sm text-muted-foreground">No promo codes yet.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Discount</TableHead>
                  <TableHead>Uses</TableHead>
                  <TableHead>Status</TableHead>
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
                      <Button size="sm" variant="ghost" onClick={() => void handleToggle(promo)}>
                        <Power className="h-3.5 w-3.5" />
                      </Button>
                      <Button size="sm" variant="ghost" className="text-destructive" onClick={() => void handleDelete(promo.id)}>
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
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
