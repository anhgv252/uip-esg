import { apiClient } from './client'

export interface TenantConfig {
  tenantId: string
  features: Record<string, { enabled: boolean }>
  branding: {
    partnerName: string
    primaryColor: string
    logoUrl: string | null
  }
}

export const tenantConfigApi = {
  getConfig: () =>
    apiClient.get<TenantConfig>('/tenant/config').then((r) => r.data),
}
