import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getBuildings,
  registerCitizen,
  linkHousehold,
  getCitizenProfile,
  getInvoices,
  getInvoiceById,
  registerMeter,
  getMeters,
  type CitizenRegistrationRequest,
  type HouseholdRequest,
  type MeterRequest,
} from '@/api/citizen'

export function useBuildings() {
  return useQuery({
    queryKey: ['buildings'],
    queryFn: getBuildings,
    staleTime: 5 * 60_000,
  })
}

export function useCitizenProfile() {
  return useQuery({
    queryKey: ['citizen-profile'],
    queryFn: getCitizenProfile,
  })
}

export function useInvoices(params?: { month?: number; year?: number }) {
  return useQuery({
    queryKey: ['invoices', params],
    queryFn: () => getInvoices(params),
  })
}

export function useInvoiceDetail(id: string | null) {
  return useQuery({
    queryKey: ['invoice', id],
    queryFn: () => getInvoiceById(id!),
    enabled: !!id,
  })
}

export function useMeters() {
  return useQuery({
    queryKey: ['meters'],
    queryFn: getMeters,
  })
}

export function useRegister() {
  return useMutation({
    mutationFn: (data: CitizenRegistrationRequest) => registerCitizen(data),
  })
}

export function useLinkHousehold() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: HouseholdRequest) => linkHousehold(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['citizen-profile'] }),
  })
}

export function useRegisterMeter() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: MeterRequest) => registerMeter(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['meters'] }),
  })
}
