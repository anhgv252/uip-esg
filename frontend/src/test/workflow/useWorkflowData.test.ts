import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import {
  useProcessDefinitions,
  useProcessInstances,
  useInstanceVariables,
  useStartProcess,
} from '@/hooks/useWorkflowData'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const asAny = (fn: unknown) => fn as ReturnType<typeof vi.fn>

vi.mock('@/api/workflow', () => ({
  getProcessDefinitions: vi.fn(),
  getProcessInstances: vi.fn(),
  getInstanceVariables: vi.fn(),
  startProcess: vi.fn(),
  getProcessDefinitionXml: vi.fn(),
}))

import {
  getProcessDefinitions,
  getProcessInstances,
  getInstanceVariables,
  startProcess,
} from '@/api/workflow'

function makeWrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

describe('useProcessDefinitions', () => {
  beforeEach(() => asAny(getProcessDefinitions).mockReset())

  it('returns definitions from API', async () => {
    const defs = [{ id: 'd1', key: 'aqi-alert', version: 1, name: 'AQI Alert', tenantId: null, deploymentId: 'dep1', suspended: false }]
    asAny(getProcessDefinitions).mockResolvedValue(defs)

    const { result } = renderHook(() => useProcessDefinitions(), { wrapper: makeWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(defs)
    expect(getProcessDefinitions).toHaveBeenCalledOnce()
  })
})

describe('useProcessInstances', () => {
  beforeEach(() => asAny(getProcessInstances).mockReset())

  it('calls API with undefined status when no filter set', async () => {
    const page = { content: [], totalElements: 0, totalPages: 0, number: 0 }
    asAny(getProcessInstances).mockResolvedValue(page)

    const { result } = renderHook(() => useProcessInstances(), { wrapper: makeWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(getProcessInstances).toHaveBeenCalledWith({
      status: undefined,
      page: 0,
      size: 20,
    })
  })

  it('passes status filter when provided', async () => {
    const page = { content: [], totalElements: 0, totalPages: 0, number: 0 }
    asAny(getProcessInstances).mockResolvedValue(page)

    const { result } = renderHook(
      () => useProcessInstances('ACTIVE', 1, 10),
      { wrapper: makeWrapper() },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(getProcessInstances).toHaveBeenCalledWith({ status: 'ACTIVE', page: 1, size: 10 })
  })
})

describe('useInstanceVariables', () => {
  beforeEach(() => asAny(getInstanceVariables).mockReset())

  it('does not call API when instanceId is null', () => {
    const { result } = renderHook(
      () => useInstanceVariables(null),
      { wrapper: makeWrapper() },
    )
    expect(result.current.fetchStatus).toBe('idle')
    expect(getInstanceVariables).not.toHaveBeenCalled()
  })

  it('calls API when instanceId is provided', async () => {
    const vars = { aqiValue: 250, aiDecision: 'P1_WARNING' }
    asAny(getInstanceVariables).mockResolvedValue(vars)

    const { result } = renderHook(
      () => useInstanceVariables('inst-001'),
      { wrapper: makeWrapper() },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(vars)
    expect(getInstanceVariables).toHaveBeenCalledWith('inst-001')
  })
})

describe('useStartProcess', () => {
  beforeEach(() => asAny(startProcess).mockReset())

  it('calls startProcess with processKey and variables', async () => {
    const created = { id: 'new-1', state: 'ACTIVE', processDefinitionKey: 'aqi-alert', businessKey: null, startTime: new Date().toISOString(), processDefinitionId: 'def-1', variables: {} }
    asAny(startProcess).mockResolvedValue(created)

    const { result } = renderHook(() => useStartProcess(), { wrapper: makeWrapper() })

    result.current.mutate({ processKey: 'aqi-alert', variables: { zone: 'Q1' } })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(startProcess).toHaveBeenCalledWith('aqi-alert', { zone: 'Q1' })
  })

  it('invalidates instances query on success', async () => {
    const created = { id: 'new-1', state: 'ACTIVE', processDefinitionKey: 'aqi-alert', businessKey: null, startTime: new Date().toISOString(), processDefinitionId: 'def-1', variables: {} }
    asAny(startProcess).mockResolvedValue(created)

    const { result } = renderHook(() => useStartProcess(), { wrapper: makeWrapper() })

    result.current.mutate({ processKey: 'aqi-alert', variables: {} })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(startProcess).toHaveBeenCalledWith('aqi-alert', {})
  })
})
