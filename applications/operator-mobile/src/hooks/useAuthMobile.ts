import { useState, useCallback, useEffect } from 'react'
import * as SecureStore from 'expo-secure-store'
import * as WebBrowser from 'expo-web-browser'
import * as AuthSession from 'expo-auth-session'
import Constants from 'expo-constants'

interface AuthState {
  token: string | null
  refreshToken: string | null
  isLoading: boolean
  error: string | null
}

const TOKEN_KEY = 'auth_token'
const REFRESH_TOKEN_KEY = 'auth_refresh_token'

export function useAuthMobile() {
  const [state, setState] = useState<AuthState>({
    token: null,
    refreshToken: null,
    isLoading: true,
    error: null,
  })

  // Load stored token on mount
  useEffect(() => {
    async function loadToken() {
      try {
        const token = await SecureStore.getItemAsync(TOKEN_KEY)
        const refreshToken = await SecureStore.getItemAsync(REFRESH_TOKEN_KEY)
        setState({ token, refreshToken, isLoading: false, error: null })
      } catch {
        setState(prev => ({ ...prev, isLoading: false }))
      }
    }
    loadToken()
  }, [])

  const login = useCallback(async () => {
    setState(prev => ({ ...prev, isLoading: true, error: null }))
    try {
      const apiBase = Constants.expoConfig?.extra?.apiBaseUrl ?? 'http://localhost:8080'

      // Fetch Keycloak config from backend
      const configResponse = await fetch(`${apiBase}/api/v1/mobile/auth/config?tenantId=hcm`)
      const config = await configResponse.json()

      const discoveryEndpoint = `${config.issuer}/.well-known/openid-configuration`
      const discovery = await AuthSession.fetchDiscoveryAsync(discoveryEndpoint)

      const redirectUrl = AuthSession.makeRedirectUri()
      const authRequest = new AuthSession.AuthRequest({
        clientId: config.clientId,
        scopes: config.scopes.split(' '),
        redirectUri: redirectUrl,
      })

      const result = await authRequest.promptAsync(discovery)

      if (result.type === 'success' && result.authentication) {
        const { accessToken, refreshToken } = result.authentication

        await SecureStore.setItemAsync(TOKEN_KEY, accessToken)
        if (refreshToken) {
          await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken)
        }

        setState({ token: accessToken, refreshToken: refreshToken ?? null, isLoading: false, error: null })
      } else if (result.type === 'cancel') {
        setState(prev => ({ ...prev, isLoading: false }))
      } else {
        setState(prev => ({ ...prev, isLoading: false, error: `Login failed: ${result.type}` }))
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Login failed'
      setState(prev => ({ ...prev, isLoading: false, error: message }))
    }
  }, [])

  const logout = useCallback(async () => {
    await SecureStore.deleteItemAsync(TOKEN_KEY)
    await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY)
    setState({ token: null, refreshToken: null, isLoading: false, error: null })
  }, [])

  return {
    token: state.token,
    isLoading: state.isLoading,
    error: state.error,
    isAuthenticated: !!state.token,
    login,
    logout,
  }
}
