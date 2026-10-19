import { createContext, useContext, useState, useEffect, useRef, type ReactNode } from 'react'
import type { User, UserCapability, UserRole } from '@/types'
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
  hasCapability: (capability: UserCapability) => boolean
  setPreferredCapability: (capability: UserCapability) => Promise<User>
  getSessionGeneration: () => number
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)
export const ADMIN_ROLES: UserRole[] = ['SUPER_ADMIN', 'RACE_ADMIN']
export const OPERATIONS_ROLES: UserRole[] = [...ADMIN_ROLES, 'OFFICIAL', 'VOLUNTEER']
export const PERSONAL_ROLES: UserRole[] = ['ATHLETE', 'SPECTATOR']
export const PERSONAL_CAPABILITIES: UserCapability[] = ['ATHLETE', 'SPECTATOR']

export function isAdminRole(role?: UserRole) {
  return !!role && ADMIN_ROLES.includes(role)
}

export function isOperationsRole(role?: UserRole) {
  return !!role && OPERATIONS_ROLES.includes(role)
}

export function isPersonalRole(role?: UserRole) {
  return !!role && PERSONAL_ROLES.includes(role)
}

export function getUserCapabilities(user?: Pick<User, 'role' | 'capabilities'> | null): UserCapability[] {
  if (!user) return []
  if (Array.isArray(user.capabilities)) return user.capabilities
  return isPersonalRole(user.role) ? [user.role as UserCapability] : []
}

export function hasUserCapability(user: Pick<User, 'role' | 'capabilities'> | null | undefined, capability: UserCapability) {
  return getUserCapabilities(user).includes(capability)
}

export function getPreferredCapability(user?: Pick<User, 'preferredCapability' | 'role' | 'capabilities'> | null) {
  if (!user) return null
  const capabilities = getUserCapabilities(user)
  return user.preferredCapability && capabilities.includes(user.preferredCapability)
    ? user.preferredCapability
    : capabilities[0] ?? null
}

export function isPersonalUser(user?: Pick<User, 'role' | 'capabilities'> | null) {
  return getUserCapabilities(user).length > 0
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const sessionGeneration = useRef(0)

  useEffect(() => {
    const generation = sessionGeneration.current
    localStorage.removeItem('auth_token')
    authApi.restoreBrowserSession()
      .then((restored) => restored ? authApi.me() : null)
      .then((currentUser) => {
        if (sessionGeneration.current === generation) setUser(currentUser)
      })
      .catch(() => {
        if (sessionGeneration.current !== generation) return
        authApi.setAccessToken(null)
        setUser(null)
      })
      .finally(() => {
        if (sessionGeneration.current === generation) setIsLoading(false)
      })
  }, [])

  const login = async (email: string, password: string) => {
    const generation = ++sessionGeneration.current
    try {
      const response = await authApi.login(email, password)
      if (sessionGeneration.current !== generation) {
        throw new Error('Authentication request superseded')
      }
      if (response.token) {
        return { kind: 'authenticated' as const, user: completeSession(response) }
      }
      throw new Error(response.requiresEmailVerification ? 'Email verification required' : 'Login did not return a session')
    } finally {
      if (sessionGeneration.current === generation) setIsLoading(false)
    }
  }

  const completeSession = (response: BrowserAuthResponse) => {
    if (!response.token || !response.user) throw new Error('Authentication response did not contain a session')
    sessionGeneration.current += 1
    authApi.setAccessToken(response.token)
    setUser(response.user)
    setIsLoading(false)
    return response.user
  }

  const logout = () => {
    sessionGeneration.current += 1
    void authApi.logout()
    setUser(null)
    setIsLoading(false)
  }

  const updateUser = (updatedUser: User) => setUser(updatedUser)

  const hasCapability = (capability: UserCapability) => hasUserCapability(user, capability)
  const getSessionGeneration = () => sessionGeneration.current

  const setPreferredCapability = async (capability: UserCapability) => {
    if (!hasUserCapability(user, capability)) {
      throw new Error('Capability is not enabled for this account')
    }
    const generation = sessionGeneration.current
    const updatedUser = await authApi.updateCapabilities({
      capabilities: getUserCapabilities(user),
      preferredCapability: capability,
    })
    if (sessionGeneration.current !== generation) {
      throw new Error('Authentication request superseded')
    }
    setUser(updatedUser)
    return updatedUser
  }

  return (
    <AuthContext.Provider value={{
      user,
      isLoading,
      isAuthenticated: !!user,
      login,
      completeSession,
      logout,
      updateUser,
      hasCapability,
      setPreferredCapability,
      getSessionGeneration,
    }}>
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
