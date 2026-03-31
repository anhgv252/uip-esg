import { apiClient, tokenStore } from './client'

export interface LoginRequest {
  username: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  tokenType: string
}

export const authApi = {
  login: async (body: LoginRequest): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/auth/login', body)
    return data
  },

  refresh: async (): Promise<AuthResponse> => {
    const refreshToken = tokenStore.getRefresh()
    const { data } = await apiClient.post<AuthResponse>('/auth/refresh', { refreshToken })
    return data
  },

  logout: () => {
    tokenStore.clear()
  },
}
