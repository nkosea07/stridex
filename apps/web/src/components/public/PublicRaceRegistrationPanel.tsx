import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuthOptional } from '@/contexts/AuthContext'
import { publicRegistrationApi } from '@/lib/api'
import { toast } from 'sonner'
import type { PublicRaceHub, RegistrationField } from '@/types'

interface PublicRaceRegistrationPanelProps {
  raceId: string
  hub: PublicRaceHub
  returnTo: string
}

const inputClass = 'bg-white/70'

export function PublicRaceRegistrationPanel({ raceId, hub, returnTo }: PublicRaceRegistrationPanelProps) {
  const auth = useAuthOptional()
  const isAuthenticated = Boolean(auth?.isAuthenticated)
  const [fields, setFields] = useState<RegistrationField[]>([])
  const [answers, setAnswers] = useState<Record<string, string>>({})
  const [form, setForm] = useState({
    categoryId: '',
    contactName: '',
    contactPhone: '',
    contactRelationship: '',
    shirtSize: '',
    clubName: '',
    teamName: '',
    waiverAccepted: false,
  })
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submittedStatus, setSubmittedStatus] = useState<string | null>(null)

  const now = useMemo(() => Date.now(), [])

  const windowState = useMemo(() => {
    if (!hub.isPublicRegistration) return 'closed'
    if (hub.registrationOpens && now < new Date(hub.registrationOpens).getTime()) return 'not-open'
    if (hub.registrationCloses && now > new Date(hub.registrationCloses).getTime()) return 'closed'
    if (hub.status === 'CANCELLED' || hub.status === 'COMPLETED' || hub.status === 'IN_PROGRESS') return 'closed'
    return 'open'
  }, [hub.isPublicRegistration, hub.registrationOpens, hub.registrationCloses, hub.status, now])

  const loadFields = useCallback(async () => {
    try {
      setFields(await publicRegistrationApi.listFields(raceId))
    } catch {
      // Custom fields are optional; ignore failures and fall back to the standard form.
    }
  }, [raceId])

  useEffect(() => {
    if (!hub.isPublicRegistration) return
    void loadFields()
  }, [hub.isPublicRegistration, loadFields])

  useEffect(() => {
    const firstCategory = hub.categories?.[0]
    if (firstCategory && !form.categoryId) {
      setForm((value) => ({ ...value, categoryId: firstCategory.id }))
    }
  }, [hub.categories, form.categoryId])

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!form.categoryId) {
      toast.error('Please select a race category')
      return
    }
    if (hub.requireWaiver && !form.waiverAccepted) {
      toast.error('You must accept the race waiver')
      return
    }
    setIsSubmitting(true)
    try {
      const registration = await publicRegistrationApi.register(raceId, {
        categoryId: form.categoryId,
        emergencyContact: {
          name: form.contactName,
          phone: form.contactPhone,
          relationship: form.contactRelationship,
        },
        waiverAccepted: form.waiverAccepted,
        shirtSize: form.shirtSize || undefined,
        clubName: form.clubName || undefined,
        teamName: form.teamName || undefined,
        answers: fields.map((field) => ({ fieldId: field.id, value: answers[field.id] })),
      })
      setSubmittedStatus(registration.status)
      toast.success('Registration submitted')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Registration failed')
    } finally {
      setIsSubmitting(false)
    }
  }

  if (!hub.isPublicRegistration) {
    return null
  }

  return (
    <section className="rounded-[28px] border border-slate-200/70 bg-white/85 p-5 shadow-[0_16px_50px_rgba(15,23,42,0.08)] backdrop-blur-sm">
      <p className="text-[11px] font-bold uppercase tracking-[0.24em] text-slate-500">Registration</p>
      <h2 className="mt-1 text-xl font-black uppercase tracking-[-0.04em] text-slate-950">Register for this race</h2>

      {windowState === 'not-open' && (
        <p className="mt-3 text-sm text-slate-600">Registration has not opened yet. Check back soon.</p>
      )}
      {windowState === 'closed' && (
        <p className="mt-3 text-sm text-slate-600">Registration is currently closed for this race.</p>
      )}

      {windowState === 'open' && submittedStatus && (
        <div className="mt-3 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          {submittedStatus === 'waitlisted'
            ? 'You have been added to the waitlist. We will contact you if a place opens up.'
            : submittedStatus === 'pending_review'
              ? 'Your registration was submitted and is pending review.'
              : 'Your registration was received. Good luck!'}
        </div>
      )}

      {windowState === 'open' && !submittedStatus && !isAuthenticated && (
        <div className="mt-3">
          <p className="text-sm text-slate-600">Sign in or create a free account to register.</p>
          <div className="mt-3 flex gap-2">
            <Button asChild className="rounded-full bg-slate-950 px-5 text-xs font-bold uppercase tracking-[0.18em] text-cyan-100 hover:bg-slate-800">
              <Link to="/login" state={{ from: returnTo }}>Sign in</Link>
            </Button>
            <Button asChild variant="outline" className="rounded-full px-5 text-xs font-bold uppercase tracking-[0.18em]">
              <Link to="/register" state={{ from: returnTo }}>Create account</Link>
            </Button>
          </div>
        </div>
      )}

      {windowState === 'open' && !submittedStatus && isAuthenticated && (
        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="reg-category">Category</Label>
              <select
                id="reg-category"
                className="h-10 w-full rounded-md border border-input bg-white/70 px-3 text-sm"
                value={form.categoryId}
                onChange={(e) => setForm((v) => ({ ...v, categoryId: e.target.value }))}
                required
              >
                <option value="">Select a category</option>
                {(hub.categories ?? []).map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}{category.registrationFee ? ` — ${category.registrationFee}` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="reg-shirt">Shirt size</Label>
              <Input id="reg-shirt" className={inputClass} value={form.shirtSize} onChange={(e) => setForm((v) => ({ ...v, shirtSize: e.target.value }))} />
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-3">
            <div className="space-y-1">
              <Label htmlFor="reg-contact-name">Emergency contact</Label>
              <Input id="reg-contact-name" className={inputClass} value={form.contactName} onChange={(e) => setForm((v) => ({ ...v, contactName: e.target.value }))} required />
            </div>
            <div className="space-y-1">
              <Label htmlFor="reg-contact-phone">Contact phone</Label>
              <Input id="reg-contact-phone" className={inputClass} value={form.contactPhone} onChange={(e) => setForm((v) => ({ ...v, contactPhone: e.target.value }))} required />
            </div>
            <div className="space-y-1">
              <Label htmlFor="reg-contact-relation">Relationship</Label>
              <Input id="reg-contact-relation" className={inputClass} value={form.contactRelationship} onChange={(e) => setForm((v) => ({ ...v, contactRelationship: e.target.value }))} />
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <Label htmlFor="reg-team">Team name (optional)</Label>
              <Input id="reg-team" className={inputClass} value={form.teamName} onChange={(e) => setForm((v) => ({ ...v, teamName: e.target.value }))} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="reg-club">Club name (optional)</Label>
              <Input id="reg-club" className={inputClass} value={form.clubName} onChange={(e) => setForm((v) => ({ ...v, clubName: e.target.value }))} />
            </div>
          </div>

          {fields.map((field) => (
            <div key={field.id} className="space-y-1">
              <Label htmlFor={`reg-field-${field.id}`}>
                {field.label}{field.required ? ' *' : ''}
              </Label>
              {field.fieldType === 'SELECT' ? (
                <select
                  id={`reg-field-${field.id}`}
                  className="h-10 w-full rounded-md border border-input bg-white/70 px-3 text-sm"
                  value={answers[field.id] ?? ''}
                  onChange={(e) => setAnswers((v) => ({ ...v, [field.id]: e.target.value }))}
                  required={field.required}
                >
                  <option value="">Select</option>
                  {field.options?.map((option) => (
                    <option key={option} value={option}>{option}</option>
                  ))}
                </select>
              ) : field.fieldType === 'CHECKBOX' ? (
                <label className="flex items-center gap-2 text-sm">
                  <input
                    id={`reg-field-${field.id}`}
                    type="checkbox"
                    checked={answers[field.id] === 'true'}
                    onChange={(e) => setAnswers((v) => ({ ...v, [field.id]: e.target.checked ? 'true' : 'false' }))}
                  />
                  {field.label}
                </label>
              ) : (
                <Input
                  id={`reg-field-${field.id}`}
                  type={field.fieldType === 'NUMBER' ? 'number' : 'text'}
                  className={inputClass}
                  value={answers[field.id] ?? ''}
                  onChange={(e) => setAnswers((v) => ({ ...v, [field.id]: e.target.value }))}
                  required={field.required}
                />
              )}
            </div>
          ))}

          {hub.requireWaiver && (
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
              {hub.waiverText && <p className="text-sm text-amber-900">{hub.waiverText}</p>}
              <label className="mt-2 flex items-center gap-2 text-sm font-medium text-amber-900">
                <input
                  type="checkbox"
                  checked={form.waiverAccepted}
                  onChange={(e) => setForm((v) => ({ ...v, waiverAccepted: e.target.checked }))}
                  required
                />
                I accept the race waiver{hub.waiverVersion ? ` (${hub.waiverVersion})` : ''}
              </label>
            </div>
          )}

          <Button type="submit" disabled={isSubmitting} className="rounded-full bg-slate-950 px-6 text-xs font-bold uppercase tracking-[0.18em] text-cyan-100 hover:bg-slate-800">
            {isSubmitting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Submit registration
          </Button>
        </form>
      )}
    </section>
  )
}
