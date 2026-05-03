import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { tenantConfigApi, type TenantConfig } from '@/api/tenantConfig'
import { useAuth } from '@/hooks/useAuth'

interface TenantConfigContextValue {
  config: TenantConfig | null
  isLoading: boolean
  isFeatureEnabled: (flag: string) => boolean
}

export const TenantConfigContext = createContext<TenantConfigContextValue | null>(null)

export function TenantConfigProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth()

  const { data: config, isLoading } = useQuery({
    queryKey: ['tenant-config'],
    queryFn: tenantConfigApi.getConfig,
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000,
  })

  // Invalidate queries on tenant switch
  const queryClient = useQueryClient()
  const prevTenantIdRef = useRef<string | null>(null)

  useEffect(() => {
    const currentTenantId = config?.tenantId ?? null
    if (prevTenantIdRef.current !== null && prevTenantIdRef.current !== currentTenantId) {
      queryClient.invalidateQueries()
    }
    prevTenantIdRef.current = currentTenantId
  }, [config?.tenantId, queryClient])

  const isFeatureEnabled = useCallback(
    (flag: string) => config?.features[flag]?.enabled ?? true,
    [config],
  )

  const value = useMemo<TenantConfigContextValue>(
    () => ({ config: config ?? null, isLoading, isFeatureEnabled }),
    [config, isLoading, isFeatureEnabled],
  )

  return (
    <TenantConfigContext.Provider value={value}>{children}</TenantConfigContext.Provider>
  )
}

export function useTenantConfig() {
  const ctx = useContext(TenantConfigContext)
  if (!ctx) throw new Error('useTenantConfig must be used within TenantConfigProvider')
  return ctx
}

export function useFeatureFlag(flag: string): boolean {
  const { isFeatureEnabled } = useTenantConfig()
  return isFeatureEnabled(flag)
}
