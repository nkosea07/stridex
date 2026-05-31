import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Flag, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { isAdminRole, useAuth } from '@/contexts/AuthContext'
import { toast } from 'sonner'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const locationState = location.state as { from?: string; reason?: string } | null
  const unauthorizedReason = locationState?.reason === 'unauthorized'
  const returnTo = typeof locationState?.from === 'string' && locationState.from.startsWith('/') && !locationState.from.startsWith('//')
    ? locationState.from
    : undefined

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsLoading(true)
    
    try {
      const user = await login(email, password)
      navigate(returnTo ?? (isAdminRole(user.role) ? '/admin' : '/app'))
      toast.success('Welcome back!')
    } catch {
      toast.error('Invalid credentials. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }

  // Demo login for testing
  const handleDemoLogin = () => {
    setEmail('admin@racetracker.com')
    setPassword('admin123')
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <div className="w-full max-w-md space-y-8">
        {/* Logo */}
        <div className="flex flex-col items-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary">
            <Flag className="h-6 w-6 text-primary-foreground" />
          </div>
          <h1 className="mt-4 text-2xl font-bold text-foreground">Race Tracker</h1>
          <p className="mt-1 text-sm text-muted-foreground">Professional Race Management Platform</p>
        </div>

        {/* Login Card */}
        <Card className="border-border bg-card">
          <CardHeader className="space-y-1">
            <CardTitle className="text-xl">Sign in</CardTitle>
            <CardDescription>
              Enter your credentials to access the admin dashboard
            </CardDescription>
            {unauthorizedReason && (
              <p className="text-sm text-destructive">Unauthorized account. Please sign in with an admin user.</p>
            )}
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  placeholder="admin@racetracker.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  className="bg-muted/50"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  type="password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  className="bg-muted/50"
                />
              </div>
              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Signing in...
                  </>
                ) : (
                  'Sign in'
                )}
              </Button>
            </form>

            <p className="mt-4 text-center text-sm text-muted-foreground">
              New user?{' '}
              <Link
                to="/register"
                state={returnTo ? { from: returnTo } : undefined}
                className="text-primary underline-offset-4 hover:underline"
              >
                Create account
              </Link>
            </p>

            {/* Demo credentials hint */}
            <div className="mt-6 rounded-lg border border-dashed border-border p-4">
              <p className="text-xs text-muted-foreground mb-2">Demo credentials:</p>
              <div className="text-xs font-mono text-muted-foreground space-y-1">
                <p>Email: admin@racetracker.com</p>
                <p>Password: admin123</p>
              </div>
              <Button 
                variant="outline" 
                size="sm" 
                className="mt-3 w-full"
                onClick={handleDemoLogin}
              >
                Use Demo Credentials
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Footer */}
        <p className="text-center text-xs text-muted-foreground">
          Secure login powered by JWT authentication
        </p>
      </div>
    </div>
  )
}
