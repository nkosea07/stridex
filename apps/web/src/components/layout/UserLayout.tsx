import { useEffect } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { BarChart3, Bell, Home, LogOut, Settings, Heart } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { CapabilityModeSwitcher } from '@/components/auth/CapabilityModeSwitcher'
import { getPreferredCapability, useAuth } from '@/contexts/AuthContext'
import { connectUserAlerts } from '@/lib/api'
import { toast } from 'sonner'
import { ThemeSelect } from '@/components/theme/ThemeSelect'

export function UserLayout() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const activeCapability = getPreferredCapability(user)
  const isSpectator = activeCapability === 'SPECTATOR'
  const navItems = [
    { to: '/app', labelKey: isSpectator ? 'nav:spectatorDashboard' : 'nav:myDashboard', icon: Home, end: true },
    { to: '/app/stats', labelKey: isSpectator ? 'nav:following' : 'nav:myPerformance', icon: isSpectator ? Heart : BarChart3 },
    { to: '/notifications', labelKey: 'nav:notifications', icon: Bell },
    { to: '/app/settings', labelKey: 'nav:accountSettings', icon: Settings },
  ]

  useEffect(() => {
    if (!user?.id) return
    const disconnect = connectUserAlerts(user.id, (alert) => {
      toast(alert.title, { description: alert.message })
    })
    return () => disconnect()
  }, [user?.id])

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <div>
            <p className="text-sm text-muted-foreground">{t('nav:signedInAs')}</p>
            <p className="font-medium">{user?.firstName} {user?.lastName}</p>
          </div>
          <div className="flex items-center gap-2">
            <CapabilityModeSwitcher compact />
            <ThemeSelect className="h-8 w-[140px]" />
            <Button variant="outline" size="sm" onClick={handleLogout}>
              <LogOut className="mr-2 h-4 w-4" />
              {t('nav:logout')}
            </Button>
          </div>
        </div>
      </header>

      <div className="mx-auto grid max-w-5xl gap-6 px-4 py-6 md:grid-cols-[220px_1fr]">
        <aside className="space-y-2">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2 rounded-lg border px-3 py-2 text-sm transition-colors',
                  isActive ? 'border-primary bg-primary/10 text-primary' : 'border-border hover:bg-muted/40',
                )
              }
            >
              <item.icon className="h-4 w-4" />
              {t(item.labelKey)}
            </NavLink>
          ))}
        </aside>
        <main>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
