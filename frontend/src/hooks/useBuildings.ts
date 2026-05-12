import { useQuery } from '@tanstack/react-query'

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
  const res = await fetch('/api/v1/buildings', {
    headers: { 'Content-Type': 'application/json' },
  })
  if (!res.ok) throw new Error(`Failed to fetch buildings: ${res.status}`)
  return res.json()
}

export function useBuildings() {
  return useQuery({
    queryKey: ['buildings'],
    queryFn: fetchBuildings,
    staleTime: 5 * 60 * 1000,
  })
}
