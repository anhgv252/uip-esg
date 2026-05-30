import { Box, Card, CardContent, Typography, Button, Alert, Chip, Stack, IconButton, Tooltip } from '@mui/material'
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive'
import NotificationsOffIcon from '@mui/icons-material/NotificationsOff'
import DeleteIcon from '@mui/icons-material/Delete'
import {
  usePushNotificationRegistration,
  usePushSubscriptions,
} from '@/hooks/usePushSubscription'
import { useUnsubscribeFromPush } from '@/hooks/usePushSubscription'
import type { PushSubscriptionResponse } from '@/api/pushSubscription'

/**
 * S6-C02 — Notification Settings page.
 * Allows users to manage push notification subscriptions.
 * Route: /settings/notifications
 */
export default function NotificationSettingsPage() {
  const {
    isSupported,
    isSubscribed,
    permissionState,
    error,
    subscribe,
    unsubscribe,
    isSubscribing,
    isUnsubscribing,
  } = usePushNotificationRegistration()

  const { data: subscriptions, isLoading } = usePushSubscriptions()
  const unsubscribeMutation = useUnsubscribeFromPush()

  const handleDelete = (sub: PushSubscriptionResponse) => {
    unsubscribeMutation.mutate(sub.id)
  }

  return (
    <Box sx={{ p: 3, maxWidth: 800, mx: 'auto' }}>
      <Typography variant="h4" gutterBottom>
        Notification Settings
      </Typography>

      {!isSupported && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          Push notifications are not supported in this browser.
        </Alert>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {/* Permission Status */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: 2 }}>
            {isSubscribed ? (
              <NotificationsActiveIcon color="success" fontSize="large" />
            ) : (
              <NotificationsOffIcon color="disabled" fontSize="large" />
            )}
            <Box>
              <Typography variant="h6">Browser Notifications</Typography>
              <Typography variant="body2" color="text.secondary">
                Permission: <Chip
                  label={permissionState}
                  size="small"
                  color={
                    permissionState === 'granted' ? 'success' :
                    permissionState === 'denied' ? 'error' : 'default'
                  }
                />
              </Typography>
            </Box>
          </Stack>

          <Stack direction="row" spacing={2}>
            {!isSubscribed && (
              <Button
                variant="contained"
                onClick={subscribe}
                disabled={isSubscribing || !isSupported || permissionState === 'denied'}
                startIcon={<NotificationsActiveIcon />}
              >
                {isSubscribing ? 'Subscribing...' : 'Enable Notifications'}
              </Button>
            )}
            {isSubscribed && (
              <Button
                variant="outlined"
                color="warning"
                onClick={unsubscribe}
                disabled={isUnsubscribing}
                startIcon={<NotificationsOffIcon />}
              >
                {isUnsubscribing ? 'Unsubscribing...' : 'Disable Notifications'}
              </Button>
            )}
          </Stack>
        </CardContent>
      </Card>

      {/* Active Subscriptions */}
      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Active Subscriptions
          </Typography>

          {isLoading && <Typography color="text.secondary">Loading...</Typography>}

          {!isLoading && (!subscriptions || subscriptions.length === 0) && (
            <Typography color="text.secondary">
              No active subscriptions.
            </Typography>
          )}

          {subscriptions && subscriptions.length > 0 && (
            <Stack spacing={1}>
              {subscriptions.map((sub: PushSubscriptionResponse) => (
                <Card key={sub.id} variant="outlined" sx={{ p: 1.5 }}>
                  <Stack direction="row" alignItems="center" justifyContent="space-between">
                    <Box>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Chip label={sub.platform} size="small" />
                        <Chip
                          label={sub.active ? 'Active' : 'Inactive'}
                          size="small"
                          color={sub.active ? 'success' : 'default'}
                        />
                      </Stack>
                      <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                        {sub.endpoint.length > 60
                          ? `${sub.endpoint.substring(0, 60)}...`
                          : sub.endpoint}
                      </Typography>
                    </Box>
                    <Tooltip title="Remove subscription">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDelete(sub)}
                        disabled={unsubscribeMutation.isPending}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                </Card>
              ))}
            </Stack>
          )}
        </CardContent>
      </Card>
    </Box>
  )
}
