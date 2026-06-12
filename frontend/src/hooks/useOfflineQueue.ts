/**
 * useOfflineQueue — offline-first mutation queue for PWA / mobile.
 *
 * Strategy:
 * - Cache-first reads via React Query's cache + IndexedDB persistence.
 * - Queue mutations when offline; replay when back online.
 * - No conflict resolution (descope per v3.1-03).
 *
 * Uses localStorage for the queue (lighter than IndexedDB for a small mutation log).
 */
import { useState, useEffect, useCallback, useRef } from 'react';
import { useQueryClient, useIsMutating } from '@tanstack/react-query';
import { apiClient } from '@/api/client';

/** Serialized mutation waiting to be replayed */
interface QueuedMutation {
  id: string;
  timestamp: string;
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  url: string;
  body: string | null;
  /** React Query key to invalidate after replay */
  invalidateKeys: string[][];
}

const STORAGE_KEY = 'uip_offline_queue';

function loadQueue(): QueuedMutation[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as QueuedMutation[]) : [];
  } catch {
    return [];
  }
}

function persistQueue(queue: QueuedMutation[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(queue));
  } catch {
    // localStorage full — drop oldest entry
    const trimmed = queue.slice(1);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
  }
}

function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

export interface OfflineQueueStatus {
  isOnline: boolean;
  pendingCount: number;
  isSyncing: boolean;
  lastSyncError: string | null;
}

export interface OfflineQueueActions {
  /** Queue a mutation for later execution. Call this instead of the API directly when offline. */
  enqueue: (mutation: {
    method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
    url: string;
    body?: unknown;
    invalidateKeys?: string[][];
  }) => string;
  /** Manually trigger sync of all queued mutations */
  syncNow: () => Promise<void>;
  /** Clear all pending mutations without replaying */
  clearQueue: () => void;
}

export function useOfflineQueue(): OfflineQueueStatus & OfflineQueueActions {
  const queryClient = useQueryClient();
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [pendingCount, setPendingCount] = useState(() => loadQueue().length);
  const [isSyncing, setIsSyncing] = useState(false);
  const [lastSyncError, setLastSyncError] = useState<string | null>(null);
  const syncLockRef = useRef(false);

  // Listen for online/offline events
  useEffect(() => {
    const handleOnline = () => {
      setIsOnline(true);
      // Auto-sync when coming back online
      void syncQueue();
    };
    const handleOffline = () => setIsOnline(false);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** Replay all queued mutations in order */
  const syncQueue = useCallback(async () => {
    if (syncLockRef.current) return;
    const queue = loadQueue();
    if (queue.length === 0) return;

    syncLockRef.current = true;
    setIsSyncing(true);
    setLastSyncError(null);

    const failed: QueuedMutation[] = [];

    for (const mutation of queue) {
      try {
        await apiClient.request({
          method: mutation.method,
          url: mutation.url,
          data: mutation.body ? (JSON.parse(mutation.body) as unknown) : undefined,
        });

        // Invalidate related React Query keys after each successful replay
        for (const key of mutation.invalidateKeys) {
          await queryClient.invalidateQueries({ queryKey: key });
        }
      } catch (err) {
        failed.push(mutation);
        const message = err instanceof Error ? err.message : 'Sync failed';
        setLastSyncError(message);
      }
    }

    persistQueue(failed);
    setPendingCount(failed.length);
    setIsSyncing(false);
    syncLockRef.current = false;
  }, [queryClient]);

  const enqueue = useCallback(
    (mutation: {
      method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
      url: string;
      body?: unknown;
      invalidateKeys?: string[][];
    }): string => {
      const entry: QueuedMutation = {
        id: generateId(),
        timestamp: new Date().toISOString(),
        method: mutation.method,
        url: mutation.url,
        body: mutation.body ? JSON.stringify(mutation.body) : null,
        invalidateKeys: mutation.invalidateKeys ?? [],
      };

      const queue = loadQueue();
      queue.push(entry);
      persistQueue(queue);
      setPendingCount(queue.length);

      // If online, try to sync immediately
      if (navigator.onLine) {
        void syncQueue();
      }

      return entry.id;
    },
    [syncQueue],
  );

  const clearQueue = useCallback(() => {
    persistQueue([]);
    setPendingCount(0);
    setLastSyncError(null);
  }, []);

  return {
    isOnline,
    pendingCount,
    isSyncing,
    lastSyncError,
    enqueue,
    syncNow: syncQueue,
    clearQueue,
  };
}

/**
 * useOfflineMutation — convenience wrapper that queues mutations when offline
 * and calls the API directly when online.
 */
export function useOfflineMutation() {
  const queue = useOfflineQueue();
  const isMutating = useIsMutating() > 0;

  const mutate = useCallback(
    async (config: {
      method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
      url: string;
      body?: unknown;
      invalidateKeys?: string[][];
    }) => {
      if (queue.isOnline) {
        // Online: execute directly
        const response = await apiClient.request({
          method: config.method,
          url: config.url,
          data: config.body,
        });
        return response.data;
      }
      // Offline: queue for later
      queue.enqueue(config);
      return null;
    },
    [queue],
  );

  return {
    mutate,
    isOnline: queue.isOnline,
    isSyncing: queue.isSyncing,
    pendingCount: queue.pendingCount,
    syncNow: queue.syncNow,
    isMutating,
  };
}
