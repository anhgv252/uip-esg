import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getAdminUsers,
  getAdminSensors,
  changeUserRole,
  deactivateUser,
  toggleSensorStatus,
} from '@/api/adminMgmt'

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
