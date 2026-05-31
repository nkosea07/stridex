import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { BarChart3, Home, LogOut } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useAuth } from '@/contexts/AuthContext'
import { ThemeSelect } from '@/components/theme/ThemeSelect'

const navItems = [
  { to: '/app', label: 'Dashboard', icon: Home, end: true },
  { to: '/app/stats', label: 'Stats', icon: BarChart3 },
]

export function UserLayout() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <div>
            <p className="text-sm text-muted-foreground">Signed in as</p>
            <p className="font-medium">{user?.firstName} {user?.lastName}</p>
          </div>
          <div className="flex items-center gap-2">
            <ThemeSelect className="h-8 w-[140px]" />
            <Button variant="outline" size="sm" onClick={handleLogout}>
              <LogOut className="mr-2 h-4 w-4" />
              Logout
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
              {item.label}
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
