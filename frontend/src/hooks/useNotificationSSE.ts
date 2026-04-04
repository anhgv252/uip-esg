import { useEffect, useRef, useCallback } from 'react';

const SSE_BASE_URL = (import.meta.env.VITE_SSE_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const SSE_STREAM_URL = `${SSE_BASE_URL}/api/v1/notifications/stream`;

export interface AlertNotification {
  id: number;
  ruleName: string;
  severity: string;
  message: string;
  detectedAt: string;
}

type OnAlertCallback = (alert: AlertNotification) => void;

/**
 * Hook that opens an SSE connection to the backend notifications stream
 * and calls `onAlert` whenever an "alert" event arrives.
 * Reconnects automatically after 5s on connection drop.
 */
export function useNotificationSSE(onAlert: OnAlertCallback) {
  const onAlertRef = useRef(onAlert);
  const esRef = useRef<EventSource | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Keep callback ref up-to-date without re-triggering effect
  useEffect(() => {
    onAlertRef.current = onAlert;
  }, [onAlert]);

  const connect = useCallback(() => {
    // Avoid double-connecting
    if (esRef.current) {
      esRef.current.close();
    }

    // SSE endpoint - auth via httpOnly cookie (set by backend on login)
    // EventSource automatically includes cookies with withCredentials behavior
    const es = new EventSource(SSE_STREAM_URL, { withCredentials: true });
    esRef.current = es;

    es.addEventListener('alert', (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data) as AlertNotification;
        onAlertRef.current(data);
      } catch {
        // ignore malformed events
      }
    });

    es.onerror = () => {
      es.close();
      esRef.current = null;
      // Reconnect after 5s
      reconnectTimerRef.current = setTimeout(connect, 5000);
    };
  }, []);

  useEffect(() => {
    connect();

    return () => {
      esRef.current?.close();
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
      }
    };
  }, [connect]);
}
