import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getWorkflowConfigs,
  getWorkflowConfig,
  createWorkflowConfig,
  updateWorkflowConfig,
  disableWorkflowConfig,
  testWorkflowConfig,
} from '@/api/workflowConfig'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPut = vi.fn()
const mockDelete = vi.fn()

vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: (...args: unknown[]) => mockPut(...args),
    delete: (...args: unknown[]) => mockDelete(...args),
  },
}))

describe('workflowConfig API', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockPost.mockReset()
    mockPut.mockReset()
    mockDelete.mockReset()
  })

  describe('getWorkflowConfigs', () => {
    it('calls GET /wf-config and returns data', async () => {
      const configs = [{ id: 1, scenarioKey: 'aiC01_aqiCitizenAlert', displayName: 'AQI Alert' }]
      mockGet.mockResolvedValue({ data: configs })

      const result = await getWorkflowConfigs()

      expect(mockGet).toHaveBeenCalledWith('/admin/workflow-configs')
      expect(result).toEqual(configs)
    })
  })

  describe('getWorkflowConfig', () => {
    it('calls GET /wf-config/{id}', async () => {
      const config = { id: 1, scenarioKey: 'test' }
      mockGet.mockResolvedValue({ data: config })

      const result = await getWorkflowConfig(1)

      expect(mockGet).toHaveBeenCalledWith('/admin/workflow-configs/1')
      expect(result).toEqual(config)
    })
  })

  describe('createWorkflowConfig', () => {
    it('calls POST /wf-config with config body', async () => {
      const newConfig = { scenarioKey: 'new-scenario', processKey: 'new-process', displayName: 'New', triggerType: 'KAFKA' as const, variableMapping: '{}', enabled: true }
      mockPost.mockResolvedValue({ data: { id: 10, ...newConfig } })

      const result = await createWorkflowConfig(newConfig)

      expect(mockPost).toHaveBeenCalledWith('/admin/workflow-configs', newConfig)
      expect(result.id).toBe(10)
    })
  })

  describe('updateWorkflowConfig', () => {
    it('calls PUT /wf-config/{id} with updates', async () => {
      const updates = { displayName: 'Updated Name' }
      mockPut.mockResolvedValue({ data: { id: 1, displayName: 'Updated Name' } })

      const result = await updateWorkflowConfig(1, updates)

      expect(mockPut).toHaveBeenCalledWith('/admin/workflow-configs/1', updates)
      expect(result.displayName).toBe('Updated Name')
    })
  })

  describe('disableWorkflowConfig', () => {
    it('calls DELETE /wf-config/{id}', async () => {
      mockDelete.mockResolvedValue({ status: 204 })

      await disableWorkflowConfig(1)

      expect(mockDelete).toHaveBeenCalledWith('/admin/workflow-configs/1')
    })
  })

  describe('testWorkflowConfig', () => {
    it('calls POST /wf-config/{id}/test with payload', async () => {
      const testResult = { filterMatch: true, mappedVariables: { sensorId: 'S-001' }, processKey: 'test', scenarioKey: 'test' }
      mockPost.mockResolvedValue({ data: testResult })

      const payload = { module: 'ENVIRONMENT', value: 180 }
      const result = await testWorkflowConfig(1, payload)

      expect(mockPost).toHaveBeenCalledWith('/admin/workflow-configs/1/test', payload)
      expect(result.filterMatch).toBe(true)
    })
  })
})
