import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { API_CONTRACT } from '@race-tracker/contracts'
import { LOCALE_LABELS, SUPPORTED_LOCALES } from '@race-tracker/i18n'
import { 
  User, 
  Shield, 
  Bell, 
  Key,
  Globe,
  Save
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Separator } from '@/components/ui/separator'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { toast } from 'sonner'
import { CapabilityModeSwitcher } from '@/components/auth/CapabilityModeSwitcher'
import { getUserCapabilities, useAuth } from '@/contexts/AuthContext'
import { useTheme } from '@/contexts/ThemeContext'
import { authApi, notificationsApi } from '@/lib/api'
import { enableWebPush, isWebPushConfigured } from '@/lib/push'
import { formatDate } from '@/lib/format'
import type { ConsentState, DeletionStatus, NotificationPreference } from '@/types'

type SessionSnapshot = {
  userId: string | null
  generation: number
}

const DEFAULT_NOTIFICATION_PREFERENCES: NotificationPreference = {
  emailEnabled: true,
  pushEnabled: true,
  smsEnabled: false,
  whatsappEnabled: true,
}

export function SettingsPage() {
  const { user, logout, updateUser, getSessionGeneration } = useAuth()
  const userId = user?.id ?? null
  const sessionGeneration = getSessionGeneration()
  const sessionRef = useRef<SessionSnapshot>({ userId, generation: sessionGeneration })
  sessionRef.current = { userId, generation: sessionGeneration }
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { theme, resolvedTheme, setTheme } = useTheme()
  const [profileSaving, setProfileSaving] = useState(false)
  const [passwordResetSending, setPasswordResetSending] = useState(false)
  const [pushBusy, setPushBusy] = useState(false)
  const [pushRegistered, setPushRegistered] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deletePassword, setDeletePassword] = useState('')
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const [deleteBusy, setDeleteBusy] = useState(false)
  const [consent, setConsent] = useState<ConsentState | null>(null)
  const [consentSaving, setConsentSaving] = useState(false)
  const [deletion, setDeletion] = useState<DeletionStatus | null>(null)
  const [deletionBusy, setDeletionBusy] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [profile, setProfile] = useState({
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    email: user?.email || '',
    phone: user?.phone || ''
  })
  const [notifications, setNotifications] = useState<NotificationPreference>(DEFAULT_NOTIFICATION_PREFERENCES)
  const [notificationPrefsSaving, setNotificationPrefsSaving] = useState(false)

  const handleSaveProfile = async () => {
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    setProfileSaving(true)
    try {
      const updatedUser = await authApi.updateProfile({
        firstName: profile.firstName.trim(),
        lastName: profile.lastName.trim(),
        phone: profile.phone.trim() || undefined,
      })
      if (!isCurrentSession()) return
      updateUser(updatedUser)
      setProfile((current) => ({ ...current, email: updatedUser.email, phone: updatedUser.phone || '' }))
      toast.success(t('nav:profileUpdated'))
    } catch (error) {
      if (isCurrentSession()) {
        toast.error(error instanceof Error ? error.message : t('nav:unableUpdateProfile'))
      }
    } finally {
      if (isCurrentSession()) setProfileSaving(false)
    }
  }

  const handleEnablePush = async () => {
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    setPushBusy(true)
    try {
      const enabled = await enableWebPush()
      if (!isCurrentSession()) return
      if (enabled) {
        setPushRegistered(true)
        toast.success(t('nav:browserPushEnabled'))
      } else {
        toast.error(t('nav:pushUnavailable'))
      }
    } catch {
      if (isCurrentSession()) toast.error(t('nav:pushEnableFailed'))
    } finally {
      if (isCurrentSession()) setPushBusy(false)
    }
  }

  const handleChangePassword = async () => {
    if (!user?.email) return
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    const email = user.email
    setPasswordResetSending(true)
    try {
      await authApi.forgotPassword(email)
      if (isCurrentSession()) toast.success(t('nav:passwordResetSent'))
    } catch (error) {
      if (isCurrentSession()) {
        toast.error(error instanceof Error ? error.message : t('nav:unableStartPasswordReset'))
      }
    } finally {
      if (isCurrentSession()) setPasswordResetSending(false)
    }
  }

  const handleDeleteAccount = async () => {
    if (deleteConfirmation.trim().toUpperCase() !== 'DELETE') {
      toast.error(t('nav:typeDeleteToConfirm', { token: 'DELETE' }))
      return
    }
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    setDeleteBusy(true)
    try {
      await authApi.deleteAccount(deletePassword, deleteConfirmation.trim())
      if (!isCurrentSession()) return
      setDeleteDialogOpen(false)
      toast.success(t('nav:accountDeletionScheduledSuccess'))
      logout()
      navigate('/login', { replace: true })
    } catch (error) {
      if (isCurrentSession()) {
        toast.error(error instanceof Error ? error.message : t('nav:unableDeleteAccount'))
      }
    } finally {
      if (isCurrentSession()) setDeleteBusy(false)
    }
  }

  useEffect(() => {
    let active = true
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    setNotifications(DEFAULT_NOTIFICATION_PREFERENCES)
    setConsent(null)
    setDeletion(null)
    setPushRegistered(false)
    setPushBusy(false)
    setPasswordResetSending(false)
    setDeleteDialogOpen(false)
    setDeletePassword('')
    setDeleteConfirmation('')
    setDeleteBusy(false)
    setExporting(false)
    setConsentSaving(false)
    setDeletionBusy(false)
    setNotificationPrefsSaving(false)

    if (!requestSession.userId) {
      return () => {
        active = false
      }
    }

    notificationsApi
      .preferences()
      .then((prefs) => {
        if (active && isCurrentSession()) setNotifications(prefs)
      })
      .catch(() => {
        // Keep defaults if preferences cannot be loaded.
      })
    authApi
      .consent()
      .then((value) => {
        if (active && isCurrentSession()) setConsent(value)
      })
      .catch(() => {
        // Consent is optional to display.
      })
    authApi
      .deletionStatus()
      .then((value) => {
        if (active && isCurrentSession()) setDeletion(value)
      })
      .catch(() => {
        // Deletion status is optional to display.
      })
    return () => {
      active = false
    }
  }, [userId, sessionGeneration])

  useEffect(() => {
    setProfile({
      firstName: user?.firstName || '',
      lastName: user?.lastName || '',
      email: user?.email || '',
      phone: user?.phone || '',
    })
    setProfileSaving(false)
  }, [userId, sessionGeneration])

  const handleExportData = async () => {
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    setExporting(true)
    try {
      const data = await authApi.exportData()
      if (!isCurrentSession()) return
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'race-tracker-data.json'
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
      toast.success(t('nav:dataExportDownloaded'))
    } catch {
      if (isCurrentSession()) toast.error(t('nav:dataExportFailed'))
    } finally {
      if (isCurrentSession()) setExporting(false)
    }
  }

  const handleConsentChange = async (patch: { dataProcessingConsent?: boolean; marketingConsent?: boolean }) => {
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    setConsentSaving(true)
    try {
      const updatedConsent = await authApi.updateConsent(patch)
      if (!isCurrentSession()) return
      setConsent(updatedConsent)
      toast.success(t('nav:consentUpdated'))
    } catch {
      if (isCurrentSession()) toast.error(t('nav:consentUpdateFailed'))
    } finally {
      if (isCurrentSession()) setConsentSaving(false)
    }
  }

  const handleCancelDeletion = async () => {
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    setDeletionBusy(true)
    try {
      const updatedDeletion = await authApi.cancelDeletion()
      if (!isCurrentSession()) return
      setDeletion(updatedDeletion)
      toast.success(t('nav:accountDeletionCancelled'))
    } catch {
      if (isCurrentSession()) toast.error(t('nav:accountDeletionCancelFailed'))
    } finally {
      if (isCurrentSession()) setDeletionBusy(false)
    }
  }

  const handleSaveNotificationPreferences = async () => {
    const requestSession = sessionRef.current
    const isCurrentSession = () => {
      const currentSession = sessionRef.current
      return currentSession.userId === requestSession.userId && getSessionGeneration() === requestSession.generation
    }
    setNotificationPrefsSaving(true)
    try {
      const saved = await notificationsApi.updatePreferences(notifications)
      if (!isCurrentSession()) return
      setNotifications(saved)
      toast.success(t('nav:notificationPreferencesSaved'))
    } catch (error) {
      if (isCurrentSession()) {
        toast.error(error instanceof Error ? error.message : t('nav:unableSavePreferences'))
      }
    } finally {
      if (isCurrentSession()) setNotificationPrefsSaving(false)
    }
  }

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{t('nav:settingsTitle')}</h1>
        <p className="text-muted-foreground">{t('nav:settingsDescription')}</p>
      </div>

      <Tabs defaultValue="profile" className="space-y-6">
        <TabsList>
          <TabsTrigger value="profile" className="gap-2">
            <User className="h-4 w-4" />
            {t('nav:profileTab')}
          </TabsTrigger>
          <TabsTrigger value="notifications" className="gap-2">
            <Bell className="h-4 w-4" />
            {t('nav:notificationsTab')}
          </TabsTrigger>
          <TabsTrigger value="preferences" className="gap-2">
            <Globe className="h-4 w-4" />
            {t('nav:preferencesTab')}
          </TabsTrigger>
          <TabsTrigger value="security" className="gap-2">
            <Shield className="h-4 w-4" />
            {t('nav:securityTab')}
          </TabsTrigger>
        </TabsList>

        {/* Profile Tab */}
        <TabsContent value="profile">
          <Card>
            <CardHeader>
              <CardTitle>{t('nav:profileInformation')}</CardTitle>
              <CardDescription>{t('nav:updatePersonalDetails')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="firstName">{t('nav:firstName')}</Label>
                  <Input
                    id="firstName"
                    value={profile.firstName}
                    onChange={(e) => setProfile({ ...profile, firstName: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="lastName">{t('nav:lastName')}</Label>
                  <Input
                    id="lastName"
                    value={profile.lastName}
                    onChange={(e) => setProfile({ ...profile, lastName: e.target.value })}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="email">{t('common:labels.email')}</Label>
                <Input
                  id="email"
                  type="email"
                  value={profile.email}
                  readOnly
                  disabled
                />
                <p className="text-xs text-muted-foreground">{t('nav:emailChangesSupport')}</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="phone">{t('common:labels.phone')}</Label>
                <Input
                  id="phone"
                  value={profile.phone}
                  onChange={(e) => setProfile({ ...profile, phone: e.target.value })}
                  placeholder={t('nav:phonePlaceholder')}
                />
              </div>
              <div className="pt-4">
                <Button onClick={handleSaveProfile} disabled={profileSaving}>
                  <Save className="mr-2 h-4 w-4" />
                  {profileSaving ? t('nav:saving') : t('nav:saveChanges')}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {getUserCapabilities(user).length > 1 && (
          <Card>
            <CardHeader>
              <CardTitle>{t('nav:capabilityMode')}</CardTitle>
              <CardDescription>{t('register:experienceDescription')}</CardDescription>
            </CardHeader>
            <CardContent>
              <CapabilityModeSwitcher />
            </CardContent>
          </Card>
        )}

        {/* Notifications Tab */}
        <TabsContent value="notifications">
          <Card>
            <CardHeader>
              <CardTitle>{t('nav:notificationPreferences')}</CardTitle>
              <CardDescription>{t('nav:notificationChannels')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <Label htmlFor="prefEmail">{t('common:labels.email')}</Label>
                    <p className="text-xs text-muted-foreground">{t('nav:transactionalRaceEmails')}</p>
                  </div>
                  <Switch
                    id="prefEmail"
                    checked={notifications.emailEnabled}
                    onCheckedChange={(checked) => setNotifications({ ...notifications, emailEnabled: checked })}
                  />
                </div>
                <Separator />
                <div className="flex items-center justify-between">
                  <div>
                    <Label htmlFor="prefPush">{t('nav:pushChannel')}</Label>
                    <p className="text-xs text-muted-foreground">{t('nav:appBrowserPush')}</p>
                  </div>
                  <Switch
                    id="prefPush"
                    checked={notifications.pushEnabled}
                    onCheckedChange={(checked) => setNotifications({ ...notifications, pushEnabled: checked })}
                  />
                </div>
                <Separator />
                <div className="flex items-center justify-between opacity-60">
                  <div>
                    <Label htmlFor="prefSms">{t('nav:smsChannel')}</Label>
                    <p className="text-xs text-muted-foreground">{t('nav:smsUnavailable')}</p>
                  </div>
                  <Switch id="prefSms" checked={false} disabled />
                </div>
                <Separator />
                <div className="flex items-center justify-between">
                  <div>
                    <Label htmlFor="prefWhatsapp">{t('nav:whatsappChannel')}</Label>
                    <p className="text-xs text-muted-foreground">{t('nav:whatsappAlerts')}</p>
                  </div>
                  <Switch
                    id="prefWhatsapp"
                    checked={notifications.whatsappEnabled}
                    onCheckedChange={(checked) => setNotifications({ ...notifications, whatsappEnabled: checked })}
                  />
                </div>
              </div>

              <Separator />

              <div className="space-y-4">
                <h3 className="text-sm font-medium flex items-center gap-2">
                  <Bell className="h-4 w-4" />
                  {t('nav:browserPush')}
                </h3>
                <div className="flex items-center justify-between">
                  <div>
                    <Label>{t('nav:enableThisBrowser')}</Label>
                    <p className="text-xs text-muted-foreground">
                      {isWebPushConfigured()
                        ? t('nav:realTimeAlerts')
                        : t('nav:pushNotConfigured')}
                    </p>
                  </div>
                  <Button
                    onClick={() => void handleEnablePush()}
                    disabled={pushBusy || pushRegistered || !isWebPushConfigured()}
                  >
                    {pushRegistered ? t('nav:enabled') : pushBusy ? t('nav:enabling') : t('nav:enable')}
                  </Button>
                </div>
              </div>

              <div className="pt-4">
                <Button onClick={() => void handleSaveNotificationPreferences()} disabled={notificationPrefsSaving}>
                  <Save className="mr-2 h-4 w-4" />
                  {notificationPrefsSaving ? t('nav:saving') : t('nav:savePreferences')}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Preferences Tab */}
        <TabsContent value="preferences">
          <Card>
            <CardHeader>
              <CardTitle>{t('nav:regionalPreferences')}</CardTitle>
              <CardDescription>{t('nav:appearanceRegional')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="theme">{t('nav:theme')}</Label>
                <Select value={theme} onValueChange={(value) => setTheme(value as 'light' | 'dark' | 'system')}>
                  <SelectTrigger id="theme">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="light">{t('nav:light')}</SelectItem>
                    <SelectItem value="dark">{t('nav:dark')}</SelectItem>
                    <SelectItem value="system">{t('nav:system')}</SelectItem>
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  {t('nav:activeTheme', { theme: resolvedTheme })}
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="language">{t('common:language')}</Label>
                <Select value={i18n.resolvedLanguage ?? 'en'} onValueChange={(value) => void i18n.changeLanguage(value)}>
                  <SelectTrigger id="language">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SUPPORTED_LOCALES.map((locale) => (
                      <SelectItem key={locale} value={locale}>
                        {LOCALE_LABELS[locale] ?? locale}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <p className="text-sm text-muted-foreground">
                {t('nav:timezoneDeviceLocale')}
              </p>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Security Tab */}
        <TabsContent value="security">
          <div className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>{t('nav:changePassword')}</CardTitle>
                <CardDescription>{t('nav:updateAccountPassword')}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-sm text-muted-foreground">
                  {t('nav:passwordResetDescription')}
                </p>
                <div className="pt-4">
                  <Button onClick={handleChangePassword} disabled={passwordResetSending || !user?.email}>
                    <Key className="mr-2 h-4 w-4" />
                    {passwordResetSending ? t('nav:sending') : t('nav:sendPasswordResetEmail')}
                  </Button>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{t('nav:signInSecurity')}</CardTitle>
                <CardDescription>{t('nav:passwordAuthenticationRelease')}</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground">
                  {t('nav:verifiedEmailPassword')}
                </p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{t('nav:privacy')}</CardTitle>
                <CardDescription>{t('nav:personalDataHandling')}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-5">
                <p className="text-sm text-muted-foreground">
                  {t('nav:privacyDescription')}
                </p>

                {deletion?.deletionRequested && (
                  <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4">
                    <p className="text-sm font-medium text-destructive">{t('nav:accountDeletionScheduled')}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {t('nav:accountDeactivated', {
                        scheduledFor: deletion.scheduledFor
                          ? t('nav:scheduledForErasure', { date: formatDate(deletion.scheduledFor) })
                          : '',
                      })}
                    </p>
                    <Button
                      variant="outline"
                      size="sm"
                      className="mt-3"
                      onClick={() => void handleCancelDeletion()}
                      disabled={deletionBusy}
                    >
                      {deletionBusy ? t('nav:cancelling') : t('nav:cancelDeletion')}
                    </Button>
                  </div>
                )}

                <div className="flex flex-wrap items-center gap-3">
                  <Button asChild variant="outline">
                    <Link to={API_CONTRACT.legal?.privacyPolicyPath ?? '/privacy'} target="_blank" rel="noreferrer">
                      <Shield className="mr-2 h-4 w-4" />
                      {t('nav:readPrivacyPolicy')}
                    </Link>
                  </Button>
                  <Button variant="outline" onClick={() => void handleExportData()} disabled={exporting}>
                    <Globe className="mr-2 h-4 w-4" />
                    {exporting ? t('nav:preparing') : t('nav:downloadMyData')}
                  </Button>
                </div>

                <div className="space-y-4 rounded-lg border border-border p-4">
                  <p className="text-sm font-medium">{t('nav:consent')}</p>
                  <div className="flex items-center justify-between">
                    <div>
                      <Label htmlFor="consentProcessing">{t('nav:dataProcessing')}</Label>
                      <p className="text-xs text-muted-foreground">
                        {t('nav:requiredPlatform', {
                          grantedAt: consent?.dataProcessingConsentAt
                            ? t('nav:grantedOn', { date: formatDate(consent.dataProcessingConsentAt) })
                            : '',
                        })}
                      </p>
                    </div>
                    <Switch
                      id="consentProcessing"
                      checked={consent?.dataProcessingConsentAt != null}
                      disabled={consentSaving || !consent}
                      onCheckedChange={(checked) => void handleConsentChange({ dataProcessingConsent: checked })}
                    />
                  </div>
                  <Separator />
                  <div className="flex items-center justify-between">
                    <div>
                      <Label htmlFor="consentMarketing">{t('nav:marketingEmails')}</Label>
                      <p className="text-xs text-muted-foreground">{t('nav:optionalProductRaceUpdates')}</p>
                    </div>
                    <Switch
                      id="consentMarketing"
                      checked={consent?.marketingConsent ?? false}
                      disabled={consentSaving || !consent}
                      onCheckedChange={(checked) => void handleConsentChange({ marketingConsent: checked })}
                    />
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card className="border-destructive/50">
              <CardHeader>
                <CardTitle className="text-destructive">{t('nav:dangerZone')}</CardTitle>
                <CardDescription>{t('nav:irreversibleActions')}</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">{t('nav:deleteAccount')}</p>
                    <p className="text-xs text-muted-foreground">
                      {t('nav:deleteAccountDescription')}
                    </p>
                  </div>
                  <Button
                    variant="destructive"
                    onClick={() => setDeleteDialogOpen(true)}
                    disabled={Boolean(deletion?.deletionRequested)}
                  >
                    {deletion?.deletionRequested ? t('nav:deletionScheduled') : t('nav:deleteAccount')}
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>
      </Tabs>

      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('nav:deleteYourAccount')}</DialogTitle>
            <DialogDescription>{t('nav:deleteAccountDialogDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="delete-password">{t('nav:confirmYourPassword')}</Label>
              <Input
                id="delete-password"
                type="password"
                value={deletePassword}
                onChange={(e) => setDeletePassword(e.target.value)}
                autoComplete="current-password"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="delete-confirmation">{t('nav:typeDeleteConfirmation', { token: 'DELETE' })}</Label>
              <Input
                id="delete-confirmation"
                value={deleteConfirmation}
                onChange={(e) => setDeleteConfirmation(e.target.value)}
                placeholder={t('nav:deleteToken')}
              />
            </div>
          </div>
          <DialogFooter>
              <Button variant="outline" onClick={() => setDeleteDialogOpen(false)} disabled={deleteBusy}>
              {t('common:actions.cancel')}
            </Button>
            <Button variant="destructive" onClick={() => void handleDeleteAccount()} disabled={deleteBusy}>
              {deleteBusy ? t('nav:scheduling') : t('nav:deleteMyAccount')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
