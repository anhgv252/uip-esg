import { Box, Typography, Card, CardContent, Chip, Skeleton, Switch, FormControlLabel, Alert } from '@mui/material'
import NotificationsIcon from '@mui/icons-material/Notifications'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import { usePushNotificationRegistration } from '@/hooks/usePushSubscription'

interface AlertDto {
  id: string
  alertType: string
  severity: string
  message: string
  acknowledged: boolean
  createdAt: string
}

const SEVERITY_COLOR: Record<string, 'error' | 'warning' | 'info' | 'success'> = {
  P0: 'error',
  P1: 'warning',
  P2: 'info',
  P3: 'success',
}

export default function MobileNotificationsPage() {
  const { data: alerts, isLoading } = useQuery({
    queryKey: ['citizen-alerts'],
    queryFn: () => apiClient.get<AlertDto[]>('/alerts').then((r) => r.data),
    staleTime: 15_000,
  })

  const {
    isSupported,
    isSubscribed,
    permissionState,
    error: pushError,
    subscribe,
    unsubscribe,
    isSubscribing,
  } = usePushNotificationRegistration()

  return (
    <Box sx={{ p: 2 }}>
      <Typography variant="h6" fontWeight={600} gutterBottom>
        Notifications
      </Typography>

      {isSupported && (
        <Card sx={{ mb: 2 }}>
          <CardContent sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', '&:last-child': { pb: 1.5 } }}>
            <Box display="flex" alignItems="center" gap={1}>
              <NotificationsIcon fontSize="small" color={isSubscribed ? 'primary' : 'action'} />
              <Box>
                <Typography variant="body2" fontWeight={600}>
                  Push Notifications
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {permissionState === 'denied'
                    ? 'Blocked — enable in browser settings'
                    : isSubscribed
                      ? 'Active — you will receive AQI alerts'
                      : 'Off — enable to receive critical alerts'}
                </Typography>
              </Box>
            </Box>
            <FormControlLabel
              control={
                <Switch
                  checked={isSubscribed}
                  onChange={isSubscribed ? unsubscribe : subscribe}
                  disabled={isSubscribing || permissionState === 'denied'}
                  color="primary"
                />
              }
              label=""
              sx={{ mr: 0 }}
            />
          </CardContent>
        </Card>
      )}

      {pushError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {pushError}
        </Alert>
      )}

      <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
        Recent Alerts
      </Typography>

      {isLoading ? (
        Array.from({ length: 3 }).map((_, i) => (
          <Card key={i} sx={{ mb: 1 }}>
            <CardContent>
              <Skeleton height={20} width="70%" />
              <Skeleton height={16} width="40%" />
            </CardContent>
          </Card>
        ))
      ) : !alerts || alerts.length === 0 ? (
        <Card>
          <CardContent>
            <Typography variant="body2" color="text.secondary" textAlign="center" py={2}>
              No notifications yet.
            </Typography>
          </CardContent>
        </Card>
      ) : (
        alerts.map((alert) => (
          <Card key={alert.id} sx={{ mb: 1, opacity: alert.acknowledged ? 0.6 : 1 }}>
            <CardContent sx={{ '&:last-child': { pb: 1.5 } }}>
              <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                <Box flex={1}>
                  <Typography variant="body2" fontWeight={600}>
                    {alert.alertType.replace(/_/g, ' ')}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {new Date(alert.createdAt).toLocaleString('vi-VN')}
                  </Typography>
                </Box>
                <Chip
                  label={alert.severity}
                  color={SEVERITY_COLOR[alert.severity] ?? 'default'}
                  size="small"
                />
              </Box>
              <Typography variant="body2" sx={{ mt: 0.5 }} color="text.secondary">
                {alert.message}
              </Typography>
            </CardContent>
          </Card>
        ))
      )}
    </Box>
  )
}
