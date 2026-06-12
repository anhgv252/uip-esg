import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getTrafficCounts,
  getTrafficIncidents,
  getCongestionMap,
  getTrafficFlow,
  updateIncidentStatus,
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
    queryFn: () => getCongestionMap(),
    refetchInterval: 60_000,
  })
}

// ── Traffic Flow hook (GAP-029) ─────────────────────────────────────────

export function useTrafficFlow(params?: {
  intersection?: string
  from?: string
  to?: string
}) {
  return useQuery({
    queryKey: ['traffic-flow', params],
    queryFn: () => getTrafficFlow(params),
    refetchInterval: 30_000,
    staleTime: 15_000,
  })
}

// ── Incident status mutation (GAP-029) ──────────────────────────────────

export function useUpdateIncidentStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      updateIncidentStatus(id, status),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['traffic-incidents'] })
    },
  })
}
