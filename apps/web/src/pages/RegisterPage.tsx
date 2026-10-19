import { useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Flag, Loader2 } from 'lucide-react'
import { API_CONTRACT } from '@race-tracker/contracts'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ApiError, authApi } from '@/lib/api'
import type { UserCapability } from '@/types'
import { toast } from 'sonner'

type ExperienceChoice = UserCapability | 'BOTH'

const experienceOptions: ReadonlyArray<{
  value: ExperienceChoice
  labelKey: string
  descriptionKey: string
}> = [
  { value: 'ATHLETE', labelKey: 'register:experienceJoinRace', descriptionKey: 'register:experienceJoinRaceDescription' },
  { value: 'SPECTATOR', labelKey: 'register:experienceFollowRaces', descriptionKey: 'register:experienceFollowRacesDescription' },
  { value: 'BOTH', labelKey: 'register:experienceBoth', descriptionKey: 'register:experienceBothDescription' },
]

function getSafePublicReturnPath(value: unknown) {
  if (typeof value !== 'string') return undefined
  if (!value.startsWith('/') || value.startsWith('//')) return undefined

  const path = value.split(/[?#]/, 1)[0]
  const isRaceHub = /^\/race\/[^/]+$/.test(path)
  const isAthletePage = /^\/race\/[^/]+\/athlete\/[^/]+$/.test(path)

  return isRaceHub || isAthletePage ? value : undefined
}

export function RegisterPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [isLoading, setIsLoading] = useState(false)
  const [acceptedPrivacy, setAcceptedPrivacy] = useState(false)
  const [experienceChoice, setExperienceChoice] = useState<ExperienceChoice | null>(null)
  const privacyPolicyPath = API_CONTRACT.legal?.privacyPolicyPath ?? '/privacy'
  const privacyPolicyVersion = API_CONTRACT.legal?.privacyPolicyVersion
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    organizationName: '',
    password: '',
    confirmPassword: '',
  })

  const update = (key: keyof typeof form, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  const fromState = (location.state as { from?: string } | null)?.from
  const returnTo = getSafePublicReturnPath(fromState)
  const organizerIntent = searchParams.get('intent') === 'organizer'

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (form.password !== form.confirmPassword) {
      toast.error(t('register:errorPasswords'))
      return
    }

    if (!acceptedPrivacy) {
      toast.error(t('register:errorPrivacy'))
      return
    }

    if (!organizerIntent && !experienceChoice) {
      toast.error(t('register:errorExperience'))
      return
    }

    setIsLoading(true)
    try {
      const commonData = {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim() || undefined,
        password: form.password,
        privacyPolicyAccepted: true,
        privacyPolicyVersion,
      }
      if (organizerIntent) {
        await authApi.registerOrganizer({ ...commonData, organizationName: form.organizationName.trim() })
      } else {
        const experiences: UserCapability[] = experienceChoice === 'BOTH'
          ? ['ATHLETE', 'SPECTATOR']
          : [experienceChoice as UserCapability]
        await authApi.register({ ...commonData, experiences })
      }
      authApi.logout()
      toast.success(organizerIntent ? t('register:successOrganizer') : t('register:successAthlete'))
      navigate('/login', { replace: true, state: returnTo ? { from: returnTo } : undefined })
    } catch (error) {
      if (error instanceof ApiError && (error.code === 'VALIDATION_ERROR' || error.status === 400)) {
        toast.error(t('register:errorValidation'))
      } else if (error instanceof ApiError && (error.code === 'CONFLICT' || error.status === 409)) {
        toast.error(t('register:errorConflict'))
      } else {
        toast.error(t('register:errorFailed'))
      }
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <div className="w-full max-w-md space-y-8">
        <div className="flex flex-col items-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary">
            <Flag className="h-6 w-6 text-primary-foreground" />
          </div>
          <h1 className="mt-4 text-2xl font-bold text-foreground">{t('common:appName')}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {organizerIntent ? t('register:organizerSubtitle') : t('register:subtitle')}
          </p>
        </div>

        <Card className="border-border bg-card">
          <CardHeader className="space-y-1">
            <CardTitle className="text-xl">{t('register:title')}</CardTitle>
            <CardDescription>
              {organizerIntent ? t('register:organizerDescription') : t('register:description')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-2">
                  <Label htmlFor="firstName">{t('register:firstName')}</Label>
                  <Input
                    id="firstName"
                    value={form.firstName}
                    onChange={(e) => update('firstName', e.target.value)}
                    required
                    className="bg-muted/50"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="lastName">{t('register:lastName')}</Label>
                  <Input
                    id="lastName"
                    value={form.lastName}
                    onChange={(e) => update('lastName', e.target.value)}
                    required
                    className="bg-muted/50"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="email">{t('register:email')}</Label>
                <Input
                  id="email"
                  type="email"
                  value={form.email}
                  onChange={(e) => update('email', e.target.value)}
                  required
                  className="bg-muted/50"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="phone">{t('register:phoneOptional')}</Label>
                <Input
                  id="phone"
                  value={form.phone}
                  onChange={(e) => update('phone', e.target.value)}
                  className="bg-muted/50"
                />
              </div>

              {!organizerIntent && (
                <fieldset className="space-y-3">
                  <legend className="text-sm font-medium text-foreground">{t('register:experienceLabel')}</legend>
                  <p className="text-xs text-muted-foreground">{t('register:experienceDescription')}</p>
                  <div className="grid gap-3">
                    {experienceOptions.map((option) => (
                      <label
                        key={option.value}
                        className="flex cursor-pointer items-start gap-3 rounded-lg border border-border p-3 transition-colors has-[:checked]:border-primary has-[:checked]:bg-primary/5"
                      >
                        <input
                          type="radio"
                          name="registration-experience"
                          value={option.value}
                          checked={experienceChoice === option.value}
                          onChange={() => setExperienceChoice(option.value)}
                          className="mt-1 h-4 w-4"
                        />
                        <span>
                          <span className="block text-sm font-medium text-foreground">{t(option.labelKey)}</span>
                          <span className="block text-xs text-muted-foreground">{t(option.descriptionKey)}</span>
                        </span>
                      </label>
                    ))}
                  </div>
                </fieldset>
              )}

              {organizerIntent && (
                <div className="space-y-2">
                  <Label htmlFor="organizationName">{t('register:organization')}</Label>
                  <Input
                    id="organizationName"
                    value={form.organizationName}
                    onChange={(e) => update('organizationName', e.target.value)}
                    required
                    maxLength={200}
                    className="bg-muted/50"
                  />
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="password">{t('register:password')}</Label>
                <Input
                  id="password"
                  type="password"
                  value={form.password}
                  onChange={(e) => update('password', e.target.value)}
                  minLength={8}
                  required
                  className="bg-muted/50"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="confirmPassword">{t('register:confirmPassword')}</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  value={form.confirmPassword}
                  onChange={(e) => update('confirmPassword', e.target.value)}
                  minLength={8}
                  required
                  className="bg-muted/50"
                />
              </div>

              <label className="flex items-start gap-2 text-sm text-muted-foreground">
                <input
                  type="checkbox"
                  className="mt-1 h-4 w-4 rounded border-border"
                  checked={acceptedPrivacy}
                  onChange={(e) => setAcceptedPrivacy(e.target.checked)}
                  required
                />
                <span>
                  {t('register:privacyPrefix')}{' '}
                  <Link to={privacyPolicyPath} target="_blank" rel="noreferrer" className="text-primary underline-offset-4 hover:underline">
                    {t('register:privacyLink')}
                  </Link>
                  .
                </span>
              </label>

              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    {t('register:creatingAccount')}
                  </>
                ) : (
                  organizerIntent ? t('register:createOrganizerAccount') : t('register:createAccount')
                )}
              </Button>
            </form>

            <p className="mt-6 text-center text-sm text-muted-foreground">
              {t('register:alreadyHaveAccount')}{' '}
              <Link
                to="/login"
                state={returnTo ? { from: returnTo } : undefined}
                className="text-primary underline-offset-4 hover:underline"
              >
                {t('register:signIn')}
              </Link>
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
