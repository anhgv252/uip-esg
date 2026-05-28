import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getBmsDevices, createBmsDevice, deleteBmsDevice, sendBmsCommand, type BmsDeviceRequest } from '@/api/bms'

export function useBmsDevices() {
  return useQuery({
    queryKey: ['bms-devices'],
    queryFn: getBmsDevices,
    refetchInterval: 30_000,
  })
}

export function useCreateBmsDevice() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: BmsDeviceRequest) => createBmsDevice(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['bms-devices'] }),
  })
}

export function useDeleteBmsDevice() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteBmsDevice(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['bms-devices'] }),
  })
}

export function useSendBmsCommand() {
  return useMutation({
    mutationFn: ({ id, commandType, payload }: { id: string; commandType: string; payload: Record<string, unknown> }) =>
      sendBmsCommand(id, commandType, payload),
  })
}
