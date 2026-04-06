import { apiClient } from './client'

export interface UserSummaryDto {
  id: string
  username: string
  email: string
  role: string
  active: boolean
  createdAt: string
}

export interface UserPage {
  content: UserSummaryDto[]
  totalElements: number
  totalPages: number
  number: number
}

export interface SensorRegistryDto {
  id: string
  sensorId: string
  sensorName: string
  sensorType: string
  districtCode: string | null
  latitude: number | null
  longitude: number | null
  active: boolean
  lastSeenAt: string | null
  installedAt: string
}

export const getAdminUsers = (params?: { page?: number; size?: number }) =>
  apiClient.get<UserPage>('/admin/users', { params }).then((r) => r.data)

export const changeUserRole = (username: string, role: string) =>
  apiClient
    .put<UserSummaryDto>(`/admin/users/${username}/role`, null, { params: { role } })
    .then((r) => r.data)

export const deactivateUser = (username: string) =>
  apiClient.put<UserSummaryDto>(`/admin/users/${username}/deactivate`).then((r) => r.data)

export const getAdminSensors = () =>
  apiClient.get<SensorRegistryDto[]>('/admin/sensors').then((r) => r.data)

export const toggleSensorStatus = (id: string, active: boolean) =>
  apiClient
    .put<SensorRegistryDto>(`/admin/sensors/${id}/status`, null, { params: { active } })
    .then((r) => r.data)
