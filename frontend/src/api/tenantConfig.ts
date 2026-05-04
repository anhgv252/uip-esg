import { apiClient } from './client'
import { type TenantConfig } from '@/types/tenant'

export type { TenantConfig }

export const tenantConfigApi = {
  getConfig: () =>
    apiClient.get<TenantConfig>('/tenant/config').then((r) => r.data),
}
