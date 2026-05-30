import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'

export interface Sensor {
  id: string
  type: string
  status: string
  district: string
  lastValue?: number
  lastReadingAt?: string
}

export function useSensors() {
  return useQuery({
    queryKey: ['sensors'],
    queryFn: () => apiClient.get<Sensor[]>('/api/v1/sensors'),
    staleTime: 30_000,
  })
}
