import { apiClient } from './client'

// ── Response types ──

export interface TenantUserDto {
  id: string
  username: string
  email: string
  role: string
  active: boolean
  lastLoginAt: string | null
  createdAt: string
}

export interface TenantUsageDto {
  tenantId: string
  readingCount: number
  from: string
  to: string
}

export interface TenantSettingsBranding {
  primaryColor: string
  partnerName?: string
  logoUrl?: string | null
}

export interface TenantSettingsDto {
  tenantId: string
  configEntries: Record<string, string>
  branding: TenantSettingsBranding
}

export interface TenantSummaryDto {
  id: string
  tenantId: string
  tenantName: string
  tier: string
  active: boolean
  locationPath: string | null
  createdAt: string
}

// ── Request types ──

export interface InviteUserRequest {
  email: string
  role: string
}

export interface UpdateRoleRequest {
  role: string
}

export interface UpdateSettingsRequest {
  configKey: string
  configValue: string
}

export interface CreateTenantRequest {
  tenantId: string
  tenantName: string
  tier?: string
  locationPath?: string
}

export interface UpdateFeatureRequest {
  featureKey: string
  enabled: boolean
}

// ── API calls ──

export const getTenantUsers = (tenantId: string) =>
  apiClient.get<TenantUserDto[]>(`/admin/tenants/${tenantId}/users`).then((r) => r.data)

export const inviteUser = (tenantId: string, body: InviteUserRequest) =>
  apiClient.post(`/admin/tenants/${tenantId}/users/invite`, body).then((r) => r.data)

export const updateUserRole = (tenantId: string, userId: string, body: UpdateRoleRequest) =>
  apiClient.put(`/admin/tenants/${tenantId}/users/${userId}/role`, body).then((r) => r.data)

export const getTenantUsage = (tenantId: string, from: string, to: string) =>
  apiClient
    .get<TenantUsageDto>(`/admin/tenants/${tenantId}/usage`, { params: { from, to } })
    .then((r) => r.data)

export const getTenantSettings = (tenantId: string) =>
  apiClient.get<TenantSettingsDto>(`/admin/tenants/${tenantId}/settings`).then((r) => r.data)

export const updateTenantSettings = (tenantId: string, body: UpdateSettingsRequest) =>
  apiClient.put(`/admin/tenants/${tenantId}/settings`, body).then((r) => r.data)

export const listAllTenants = () =>
  apiClient.get<TenantSummaryDto[]>('/admin/tenants').then((r) => r.data)

export const createTenant = (body: CreateTenantRequest) =>
  apiClient.post<TenantSummaryDto>('/admin/tenants', body).then((r) => r.data)

export const updateTenantFeature = (tenantId: string, body: UpdateFeatureRequest) =>
  apiClient.put(`/admin/tenants/${tenantId}/features`, body).then((r) => r.data)

export const getTenantFeatures = (tenantId: string) =>
  apiClient.get<Record<string, boolean>>(`/admin/tenants/${tenantId}/features`).then((r) => r.data)
