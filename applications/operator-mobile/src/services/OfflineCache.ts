import AsyncStorage from '@react-native-async-storage/async-storage'

const DEFAULT_TTL_MS = 5 * 60 * 1000 // 5 minutes

interface CacheEntry<T> {
  data: T
  expiresAt: number
}

export class OfflineCache {
  private prefix: string

  constructor(prefix = 'uip_cache') {
    this.prefix = prefix
  }

  private key(cacheKey: string): string {
    return `${this.prefix}:${cacheKey}`
  }

  async set<T>(cacheKey: string, data: T, ttlMs = DEFAULT_TTL_MS): Promise<void> {
    const entry: CacheEntry<T> = {
      data,
      expiresAt: Date.now() + ttlMs,
    }
    try {
      await AsyncStorage.setItem(this.key(cacheKey), JSON.stringify(entry))
    } catch {
      // Storage full or unavailable — fail silently
    }
  }

  async get<T>(cacheKey: string): Promise<T | null> {
    try {
      const raw = await AsyncStorage.getItem(this.key(cacheKey))
      if (!raw) return null
      const entry: CacheEntry<T> = JSON.parse(raw)
      if (Date.now() > entry.expiresAt) {
        await this.delete(cacheKey)
        return null
      }
      return entry.data
    } catch {
      return null
    }
  }

  async delete(cacheKey: string): Promise<void> {
    try {
      await AsyncStorage.removeItem(this.key(cacheKey))
    } catch {
      // Ignore
    }
  }

  async clear(): Promise<void> {
    try {
      const allKeys = await AsyncStorage.getAllKeys()
      const ours = allKeys.filter((k) => k.startsWith(this.prefix + ':'))
      if (ours.length > 0) await AsyncStorage.multiRemove(ours)
    } catch {
      // Ignore
    }
  }

  /** Removes only entries whose TTL has expired */
  async evictExpired(): Promise<void> {
    try {
      const allKeys = await AsyncStorage.getAllKeys()
      const ours = allKeys.filter((k) => k.startsWith(this.prefix + ':'))
      const pairs = await AsyncStorage.multiGet(ours)
      const expired: string[] = []
      for (const [k, v] of pairs) {
        if (!v) continue
        try {
          const entry = JSON.parse(v) as CacheEntry<unknown>
          if (Date.now() > entry.expiresAt) expired.push(k)
        } catch {
          expired.push(k)
        }
      }
      if (expired.length > 0) await AsyncStorage.multiRemove(expired)
    } catch {
      // Ignore
    }
  }
}

export const offlineCache = new OfflineCache()
