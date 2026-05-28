import { apiClient } from './client'

export interface BmsDevice {
  id: string
  tenantId: string
  deviceName: string
  protocol: 'MODBUS_TCP' | 'BACNET_IP' | 'MANUAL'
  host: string | null
  port: number | null
  unitId: number | null
  deviceId: number | null
  pollInterval: number | null
  lastSeen: string | null
  status: string
  metadata: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
}

export interface BmsDeviceRequest {
  deviceName: string
  protocol: string
  host?: string | null
  port?: number | null
  unitId?: number | null
  deviceId?: number | null
  pollInterval?: number | null
  metadata?: Record<string, unknown> | null
}

export async function getBmsDevices(): Promise<BmsDevice[]> {
  const { data } = await apiClient.get('/api/v1/bms/devices')
  return data
}

export async function createBmsDevice(req: BmsDeviceRequest): Promise<BmsDevice> {
  const { data } = await apiClient.post('/api/v1/bms/devices', req)
  return data
}

export async function deleteBmsDevice(id: string): Promise<void> {
  await apiClient.delete(`/api/v1/bms/devices/${id}`)
}

export async function sendBmsCommand(id: string, commandType: string, payload: Record<string, unknown>): Promise<{ commandId: string; status: string }> {
  const { data } = await apiClient.post(`/api/v1/bms/devices/${id}/commands`, { commandType, payload })
  return data
}
