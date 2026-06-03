import { useState } from 'react'
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Divider,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material'
import RefreshIcon from '@mui/icons-material/Refresh'
import { format } from 'date-fns'
import { useAlerts } from '@/hooks/useAlertManagement'
import { useQueryClient } from '@tanstack/react-query'
import type { AlertEvent } from '@/api/alerts'

type SeverityFilter = '' | 'CRITICAL' | 'HIGH' | 'MEDIUM'

const SEVERITY_LABELS: Record<string, string> = {
  CRITICAL: 'P0',
  HIGH: 'P1',
  MEDIUM: 'P2',
}

const SEVERITY_COLORS: Record<string, 'error' | 'warning' | 'default'> = {
  CRITICAL: 'error',
  HIGH: 'warning',
  MEDIUM: 'default',
}

function AlertRow({ alert }: { alert: AlertEvent }) {
  const chipColor = SEVERITY_COLORS[alert.severity] ?? 'default'
  const label = SEVERITY_LABELS[alert.severity] ?? alert.severity
  return (
    <>
      <ListItem alignItems="flex-start" sx={{ px: 2, py: 1 }}>
        <ListItemText
          primary={
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.25 }}>
              <Chip label={label} color={chipColor} size="small" sx={{ height: 20, fontSize: 10 }} />
              <Typography variant="body2" fontWeight={600} noWrap>
                {alert.module} — {alert.measureType}
              </Typography>
            </Box>
          }
          secondary={
            <Box component="span">
              <Typography variant="caption" color="text.secondary" component="span">
                {alert.sensorId ?? '—'} · {format(new Date(alert.detectedAt), 'dd/MM HH:mm')}
              </Typography>
              <br />
              <Typography variant="caption" component="span">
                Giá trị: {alert.value} (ngưỡng: {alert.threshold})
              </Typography>
            </Box>
          }
        />
      </ListItem>
      <Divider component="li" />
    </>
  )
}

export function MobileAlertsPage() {
  const [severity, setSeverity] = useState<SeverityFilter>('')
  const queryClient = useQueryClient()

  const { data, isLoading, error, refetch, isFetching } = useAlerts({
    status: 'OPEN',
    severity: severity || undefined,
    size: 30,
  })

  const alerts = data?.content ?? []

  function handleRefresh() {
    queryClient.invalidateQueries({ queryKey: ['alerts'] })
    refetch()
  }

  return (
    <Box>
      {/* Header */}
      <Box sx={{ px: 2, pt: 2, pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
          <Typography variant="h6" fontWeight={700}>Cảnh báo</Typography>
          <Chip
            icon={<RefreshIcon sx={{ fontSize: 14 }} />}
            label={isFetching ? 'Đang tải…' : 'Làm mới'}
            size="small"
            onClick={handleRefresh}
            disabled={isFetching}
            variant="outlined"
            aria-label="Làm mới danh sách cảnh báo"
          />
        </Box>

        {/* Severity filter chips — P0/P1/P2 */}
        <Stack direction="row" spacing={1} flexWrap="wrap">
          {(['', 'CRITICAL', 'HIGH', 'MEDIUM'] as const).map(s => (
            <Chip
              key={s || 'all'}
              label={s ? SEVERITY_LABELS[s] : 'Tất cả'}
              size="small"
              color={s ? SEVERITY_COLORS[s] : 'default'}
              variant={severity === s ? 'filled' : 'outlined'}
              onClick={() => setSeverity(s)}
              aria-pressed={severity === s}
              sx={{ mb: 0.5 }}
            />
          ))}
        </Stack>
      </Box>

      {/* Content */}
      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress size={32} />
        </Box>
      ) : error ? (
        <Alert severity="error" sx={{ mx: 2 }}>Không thể tải cảnh báo.</Alert>
      ) : alerts.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
          Không có cảnh báo nào đang mở
        </Typography>
      ) : (
        <List disablePadding>
          {alerts.map(alert => (
            <AlertRow key={alert.id} alert={alert} />
          ))}
        </List>
      )}

      {data && data.totalElements > 30 && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', textAlign: 'center', py: 1 }}>
          Hiển thị 30/{data.totalElements} cảnh báo
        </Typography>
      )}
    </Box>
  )
}
