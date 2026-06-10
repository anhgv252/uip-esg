import { apiClient } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useOfflineQuery } from './useOfflineQuery'
import { CACHE_TTL } from '../services/OfflineCache'

export interface Building {
  id: string
  name: string
  address: string
  district: string
  status: string
  deviceCount: number
}

export function useBuildingList() {
  const { token } = useAuth()
  return useOfflineQuery<Building[]>({
    queryKey: ['buildings'],
    queryFn: () => apiClient.get<Building[]>('/api/v1/bms/buildings', token ?? undefined),
    staleTime: 60_000,
    cacheTtl: CACHE_TTL.TIER2, // 30 min — reference data
    enabled: !!token,
  })
}
