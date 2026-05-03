import axios, { type InternalAxiosRequestConfig } from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export const apiClient = axios.create({
  baseURL: `${BASE_URL}/api/v1`,
  timeout: 10_000,
  withCredentials: true, // sends httpOnly refresh-token cookie
})

// In-memory access token — never touches localStorage / sessionStorage
let _accessToken: string | null = null
let _refreshToken: string | null = null

export const tokenStore = {
  set: (token: string) => {
    _accessToken = token
  },
  setRefresh: (token: string) => {
    _refreshToken = token
  },
  get: () => _accessToken,
  getRefresh: () => _refreshToken,
  clear: () => {
    _accessToken = null
    _refreshToken = null
    _tenantId = null
  },
}

// In-memory tenant context for request headers
let _tenantId: string | null = null

export const tenantStore = {
  set: (id: string | null) => {
    _tenantId = id
  },
  get: () => _tenantId,
}

// Attach Bearer token to every request
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.get()
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// Attach tenant context header
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const tid = tenantStore.get()
  if (tid) {
    config.headers['X-Tenant-Id'] = tid
  }
  return config
})

// Silent refresh on 401
let _isRefreshing = false
let _refreshQueue: Array<(token: string) => void> = []

// Extract traceId from error responses for user-facing error display
export interface ApiError extends Error {
  traceId?: string
  timestamp?: string
  path?: string
  status?: number
}

export function createApiError(error: unknown): ApiError {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as { response?: { status?: number; data?: Record<string, unknown> } }
    const data = axiosError.response?.data
    const apiError = new Error(
      (data?.detail as string) || (data?.title as string) || 'An error occurred'
    ) as ApiError
    apiError.traceId = data?.traceId as string | undefined
    apiError.timestamp = data?.timestamp as string | undefined
    apiError.path = data?.path as string | undefined
    apiError.status = axiosError.response?.status
    return apiError
  }
  return error instanceof Error ? (error as ApiError) : new Error(String(error))
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    if (
      error.response?.status === 401 &&
      !originalRequest._retried &&
      !originalRequest.url?.includes('/auth/')
    ) {
      originalRequest._retried = true

      if (_isRefreshing) {
        // Queue request until refresh completes
        return new Promise((resolve) => {
          _refreshQueue.push((newToken: string) => {
            originalRequest.headers['Authorization'] = `Bearer ${newToken}`
            resolve(apiClient(originalRequest))
          })
        })
      }

      _isRefreshing = true
      try {
        const refreshToken = tokenStore.getRefresh()
        const { data } = await apiClient.post<{ accessToken: string }>(
          '/auth/refresh',
          { refreshToken },
        )
        tokenStore.set(data.accessToken)
        _refreshQueue.forEach((cb) => cb(data.accessToken))
        _refreshQueue = []
        originalRequest.headers['Authorization'] = `Bearer ${data.accessToken}`
        return apiClient(originalRequest)
      } catch {
        tokenStore.clear()
        _refreshQueue = []
        window.location.href = '/login'
      } finally {
        _isRefreshing = false
      }
    }
    return Promise.reject(error)
  },
)
