import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { AxiosInstance } from 'axios'
import { defaultApiClient } from './apiClient'

export interface AlertEvent {
  id: string
  ruleId: string | null
  ruleName: string | null
  sensorId: string | null
  module: string
  measureType: string
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  value: number
  threshold: number
  note: string | null
  status: 'OPEN' | 'ACKNOWLEDGED' | 'ESCALATED' | 'RESOLVED'
  acknowledgedBy: string | null
  acknowledgedAt: string | null
  detectedAt: string
}

export interface AlertEventsPage {
  content: AlertEvent[]
  totalElements: number
  totalPages: number
  number: number
}

interface AlertFilters {
  status?: string
  severity?: string
  module?: string
  from?: string
  to?: string
  page?: number
  size?: number
  tenantId?: string
}

/**
 * Fetch paginated alerts with optional filters
 * 
 * @param filters - Optional filter parameters
 * @param client - Optional axios instance
 * @returns React Query result with alerts page
 * 
 * @example
 * ```tsx
 * const { data } = useAlerts({ status: 'OPEN', severity: 'HIGH' })
 * ```
 */
export function useAlerts(
  filters?: AlertFilters,
  client: AxiosInstance = defaultApiClient
) {
  return useQuery({
    queryKey: ['alerts', filters],
    queryFn: () =>
      client.get<AlertEventsPage>('/alerts', { params: filters }).then((r) => r.data),
    refetchInterval: 15_000,
  })
}

/**
 * Acknowledge an alert
 * 
 * @param client - Optional axios instance
 * @returns Mutation hook for acknowledging alerts
 * 
 * @example
 * ```tsx
 * const ackMutation = useAcknowledgeAlert()
 * await ackMutation.mutateAsync({ id: 'alert-123', note: 'Investigating' })
 * ```
 */
export function useAcknowledgeAlert(client: AxiosInstance = defaultApiClient) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, note, tenantId }: { id: string; note?: string; tenantId?: string }) =>
      client.put<AlertEvent>(`/alerts/${id}/acknowledge`, { note, tenantId }).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['alerts'] }),
  })
}

/**
 * Escalate an alert
 * 
 * @param client - Optional axios instance
 * @returns Mutation hook for escalating alerts
 */
export function useEscalateAlert(client: AxiosInstance = defaultApiClient) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, note, tenantId }: { id: string; note?: string; tenantId?: string }) =>
      client.put<AlertEvent>(`/alerts/${id}/escalate`, { note, tenantId }).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['alerts'] }),
  })
}

/**
 * Resolve an alert
 * 
 * @param client - Optional axios instance
 * @returns Mutation hook for resolving alerts
 */
export function useResolveAlert(client: AxiosInstance = defaultApiClient) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) =>
      client.put<AlertEvent>(`/alerts/${id}/resolve`, { note }).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['alerts'] }),
  })
}

/**
 * Fetch citizen notifications (public alerts)
 * 
 * @param page - Page number (0-indexed)
 * @param size - Page size
 * @param client - Optional axios instance
 * @returns React Query result with notifications page
 */
export function useCitizenNotifications(
  page = 0,
  size = 20,
  client: AxiosInstance = defaultApiClient
) {
  return useQuery({
    queryKey: ['citizen-notifications', page, size],
    queryFn: () =>
      client.get<AlertEventsPage>('/alerts/notifications', { params: { page, size } }).then((r) => r.data),
    refetchInterval: 30_000,
  })
}
