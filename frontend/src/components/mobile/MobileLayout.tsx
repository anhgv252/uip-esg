import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { Box, Alert } from '@mui/material'
import MobileNav from './MobileNav'
import { MobileNotificationBanner } from './MobileNotificationBanner'

export default function MobileLayout() {
  const [isOnline, setIsOnline] = useState(navigator.onLine)

  useEffect(() => {
    const handleOffline = () => setIsOnline(false)
    const handleOnline = () => setIsOnline(true)
    window.addEventListener('offline', handleOffline)
    window.addEventListener('online', handleOnline)
    return () => {
      window.removeEventListener('offline', handleOffline)
      window.removeEventListener('online', handleOnline)
    }
  }, [])

  return (
    <Box data-testid="app-shell" sx={{ pb: '64px', minHeight: '100vh' }}>
      {/* FE-9: foreground notification banner */}
      <MobileNotificationBanner />
      {!isOnline && (
        <Alert severity="warning" sx={{ borderRadius: 0 }}>You are offline</Alert>
      )}
      <Outlet />
      <MobileNav />
    </Box>
  )
}
