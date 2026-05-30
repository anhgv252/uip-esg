import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'

export interface Building {
  id: string
  name: string
  address: string
  district: string
  status: string
  deviceCount: number
}

export function useBuildingList() {
  return useQuery({
    queryKey: ['buildings'],
    queryFn: () => apiClient.get<Building[]>('/api/v1/bms/buildings'),
    staleTime: 60_000,
  })
}
