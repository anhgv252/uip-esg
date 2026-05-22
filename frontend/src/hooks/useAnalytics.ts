import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  fetchEnergyAnalytics,
  fetchEmissionsAnalytics,
  fetchAqiTrend,
} from '@/api/analytics'

export function useEnergyAnalytics(tenantId: string, buildingIds: string[]) {
  const { fromEpoch, toEpoch } = useMemo(() => {
    const to = Date.now() / 1000
    const from = to - 30 * 24 * 3600
    return { fromEpoch: Math.floor(from), toEpoch: Math.floor(to) }
  }, [])
  return useQuery({
    queryKey: ['analytics', 'energy', tenantId, buildingIds, fromEpoch, toEpoch],
    queryFn: () => fetchEnergyAnalytics({ tenantId, buildingIds, fromEpoch, toEpoch }),
    enabled: !!tenantId && buildingIds.length > 0,
    staleTime: 2 * 60 * 1000,
  })
}

export function useEmissionsAnalytics(tenantId: string, buildingIds: string[]) {
  const { fromEpoch, toEpoch } = useMemo(() => {
    const to = Date.now() / 1000
    const from = to - 30 * 24 * 3600
    return { fromEpoch: Math.floor(from), toEpoch: Math.floor(to) }
  }, [])
  return useQuery({
    queryKey: ['analytics', 'emissions', tenantId, buildingIds, fromEpoch, toEpoch],
    queryFn: () => fetchEmissionsAnalytics({ tenantId, buildingIds, fromEpoch, toEpoch }),
    enabled: !!tenantId && buildingIds.length > 0,
    staleTime: 2 * 60 * 1000,
  })
}

export function useAqiTrend(tenantId: string, buildingIds: string[]) {
  const { fromEpoch, toEpoch } = useMemo(() => {
    const to = Date.now() / 1000
    const from = to - 30 * 24 * 3600
    return { fromEpoch: Math.floor(from), toEpoch: Math.floor(to) }
  }, [])
  return useQuery({
    queryKey: ['analytics', 'aqi', tenantId, buildingIds, fromEpoch, toEpoch],
    queryFn: () => fetchAqiTrend({ tenantId, buildingIds, fromEpoch, toEpoch }),
    enabled: !!tenantId && buildingIds.length > 0,
    staleTime: 2 * 60 * 1000,
    refetchInterval: 15_000,
  })
}
