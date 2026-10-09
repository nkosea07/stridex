import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Plus, Trash2, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { registrationApi } from '@/lib/api'
import { toast } from 'sonner'
import type { RegistrationField } from '@/types'

export function RegistrationFieldsPage() {
  const { raceId } = useParams()
  const [fields, setFields] = useState<RegistrationField[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isDialogOpen, setIsDialogOpen] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [form, setForm] = useState({
    label: '',
    fieldType: 'TEXT' as RegistrationField['fieldType'],
    required: false,
    options: '',
  })

  const load = useCallback(async () => {
    if (!raceId) return
    setIsLoading(true)
    try {
      setFields(await registrationApi.listFields(raceId))
    } catch {
      toast.error('Failed to load registration fields')
    } finally {
      setIsLoading(false)
    }
  }, [raceId])

  useEffect(() => {
    void load()
  }, [load])

  const handleCreate = async () => {
    if (!raceId || !form.label.trim()) {
      toast.error('A label is required')
      return
    }
    setIsSaving(true)
    try {
      await registrationApi.createField(raceId, {
        label: form.label.trim(),
        fieldType: form.fieldType,
        required: form.required,
        options: form.fieldType === 'SELECT'
          ? form.options.split(',').map((option) => option.trim()).filter(Boolean)
          : [],
      })
      toast.success('Registration field added')
      setIsDialogOpen(false)
      setForm({ label: '', fieldType: 'TEXT', required: false, options: '' })
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to add field')
    } finally {
      setIsSaving(false)
    }
  }

  const handleDelete = async (fieldId: string) => {
    if (!raceId) return
    try {
      await registrationApi.deleteField(raceId, fieldId)
      toast.success('Registration field removed')
      await load()
    } catch {
      toast.error('Failed to remove field')
    }
  }

  const handleToggleActive = async (field: RegistrationField) => {
    if (!raceId) return
    try {
      await registrationApi.updateField(raceId, field.id, { ...field, active: !field.active })
      await load()
    } catch {
      toast.error('Failed to update field')
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Registration Form</h1>
          <p className="text-muted-foreground">Configure custom questions asked during race registration</p>
        </div>
        <Button onClick={() => setIsDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          Add Field
        </Button>
      </div>

      <Card>
        <CardHeader><CardTitle className="text-base">Custom Fields</CardTitle></CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Label</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Required</TableHead>
                <TableHead>Options</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-[120px]">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {fields.map((field) => (
                <TableRow key={field.id}>
                  <TableCell className="font-medium">{field.label}</TableCell>
                  <TableCell>{field.fieldType}</TableCell>
                  <TableCell>{field.required ? 'Yes' : 'No'}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{field.options?.join(', ') || '-'}</TableCell>
                  <TableCell>
                    <Badge variant={field.active ? 'default' : 'outline'}>{field.active ? 'Active' : 'Inactive'}</Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="sm" onClick={() => void handleToggleActive(field)}>
                        {field.active ? 'Disable' : 'Enable'}
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => void handleDelete(field.id)}>
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {!isLoading && fields.length === 0 && (
            <p className="p-4 text-sm text-muted-foreground">No custom fields yet. Registration uses the standard form.</p>
          )}
        </CardContent>
      </Card>

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>Add Registration Field</DialogTitle></DialogHeader>
          <div className="grid gap-3">
            <div>
              <Label htmlFor="field-label">Label</Label>
              <Input id="field-label" value={form.label} onChange={(e) => setForm((v) => ({ ...v, label: e.target.value }))} placeholder="e.g. Shirt size" />
            </div>
            <div>
              <Label>Type</Label>
              <Select value={form.fieldType} onValueChange={(value) => setForm((v) => ({ ...v, fieldType: value as RegistrationField['fieldType'] }))}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="TEXT">Text</SelectItem>
                  <SelectItem value="NUMBER">Number</SelectItem>
                  <SelectItem value="SELECT">Select</SelectItem>
                  <SelectItem value="CHECKBOX">Checkbox</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {form.fieldType === 'SELECT' && (
              <div>
                <Label>Options (comma separated)</Label>
                <Input value={form.options} onChange={(e) => setForm((v) => ({ ...v, options: e.target.value }))} placeholder="S, M, L, XL" />
              </div>
            )}
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={form.required}
                onChange={(e) => setForm((v) => ({ ...v, required: e.target.checked }))}
              />
              Required
            </label>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsDialogOpen(false)}>Cancel</Button>
            <Button onClick={() => void handleCreate()} disabled={isSaving}>
              {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              Add
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
