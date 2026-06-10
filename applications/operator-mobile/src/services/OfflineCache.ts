import AsyncStorage from '@react-native-async-storage/async-storage'

const TIER1_DEFAULT_TTL_MS = 5 * 60 * 1000   // 5 minutes — fast reads for sensors/alerts
const TIER2_DEFAULT_TTL_MS = 30 * 60 * 1000  // 30 minutes — persistent for buildings/metadata
const TIER1_MAX_ENTRIES = 64

interface CacheEntry<T> {
  data: T
  expiresAt: number
}

/**
 * Two-tier offline cache:
 *   Tier 1: In-memory LRU (Map) — fast, lost on app close, for frequently accessed data
 *   Tier 2: AsyncStorage — persistent, for reference data
 *
 * Tier 2 hits get promoted to Tier 1 on read for faster subsequent access.
 */
export class OfflineCache {
  private prefix: string
  private tier1: Map<string, CacheEntry<unknown>> = new Map()

  constructor(prefix = 'uip_cache') {
    this.prefix = prefix
  }

  private storageKey(cacheKey: string): string {
    return `${this.prefix}:${cacheKey}`
  }

  // ── Public API ──────────────────────────────────────────────────────────

  /**
   * Get cached value. Checks Tier 1 first, then Tier 2.
   * Tier 2 hits are promoted to Tier 1.
   */
  async getCached<T>(cacheKey: string): Promise<T | null> {
    // Tier 1: in-memory
    const memEntry = this.tier1.get(cacheKey) as CacheEntry<T> | undefined
    if (memEntry) {
      if (Date.now() > memEntry.expiresAt) {
        this.tier1.delete(cacheKey)
        return null
      }
      return memEntry.data
    }

    // Tier 2: AsyncStorage
    try {
      const raw = await AsyncStorage.getItem(this.storageKey(cacheKey))
      if (!raw) return null
      const entry: CacheEntry<T> = JSON.parse(raw)
      if (Date.now() > entry.expiresAt) {
        await AsyncStorage.removeItem(this.storageKey(cacheKey))
        return null
      }
      // Promote to Tier 1
      this.putTier1(cacheKey, entry)
      return entry.data
    } catch {
      return null
    }
  }

  /**
   * Store value in cache. Writes to both tiers for immediate availability.
   * @param ttlMs - TTL in ms. Defaults to tier-appropriate value based on data type hint.
   */
  async setCached<T>(cacheKey: string, data: T, ttlMs?: number): Promise<void> {
    const ttl = ttlMs ?? TIER1_DEFAULT_TTL_MS
    const entry: CacheEntry<T> = { data, expiresAt: Date.now() + ttl }

    // Tier 1: in-memory (always)
    this.putTier1(cacheKey, entry)

    // Tier 2: AsyncStorage (persistent)
    try {
      await AsyncStorage.setItem(this.storageKey(cacheKey), JSON.stringify(entry))
    } catch {
      // Storage full or unavailable — Tier 1 still works
    }
  }

  /** Remove entry from both tiers */
  async invalidate(cacheKey: string): Promise<void> {
    this.tier1.delete(cacheKey)
    try {
      await AsyncStorage.removeItem(this.storageKey(cacheKey))
    } catch {
      // Ignore
    }
  }

  /** Clear all cached data from both tiers */
  async clearAll(): Promise<void> {
    this.tier1.clear()
    try {
      const allKeys = await AsyncStorage.getAllKeys()
      const ours = allKeys.filter((k) => k.startsWith(this.prefix + ':'))
      if (ours.length > 0) await AsyncStorage.multiRemove(ours)
    } catch {
      // Ignore
    }
  }

  /** Remove only expired entries from both tiers */
  async evictExpired(): Promise<void> {
    // Tier 1: evict expired in-memory entries
    const now = Date.now()
    for (const [k, v] of this.tier1) {
      if (now > v.expiresAt) this.tier1.delete(k)
    }

    // Tier 2: evict expired AsyncStorage entries
    try {
      const allKeys = await AsyncStorage.getAllKeys()
      const ours = allKeys.filter((k) => k.startsWith(this.prefix + ':'))
      const pairs = await AsyncStorage.multiGet(ours)
      const expired: string[] = []
      for (const [k, v] of pairs) {
        if (!v) continue
        try {
          const entry = JSON.parse(v) as CacheEntry<unknown>
          if (now > entry.expiresAt) expired.push(k)
        } catch {
          expired.push(k)
        }
      }
      if (expired.length > 0) await AsyncStorage.multiRemove(expired)
    } catch {
      // Ignore
    }
  }

  // ── Tier 1 helpers ──────────────────────────────────────────────────────

  private putTier1<T>(cacheKey: string, entry: CacheEntry<T>): void {
    // LRU eviction: delete oldest if at capacity
    if (this.tier1.size >= TIER1_MAX_ENTRIES && !this.tier1.has(cacheKey)) {
      const firstKey = this.tier1.keys().next().value
      if (firstKey) this.tier1.delete(firstKey)
    }
    this.tier1.set(cacheKey, entry as CacheEntry<unknown>)
  }
}

/** Default TTL constants for consumers */
export const CACHE_TTL = {
  TIER1: TIER1_DEFAULT_TTL_MS,
  TIER2: TIER2_DEFAULT_TTL_MS,
} as const

export const offlineCache = new OfflineCache()
