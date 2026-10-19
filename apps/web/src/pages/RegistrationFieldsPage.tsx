import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Plus, Trash2, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { registrationApi } from '@/lib/api'
import { toast } from 'sonner'
import type { RegistrationField } from '@/types'

export function RegistrationFieldsPage() {
  const { raceId } = useParams()
  const { t } = useTranslation()
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
      toast.error(t('register:registrationFieldsLoadFailed'))
    } finally {
      setIsLoading(false)
    }
  }, [raceId])

  useEffect(() => {
    void load()
  }, [load])

  const handleCreate = async () => {
    if (!raceId || !form.label.trim()) {
      toast.error(t('register:fieldLabelRequired'))
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
      toast.success(t('register:registrationFieldAdded'))
      setIsDialogOpen(false)
      setForm({ label: '', fieldType: 'TEXT', required: false, options: '' })
      await load()
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('register:registrationFieldAddFailed'))
    } finally {
      setIsSaving(false)
    }
  }

  const handleDelete = async (fieldId: string) => {
    if (!raceId) return
    try {
      await registrationApi.deleteField(raceId, fieldId)
      toast.success(t('register:registrationFieldRemoved'))
      await load()
    } catch {
      toast.error(t('register:registrationFieldRemoveFailed'))
    }
  }

  const handleToggleActive = async (field: RegistrationField) => {
    if (!raceId) return
    try {
      await registrationApi.updateField(raceId, field.id, { ...field, active: !field.active })
      await load()
    } catch {
      toast.error(t('register:registrationFieldUpdateFailed'))
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{t('register:registrationFieldsTitle')}</h1>
          <p className="text-muted-foreground">{t('register:registrationFieldsDescription')}</p>
        </div>
        <Button onClick={() => setIsDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          {t('register:addField')}
        </Button>
      </div>

      <Card>
        <CardHeader><CardTitle className="text-base">{t('register:customFields')}</CardTitle></CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('register:fieldLabel')}</TableHead>
                <TableHead>{t('register:fieldType')}</TableHead>
                <TableHead>{t('register:fieldRequired')}</TableHead>
                <TableHead>{t('register:fieldOptions')}</TableHead>
                <TableHead>{t('register:fieldStatus')}</TableHead>
                <TableHead className="w-[120px]">{t('register:fieldActions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {fields.map((field) => (
                <TableRow key={field.id}>
                  <TableCell className="font-medium">{field.label}</TableCell>
                  <TableCell>{t(`register:fieldType${field.fieldType.charAt(0)}${field.fieldType.slice(1).toLowerCase()}`)}</TableCell>
                  <TableCell>{field.required ? t('common:yes') : t('common:no')}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{field.options?.join(', ') || '-'}</TableCell>
                  <TableCell>
                    <Badge variant={field.active ? 'default' : 'outline'}>{field.active ? t('register:fieldActive') : t('register:fieldInactive')}</Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="sm" onClick={() => void handleToggleActive(field)}>
                        {field.active ? t('common:actions.disable') : t('common:actions.enable')}
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
            <p className="p-4 text-sm text-muted-foreground">{t('register:noCustomFields')}</p>
          )}
        </CardContent>
      </Card>

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('register:addRegistrationField')}</DialogTitle>
            <DialogDescription>{t('register:registrationFieldDescription')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3">
            <div>
              <Label htmlFor="field-label">{t('register:fieldLabel')}</Label>
              <Input id="field-label" value={form.label} onChange={(e) => setForm((v) => ({ ...v, label: e.target.value }))} placeholder={t('register:fieldLabelPlaceholder')} />
            </div>
            <div>
              <Label>{t('register:fieldType')}</Label>
              <Select value={form.fieldType} onValueChange={(value) => setForm((v) => ({ ...v, fieldType: value as RegistrationField['fieldType'] }))}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="TEXT">{t('register:fieldTypeText')}</SelectItem>
                  <SelectItem value="NUMBER">{t('register:fieldTypeNumber')}</SelectItem>
                  <SelectItem value="SELECT">{t('register:fieldTypeSelect')}</SelectItem>
                  <SelectItem value="CHECKBOX">{t('register:fieldTypeCheckbox')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {form.fieldType === 'SELECT' && (
              <div>
                <Label>{t('register:fieldOptionsCommaSeparated')}</Label>
                <Input value={form.options} onChange={(e) => setForm((v) => ({ ...v, options: e.target.value }))} placeholder={t('register:fieldOptionsPlaceholder')} />
              </div>
            )}
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={form.required}
                onChange={(e) => setForm((v) => ({ ...v, required: e.target.checked }))}
              />
              {t('register:fieldRequired')}
            </label>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsDialogOpen(false)}>{t('common:actions.cancel')}</Button>
            <Button onClick={() => void handleCreate()} disabled={isSaving}>
              {isSaving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              {t('common:actions.add')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
