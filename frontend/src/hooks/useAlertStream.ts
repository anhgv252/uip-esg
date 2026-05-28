import { useEffect, useRef, useCallback, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

interface UseAlertStreamOptions {
  severity?: string
  enabled?: boolean
}

export function useAlertStream({ severity, enabled = true }: UseAlertStreamOptions = {}) {
  const qc = useQueryClient()
  const esRef = useRef<EventSource | null>(null)
  const retryCount = useRef(0)
  const [status, setStatus] = useState<'connecting' | 'connected' | 'disconnected'>('disconnected')

  const connect = useCallback(() => {
    if (!enabled) return

    const params = new URLSearchParams()
    if (severity) params.set('severity', severity)

    const url = `/api/v1/alerts/stream${params.toString() ? '?' + params.toString() : ''}`

    const es = new EventSource(url)
    esRef.current = es

    es.onopen = () => {
      setStatus('connected')
      retryCount.current = 0
    }

    es.onmessage = () => {
      qc.invalidateQueries({ queryKey: ['alerts'] })
    }

    es.onerror = () => {
      setStatus('disconnected')
      es.close()

      const delay = Math.min(1000 * Math.pow(2, retryCount.current), 30000)
      retryCount.current++
      setTimeout(connect, delay)
    }
  }, [enabled, severity, qc])

  useEffect(() => {
    connect()
    return () => {
      esRef.current?.close()
      esRef.current = null
      setStatus('disconnected')
    }
  }, [connect])

  return { status }
}
