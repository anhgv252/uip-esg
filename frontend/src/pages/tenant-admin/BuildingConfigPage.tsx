import { useState } from 'react'
import { Box, Typography, Card, CardContent, Grid, Switch, Skeleton, Chip, Snackbar, Alert } from '@mui/material'
import { useAuth } from '@/hooks/useAuth'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

interface BuildingDto {
  id: string
  name: string
  address: string
  district: string
  active: boolean
  sensorCount: number
}

const TENANT_BUILDINGS_KEY = (tenantId: string) => ['tenant-buildings', tenantId] as const

async function updateBuildingActive(tenantId: string, buildingId: string, active: boolean): Promise<BuildingDto> {
  const { data } = await apiClient.put<BuildingDto>(
    `/admin/tenants/${tenantId}/buildings/${buildingId}`,
    { active },
  )
  return data
}

export default function BuildingConfigPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({
    open: false, message: '', severity: 'success',
  })

  const { data: buildings, isLoading } = useQuery({
    queryKey: TENANT_BUILDINGS_KEY(user?.tenantId ?? ''),
    queryFn: () =>
      apiClient.get<BuildingDto[]>(`/admin/tenants/${user?.tenantId}/buildings`).then((r) => r.data),
    enabled: !!user?.tenantId,
  })

  const toggleMutation = useMutation({
    mutationFn: ({ buildingId, active }: { buildingId: string; active: boolean }) =>
      updateBuildingActive(user!.tenantId!, buildingId, active),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: TENANT_BUILDINGS_KEY(user!.tenantId!) })
      setSnackbar({ open: true, message: 'Building status updated', severity: 'success' })
    },
    onError: () => {
      setSnackbar({ open: true, message: 'Failed to update building status', severity: 'error' })
    },
  })

  const handleToggle = (buildingId: string, currentActive: boolean) => {
    toggleMutation.mutate({ buildingId, active: !currentActive })
  }

  return (
    <Box>
      <Typography variant="h5" component="h2" gutterBottom>
        Building Configuration
      </Typography>

      {isLoading ? (
        <Grid container spacing={2}>
          {Array.from({ length: 3 }).map((_, i) => (
            <Grid item xs={12} sm={6} key={i}>
              <Skeleton variant="rounded" height={100} />
            </Grid>
          ))}
        </Grid>
      ) : !buildings?.length ? (
        <Typography color="text.secondary" mt={2}>
          No buildings found for this tenant.
        </Typography>
      ) : (
        <Grid container spacing={2}>
          {buildings.map((b) => (
            <Grid item xs={12} sm={6} md={4} key={b.id}>
              <Card variant="outlined">
                <CardContent>
                  <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                    <Box>
                      <Typography variant="subtitle1" fontWeight={600}>{b.name}</Typography>
                      <Typography variant="body2" color="text.secondary">{b.address}</Typography>
                      <Typography variant="caption" color="text.secondary">{b.district}</Typography>
                    </Box>
                    <Switch
                      checked={b.active}
                      onChange={() => handleToggle(b.id, b.active)}
                      disabled={toggleMutation.isPending}
                      size="small"
                    />
                  </Box>
                  <Box mt={1} display="flex" gap={1}>
                    <Chip label={`${b.sensorCount} sensors`} size="small" variant="outlined" />
                    <Chip label={b.active ? 'Active' : 'Inactive'} size="small" color={b.active ? 'success' : 'default'} />
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      )}

      <Snackbar
        open={snackbar.open}
        autoHideDuration={3000}
        onClose={() => setSnackbar((s) => ({ ...s, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity={snackbar.severity} variant="filled" onClose={() => setSnackbar((s) => ({ ...s, open: false }))}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}
