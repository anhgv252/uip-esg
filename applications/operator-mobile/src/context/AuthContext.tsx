import { createContext, useContext, type ReactNode } from 'react'
import { useAuthMobile } from '../hooks/useAuthMobile'

interface AuthContextValue {
  token: string | null
  selectedTenant: string | null
  isLoading: boolean
  error: string | null
  isAuthenticated: boolean
  login: () => Promise<void>
  logout: () => Promise<void>
  selectTenant: (tenantId: string) => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const auth = useAuthMobile()
  return <AuthContext.Provider value={auth}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
