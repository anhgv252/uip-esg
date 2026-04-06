import {
  Box,
  Typography,
  List,
  ListItem,
  ListItemText,
  Chip,
  Divider,
  CircularProgress,
} from '@mui/material'
import { formatDistanceToNow } from 'date-fns'
import type { AlertEvent } from '@/api/alerts'

const SEVERITY_COLORS = {
  LOW: 'default',
  MEDIUM: 'warning',
  HIGH: 'error',
  CRITICAL: 'error',
} as const

interface AlertFeedPanelProps {
  alerts: AlertEvent[]
  loading?: boolean
}

export default function AlertFeedPanel({ alerts, loading }: AlertFeedPanelProps) {
  return (
    <Box
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 2,
        overflow: 'hidden',
      }}
    >
      <Box sx={{ px: 2, py: 1.5, bgcolor: 'background.paper', borderBottom: '1px solid', borderColor: 'divider' }}>
        <Typography variant="subtitle1" fontWeight={600}>
          Recent Alerts
        </Typography>
      </Box>

      <Box sx={{ flex: 1, overflowY: 'auto' }}>
        {loading && (
          <Box display="flex" justifyContent="center" p={3}>
            <CircularProgress size={24} />
          </Box>
        )}

        {!loading && alerts.length === 0 && (
          <Box p={2}>
            <Typography color="text.secondary" variant="body2">
              No recent alerts
            </Typography>
          </Box>
        )}

        <List dense disablePadding>
          {alerts.map((alert, idx) => (
            <Box key={alert.id}>
              <ListItem alignItems="flex-start" sx={{ px: 2, py: 1 }}>
                <ListItemText
                  primary={
                    <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                      <Chip
                        label={alert.severity}
                        size="small"
                        color={SEVERITY_COLORS[alert.severity] ?? 'default'}
                        sx={{ fontSize: 10, height: 20 }}
                      />
                      <Typography variant="body2" fontWeight={500} noWrap>
                        {alert.ruleName}
                      </Typography>
                    </Box>
                  }
                  secondary={
                    <Box mt={0.5}>
                      <Typography variant="caption" display="block" color="text.secondary">
                        {alert.sensorId ?? alert.module} — {alert.measureType}: {alert.value}
                      </Typography>
                      <Typography variant="caption" color="text.disabled">
                        {formatDistanceToNow(new Date(alert.detectedAt), { addSuffix: true })}
                      </Typography>
                    </Box>
                  }
                />
              </ListItem>
              {idx < alerts.length - 1 && <Divider component="li" />}
            </Box>
          ))}
        </List>
      </Box>
    </Box>
  )
}
