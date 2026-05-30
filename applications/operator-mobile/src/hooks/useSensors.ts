import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { useAuth } from '../context/AuthContext'

export interface Sensor {
  id: string
  type: string
  status: string
  district: string
  lastValue?: number
  lastReadingAt?: string
}

export function useSensors() {
  const { token } = useAuth()
  return useQuery({
    queryKey: ['sensors'],
    queryFn: () => apiClient.get<Sensor[]>('/api/v1/sensors', token ?? undefined),
    staleTime: 30_000,
    enabled: !!token,
  })
}
