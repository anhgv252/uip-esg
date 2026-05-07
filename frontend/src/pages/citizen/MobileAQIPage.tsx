import { Box, Typography, Button, Skeleton, Chip, Alert, Switch, FormControlLabel } from '@mui/material'
import NotificationsIcon from '@mui/icons-material/Notifications'
import AqiGauge from '@/components/mobile/AqiGauge'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'
import type { AqiResponse } from '@/api/environment'
import { usePushNotificationRegistration } from '@/hooks/usePushSubscription'

export default function MobileAQIPage() {
  const { data: aqiData, isLoading, refetch } = useQuery({
    queryKey: ['citizen-aqi'],
    queryFn: () => apiClient.get<AqiResponse[]>('/environment/aqi').then((r) => r.data),
    staleTime: 60_000,
  })

  const {
    isSupported: pushSupported,
    isSubscribed,
    permissionState,
    error: pushError,
    subscribe,
    unsubscribe,
    isSubscribing,
  } = usePushNotificationRegistration()

  const latestAqi = aqiData?.[0]
  const aqiValue = latestAqi?.aqi ?? 0

  return (
    <Box sx={{ p: 2, width: '100%', overflowX: 'hidden' }}>
      <Typography variant="h6" fontWeight={600} gutterBottom>
        Air Quality Index
      </Typography>

      {isLoading ? (
        <Box display="flex" flexDirection="column" alignItems="center" gap={2} mt={3}>
          <Skeleton variant="circular" width={200} height={100} />
          <Skeleton width={120} />
        </Box>
      ) : (
        <Box display="flex" flexDirection="column" alignItems="center" gap={2} mt={2} sx={{ width: '100%' }}>
          <AqiGauge value={aqiValue} size={220} />

          {latestAqi && (
            <Box textAlign="center" mt={1}>
              <Typography variant="body2" color="text.secondary">
                {latestAqi.sensorName} — {latestAqi.district}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Dominant pollutant: {latestAqi.dominantPollutant}
              </Typography>
            </Box>
          )}

          <Box display="flex" gap={1} mt={2} sx={{ flexWrap: 'wrap', justifyContent: 'center', maxWidth: '100%' }}>
            {latestAqi && (
              <>
                {latestAqi.pm25 != null && <Chip label={`PM2.5: ${latestAqi.pm25}`} size="small" />}
                {latestAqi.pm10 != null && <Chip label={`PM10: ${latestAqi.pm10}`} size="small" />}
              </>
            )}
          </Box>

          {pushSupported && (
            <Box display="flex" flexDirection="column" alignItems="center" gap={1} mt={2} width="100%">
              <FormControlLabel
                control={
                  <Switch
                    checked={isSubscribed}
                    onChange={isSubscribed ? unsubscribe : subscribe}
                    disabled={isSubscribing || permissionState === 'denied'}
                    color="primary"
                    size="small"
                  />
                }
                label={
                  <Typography variant="body2" display="flex" alignItems="center" gap={0.5}>
                    <NotificationsIcon fontSize="small" />
                    {permissionState === 'denied'
                      ? 'Push blocked'
                      : isSubscribed
                        ? 'Push alerts on'
                        : 'Enable push alerts'}
                  </Typography>
                }
              />
              {pushError && <Alert severity="error" sx={{ mt: 1, width: '100%' }}>{pushError}</Alert>}
            </Box>
          )}

          <Button variant="text" size="small" onClick={() => refetch()}>
            Refresh
          </Button>
        </Box>
      )}
    </Box>
  )
}
