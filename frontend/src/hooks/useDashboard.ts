import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import type { components } from '@uip/api-types'

export type DashboardStats = components['schemas']['DashboardStats']

async function fetchDashboard(): Promise<DashboardStats> {
  try {
    // Try the full dashboard endpoint first (SA fix C-2)
    const res = await apiClient.get<DashboardStats>('/dashboard')
    return res.data
  } catch (error: any) {
    // Fallback to /dashboard/stats if /dashboard returns 404
    if (error?.response?.status === 404) {
      const res = await apiClient.get<DashboardStats>('/dashboard/stats')
      return res.data
    }
    throw error
  }
}

export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: fetchDashboard,
    staleTime: 30_000, // 30s
    refetchInterval: 60_000, // 1 min
  })
}
