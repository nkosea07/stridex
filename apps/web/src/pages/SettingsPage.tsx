import { useEffect, useState } from 'react'
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
import { useAuth } from '@/contexts/AuthContext'
import { useTheme } from '@/contexts/ThemeContext'
import { authApi, notificationsApi } from '@/lib/api'
import { enableWebPush, isWebPushConfigured } from '@/lib/push'
import type { ConsentState, DeletionStatus, NotificationPreference } from '@/types'

export function SettingsPage() {
  const { user, updateUser } = useAuth()
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
  const [notifications, setNotifications] = useState<NotificationPreference>({
    emailEnabled: true,
    pushEnabled: true,
    smsEnabled: true,
    whatsappEnabled: true,
  })
  const [notificationPrefsSaving, setNotificationPrefsSaving] = useState(false)

  const handleSaveProfile = async () => {
    setProfileSaving(true)
    try {
      const updatedUser = await authApi.updateProfile({
        firstName: profile.firstName.trim(),
        lastName: profile.lastName.trim(),
        phone: profile.phone.trim() || undefined,
      })
      updateUser(updatedUser)
      setProfile((current) => ({ ...current, email: updatedUser.email, phone: updatedUser.phone || '' }))
      toast.success('Profile updated successfully!')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Unable to update your profile')
    } finally {
      setProfileSaving(false)
    }
  }

  const handleEnablePush = async () => {
    setPushBusy(true)
    try {
      const enabled = await enableWebPush()
      if (enabled) {
        setPushRegistered(true)
        toast.success('Browser push notifications enabled')
      } else {
        toast.error('Push notifications are not available in this browser')
      }
    } catch {
      toast.error('Failed to enable push notifications')
    } finally {
      setPushBusy(false)
    }
  }

  const handleChangePassword = async () => {
    if (!user?.email) return
    setPasswordResetSending(true)
    try {
      await authApi.forgotPassword(user.email)
      toast.success('If the email exists, password reset instructions have been sent.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Unable to start password reset')
    } finally {
      setPasswordResetSending(false)
    }
  }

  const handleDeleteAccount = async () => {
    if (deleteConfirmation.trim().toUpperCase() !== 'DELETE') {
      toast.error('Type DELETE to confirm.')
      return
    }
    setDeleteBusy(true)
    try {
      await authApi.deleteAccount(deletePassword, deleteConfirmation.trim())
      setDeleteDialogOpen(false)
      toast.success('Your account is scheduled for deletion and you have been signed out.')
      authApi.logout()
      navigate('/login', { replace: true })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Unable to delete account')
    } finally {
      setDeleteBusy(false)
    }
  }

  useEffect(() => {
    let active = true
    notificationsApi
      .preferences()
      .then((prefs) => {
        if (active) setNotifications(prefs)
      })
      .catch(() => {
        // Keep defaults if preferences cannot be loaded.
      })
    authApi
      .consent()
      .then((value) => {
        if (active) setConsent(value)
      })
      .catch(() => {
        // Consent is optional to display.
      })
    authApi
      .deletionStatus()
      .then((value) => {
        if (active) setDeletion(value)
      })
      .catch(() => {
        // Deletion status is optional to display.
      })
    return () => {
      active = false
    }
  }, [])

  const handleExportData = async () => {
    setExporting(true)
    try {
      const data = await authApi.exportData()
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'race-tracker-data.json'
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
      toast.success('Your data export has been downloaded.')
    } catch {
      toast.error('Failed to export your data')
    } finally {
      setExporting(false)
    }
  }

  const handleConsentChange = async (patch: { dataProcessingConsent?: boolean; marketingConsent?: boolean }) => {
    setConsentSaving(true)
    try {
      setConsent(await authApi.updateConsent(patch))
      toast.success('Consent updated.')
    } catch {
      toast.error('Failed to update consent')
    } finally {
      setConsentSaving(false)
    }
  }

  const handleCancelDeletion = async () => {
    setDeletionBusy(true)
    try {
      setDeletion(await authApi.cancelDeletion())
      toast.success('Account deletion cancelled.')
    } catch {
      toast.error('Failed to cancel deletion')
    } finally {
      setDeletionBusy(false)
    }
  }

  const handleSaveNotificationPreferences = async () => {
    setNotificationPrefsSaving(true)
    try {
      const saved = await notificationsApi.updatePreferences(notifications)
      setNotifications(saved)
      toast.success('Notification preferences saved.')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Unable to save preferences')
    } finally {
      setNotificationPrefsSaving(false)
    }
  }

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Settings</h1>
        <p className="text-muted-foreground">Manage your account and preferences</p>
      </div>

      <Tabs defaultValue="profile" className="space-y-6">
        <TabsList>
          <TabsTrigger value="profile" className="gap-2">
            <User className="h-4 w-4" />
            Profile
          </TabsTrigger>
          <TabsTrigger value="notifications" className="gap-2">
            <Bell className="h-4 w-4" />
            Notifications
          </TabsTrigger>
          <TabsTrigger value="preferences" className="gap-2">
            <Globe className="h-4 w-4" />
            Preferences
          </TabsTrigger>
          <TabsTrigger value="security" className="gap-2">
            <Shield className="h-4 w-4" />
            Security
          </TabsTrigger>
        </TabsList>

        {/* Profile Tab */}
        <TabsContent value="profile">
          <Card>
            <CardHeader>
              <CardTitle>Profile Information</CardTitle>
              <CardDescription>Update your personal details</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="firstName">First Name</Label>
                  <Input
                    id="firstName"
                    value={profile.firstName}
                    onChange={(e) => setProfile({ ...profile, firstName: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="lastName">Last Name</Label>
                  <Input
                    id="lastName"
                    value={profile.lastName}
                    onChange={(e) => setProfile({ ...profile, lastName: e.target.value })}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  value={profile.email}
                  readOnly
                  disabled
                />
                <p className="text-xs text-muted-foreground">Email changes require account support.</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="phone">Phone</Label>
                <Input
                  id="phone"
                  value={profile.phone}
                  onChange={(e) => setProfile({ ...profile, phone: e.target.value })}
                  placeholder="+27 82 123 4567"
                />
              </div>
              <div className="pt-4">
                <Button onClick={handleSaveProfile} disabled={profileSaving}>
                  <Save className="mr-2 h-4 w-4" />
                  {profileSaving ? 'Saving…' : 'Save Changes'}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Notifications Tab */}
        <TabsContent value="notifications">
          <Card>
            <CardHeader>
              <CardTitle>Notification Preferences</CardTitle>
              <CardDescription>Choose which channels we may use to reach you.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <Label htmlFor="prefEmail">Email</Label>
                    <p className="text-xs text-muted-foreground">Transactional and race emails</p>
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
                    <Label htmlFor="prefPush">Push</Label>
                    <p className="text-xs text-muted-foreground">App and browser push notifications</p>
                  </div>
                  <Switch
                    id="prefPush"
                    checked={notifications.pushEnabled}
                    onCheckedChange={(checked) => setNotifications({ ...notifications, pushEnabled: checked })}
                  />
                </div>
                <Separator />
                <div className="flex items-center justify-between">
                  <div>
                    <Label htmlFor="prefSms">SMS</Label>
                    <p className="text-xs text-muted-foreground">Text-message alerts (when configured)</p>
                  </div>
                  <Switch
                    id="prefSms"
                    checked={notifications.smsEnabled}
                    onCheckedChange={(checked) => setNotifications({ ...notifications, smsEnabled: checked })}
                  />
                </div>
                <Separator />
                <div className="flex items-center justify-between">
                  <div>
                    <Label htmlFor="prefWhatsapp">WhatsApp</Label>
                    <p className="text-xs text-muted-foreground">WhatsApp alerts (when configured)</p>
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
                  Browser Push
                </h3>
                <div className="flex items-center justify-between">
                  <div>
                    <Label>Enable this browser</Label>
                    <p className="text-xs text-muted-foreground">
                      {isWebPushConfigured()
                        ? 'Receive real-time race and checkpoint alerts in this browser.'
                        : 'Push is not configured for this deployment.'}
                    </p>
                  </div>
                  <Button
                    onClick={() => void handleEnablePush()}
                    disabled={pushBusy || pushRegistered || !isWebPushConfigured()}
                  >
                    {pushRegistered ? 'Enabled' : pushBusy ? 'Enabling…' : 'Enable'}
                  </Button>
                </div>
              </div>

              <div className="pt-4">
                <Button onClick={() => void handleSaveNotificationPreferences()} disabled={notificationPrefsSaving}>
                  <Save className="mr-2 h-4 w-4" />
                  {notificationPrefsSaving ? 'Saving…' : 'Save Preferences'}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Preferences Tab */}
        <TabsContent value="preferences">
          <Card>
            <CardHeader>
              <CardTitle>Regional Preferences</CardTitle>
              <CardDescription>Appearance and regional display settings.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="theme">Theme</Label>
                <Select value={theme} onValueChange={(value) => setTheme(value as 'light' | 'dark' | 'system')}>
                  <SelectTrigger id="theme">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="light">Light</SelectItem>
                    <SelectItem value="dark">Dark</SelectItem>
                    <SelectItem value="system">System</SelectItem>
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  Active theme: {resolvedTheme}. The theme updates immediately.
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
                Timezone, date format, and units follow your device and the race locale.
              </p>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Security Tab */}
        <TabsContent value="security">
          <div className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>Change Password</CardTitle>
                <CardDescription>Update your account password</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-sm text-muted-foreground">
                  Password changes use a secure email reset flow. We never expose or store your current password in this form.
                </p>
                <div className="pt-4">
                  <Button onClick={handleChangePassword} disabled={passwordResetSending || !user?.email}>
                    <Key className="mr-2 h-4 w-4" />
                    {passwordResetSending ? 'Sending…' : 'Send Password Reset Email'}
                  </Button>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Two-Factor Authentication</CardTitle>
                <CardDescription>Add an extra layer of security to your account</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground">
                  Multi-factor authentication is required for privileged roles and is set up during sign-in. If your
                  role requires it, you will be prompted to enrol when you next sign in.
                </p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Privacy</CardTitle>
                <CardDescription>How we handle your personal data</CardDescription>
              </CardHeader>
              <CardContent className="space-y-5">
                <p className="text-sm text-muted-foreground">
                  Review what we collect, how it is used, and your rights, including data export and account deletion.
                </p>

                {deletion?.deletionRequested && (
                  <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4">
                    <p className="text-sm font-medium text-destructive">Account deletion scheduled</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Your account is deactivated
                      {deletion.scheduledFor ? ` and scheduled for erasure on ${new Date(deletion.scheduledFor).toLocaleDateString()}` : ''}.
                    </p>
                    <Button
                      variant="outline"
                      size="sm"
                      className="mt-3"
                      onClick={() => void handleCancelDeletion()}
                      disabled={deletionBusy}
                    >
                      {deletionBusy ? 'Cancelling…' : 'Cancel deletion'}
                    </Button>
                  </div>
                )}

                <div className="flex flex-wrap items-center gap-3">
                  <Button asChild variant="outline">
                    <Link to={API_CONTRACT.legal?.privacyPolicyPath ?? '/privacy'} target="_blank" rel="noreferrer">
                      <Shield className="mr-2 h-4 w-4" />
                      Read the Privacy Policy
                    </Link>
                  </Button>
                  <Button variant="outline" onClick={() => void handleExportData()} disabled={exporting}>
                    <Globe className="mr-2 h-4 w-4" />
                    {exporting ? 'Preparing…' : 'Download my data'}
                  </Button>
                </div>

                <div className="space-y-4 rounded-lg border border-border p-4">
                  <p className="text-sm font-medium">Consent</p>
                  <div className="flex items-center justify-between">
                    <div>
                      <Label htmlFor="consentProcessing">Data processing</Label>
                      <p className="text-xs text-muted-foreground">
                        Required to use the platform{consent?.dataProcessingConsentAt
                          ? ` (granted ${new Date(consent.dataProcessingConsentAt).toLocaleDateString()})`
                          : ''}.
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
                      <Label htmlFor="consentMarketing">Marketing emails</Label>
                      <p className="text-xs text-muted-foreground">Optional product and race updates.</p>
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
                <CardTitle className="text-destructive">Danger Zone</CardTitle>
                <CardDescription>Irreversible actions</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">Delete Account</p>
                    <p className="text-xs text-muted-foreground">
                      Deactivates your account immediately and erases your personal data after a grace period.
                      You can cancel during the grace period by signing back in.
                    </p>
                  </div>
                  <Button
                    variant="destructive"
                    onClick={() => setDeleteDialogOpen(true)}
                    disabled={Boolean(deletion?.deletionRequested)}
                  >
                    {deletion?.deletionRequested ? 'Deletion scheduled' : 'Delete Account'}
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
            <DialogTitle>Delete your account</DialogTitle>
            <DialogDescription>
              This deactivates your account and schedules your personal data for erasure after a grace period.
              Export any data you need first.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="delete-password">Confirm your password</Label>
              <Input
                id="delete-password"
                type="password"
                value={deletePassword}
                onChange={(e) => setDeletePassword(e.target.value)}
                autoComplete="current-password"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="delete-confirmation">Type DELETE to confirm</Label>
              <Input
                id="delete-confirmation"
                value={deleteConfirmation}
                onChange={(e) => setDeleteConfirmation(e.target.value)}
                placeholder="DELETE"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)} disabled={deleteBusy}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={() => void handleDeleteAccount()} disabled={deleteBusy}>
              {deleteBusy ? 'Scheduling…' : 'Delete my account'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
