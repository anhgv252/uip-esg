import { useRef, useEffect, useCallback } from 'react'
import type { SensorWithAqi } from '@/api/cityops'
import type { AlertEvent } from '@/api/alerts'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

interface MapSSEMessage {
  type: 'SENSOR_UPDATE' | 'ALERT'
  sensor?: SensorWithAqi
  alert?: AlertEvent
}

interface UseMapSSEOptions {
  onSensorUpdate?: (sensor: SensorWithAqi) => void
  onAlert?: (alert: AlertEvent) => void
}

/**
 * SSE hook for real-time sensor/alert updates on the city ops map.
 * Re-uses the existing /api/v1/notifications/stream endpoint — messages
 * with type="SENSOR_UPDATE" update marker colours; type="ALERT" append to feed.
 */
export function useMapSSE(options: UseMapSSEOptions) {
  const { onSensorUpdate, onAlert } = options
  const esRef = useRef<EventSource | null>(null)

  const connect = useCallback(() => {
    if (esRef.current) return

    const url = `${BASE_URL}/api/v1/notifications/stream`
    const es = new EventSource(url, { withCredentials: true })
    esRef.current = es

    es.onmessage = (event) => {
      try {
        const msg: MapSSEMessage = JSON.parse(event.data)
        if (msg.type === 'SENSOR_UPDATE' && msg.sensor && onSensorUpdate) {
          onSensorUpdate(msg.sensor)
        }
        if (msg.type === 'ALERT' && msg.alert && onAlert) {
          onAlert(msg.alert)
        }
      } catch {
        // ignore parse errors from heartbeat / non-JSON events
      }
    }

    es.onerror = () => {
      es.close()
      esRef.current = null
      // reconnect after 5s
      setTimeout(connect, 5000)
    }
  }, [onSensorUpdate, onAlert])

  useEffect(() => {
    connect()
    return () => {
      esRef.current?.close()
      esRef.current = null
    }
  }, [connect])
}
