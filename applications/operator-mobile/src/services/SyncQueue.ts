import NetInfo, { NetInfoState } from '@react-native-community/netinfo'
import AsyncStorage from '@react-native-async-storage/async-storage'
import { storageGet } from '../storage/secureStorage'

const QUEUE_KEY = 'uip_sync_queue'
const MAX_RETRIES = 3

export interface QueuedAction {
  id: string
  type: string
  endpoint: string
  method: 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  payload?: unknown
  /** tokenKey: key in SecureStore to fetch token at execution time (NOT stored as plaintext) */
  tokenKey?: string
  retries: number
  createdAt: number
}

type SyncStatusListener = (pending: number) => void

class SyncQueue {
  private isOnline = true
  private isFlushing = false
  private listeners: SyncStatusListener[] = []
  private unsubscribeNetInfo?: () => void
  private enqueueLock = false

  init(): void {
    this.unsubscribeNetInfo = NetInfo.addEventListener((state: NetInfoState) => {
      const wasOffline = !this.isOnline
      this.isOnline = state.isConnected === true
      if (wasOffline && this.isOnline) {
        this.flush()
      }
    })
    NetInfo.fetch().then((state) => {
      this.isOnline = state.isConnected === true
    })
  }

  destroy(): void {
    this.unsubscribeNetInfo?.()
  }

  onStatusChange(listener: SyncStatusListener): () => void {
    this.listeners.push(listener)
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener)
    }
  }

  private async notify(): Promise<void> {
    const queue = await this.getQueue()
    this.listeners.forEach((l) => l(queue.length))
  }

  async enqueue(action: Omit<QueuedAction, 'id' | 'retries' | 'createdAt'>): Promise<void> {
    const entry: QueuedAction = {
      ...action,
      id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      retries: 0,
      createdAt: Date.now(),
    }

    // Serialize writes to prevent concurrent enqueue losing entries
    while (this.enqueueLock) {
      await new Promise((r) => setTimeout(r, 20))
    }
    this.enqueueLock = true
    try {
      const queue = await this.getQueue()
      queue.push(entry)
      await this.saveQueue(queue)
    } finally {
      this.enqueueLock = false
    }

    await this.notify()

    if (this.isOnline) {
      this.flush()
    }
  }

  async flush(): Promise<void> {
    // Prevent concurrent flush — only one at a time
    if (this.isFlushing) return
    this.isFlushing = true

    try {
      const queue = await this.getQueue()
      if (queue.length === 0) return

      const remaining: QueuedAction[] = []
      for (const action of queue) {
        const success = await this.executeAction(action)
        if (!success && action.retries < MAX_RETRIES) {
          remaining.push({ ...action, retries: action.retries + 1 })
        }
      }

      // Merge: actions enqueued DURING flush must be preserved
      const enqueuedDuringFlush = await this.getQueue()
      const processedIds = new Set(queue.map((a) => a.id))
      const newEntries = enqueuedDuringFlush.filter((a) => !processedIds.has(a.id))
      await this.saveQueue([...remaining, ...newEntries])
    } finally {
      this.isFlushing = false
    }

    await this.notify()
  }

  private async executeAction(action: QueuedAction): Promise<boolean> {
    let token: string | null = null
    if (action.tokenKey) {
      token = await storageGet(action.tokenKey)
    }

    try {
      const response = await fetch(action.endpoint, {
        method: action.method,
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: action.payload ? JSON.stringify(action.payload) : undefined,
      })
      return response.ok
    } catch {
      return false
    }
  }

  private async getQueue(): Promise<QueuedAction[]> {
    try {
      const raw = await AsyncStorage.getItem(QUEUE_KEY)
      return raw ? JSON.parse(raw) : []
    } catch {
      return []
    }
  }

  private async saveQueue(queue: QueuedAction[]): Promise<void> {
    try {
      await AsyncStorage.setItem(QUEUE_KEY, JSON.stringify(queue))
    } catch (err) {
      // Storage full — evict oldest half and retry once
      try {
        const current = await this.getQueue()
        const trimmed = current.slice(Math.floor(current.length / 2))
        await AsyncStorage.setItem(QUEUE_KEY, JSON.stringify(trimmed))
      } catch {
        // If still failing, the queue state is unrecoverable — silently accept
      }
    }
  }

  async getPendingCount(): Promise<number> {
    const queue = await this.getQueue()
    return queue.length
  }
}

export const syncQueue = new SyncQueue()
