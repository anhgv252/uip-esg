import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useNotificationSSE, AlertNotification } from '@/hooks/useNotificationSSE'

// Mock EventSource
class MockEventSource {
  url: string
  readyState: number = 0
  withCredentials?: boolean
  private listeners: Map<string, Array<(event: any) => void>> = new Map()
  onerror: ((event: Event) => void) | null = null

  constructor(url: string, config?: { withCredentials?: boolean }) {
    this.url = url
    this.withCredentials = config?.withCredentials
    this.readyState = 1 // OPEN
    // Store reference to allow triggering events in tests
    ; (globalThis as any).__lastEventSource = this
  }

  addEventListener(type: string, listener: (event: any) => void) {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, [])
    }
    this.listeners.get(type)!.push(listener)
  }

  close() {
    this.readyState = 2 // CLOSED
  }

  // Test helper to trigger events
  _trigger(type: string, data: string) {
    const listeners = this.listeners.get(type) || []
    listeners.forEach(listener => {
      listener({ data, type } as MessageEvent)
    })
  }

  _triggerError() {
    if (this.onerror) {
      this.onerror(new Event('error'))
    }
  }
}

describe('useNotificationSSE', () => {
  const expectedSseUrl = 'http://localhost:8080/api/v1/notifications/stream'

  beforeEach(() => {
    vi.useFakeTimers()
    ; (globalThis as any).EventSource = MockEventSource
    ; (globalThis as any).__lastEventSource = null
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
    ; (globalThis as any).__lastEventSource = null
  })

  it('creates EventSource without token in URL', () => {
    const onAlert = vi.fn()
    renderHook(() => useNotificationSSE(onAlert))

    const es = (globalThis as any).__lastEventSource as MockEventSource
    expect(es).toBeDefined()
    expect(es.url).toBe(expectedSseUrl)
    expect(es.url).not.toContain('token=')
  })

  it('creates EventSource with withCredentials enabled', () => {
    const onAlert = vi.fn()
    renderHook(() => useNotificationSSE(onAlert))

    const es = (globalThis as any).__lastEventSource as MockEventSource
    expect(es.withCredentials).toBe(true)
  })

  it('calls onAlert when alert event received', () => {
    const onAlert = vi.fn()
    renderHook(() => useNotificationSSE(onAlert))

    const es = (globalThis as any).__lastEventSource as MockEventSource
    const alertData: AlertNotification = {
      id: '1',
      ruleName: 'High PM2.5',
      severity: 'critical',
      message: 'PM2.5 level exceeds 150 µg/m³',
      detectedAt: '2026-03-31T10:00:00Z'
    }

    es._trigger('alert', JSON.stringify(alertData))

    expect(onAlert).toHaveBeenCalledTimes(1)
    expect(onAlert).toHaveBeenCalledWith(alertData)
  })

  it('ignores malformed alert events', () => {
    const onAlert = vi.fn()
    renderHook(() => useNotificationSSE(onAlert))

    const es = (globalThis as any).__lastEventSource as MockEventSource
    
    // Send invalid JSON
    es._trigger('alert', 'not-json-data')

    expect(onAlert).not.toHaveBeenCalled()
  })

  it('reconnects after 5s on error', async () => {
    const onAlert = vi.fn()
    renderHook(() => useNotificationSSE(onAlert))

    const firstEs = (globalThis as any).__lastEventSource as MockEventSource
    expect(firstEs.readyState).toBe(1) // OPEN

    // Trigger error
    firstEs._triggerError()

    expect(firstEs.readyState).toBe(2) // CLOSED

    // Fast-forward 5 seconds to trigger reconnect
    await vi.advanceTimersByTimeAsync(5000)

    // Check new EventSource was created
    const newEs = (globalThis as any).__lastEventSource as MockEventSource
    expect(newEs).not.toBe(firstEs)
    expect(newEs.url).toBe(expectedSseUrl)
    expect(newEs.readyState).toBe(1) // OPEN again
  })

  it('closes connection on unmount', () => {
    const onAlert = vi.fn()
    const { unmount } = renderHook(() => useNotificationSSE(onAlert))

    const es = (globalThis as any).__lastEventSource as MockEventSource
    expect(es.readyState).toBe(1) // OPEN

    unmount()

    expect(es.readyState).toBe(2) // CLOSED
  })

  it('does not reconnect if unmounted during reconnect delay', () => {
    const onAlert = vi.fn()
    const { unmount } = renderHook(() => useNotificationSSE(onAlert))

    const firstEs = (globalThis as any).__lastEventSource as MockEventSource
    
    // Trigger error
    firstEs._triggerError()

    // Unmount before reconnect timer fires
    unmount()

    // Fast-forward past reconnect delay
    vi.advanceTimersByTime(10000)

    // Should not have created new EventSource
    const lastEs = (globalThis as any).__lastEventSource as MockEventSource
    expect(lastEs).toBe(firstEs)
  })
})
