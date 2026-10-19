import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { categoriesApi } from '@/lib/api'
import type { RaceCategory } from '@/types'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { toast } from 'sonner'

const emptyForm = {
  name: '',
  description: '',
  minAge: '',
  maxAge: '',
  gender: 'ANY',
  bibPrefix: '',
  maxParticipants: '',
  registrationFee: '',
}

export function CategoriesPage() {
  const { raceId } = useParams()
  const { t } = useTranslation()
  const { user } = useAuth()
  const canManageCategories = isAdminRole(user?.role)
  const [categories, setCategories] = useState<RaceCategory[]>([])
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState({ ...emptyForm })

  const load = async () => {
    if (!raceId) return
    setLoading(true)
    try {
      setCategories(await categoriesApi.list(raceId))
    } catch {
      toast.error(t('nav:categoriesLoadFailed'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [raceId])

  const openCreate = () => {
    setEditingId(null)
    setForm({ ...emptyForm })
    setDialogOpen(true)
  }

  const openEdit = (category: RaceCategory) => {
    setEditingId(category.id)
    setForm({
      name: category.name,
      description: category.description ?? '',
      minAge: category.minAge != null ? String(category.minAge) : '',
      maxAge: category.maxAge != null ? String(category.maxAge) : '',
      gender: category.gender ?? 'ANY',
      bibPrefix: category.bibPrefix ?? '',
      maxParticipants: category.maxParticipants != null ? String(category.maxParticipants) : '',
      registrationFee: category.registrationFee != null ? String(category.registrationFee) : '',
    })
    setDialogOpen(true)
  }

  const handleSave = async () => {
    if (!raceId) return
    if (!form.name.trim()) {
      toast.error(t('nav:categoryNameRequired'))
      return
    }
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      minAge: form.minAge ? Number(form.minAge) : undefined,
      maxAge: form.maxAge ? Number(form.maxAge) : undefined,
      gender: form.gender || undefined,
      bibPrefix: form.bibPrefix.trim() || undefined,
      maxParticipants: form.maxParticipants ? Number(form.maxParticipants) : undefined,
      registrationFee: form.registrationFee ? Number(form.registrationFee) : undefined,
    }
    setSaving(true)
    try {
      if (editingId) {
        await categoriesApi.update(raceId, editingId, payload)
        toast.success(t('nav:categoryUpdated'))
      } else {
        await categoriesApi.create(raceId, payload)
        toast.success(t('nav:categoryCreated'))
      }
      setDialogOpen(false)
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('nav:categorySaveFailed'))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (category: RaceCategory) => {
    if (!raceId) return
    if (!window.confirm(t('nav:deleteCategoryConfirmation', { name: category.name }))) return
    try {
      await categoriesApi.delete(raceId, category.id)
      toast.success(t('nav:categoryDeleted'))
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('nav:categoryDeleteFailed'))
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{t('nav:categoriesPageTitle')}</h1>
          <p className="text-muted-foreground">{t('nav:categoriesPageDescription')}</p>
        </div>
        {canManageCategories && (
          <Button onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" />
            {t('nav:newCategory')}
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('nav:configuredCategories')}</CardTitle>
          <CardDescription>{t('nav:categoriesConfigurationDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('common:labels.name')}</TableHead>
                <TableHead>{t('nav:gender')}</TableHead>
                <TableHead>{t('nav:age')}</TableHead>
                <TableHead>{t('nav:bibPrefix')}</TableHead>
                <TableHead>{t('nav:fee')}</TableHead>
                <TableHead>{t('nav:participants')}</TableHead>
                <TableHead className="text-right">{t('nav:actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {categories.map((category) => (
                <TableRow key={category.id}>
                  <TableCell className="font-medium">
                    <div>{category.name}</div>
                    {category.description && <div className="text-xs text-muted-foreground">{category.description}</div>}
                  </TableCell>
                  <TableCell>{category.gender ?? t('nav:any')}</TableCell>
                  <TableCell>
                    {category.minAge ?? '-'}–{category.maxAge ?? '-'}
                  </TableCell>
                  <TableCell>{category.bibPrefix ?? '-'}</TableCell>
                  <TableCell>{category.registrationFee != null ? category.registrationFee : '-'}</TableCell>
                  <TableCell>{category.participantCount ?? 0}</TableCell>
                  <TableCell className="text-right">
                    {canManageCategories && (
                      <>
                        <Button variant="ghost" size="icon" onClick={() => openEdit(category)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button variant="ghost" size="icon" className="text-destructive" onClick={() => void handleDelete(category)}>
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {!loading && categories.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="text-sm text-muted-foreground">
                    {t('nav:noCategoriesConfigured')}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {canManageCategories && <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{editingId ? t('nav:editCategory') : t('nav:newCategory')}</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div><Label>{t('common:labels.name')}</Label><Input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} /></div>
            <div><Label>{t('nav:description')}</Label><Input value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} /></div>
            <div className="grid grid-cols-3 gap-3">
              <div><Label>{t('nav:minAge')}</Label><Input type="number" value={form.minAge} onChange={(e) => setForm((f) => ({ ...f, minAge: e.target.value }))} /></div>
              <div><Label>{t('nav:maxAge')}</Label><Input type="number" value={form.maxAge} onChange={(e) => setForm((f) => ({ ...f, maxAge: e.target.value }))} /></div>
              <div><Label>{t('nav:gender')}</Label><Input value={form.gender} onChange={(e) => setForm((f) => ({ ...f, gender: e.target.value.toUpperCase() }))} placeholder={t('nav:any')} /></div>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div><Label>{t('nav:bibPrefix')}</Label><Input value={form.bibPrefix} onChange={(e) => setForm((f) => ({ ...f, bibPrefix: e.target.value }))} /></div>
              <div><Label>{t('nav:maxParticipants')}</Label><Input type="number" value={form.maxParticipants} onChange={(e) => setForm((f) => ({ ...f, maxParticipants: e.target.value }))} /></div>
              <div><Label>{t('nav:fee')}</Label><Input type="number" step="0.01" value={form.registrationFee} onChange={(e) => setForm((f) => ({ ...f, registrationFee: e.target.value }))} /></div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={saving}>{t('common:actions.cancel')}</Button>
            <Button onClick={() => void handleSave()} disabled={saving}>{saving ? t('nav:saving') : t('common:actions.save')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>}
    </div>
  )
}
