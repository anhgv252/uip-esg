import { apiClient } from '@/api/client'

let _cachedKey: string | null = null

export async function getVapidPublicKey(): Promise<string> {
  if (_cachedKey) return _cachedKey

  const { data } = await apiClient.get<{ publicKey: string }>('/push/vapid-key')
  _cachedKey = data.publicKey
  return _cachedKey
}
