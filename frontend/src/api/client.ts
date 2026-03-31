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
  },
}

// Attach Bearer token to every request
apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.get()
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// Silent refresh on 401
let _isRefreshing = false
let _refreshQueue: Array<(token: string) => void> = []

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
