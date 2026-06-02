import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getAdminUsers,
  getAdminSensors,
  changeUserRole,
  deactivateUser,
  toggleSensorStatus,
  createSensor,
  type CreateSensorRequest,
} from '@/api/adminMgmt'
import {
  listAllTenants,
  createTenant,
  updateTenantFeature,
  getTenantFeatures,
  getTenantUsers,
  inviteUser,
  type CreateTenantRequest,
  type UpdateFeatureRequest,
  type InviteUserRequest,
} from '@/api/tenantAdmin'

export function useAdminUsers(page = 0) {
  return useQuery({
    queryKey: ['admin-users', page],
    queryFn: () => getAdminUsers({ page, size: 50 }),
  })
}

export function useAdminSensors() {
  return useQuery({
    queryKey: ['admin-sensors'],
    queryFn: getAdminSensors,
  })
}

export function useChangeUserRole() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ username, role }: { username: string; role: string }) =>
      changeUserRole(username, role),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}

export function useDeactivateUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (username: string) => deactivateUser(username),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })
}

export function useToggleSensorStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      toggleSensorStatus(id, active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-sensors'] }),
  })
}

export function useCreateSensor() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateSensorRequest) => createSensor(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-sensors'] }),
  })
}

export function useListTenants() {
  return useQuery({
    queryKey: ['admin-tenants'],
    queryFn: listAllTenants,
  })
}

export function useCreateTenant() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateTenantRequest) => createTenant(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-tenants'] }),
  })
}

export function useUpdateTenantFeature() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ tenantId, ...body }: { tenantId: string } & UpdateFeatureRequest) =>
      updateTenantFeature(tenantId, body),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['admin-tenants'] })
      qc.invalidateQueries({ queryKey: ['tenant-features', variables.tenantId] })
    },
  })
}

export function useTenantFeatures(tenantId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['tenant-features', tenantId],
    queryFn: () => getTenantFeatures(tenantId),
    enabled,
  })
}

export function useTenantUsers(tenantId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['tenant-users', tenantId],
    queryFn: () => getTenantUsers(tenantId),
    enabled,
  })
}

export function useInviteTenantUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ tenantId, body }: { tenantId: string; body: InviteUserRequest }) =>
      inviteUser(tenantId, body),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: ['tenant-users', variables.tenantId] })
    },
  })
}
