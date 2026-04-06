import { useQuery } from '@tanstack/react-query'
import {
  getTrafficCounts,
  getTrafficIncidents,
  getCongestionMap,
} from '@/api/traffic'

export function useTrafficCounts(params?: {
  intersection?: string
  from?: string
  to?: string
}) {
  return useQuery({
    queryKey: ['traffic-counts', params],
    queryFn: () => getTrafficCounts(params),
    refetchInterval: 30_000,
  })
}

export function useTrafficIncidents(status?: string) {
  return useQuery({
    queryKey: ['traffic-incidents', status],
    queryFn: () => getTrafficIncidents({ status: status ?? 'OPEN', size: 50 }),
    refetchInterval: 30_000,
  })
}

export function useCongestionMap() {
  return useQuery({
    queryKey: ['congestion-map'],
    queryFn: getCongestionMap,
    refetchInterval: 60_000,
  })
}
