import { apiClient } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useOfflineQuery } from './useOfflineQuery'
import { CACHE_TTL } from '../services/OfflineCache'

export interface Sensor {
  id: string
  type: string
  status: string
  district: string
  lastValue?: number
  lastReadingAt?: string
}

export function useSensors() {
  const { token } = useAuth()
  return useOfflineQuery<Sensor[]>({
    queryKey: ['sensors'],
    queryFn: () => apiClient.get<Sensor[]>('/api/v1/sensors', token ?? undefined),
    staleTime: 30_000,
    cacheTtl: CACHE_TTL.TIER1, // 5 min — frequently changing
    enabled: !!token,
  })
}
