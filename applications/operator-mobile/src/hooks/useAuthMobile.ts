import { useState, useCallback, useEffect } from 'react'
import * as WebBrowser from 'expo-web-browser'
import * as AuthSession from 'expo-auth-session'
import Constants from 'expo-constants'
import { storageGet, storageSet, storageDelete } from '../storage/secureStorage'

WebBrowser.maybeCompleteAuthSession()

interface AuthState {
  token: string | null
  refreshToken: string | null
  selectedTenant: string | null
  isLoading: boolean
  error: string | null
}

const TOKEN_KEY = 'auth_token'
const REFRESH_TOKEN_KEY = 'auth_refresh_token'
const TENANT_KEY = 'selected_tenant'

export function useAuthMobile() {
  const [state, setState] = useState<AuthState>({
    token: null,
    refreshToken: null,
    selectedTenant: null,
    isLoading: true,
    error: null,
  })

  useEffect(() => {
    async function loadStoredState() {
      const [token, refreshToken, selectedTenant] = await Promise.all([
        storageGet(TOKEN_KEY),
        storageGet(REFRESH_TOKEN_KEY),
        storageGet(TENANT_KEY),
      ])
      setState({ token, refreshToken, selectedTenant, isLoading: false, error: null })
    }
    loadStoredState()
  }, [])

  const selectTenant = useCallback(async (tenantId: string) => {
    await storageSet(TENANT_KEY, tenantId)
    setState(prev => ({ ...prev, selectedTenant: tenantId }))
  }, [])

  const login = useCallback(async () => {
    setState(prev => ({ ...prev, isLoading: true, error: null }))

    const apiBase = Constants.expoConfig?.extra?.apiBaseUrl ?? 'http://localhost:8080'
    const tenantId = state.selectedTenant ?? await storageGet(TENANT_KEY)

    if (!tenantId) {
      setState(prev => ({ ...prev, isLoading: false, error: 'Please select a city before logging in' }))
      return
    }

    // Step 1 — fetch mobile auth config
    let config: { issuer: string; clientId: string; scopes: string; redirectUri: string }
    try {
      const res = await fetch(`${apiBase}/api/v1/mobile/auth/config?tenantId=${tenantId}`)
      if (!res.ok) {
        setState(prev => ({ ...prev, isLoading: false, error: `[step1] config HTTP ${res.status} from ${apiBase}` }))
        return
      }
      config = await res.json()
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e)
      setState(prev => ({ ...prev, isLoading: false, error: `[step1] ${msg} → ${apiBase}` }))
      return
    }

    // Step 2 — fetch OIDC discovery
    const discoveryUrl = `${config.issuer}/.well-known/openid-configuration`
    let discovery: Awaited<ReturnType<typeof AuthSession.fetchDiscoveryAsync>>
    try {
      discovery = await AuthSession.fetchDiscoveryAsync(discoveryUrl)
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e)
      setState(prev => ({ ...prev, isLoading: false, error: `[step2] ${msg} → ${discoveryUrl}` }))
      return
    }

    // Step 3 — PKCE prompt
    const redirectUrl = AuthSession.makeRedirectUri()
    const authRequest = new AuthSession.AuthRequest({
      clientId: config.clientId,
      scopes: config.scopes.split(' '),
      redirectUri: redirectUrl,
    })

    let result: Awaited<ReturnType<typeof authRequest.promptAsync>>
    try {
      result = await authRequest.promptAsync(discovery)
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e)
      setState(prev => ({ ...prev, isLoading: false, error: `[step3] ${msg}` }))
      return
    }

    // Step 4 — handle result
    if (result.type === 'success' && result.authentication) {
      const { accessToken, refreshToken } = result.authentication
      await storageSet(TOKEN_KEY, accessToken)
      if (refreshToken) {
        await storageSet(REFRESH_TOKEN_KEY, refreshToken)
      }
      setState(prev => ({
        ...prev,
        token: accessToken,
        refreshToken: refreshToken ?? null,
        isLoading: false,
        error: null,
      }))
    } else if (result.type === 'cancel') {
      setState(prev => ({ ...prev, isLoading: false }))
    } else {
      setState(prev => ({ ...prev, isLoading: false, error: `Login failed: ${result.type}` }))
    }
  }, [state.selectedTenant])

  const logout = useCallback(async () => {
    await Promise.all([
      storageDelete(TOKEN_KEY),
      storageDelete(REFRESH_TOKEN_KEY),
      storageDelete(TENANT_KEY),
    ])
    setState({ token: null, refreshToken: null, selectedTenant: null, isLoading: false, error: null })
  }, [])

  return {
    token: state.token,
    selectedTenant: state.selectedTenant,
    isLoading: state.isLoading,
    error: state.error,
    isAuthenticated: !!state.token,
    login,
    logout,
    selectTenant,
  }
}
