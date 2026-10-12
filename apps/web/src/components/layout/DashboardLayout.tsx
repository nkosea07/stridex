import { useState } from 'react'
import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom'
import { 
  LayoutDashboard, 
  Flag, 
  Users, 
  MapPin, 
  Wifi,
  Timer, 
  Trophy,
  ClipboardList,
  BarChart3,
  Layers,
  UsersRound,
  Image as ImageIcon,
  Tag,
  Bell,
  Sparkles,
  Settings,
  LogOut,
  Menu,
  X,
  ChevronRight
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { ThemeSelect } from '@/components/theme/ThemeSelect'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useAuth } from '@/contexts/AuthContext'
import { useTranslation } from 'react-i18next'

const mainNavItems = [
  { to: '/admin', icon: LayoutDashboard, labelKey: 'nav.dashboard', end: true },
  { to: '/admin/races', icon: Flag, labelKey: 'nav.races' },
]

const raceNavItems = [
  { to: 'participants', icon: Users, labelKey: 'nav.participants' },
  { to: 'registration', icon: ClipboardList, labelKey: 'nav.registration' },
  { to: 'categories', icon: Layers, labelKey: 'nav.categories' },
  { to: 'teams', icon: UsersRound, labelKey: 'nav.teams' },
  { to: 'live', icon: MapPin, labelKey: 'nav.liveTracking' },
  { to: 'checkpoints', icon: Timer, labelKey: 'nav.checkpoints' },
  { to: 'rfid', icon: Wifi, labelKey: 'nav.rfid' },
  { to: 'results', icon: Trophy, labelKey: 'nav.results' },
  { to: 'analytics', icon: BarChart3, labelKey: 'nav.analytics' },
  { to: 'insights', icon: Sparkles, labelKey: 'nav.insights' },
  { to: 'media', icon: ImageIcon, labelKey: 'nav.media' },
  { to: 'promos', icon: Tag, labelKey: 'nav.promoCodes' },
]

export function DashboardLayout() {
  const { t } = useTranslation()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  
  // Check if we're in a race detail context
  const raceMatch = location.pathname.match(/\/admin\/races\/([^/]+)/)
  const raceId = raceMatch?.[1]
  const isInRaceContext = raceId && raceId !== 'new'
  const visibleMainNavItems = user?.role === 'SUPER_ADMIN'
    ? [...mainNavItems, { to: '/admin/organizer-applications', icon: ClipboardList, labelKey: 'nav.organizerApplications' }]
    : mainNavItems

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="flex h-screen bg-background">
      {/* Mobile sidebar backdrop */}
      {sidebarOpen && (
        <div 
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside className={cn(
        "fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-sidebar border-r border-sidebar-border transition-transform lg:static lg:translate-x-0",
        sidebarOpen ? "translate-x-0" : "-translate-x-full"
      )}>
        {/* Logo */}
        <div className="flex h-16 items-center justify-between px-4 border-b border-sidebar-border">
          <NavLink to="/admin" className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
              <Flag className="h-4 w-4 text-primary-foreground" />
            </div>
            <span className="font-semibold text-sidebar-foreground">{t('common:appName')}</span>
          </NavLink>
          <Button 
            variant="ghost" 
            size="icon" 
            className="lg:hidden"
            onClick={() => setSidebarOpen(false)}
          >
            <X className="h-5 w-5" />
          </Button>
        </div>

        {/* Navigation */}
        <ScrollArea className="flex-1 px-3 py-4">
          <nav className="space-y-1">
            {visibleMainNavItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                  isActive 
                    ? "bg-sidebar-accent text-sidebar-primary" 
                    : "text-sidebar-foreground hover:bg-sidebar-accent/50"
                )}
              >
                <item.icon className="h-4 w-4" />
                {t(item.labelKey)}
              </NavLink>
            ))}
          </nav>

          {/* Race Context Navigation */}
          {isInRaceContext && (
            <>
              <Separator className="my-4" />
              <div className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {t('nav:currentRace')}
              </div>
              <nav className="space-y-1">
                <NavLink
                  to={`/admin/races/${raceId}`}
                  end
                  className={({ isActive }) => cn(
                    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                    isActive 
                      ? "bg-sidebar-accent text-sidebar-primary" 
                      : "text-sidebar-foreground hover:bg-sidebar-accent/50"
                  )}
                >
                  <Flag className="h-4 w-4" />
                  {t('nav:overview')}
                </NavLink>
                {raceNavItems.map((item) => (
                  <NavLink
                    key={item.to}
                    to={`/admin/races/${raceId}/${item.to}`}
                    className={({ isActive }) => cn(
                      "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                      isActive 
                        ? "bg-sidebar-accent text-sidebar-primary" 
                        : "text-sidebar-foreground hover:bg-sidebar-accent/50"
                    )}
                  >
                    <item.icon className="h-4 w-4" />
                    {t(item.labelKey)}
                  </NavLink>
                ))}
              </nav>
            </>
          )}
        </ScrollArea>

        {/* User menu */}
        <div className="border-t border-sidebar-border p-3">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="w-full justify-start gap-3 px-3">
                <Avatar className="h-8 w-8">
                  <AvatarFallback className="bg-primary/10 text-primary text-xs">
                    {user?.firstName?.[0]}{user?.lastName?.[0]}
                  </AvatarFallback>
                </Avatar>
                <div className="flex-1 text-left">
                  <p className="text-sm font-medium">{user?.firstName} {user?.lastName}</p>
                  <p className="text-xs text-muted-foreground capitalize">{user?.role?.toLowerCase().replace('_', ' ')}</p>
                </div>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <DropdownMenuItem onClick={() => navigate('/notifications')}>
                <Bell className="mr-2 h-4 w-4" />
                {t('nav:notifications')}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => navigate('/admin/settings')}>
                <Settings className="mr-2 h-4 w-4" />
                {t('nav:settings')}
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={handleLogout} className="text-destructive">
                <LogOut className="mr-2 h-4 w-4" />
                {t('nav:logout')}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Top bar */}
        <header className="flex h-16 items-center gap-4 border-b border-border bg-card px-4 lg:px-6">
          <Button 
            variant="ghost" 
            size="icon" 
            className="lg:hidden"
            onClick={() => setSidebarOpen(true)}
          >
            <Menu className="h-5 w-5" />
          </Button>
          
          <div className="flex-1" />

          <ThemeSelect className="h-8 w-[140px]" />
          
          {/* Live indicator when in race context */}
          {isInRaceContext && (
            <div className="flex items-center gap-2 text-sm">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-live opacity-75" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-live" />
              </span>
              <span className="text-muted-foreground">{t('nav:liveTrackingActive')}</span>
            </div>
          )}
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
