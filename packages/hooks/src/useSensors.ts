import { useQuery } from '@tanstack/react-query'
import type { AxiosInstance } from 'axios'
import { defaultApiClient } from './apiClient'

export interface Sensor {
  id: string
  sensorId: string
  type: string
  location: string
  latitude?: number
  longitude?: number
  status: 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE'
  lastReading?: string
  metadata?: Record<string, any>
}

export interface SensorsPage {
  content: Sensor[]
  totalElements: number
  totalPages: number
  number: number
}

interface SensorFilters {
  type?: string
  status?: string
  page?: number
  size?: number
}

/**
 * Fetch paginated sensors with optional filters
 * 
 * @param filters - Optional filter parameters
 * @param client - Optional axios instance
 * @returns React Query result with sensors page
 * 
 * @example
 * ```tsx
 * const { data, isLoading } = useSensors({ type: 'AIR_QUALITY', status: 'ACTIVE' })
 * ```
 */
export function useSensors(
  filters?: SensorFilters,
  client: AxiosInstance = defaultApiClient
) {
  return useQuery({
    queryKey: ['sensors', filters],
    queryFn: () =>
      client.get<SensorsPage>('/sensors', { params: filters }).then((r) => r.data),
    staleTime: 60_000, // 1 min
    refetchInterval: 120_000, // 2 min
  })
}

/**
 * Fetch a single sensor by ID
 * 
 * @param sensorId - Sensor ID
 * @param client - Optional axios instance
 * @returns React Query result with sensor details
 * 
 * @example
 * ```tsx
 * const { data: sensor } = useSensor('sensor-123')
 * ```
 */
export function useSensor(
  sensorId: string | undefined,
  client: AxiosInstance = defaultApiClient
) {
  return useQuery({
    queryKey: ['sensor', sensorId],
    queryFn: () =>
      client.get<Sensor>(`/sensors/${sensorId}`).then((r) => r.data),
    enabled: !!sensorId,
    staleTime: 60_000,
  })
}
