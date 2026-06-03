import { Alert, AlertTitle, Box } from '@mui/material'
import type { AlertEvent } from '@/api/alerts'

interface SafetyAlertBannerProps {
  alerts: AlertEvent[]
  loading?: boolean
}

function getAlertSeverityColor(severity: AlertEvent['severity']): 'error' | 'warning' | 'info' {
  if (severity === 'CRITICAL') return 'error'
  if (severity === 'HIGH') return 'warning'
  return 'info'
}

/**
 * Sticky banner showing open STRUCTURAL alerts for a building.
 * P0 (CRITICAL) banners are non-dismissible per BR-010 — operator must review.
 */
export function SafetyAlertBanner({ alerts, loading = false }: SafetyAlertBannerProps) {
  if (loading || alerts.length === 0) return null

  // Show at most 3 banners to avoid overwhelming the UI; prioritise CRITICAL first
  const sorted = [...alerts].sort((a, b) => {
    const order: Record<string, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }
    return (order[a.severity] ?? 99) - (order[b.severity] ?? 99)
  })
  const visible = sorted.slice(0, 3)

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, mb: 2 }}>
      {visible.map(alert => {
        const isCritical = alert.severity === 'CRITICAL'
        return (
          <Alert
            key={alert.id}
            severity={getAlertSeverityColor(alert.severity)}
            variant="filled"
            // BR-010: P0 (CRITICAL) is non-dismissible — operator must review before clearing
            onClose={isCritical ? undefined : () => {}}
            role="alert"
            aria-live="assertive"
          >
            <AlertTitle>
              {isCritical ? '⚠ P0 — Cần kiểm tra ngay' : 'P1 — Cảnh báo kết cấu'}
            </AlertTitle>
            Cảm biến {alert.sensorId ?? 'không xác định'} —{' '}
            {alert.measureType}: {alert.value} (ngưỡng: {alert.threshold})
            {isCritical && (
              <Box component="span" sx={{ display: 'block', fontSize: '0.75rem', mt: 0.5 }}>
                Hệ thống không tự động sơ tán — vui lòng xác nhận trước khi hành động
              </Box>
            )}
          </Alert>
        )
      })}
    </Box>
  )
}
