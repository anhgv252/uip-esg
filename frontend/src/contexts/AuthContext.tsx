import React, {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { tokenStore, tenantStore } from '@/api/client'
import { authApi, type LoginRequest } from '@/api/auth'

export type UserRole = 'ROLE_ADMIN' | 'ROLE_OPERATOR' | 'ROLE_CITIZEN' | 'ROLE_TENANT_ADMIN'

export interface AuthUser {
  username: string
  role: UserRole
  tenantId: string
  tenantPath: string
  scopes: string[]
  allowedBuildings: string[]
}

export interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (req: LoginRequest) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

function parseJwtPayload(token: string): Record<string, unknown> {
  try {
    const base64Url = token.split('.')[1]
    // JWT uses base64url (no padding). atob() requires standard base64 with padding.
    // Convert: replace url-safe chars, then pad to a multiple of 4.
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '==='.slice(0, (4 - (base64.length % 4)) % 4)
    const json = atob(padded)
    return JSON.parse(json) as Record<string, unknown>
  } catch {
    return {}
  }
}

function userFromToken(token: string): AuthUser | null {
  const payload = parseJwtPayload(token)
  if (!payload.sub || !payload.roles) return null
  const roles = Array.isArray(payload.roles)
    ? (payload.roles as string[])
    : [String(payload.roles)]
  return {
    username: String(payload.sub),
    role: (roles[0] as UserRole) ?? 'ROLE_CITIZEN',
    tenantId: String(payload.tenant_id ?? 'default'),
    tenantPath: String(payload.tenant_path ?? 'city'),
    scopes: Array.isArray(payload.scopes) ? (payload.scopes as string[]) : [],
    allowedBuildings: Array.isArray(payload.allowed_buildings)
      ? (payload.allowed_buildings as string[])
      : [],
  }
}

// Attempt a silent token refresh when app mounts (e.g. page reload)
async function trySilentRefresh(): Promise<AuthUser | null> {
  try {
    const response = await authApi.refresh()
    tokenStore.set(response.accessToken)
    tokenStore.setRefresh(response.refreshToken)
    return userFromToken(response.accessToken)
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const refreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const scheduleRefresh = useCallback((expiresIn: number) => {
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current)
    // Refresh 60 s before expiry, min 5 s
    const delay = Math.max((expiresIn - 60) * 1000, 5_000)
    refreshTimerRef.current = setTimeout(async () => {
      try {
        const response = await authApi.refresh()
        tokenStore.set(response.accessToken)
        tokenStore.setRefresh(response.refreshToken)
        setUser(userFromToken(response.accessToken))
        scheduleRefresh(response.expiresIn)
      } catch {
        tokenStore.clear()
        setUser(null)
      }
    }, delay)
  }, [])

  // On mount: attempt silent refresh via httpOnly cookie
  useEffect(() => {
    trySilentRefresh().then((u) => {
      setUser(u)
      setIsLoading(false)
    })

    return () => {
      if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Sync tenantId to tenantStore for API interceptor
  useEffect(() => {
    tenantStore.set(user?.tenantId ?? null)
  }, [user?.tenantId])

  const login = useCallback(
    async (req: LoginRequest) => {
      const response = await authApi.login(req)
      tokenStore.set(response.accessToken)
      tokenStore.setRefresh(response.refreshToken)
      const u = userFromToken(response.accessToken)
      setUser(u)
      scheduleRefresh(response.expiresIn)
    },
    [scheduleRefresh],
  )

  const logout = useCallback(() => {
    authApi.logout()
    if (refreshTimerRef.current) clearTimeout(refreshTimerRef.current)
    setUser(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: user !== null, isLoading, login, logout }),
    [user, isLoading, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
