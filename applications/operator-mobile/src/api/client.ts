import Constants from 'expo-constants'

const API_BASE_URL = Constants.expoConfig?.extra?.apiBaseUrl ?? 'http://localhost:8080'

interface ApiOptions {
  method?: string
  body?: unknown
  headers?: Record<string, string>
  token?: string
}

export async function apiRequest<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const { method = 'GET', body, headers = {}, token } = options

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    const error = await response.text()
    throw new Error(`API Error ${response.status}: ${error}`)
  }

  if (response.status === 204) return undefined as T
  return response.json()
}

export const apiClient = {
  get: <T>(path: string, token?: string) => apiRequest<T>(path, { token }),
  post: <T>(path: string, body: unknown, token?: string) => apiRequest<T>(path, { method: 'POST', body, token }),
  put: <T>(path: string, body: unknown, token?: string) => apiRequest<T>(path, { method: 'PUT', body, token }),
  delete: <T>(path: string, token?: string) => apiRequest<T>(path, { method: 'DELETE', token }),
}
