import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import type { User, UserRole } from '@/types'
import { authApi, type BrowserAuthResponse } from '@/lib/api'

export type LoginResult = { kind: 'authenticated'; user: User }

interface AuthContextType {
  user: User | null
  isLoading: boolean
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<LoginResult>
  completeSession: (response: BrowserAuthResponse) => User
  logout: () => void
  updateUser: (user: User) => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)
export const ADMIN_ROLES: UserRole[] = ['SUPER_ADMIN', 'RACE_ADMIN']
export const OPERATIONS_ROLES: UserRole[] = [...ADMIN_ROLES, 'OFFICIAL', 'VOLUNTEER']
export const PERSONAL_ROLES: UserRole[] = ['ATHLETE', 'SPECTATOR']

export function isAdminRole(role?: UserRole) {
  return !!role && ADMIN_ROLES.includes(role)
}

export function isOperationsRole(role?: UserRole) {
  return !!role && OPERATIONS_ROLES.includes(role)
}

export function isPersonalRole(role?: UserRole) {
  return !!role && PERSONAL_ROLES.includes(role)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    localStorage.removeItem('auth_token')
    authApi.restoreBrowserSession()
      .then((restored) => restored ? authApi.me() : null)
      .then((currentUser) => setUser(currentUser))
      .catch(() => {
        authApi.setAccessToken(null)
        setUser(null)
      })
      .finally(() => setIsLoading(false))
  }, [])

  const login = async (email: string, password: string) => {
    const response = await authApi.login(email, password)
    if (response.token) {
      return { kind: 'authenticated' as const, user: completeSession(response) }
    }
    throw new Error(response.requiresEmailVerification ? 'Email verification required' : 'Login did not return a session')
  }

  const completeSession = (response: BrowserAuthResponse) => {
    if (!response.token || !response.user) throw new Error('Authentication response did not contain a session')
    authApi.setAccessToken(response.token)
    setUser(response.user)
    return response.user
  }

  const logout = () => {
    void authApi.logout()
    setUser(null)
  }

  const updateUser = (updatedUser: User) => setUser(updatedUser)

  return (
    <AuthContext.Provider value={{ user, isLoading, isAuthenticated: !!user, login, completeSession, logout, updateUser }}>
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

export function useAuthOptional() {
  return useContext(AuthContext) ?? null
}
