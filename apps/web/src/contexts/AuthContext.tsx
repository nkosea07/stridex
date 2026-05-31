import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import type { User, UserRole } from '@/types'
import { authApi } from '@/lib/api'

interface AuthContextType {
  user: User | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<User>
  logout: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)
export const ADMIN_ROLES: UserRole[] = ['SUPER_ADMIN', 'RACE_ADMIN']

export function isAdminRole(role?: UserRole) {
  return !!role && ADMIN_ROLES.includes(role)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('auth_token')
    if (token) {
      authApi.me()
        .then((currentUser) => setUser(currentUser))
        .catch(() => {
          localStorage.removeItem('auth_token')
          setUser(null)
        })
        .finally(() => setIsLoading(false))
    } else {
      setIsLoading(false)
    }
  }, [])

  const login = async (email: string, password: string) => {
    const { token, user: loggedInUser } = await authApi.login(email, password)
    localStorage.setItem('auth_token', token)
    setUser(loggedInUser)
    return loggedInUser
  }

  const logout = () => {
    authApi.logout()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, isLoading, isAuthenticated: !!user, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
