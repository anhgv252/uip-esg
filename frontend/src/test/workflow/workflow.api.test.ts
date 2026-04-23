import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getProcessDefinitions,
  getProcessInstances,
  getInstanceVariables,
  startProcess,
  getProcessDefinitionXml,
} from '@/api/workflow'

const mockGet = vi.fn()
const mockPost = vi.fn()

vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
}))

describe('workflow API', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockPost.mockReset()
  })

  describe('getProcessDefinitions', () => {
    it('calls GET /workflow/definitions and returns data', async () => {
      const defs = [{ id: 'def-1', key: 'aqi-alert', version: 1 }]
      mockGet.mockResolvedValue({ data: defs })

      const result = await getProcessDefinitions()

      expect(mockGet).toHaveBeenCalledWith('/workflow/definitions')
      expect(result).toEqual(defs)
    })
  })

  describe('getProcessInstances', () => {
    it('calls GET /workflow/instances with no params by default', async () => {
      const page = { content: [], totalElements: 0, totalPages: 0, number: 0 }
      mockGet.mockResolvedValue({ data: page })

      await getProcessInstances()

      expect(mockGet).toHaveBeenCalledWith('/workflow/instances', {
        params: undefined,
      })
    })

    it('passes status, page and size params', async () => {
      mockGet.mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0, number: 0 } })

      await getProcessInstances({ status: 'ACTIVE', page: 2, size: 10 })

      expect(mockGet).toHaveBeenCalledWith('/workflow/instances', {
        params: { status: 'ACTIVE', page: 2, size: 10 },
      })
    })
  })

  describe('getInstanceVariables', () => {
    it('calls GET /workflow/instances/{id}/variables', async () => {
      const vars = { aqiValue: 245, alertLevel: 'P1_WARNING' }
      mockGet.mockResolvedValue({ data: vars })

      const result = await getInstanceVariables('instance-abc')

      expect(mockGet).toHaveBeenCalledWith('/workflow/instances/instance-abc/variables')
      expect(result).toEqual(vars)
    })
  })

  describe('startProcess', () => {
    it('calls POST /workflow/start/{processKey} with variables body', async () => {
      const newInstance = { id: 'new-1', state: 'ACTIVE', processDefinitionKey: 'aqi-alert' }
      mockPost.mockResolvedValue({ data: newInstance })

      const variables = { zone: 'QUAN_1', triggerAqi: 280 }
      const result = await startProcess('aqi-alert', variables)

      expect(mockPost).toHaveBeenCalledWith('/workflow/start/aqi-alert', variables)
      expect(result).toEqual(newInstance)
    })
  })

  describe('getProcessDefinitionXml', () => {
    it('calls GET /workflow/definitions/{id}/xml with responseType text', async () => {
      const xml = '<?xml version="1.0" encoding="UTF-8"?><definitions/>'
      mockGet.mockResolvedValue({ data: xml })

      const result = await getProcessDefinitionXml('def-1')

      expect(mockGet).toHaveBeenCalledWith('/workflow/definitions/def-1/xml', {
        responseType: 'text',
      })
      expect(result).toBe(xml)
    })
  })
})
