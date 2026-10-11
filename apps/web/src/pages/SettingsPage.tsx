import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { API_CONTRACT } from '@race-tracker/contracts'
import { 
  User, 
  Shield, 
  Bell, 
  Mail,
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
import { authApi } from '@/lib/api'
import { enableWebPush, isWebPushConfigured } from '@/lib/push'

export function SettingsPage() {
  const { user, updateUser } = useAuth()
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
  const [profile, setProfile] = useState({
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    email: user?.email || '',
    phone: ''
  })
  const [notifications, setNotifications] = useState({
    emailRaceUpdates: true,
    emailParticipantRegistration: true,
    emailCheckpointAlerts: false,
    pushNotifications: true
  })
  const [preferences, setPreferences] = useState({
    timezone: 'Africa/Johannesburg',
    dateFormat: 'DD/MM/YYYY',
    distanceUnit: 'km'
  })

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
              <CardDescription>Notification preferences are planned for a later release.</CardDescription>
              <p className="text-xs font-medium text-amber-600">Coming soon — these controls are not saved yet.</p>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="space-y-4">
                <h3 className="text-sm font-medium flex items-center gap-2">
                  <Mail className="h-4 w-4" />
                  Email Notifications
                </h3>
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <Label htmlFor="emailRaceUpdates">Race Updates</Label>
                      <p className="text-xs text-muted-foreground">Get notified about race status changes</p>
                    </div>
                    <Switch
                      id="emailRaceUpdates"
                      checked={notifications.emailRaceUpdates}
                      disabled
                      onCheckedChange={(checked) => setNotifications({ ...notifications, emailRaceUpdates: checked })}
                    />
                  </div>
                  <Separator />
                  <div className="flex items-center justify-between">
                    <div>
                      <Label htmlFor="emailParticipantRegistration">New Registrations</Label>
                      <p className="text-xs text-muted-foreground">Notify when participants register</p>
                    </div>
                    <Switch
                      id="emailParticipantRegistration"
                      checked={notifications.emailParticipantRegistration}
                      disabled
                      onCheckedChange={(checked) => setNotifications({ ...notifications, emailParticipantRegistration: checked })}
                    />
                  </div>
                  <Separator />
                  <div className="flex items-center justify-between">
                    <div>
                      <Label htmlFor="emailCheckpointAlerts">Checkpoint Alerts</Label>
                      <p className="text-xs text-muted-foreground">Receive alerts for checkpoint issues</p>
                    </div>
                    <Switch
                      id="emailCheckpointAlerts"
                      checked={notifications.emailCheckpointAlerts}
                      disabled
                      onCheckedChange={(checked) => setNotifications({ ...notifications, emailCheckpointAlerts: checked })}
                    />
                  </div>
                </div>
              </div>

              <Separator />

              <div className="space-y-4">
                <h3 className="text-sm font-medium flex items-center gap-2">
                  <Bell className="h-4 w-4" />
                  Push Notifications
                </h3>
                <div className="flex items-center justify-between">
                  <div>
                    <Label>Browser Push Notifications</Label>
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
                <Button disabled>
                  <Save className="mr-2 h-4 w-4" />
                  Coming soon
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
              <CardDescription>Theme changes apply immediately. Regional preferences are planned for a later release.</CardDescription>
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
                <Label htmlFor="timezone">Timezone</Label>
                <Select 
                  value={preferences.timezone}
                  disabled
                  onValueChange={(value) => setPreferences({ ...preferences, timezone: value })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="Africa/Johannesburg">Africa/Johannesburg (SAST)</SelectItem>
                    <SelectItem value="Africa/Harare">Africa/Harare (CAT)</SelectItem>
                    <SelectItem value="Europe/London">Europe/London (GMT)</SelectItem>
                    <SelectItem value="America/New_York">America/New_York (EST)</SelectItem>
                    <SelectItem value="Asia/Tokyo">Asia/Tokyo (JST)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="dateFormat">Date Format</Label>
                <Select 
                  value={preferences.dateFormat}
                  disabled
                  onValueChange={(value) => setPreferences({ ...preferences, dateFormat: value })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="DD/MM/YYYY">DD/MM/YYYY (30/03/2024)</SelectItem>
                    <SelectItem value="MM/DD/YYYY">MM/DD/YYYY (03/30/2024)</SelectItem>
                    <SelectItem value="YYYY-MM-DD">YYYY-MM-DD (2024-03-30)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="distanceUnit">Distance Unit</Label>
                <Select 
                  value={preferences.distanceUnit}
                  disabled
                  onValueChange={(value) => setPreferences({ ...preferences, distanceUnit: value })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="km">Kilometers (km)</SelectItem>
                    <SelectItem value="mi">Miles (mi)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="pt-4">
                <Button disabled>
                  <Save className="mr-2 h-4 w-4" />
                  Coming soon
                </Button>
              </div>
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
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">2FA Status</p>
                    <p className="text-xs text-muted-foreground">MFA enrollment is planned for the privileged-security release.</p>
                  </div>
                  <Button variant="outline" disabled>Coming soon</Button>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Privacy</CardTitle>
                <CardDescription>How we handle your personal data</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <p className="text-sm text-muted-foreground">
                  Review what we collect, how it is used, and your rights, including data export and account deletion.
                </p>
                <Button asChild variant="outline">
                  <Link to={API_CONTRACT.legal?.privacyPolicyPath ?? '/privacy'} target="_blank" rel="noreferrer">
                    <Shield className="mr-2 h-4 w-4" />
                    Read the Privacy Policy
                  </Link>
                </Button>
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
                  <Button variant="destructive" onClick={() => setDeleteDialogOpen(true)}>Delete Account</Button>
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
