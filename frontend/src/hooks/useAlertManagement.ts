import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getAlerts, acknowledgeAlert } from '@/api/alerts'

export function useAlerts(filters?: {
  status?: string
  severity?: string
  from?: string
  to?: string
  page?: number
  size?: number
}) {
  return useQuery({
    queryKey: ['alerts', filters],
    queryFn: () => getAlerts(filters),
    refetchInterval: 15_000,
  })
}

export function useAcknowledgeAlert() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) =>
      acknowledgeAlert(id, note),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['alerts'] }),
  })
}
