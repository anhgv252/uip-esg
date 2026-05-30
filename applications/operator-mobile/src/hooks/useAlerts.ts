import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { useAuth } from '../context/AuthContext'

export interface AlertItem {
  id: number
  module: string
  severity: string
  message: string
  status: string
  sensorId: string
  tenantId: string
  location?: string
  createdAt: string
}

export function useAlerts(module?: string) {
  const { token } = useAuth()
  return useQuery({
    queryKey: ['alerts', module],
    queryFn: () => {
      const params = module ? `?module=${module}` : ''
      return apiClient.get<AlertItem[]>(`/api/v1/alerts${params}`, token ?? undefined)
    },
    staleTime: 10_000,
    enabled: !!token,
  })
}
