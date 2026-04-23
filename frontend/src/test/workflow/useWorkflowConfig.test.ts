import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import {
  useWorkflowConfigs,
  useWorkflowConfig,
  useCreateWorkflowConfig,
  useUpdateWorkflowConfig,
  useDisableWorkflowConfig,
  useTestWorkflowConfig,
} from '@/hooks/useWorkflowConfig'
import type { TriggerConfig } from '@/api/workflowConfig'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const asAny = (fn: unknown) => fn as ReturnType<typeof vi.fn>

vi.mock('@/api/workflowConfig', () => ({
  getWorkflowConfigs: vi.fn(),
  getWorkflowConfig: vi.fn(),
  createWorkflowConfig: vi.fn(),
  updateWorkflowConfig: vi.fn(),
  disableWorkflowConfig: vi.fn(),
  testWorkflowConfig: vi.fn(),
}))

import {
  getWorkflowConfigs,
  getWorkflowConfig,
  createWorkflowConfig,
  updateWorkflowConfig,
  disableWorkflowConfig,
  testWorkflowConfig,
} from '@/api/workflowConfig'

function makeWrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: qc }, children)
}

const MOCK_CONFIG: TriggerConfig = {
  id: 1,
  scenarioKey: 'aiC01_aqiCitizenAlert',
  processKey: 'aiC01_aqiCitizenAlert',
  displayName: 'Cảnh báo AQI cho cư dân',
  description: 'AQI alert',
  triggerType: 'KAFKA',
  kafkaTopic: 'UIP.flink.alert.detected.v1',
  kafkaConsumerGroup: 'uip-workflow-generic',
  filterConditions: '[{"field":"module","op":"EQ","value":"ENVIRONMENT"}]',
  variableMapping: '{"sensorId":{"source":"payload.sensorId","default":"UNKNOWN"}}',
  scheduleCron: null,
  scheduleQueryBean: null,
  promptTemplatePath: 'prompts/aiC01.txt',
  aiConfidenceThreshold: 0.85,
  deduplicationKey: 'sensorId',
  enabled: true,
  createdAt: '2026-04-20T10:00:00',
  updatedAt: '2026-04-20T10:00:00',
  updatedBy: 'admin',
}

describe('useWorkflowConfigs', () => {
  beforeEach(() => asAny(getWorkflowConfigs).mockReset())

  it('returns configs from API', async () => {
    asAny(getWorkflowConfigs).mockResolvedValue([MOCK_CONFIG])

    const { result } = renderHook(() => useWorkflowConfigs(), { wrapper: makeWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([MOCK_CONFIG])
    expect(getWorkflowConfigs).toHaveBeenCalledOnce()
  })
})

describe('useWorkflowConfig', () => {
  beforeEach(() => asAny(getWorkflowConfig).mockReset())

  it('does not call API when id is null', () => {
    const { result } = renderHook(() => useWorkflowConfig(null), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
    expect(getWorkflowConfig).not.toHaveBeenCalled()
  })

  it('calls API when id is provided', async () => {
    asAny(getWorkflowConfig).mockResolvedValue(MOCK_CONFIG)

    const { result } = renderHook(() => useWorkflowConfig(1), { wrapper: makeWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(MOCK_CONFIG)
    expect(getWorkflowConfig).toHaveBeenCalledWith(1)
  })
})

describe('useCreateWorkflowConfig', () => {
  beforeEach(() => asAny(createWorkflowConfig).mockReset())

  it('calls createWorkflowConfig and invalidates queries', async () => {
    asAny(createWorkflowConfig).mockResolvedValue({ ...MOCK_CONFIG, id: 99 })

    const { result } = renderHook(() => useCreateWorkflowConfig(), { wrapper: makeWrapper() })

    result.current.mutate({ scenarioKey: 'new', processKey: 'new', displayName: 'New', triggerType: 'KAFKA', variableMapping: '{}', enabled: true })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(createWorkflowConfig).toHaveBeenCalledOnce()
  })
})

describe('useUpdateWorkflowConfig', () => {
  beforeEach(() => asAny(updateWorkflowConfig).mockReset())

  it('calls updateWorkflowConfig with id and config', async () => {
    asAny(updateWorkflowConfig).mockResolvedValue({ ...MOCK_CONFIG, displayName: 'Updated' })

    const { result } = renderHook(() => useUpdateWorkflowConfig(), { wrapper: makeWrapper() })

    result.current.mutate({ id: 1, config: { displayName: 'Updated' } })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(updateWorkflowConfig).toHaveBeenCalledWith(1, { displayName: 'Updated' })
  })
})

describe('useDisableWorkflowConfig', () => {
  beforeEach(() => asAny(disableWorkflowConfig).mockReset())

  it('calls disableWorkflowConfig with id', async () => {
    asAny(disableWorkflowConfig).mockResolvedValue({ status: 204 })

    const { result } = renderHook(() => useDisableWorkflowConfig(), { wrapper: makeWrapper() })

    result.current.mutate(1)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(disableWorkflowConfig).toHaveBeenCalledWith(1)
  })
})

describe('useTestWorkflowConfig', () => {
  beforeEach(() => asAny(testWorkflowConfig).mockReset())

  it('calls testWorkflowConfig with id and payload', async () => {
    const testResult = { filterMatch: true, mappedVariables: { sensorId: 'S-001' }, processKey: 'test', scenarioKey: 'test' }
    asAny(testWorkflowConfig).mockResolvedValue(testResult)

    const { result } = renderHook(() => useTestWorkflowConfig(), { wrapper: makeWrapper() })

    result.current.mutate({ id: 1, payload: { module: 'ENVIRONMENT' } })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(testWorkflowConfig).toHaveBeenCalledWith(1, { module: 'ENVIRONMENT' })
    expect(result.current.data?.filterMatch).toBe(true)
  })
})
