import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { useAuth } from '../context/AuthContext'

/** Building safety score from BuildingSafetyService */
export interface BuildingSafetyScore {
  buildingId: string
  score: number       // 0-100
  status: 'SAFE' | 'WARNING' | 'CRITICAL' | 'OFFLINE'
  activeAlerts: number
  lastUpdated: string
}

/** Aggregated dashboard data from backend */
export interface DashboardData {
  energyKwh: number
  energyTrend: 'up' | 'down' | 'stable'
  safetyScore: number        // Average across buildings (0-100)
  safetyStatus: 'SAFE' | 'WARNING' | 'CRITICAL' | 'OFFLINE'
  aqi: number                // Air Quality Index (0-500)
  aqiLabel: string           // "Good", "Moderate", etc.
  activeAlerts: number
  onlineSensors: number
  totalSensors: number
}

export function useBuildingSafety(buildingId: string | null) {
  const { token } = useAuth()
  return useQuery({
    queryKey: ['building-safety', buildingId],
    queryFn: () =>
      apiClient.get<BuildingSafetyScore>(
        `/api/v1/buildings/${buildingId}/safety`,
        token ?? undefined
      ),
    staleTime: 5 * 60_000, // 5 min cache
    enabled: !!token && !!buildingId,
  })
}

export function useDashboard() {
  const { token } = useAuth()
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: () =>
      apiClient.get<DashboardData>('/api/v1/dashboard', token ?? undefined),
    staleTime: 30_000,
    refetchInterval: 30_000, // Auto-refresh every 30s
    enabled: !!token,
  })
}

/** Safety score color based on value */
export function getSafetyColor(score: number): string {
  if (score >= 80) return '#4CAF50' // green
  if (score >= 50) return '#FF9800' // amber
  return '#F44336'                   // red
}

/** AQI color based on value */
export function getAqiColor(aqi: number): string {
  if (aqi <= 50) return '#4CAF50'   // Good
  if (aqi <= 100) return '#FF9800'  // Moderate
  if (aqi <= 200) return '#F44336'  // Unhealthy
  if (aqi <= 300) return '#9C27B0'  // Very Unhealthy
  return '#B71C1C'                   // Hazardous
}

/** AQI label based on value */
export function getAqiLabel(aqi: number): string {
  if (aqi <= 50) return 'Tốt'
  if (aqi <= 100) return 'Trung bình'
  if (aqi <= 200) return 'Kém'
  if (aqi <= 300) return 'Rất kém'
  return 'Nguy hiểm'
}
