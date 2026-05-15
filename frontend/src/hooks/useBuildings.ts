import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface Building {
  id: string
  buildingCode: string
  buildingName: string
  tenantId: string
  clusterId?: string
  floorCount: number
  totalAreaM2?: number
  isActive: boolean
}

async function fetchBuildings(): Promise<Building[]> {
  const res = await apiClient.get<Building[]>('/buildings')
  return res.data
}

export function useBuildings() {
  return useQuery({
    queryKey: ['buildings'],
    queryFn: fetchBuildings,
    staleTime: 5 * 60 * 1000,
  })
}
