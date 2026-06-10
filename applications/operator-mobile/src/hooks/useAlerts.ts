import { apiClient } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useOfflineQuery } from './useOfflineQuery'
import { CACHE_TTL } from '../services/OfflineCache'

export interface AlertItem {
  id: number
  module: string
  severity: string
  message: string
  status: string
  sensorId: string
  tenantId: string
  location?: string
  createdAt: string
}

export function useAlerts(module?: string) {
  const { token } = useAuth()
  return useOfflineQuery<AlertItem[]>({
    queryKey: ['alerts', module],
    queryFn: () => {
      const params = module ? `?module=${module}` : ''
      return apiClient.get<AlertItem[]>(`/api/v1/alerts${params}`, token ?? undefined)
    },
    staleTime: 10_000,
    cacheTtl: CACHE_TTL.TIER1, // 5 min — frequently changing
    enabled: !!token,
  })
}
