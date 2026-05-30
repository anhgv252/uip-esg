import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { useAuth } from '../context/AuthContext'

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
  return useQuery({
    queryKey: ['buildings'],
    queryFn: () => apiClient.get<Building[]>('/api/v1/bms/buildings', token ?? undefined),
    staleTime: 60_000,
    enabled: !!token,
  })
}
