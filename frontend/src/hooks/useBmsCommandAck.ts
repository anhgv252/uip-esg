import { useEffect, useRef } from 'react'
import { useQueryClient } from '@tanstack/react-query'

const SSE_BASE_URL = (import.meta.env.VITE_SSE_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')

export interface BmsCommandAckEvent {
  commandId: string
  status: string     // ONLINE | OFFLINE | ERROR | UNKNOWN
  deviceId: string
  timestamp: string
}

/**
 * Subscribes to the `bms-command-ack` SSE event channel.
 * When an ACK arrives, invalidates the `bms-devices` query to trigger
 * an immediate status refresh in the BMS devices list (FE-6).
 *
 * The SSE stream from `/api/v1/alerts/stream` carries `bms-command-ack` events
 * broadcast by BmsCommandAckConsumer after updating device status in DB.
 */
export function useBmsCommandAck() {
  const queryClient = useQueryClient()
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => {
    const es = new EventSource(`${SSE_BASE_URL}/api/v1/alerts/stream`, { withCredentials: true })
    esRef.current = es

    es.addEventListener('bms-command-ack', (event: MessageEvent) => {
      try {
        const ack = JSON.parse(event.data) as BmsCommandAckEvent
        // Invalidate device list so UI reflects new status immediately
        queryClient.invalidateQueries({ queryKey: ['bms-devices'] })

        // Also update individual device cache entry if we have the ID
        if (ack.deviceId) {
          queryClient.invalidateQueries({ queryKey: ['bms-device', ack.deviceId] })
        }
      } catch {
        // Ignore parse errors — non-critical
      }
    })

    return () => {
      es.close()
      esRef.current = null
    }
  }, [queryClient])
}
