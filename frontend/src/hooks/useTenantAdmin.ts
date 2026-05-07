import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getTenantUsers,
  inviteUser,
  updateUserRole,
  getTenantUsage,
  getTenantSettings,
  updateTenantSettings,
  type InviteUserRequest,
  type UpdateRoleRequest,
  type UpdateSettingsRequest,
} from '@/api/tenantAdmin'

const keys = {
  users: (tenantId: string) => ['tenant-admin', tenantId, 'users'] as const,
  usage: (tenantId: string, from: string, to: string) =>
    ['tenant-admin', tenantId, 'usage', from, to] as const,
  settings: (tenantId: string) => ['tenant-admin', tenantId, 'settings'] as const,
}

export function useTenantUsers(tenantId: string | null) {
  return useQuery({
    queryKey: keys.users(tenantId!),
    queryFn: () => getTenantUsers(tenantId!),
    enabled: !!tenantId,
  })
}

export function useInviteUser(tenantId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: InviteUserRequest) => inviteUser(tenantId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tenant-admin', tenantId] }),
  })
}

export function useUpdateUserRole(tenantId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, body }: { userId: string; body: UpdateRoleRequest }) =>
      updateUserRole(tenantId, userId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tenant-admin', tenantId] }),
  })
}

export function useTenantUsage(tenantId: string | null, from: string, to: string) {
  return useQuery({
    queryKey: keys.usage(tenantId!, from, to),
    queryFn: () => getTenantUsage(tenantId!, from, to),
    enabled: !!tenantId && !!from && !!to,
  })
}

export function useTenantSettings(tenantId: string | null) {
  return useQuery({
    queryKey: keys.settings(tenantId!),
    queryFn: () => getTenantSettings(tenantId!),
    enabled: !!tenantId,
  })
}

export function useUpdateSettings(tenantId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateSettingsRequest) => updateTenantSettings(tenantId, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tenant-admin', tenantId] }),
  })
}
