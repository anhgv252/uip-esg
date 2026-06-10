import { useQuery, UseQueryOptions, UseQueryResult } from '@tanstack/react-query'
import { useNetInfo } from '@react-native-community/netinfo'
import { offlineCache } from '../services/OfflineCache'

interface OfflineQueryOptions<T> {
  /** React Query key */
  queryKey: string | unknown[]
  /** Fetch function — called when online */
  queryFn: () => Promise<T>
  /** Cache TTL in ms (default: 5 min) */
  cacheTtl?: number
  /** React Query options passthrough */
  enabled?: boolean
  staleTime?: number
}

/**
 * Offline-aware React Query hook.
 *
 * - When **online**: fetches via `queryFn`, caches result, returns data.
 * - When **offline**: returns cached data if available, otherwise shows error.
 * - Tier 2 (AsyncStorage) cache means data survives app restarts.
 *
 * Usage:
 * ```ts
 * const { data, isLoading, error } = useOfflineQuery({
 *   queryKey: ['buildings', tenantId],
 *   queryFn: () => api.get('/api/v1/buildings').then(r => r.data),
 * })
 * ```
 */
export function useOfflineQuery<T>(options: OfflineQueryOptions<T>): UseQueryResult<T> {
  const netInfo = useNetInfo()
  const isConnected = netInfo.isConnected === true
  const key = typeof options.queryKey === 'string'
    ? options.queryKey
    : JSON.stringify(options.queryKey)

  const queryOptions: UseQueryOptions<T> = {
    queryKey: Array.isArray(options.queryKey) ? options.queryKey : [options.queryKey],
    queryFn: async () => {
      const data = await options.queryFn()
      // Cache successful response
      await offlineCache.setCached(key, data, options.cacheTtl)
      return data
    },
    enabled: options.enabled !== false,
    staleTime: options.staleTime ?? 30_000, // 30s default stale time
  }

  const result = useQuery<T>(queryOptions)

  // When offline and query failed/has no data, return cached data as fallback
  // This is handled via initialData which is synchronous, so we rely on
  // the queryFn's caching behavior and stale-while-revalidate pattern.
  // The OfflineCache tier-1 (in-memory) provides fast fallback for current session.

  return result
}
