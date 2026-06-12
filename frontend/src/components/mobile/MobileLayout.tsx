import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { Box, Alert } from '@mui/material'
import MobileNav from './MobileNav'
import { MobileNotificationBanner } from './MobileNotificationBanner'
import { OfflineStatusBanner } from './OfflineStatusBanner'
import { useOfflineQueue } from '@/hooks/useOfflineQueue'

export default function MobileLayout() {
  const offlineQueue = useOfflineQueue()

  const [isOnline, setIsOnline] = useState<boolean>(navigator.onLine)
  const [bannerDismissed, setBannerDismissed] = useState<boolean>(false)

  useEffect(() => {
    const handleOnline = () => {
      setIsOnline(true)
      // Reset dismissed state so banner reappears on next offline event
      setBannerDismissed(false)
    }
    const handleOffline = () => {
      setIsOnline(false)
      setBannerDismissed(false)
    }
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [])

  return (
    <Box data-testid="app-shell" sx={{ pb: '64px', minHeight: '100vh' }}>
      {/* FE-9: foreground notification banner */}
      <MobileNotificationBanner />

      {/* Offline queue status banner (v3.1-03) */}
      <OfflineStatusBanner status={offlineQueue} />

      {!isOnline && !bannerDismissed && (
        <Alert
          severity="warning"
          sx={{ borderRadius: 0 }}
          aria-label="offline-banner"
          onClose={() => setBannerDismissed(true)}
        >
          Đang offline — dữ liệu có thể không cập nhật
        </Alert>
      )}
      <Outlet />
      <MobileNav />
    </Box>
  )
}
