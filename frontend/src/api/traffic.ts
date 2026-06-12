import { apiClient } from './client'

export interface TrafficCountDto {
  id: string
  intersectionId: string
  recordedAt: string
  vehicleCount: number
  vehicleType: string | null
}

export interface TrafficIncidentDto {
  id: string
  intersectionId: string
  incidentType: 'ACCIDENT' | 'CONGESTION' | 'ROADWORK'
  description: string
  latitude: number | null
  longitude: number | null
  status: 'OPEN' | 'RESOLVED' | 'ESCALATED'
  occurredAt: string
  resolvedAt: string | null
  createdAt: string
}

export interface TrafficIncidentPage {
  content: TrafficIncidentDto[]
  totalElements: number
  totalPages: number
  number: number
}

export interface CongestionGeoJsonDto {
  type: 'FeatureCollection'
  features: Array<{
    type: 'Feature'
    geometry: {
      type: 'LineString'
      coordinates: Array<[number, number]>
    }
    properties: {
      name: string
      congestion: 'FREE' | 'MODERATE' | 'HEAVY' | 'STANDSTILL'
      speed: number
    }
  }>
}

export const getTrafficCounts = (params?: {
  intersection?: string
  from?: string
  to?: string
  tenantId?: string
}) =>
  apiClient
    .get<TrafficCountDto[]>('/traffic/counts', { params })
    .then((r) => r.data)

export const getTrafficIncidents = (params?: {
  status?: string
  page?: number
  size?: number
  tenantId?: string
}) =>
  apiClient
    .get<TrafficIncidentPage>('/traffic/incidents', { params })
    .then((r) => r.data)

export const getCongestionMap = (tenantId?: string) =>
  apiClient.get<CongestionGeoJsonDto>('/traffic/congestion-map', { params: { tenantId } }).then((r) => r.data)

export const updateIncidentStatus = (id: string, status: string) =>
  apiClient
    .put<TrafficIncidentDto>(`/traffic/incidents/${id}/status`, null, {
      params: { status },
    })
    .then((r) => r.data)

// ── Traffic Flow API (GAP-029) ─────────────────────────────────────────

export interface TrafficFlowDto {
  intersectionId: string
  timestamp: string
  avgSpeed: number
  vehicleCount: number
  congestionLevel: 'FREE' | 'MODERATE' | 'HEAVY' | 'STANDSTILL'
}

export const getTrafficFlow = (params?: {
  intersection?: string
  from?: string
  to?: string
}) =>
  apiClient
    .get<TrafficFlowDto[]>('/traffic/flow', { params })
    .then((r) => r.data)
