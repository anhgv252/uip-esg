import { Box, Typography, List, ListItem, ListItemIcon, ListItemText, Chip, Alert, CircularProgress, Button } from '@mui/material'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import WifiIcon from '@mui/icons-material/Wifi'
import { useCitizenNotifications } from '@/hooks/useAlertManagement'
import type { AlertNotification } from '@/hooks/useNotificationSSE'
import type { AlertEvent } from '@/api/alerts'
import { formatDistanceToNow } from 'date-fns'
import { useState } from 'react'

const SEVERITY_BG: Record<string, string> = {
  CRITICAL: '#b71c1c',
  HIGH: '#f44336',
  MEDIUM: '#ff9800',
  LOW: '#4caf50',
}

interface Props {
  liveAlerts: AlertNotification[]
}

export default function CitizenNotificationsPage({ liveAlerts }: Props) {
  const [page, setPage] = useState(0)
  const { data, isLoading, error } = useCitizenNotifications(page, 20)
  const apiAlerts: AlertEvent[] = data?.content ?? []

  // Deduplicate: live alerts take precedence
  const liveIds = new Set(liveAlerts.map((a) => a.id))
  const dedupedApi = apiAlerts.filter((a) => !liveIds.has(a.id))

  if (isLoading && page === 0) return <CircularProgress sx={{ mt: 2 }} />
  if (error) return <Alert severity="error">Không thể tải thông báo.</Alert>

  const hasMore = data ? data.number < data.totalPages - 1 : false

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={1.5}>
        <WifiIcon fontSize="small" color="success" />
        <Typography variant="caption" color="text.secondary">
          Cập nhật tự động mỗi 30 giây
        </Typography>
      </Box>

      {liveAlerts.length === 0 && dedupedApi.length === 0 ? (
        <Alert severity="info">Không có cảnh báo nào trong 48 giờ qua.</Alert>
      ) : (
        <>
          <List disablePadding>
            {/* Live SSE alerts */}
            {liveAlerts.map((n) => (
              <ListItem
                key={n.id}
                divider
                sx={{ bgcolor: 'primary.50', borderLeft: '3px solid', borderColor: 'primary.main', mb: 0.5, borderRadius: 1 }}
              >
                <ListItemIcon sx={{ minWidth: 36 }}>
                  <WarningAmberIcon sx={{ color: SEVERITY_BG[n.severity] ?? '#9e9e9e' }} />
                </ListItemIcon>
                <ListItemText
                  primaryTypographyProps={{ component: 'div' }}
                  secondaryTypographyProps={{ component: 'div' }}
                  primary={
                    <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                      <Typography variant="body2" fontWeight={600}>{n.ruleName}</Typography>
                      <Chip
                        label={n.severity}
                        size="small"
                        sx={{ bgcolor: SEVERITY_BG[n.severity], color: '#fff', fontSize: 10, height: 18 }}
                      />
                      <Chip label="MỚI" size="small" color="primary" sx={{ fontSize: 10, height: 18 }} />
                    </Box>
                  }
                  secondary={
                    <Typography variant="caption" color="text.secondary">
                      {n.message} · {formatDistanceToNow(new Date(n.detectedAt), { addSuffix: true })}
                    </Typography>
                  }
                />
              </ListItem>
            ))}

            {/* API-fetched alerts */}
            {dedupedApi.map((n) => (
              <ListItem
                key={n.id}
                divider
                sx={{
                  bgcolor: n.severity === 'CRITICAL' ? '#fff8f8' : undefined,
                  borderRadius: 1,
                  mb: 0.5,
                }}
              >
                <ListItemIcon sx={{ minWidth: 36 }}>
                  <WarningAmberIcon sx={{ color: SEVERITY_BG[n.severity] ?? '#9e9e9e' }} />
                </ListItemIcon>
                <ListItemText
                  primaryTypographyProps={{ component: 'div' }}
                  secondaryTypographyProps={{ component: 'div' }}
                  primary={
                    <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                      <Typography variant="body2" fontWeight={600}>
                        {n.module} — {n.measureType}
                      </Typography>
                      <Chip
                        label={n.severity}
                        size="small"
                        sx={{ bgcolor: SEVERITY_BG[n.severity], color: '#fff', fontSize: 10, height: 18 }}
                      />
                      <Chip label={n.status} size="small" variant="outlined" sx={{ fontSize: 10, height: 18 }} />
                    </Box>
                  }
                  secondary={
                    <Typography variant="caption" color="text.secondary">
                      Sensor: {n.sensorId ?? '—'} · Giá trị: {n.value} (ngưỡng: {n.threshold}) ·{' '}
                      {formatDistanceToNow(new Date(n.detectedAt), { addSuffix: true })}
                    </Typography>
                  }
                />
              </ListItem>
            ))}
          </List>

          {hasMore && (
            <Box textAlign="center" mt={2}>
              <Button
                variant="outlined"
                size="small"
                onClick={() => setPage((p) => p + 1)}
                disabled={isLoading}
              >
                {isLoading ? <CircularProgress size={16} sx={{ mr: 1 }} /> : null}
                Tải thêm
              </Button>
            </Box>
          )}
        </>
      )}
    </Box>
  )
}
