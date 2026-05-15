import { apiClient } from './client'

export interface EnergyAggregateRequest {
  tenantId: string
  buildingIds: string[]
  fromEpoch: number
  toEpoch: number
}

export interface BuildingEnergyBreakdown {
  buildingId: string
  totalKwh: number
  peakDemandKw: number
}

export interface EnergyAggregateResponse {
  tenantId: string
  fromEpoch: number
  toEpoch: number
  totalKwh: number
  peakDemandKw: number
  averagePowerFactor: number
  buildings: BuildingEnergyBreakdown[]
}

export interface TenantEmissionsBreakdown {
  buildingId: string
  totalCo2Kg: number
  avgCo2PerHour: number
}

export interface EmissionsAggregateResponse {
  tenantId: string
  fromEpoch: number
  toEpoch: number
  totalCo2Kg: number
  buildings: TenantEmissionsBreakdown[]
}

export interface AqiDataPoint {
  buildingId: string
  timestampEpoch: number
  avgAqi: number
  maxAqi: number
}

export interface AqiTrendResponse {
  tenantId: string
  dataPoints: AqiDataPoint[]
}

export async function fetchEnergyAnalytics(req: EnergyAggregateRequest): Promise<EnergyAggregateResponse> {
  const { data } = await apiClient.post<EnergyAggregateResponse>('/analytics/energy-aggregate', req)
  return data
}

export async function fetchEmissionsAnalytics(req: EnergyAggregateRequest): Promise<EmissionsAggregateResponse> {
  const { data } = await apiClient.post<EmissionsAggregateResponse>('/analytics/emissions-aggregate', req)
  return data
}

export async function fetchAqiTrend(req: EnergyAggregateRequest): Promise<AqiTrendResponse> {
  const { data } = await apiClient.post<AqiTrendResponse>('/analytics/aqi-trend', req)
  return data
}
