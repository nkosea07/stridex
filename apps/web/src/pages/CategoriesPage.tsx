import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
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
      toast.error('Failed to load categories')
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
      toast.error('Category name is required')
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
        toast.success('Category updated')
      } else {
        await categoriesApi.create(raceId, payload)
        toast.success('Category created')
      }
      setDialogOpen(false)
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to save category')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (category: RaceCategory) => {
    if (!raceId) return
    if (!window.confirm(`Delete category "${category.name}"?`)) return
    try {
      await categoriesApi.delete(raceId, category.id)
      toast.success('Category deleted')
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete category')
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Categories</h1>
          <p className="text-muted-foreground">Race entry categories and fees</p>
        </div>
        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" />
          New category
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Configured categories</CardTitle>
          <CardDescription>Categories drive registration options and bib prefixes.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Gender</TableHead>
                <TableHead>Age</TableHead>
                <TableHead>Bib prefix</TableHead>
                <TableHead>Fee</TableHead>
                <TableHead>Participants</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {categories.map((category) => (
                <TableRow key={category.id}>
                  <TableCell className="font-medium">
                    <div>{category.name}</div>
                    {category.description && <div className="text-xs text-muted-foreground">{category.description}</div>}
                  </TableCell>
                  <TableCell>{category.gender ?? 'ANY'}</TableCell>
                  <TableCell>
                    {category.minAge ?? '-'}–{category.maxAge ?? '-'}
                  </TableCell>
                  <TableCell>{category.bibPrefix ?? '-'}</TableCell>
                  <TableCell>{category.registrationFee != null ? category.registrationFee : '-'}</TableCell>
                  <TableCell>{category.participantCount ?? 0}</TableCell>
                  <TableCell className="text-right">
                    <Button variant="ghost" size="icon" onClick={() => openEdit(category)}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button variant="ghost" size="icon" className="text-destructive" onClick={() => void handleDelete(category)}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {!loading && categories.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="text-sm text-muted-foreground">
                    No categories configured.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{editingId ? 'Edit category' : 'New category'}</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div><Label>Name</Label><Input value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} /></div>
            <div><Label>Description</Label><Input value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} /></div>
            <div className="grid grid-cols-3 gap-3">
              <div><Label>Min age</Label><Input type="number" value={form.minAge} onChange={(e) => setForm((f) => ({ ...f, minAge: e.target.value }))} /></div>
              <div><Label>Max age</Label><Input type="number" value={form.maxAge} onChange={(e) => setForm((f) => ({ ...f, maxAge: e.target.value }))} /></div>
              <div><Label>Gender</Label><Input value={form.gender} onChange={(e) => setForm((f) => ({ ...f, gender: e.target.value.toUpperCase() }))} placeholder="ANY" /></div>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div><Label>Bib prefix</Label><Input value={form.bibPrefix} onChange={(e) => setForm((f) => ({ ...f, bibPrefix: e.target.value }))} /></div>
              <div><Label>Max participants</Label><Input type="number" value={form.maxParticipants} onChange={(e) => setForm((f) => ({ ...f, maxParticipants: e.target.value }))} /></div>
              <div><Label>Fee</Label><Input type="number" step="0.01" value={form.registrationFee} onChange={(e) => setForm((f) => ({ ...f, registrationFee: e.target.value }))} /></div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={saving}>Cancel</Button>
            <Button onClick={() => void handleSave()} disabled={saving}>{saving ? 'Saving…' : 'Save'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
