import { useQuery } from '@tanstack/react-query'
import type { AxiosInstance } from 'axios'
import { defaultApiClient } from './apiClient'

export interface DashboardStats {
  totalBuildings: number
  activeSensors: number
  openAlerts: number
  generatedAt?: string
}

async function fetchDashboard(client: AxiosInstance): Promise<DashboardStats> {
  try {
    // Try the full dashboard endpoint first
    const res = await client.get<DashboardStats>('/dashboard')
    return res.data
  } catch (error: any) {
    // Fallback to /dashboard/stats if /dashboard returns 404
    if (error?.response?.status === 404) {
      const res = await client.get<DashboardStats>('/dashboard/stats')
      return res.data
    }
    throw error
  }
}

/**
 * Fetch dashboard statistics
 * 
 * @param client - Optional axios instance (defaults to defaultApiClient)
 * @returns React Query result with dashboard stats
 * 
 * @example
 * ```tsx
 * const { data, isLoading, error } = useDashboard()
 * 
 * // With custom client:
 * const customClient = axios.create({ baseURL: '...' })
 * const { data } = useDashboard(customClient)
 * ```
 */
export function useDashboard(client: AxiosInstance = defaultApiClient) {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: () => fetchDashboard(client),
    staleTime: 30_000, // 30s
    refetchInterval: 60_000, // 1 min
  })
}
