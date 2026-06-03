import { useCallback, useState } from 'react'
import { Alert, Box, Collapse, IconButton, Typography } from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import { useNavigate } from 'react-router-dom'
import { useNotificationSSE } from '@/hooks/useNotificationSSE'
import type { AlertNotification } from '@/hooks/useNotificationSSE'

interface BannerState {
  notification: AlertNotification | null
  visible: boolean
}

/**
 * FE-9: Foreground notification banner for mobile operator view.
 *
 * When the app is open (foreground) and a push/alert notification arrives via SSE,
 * this banner slides in at the top of the screen. Tapping it navigates to alert detail.
 *
 * Equivalent to expo-notifications foreground handler in the web context.
 */
export function MobileNotificationBanner() {
  const navigate = useNavigate()
  const [banner, setBanner] = useState<BannerState>({ notification: null, visible: false })

  const handleAlert = useCallback((notification: AlertNotification) => {
    setBanner({ notification, visible: true })
    // Auto-dismiss after 8 seconds for non-critical alerts
    if (notification.severity !== 'CRITICAL') {
      setTimeout(() => setBanner(s => ({ ...s, visible: false })), 8000)
    }
  }, [])

  useNotificationSSE(handleAlert)

  if (!banner.notification) return null

  const isCritical = banner.notification.severity === 'CRITICAL'

  function handleTap() {
    setBanner(s => ({ ...s, visible: false }))
    // Deep-link navigation to alert detail
    navigate('/alerts')
  }

  function handleClose(e: React.MouseEvent) {
    e.stopPropagation()
    setBanner(s => ({ ...s, visible: false }))
  }

  return (
    <Collapse in={banner.visible} unmountOnExit>
      <Alert
        severity={isCritical ? 'error' : 'warning'}
        variant="filled"
        onClick={handleTap}
        action={
          <IconButton
            color="inherit"
            size="small"
            onClick={handleClose}
            aria-label="Đóng thông báo"
          >
            <CloseIcon fontSize="small" />
          </IconButton>
        }
        sx={{
          cursor: 'pointer',
          borderRadius: 0,
          '&:active': { opacity: 0.9 },
        }}
        role="alert"
        aria-live="assertive"
      >
        <Box>
          <Typography variant="body2" fontWeight={700} component="span">
            {isCritical ? '⚠ Cảnh báo P0 — Cần xem xét ngay' : 'Cảnh báo mới'}
          </Typography>
          {banner.notification.ruleName && (
            <Typography variant="caption" display="block">
              {banner.notification.ruleName}
            </Typography>
          )}
          <Typography variant="caption" color="inherit" sx={{ opacity: 0.85 }}>
            Nhấn để xem chi tiết
          </Typography>
        </Box>
      </Alert>
    </Collapse>
  )
}
