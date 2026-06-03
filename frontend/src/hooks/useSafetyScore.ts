import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface SafetyScoreResponse {
  score: number
  status: 'SAFE' | 'WARNING' | 'CRITICAL' | 'OFFLINE'
  lastUpdated: string
  activeAlerts: number
}

async function fetchSafetyScore(buildingId: string): Promise<SafetyScoreResponse> {
  const res = await apiClient.get<SafetyScoreResponse>(`/buildings/${buildingId}/safety`)
  return res.data
}

export function useSafetyScore(buildingId: string | undefined) {
  return useQuery({
    queryKey: ['safety', 'score', buildingId],
    queryFn: () => fetchSafetyScore(buildingId!),
    enabled: !!buildingId,
    staleTime: 30_000,
    refetchInterval: 30_000,
  })
}
