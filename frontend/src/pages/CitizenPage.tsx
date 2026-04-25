import { useState, useCallback } from 'react'
import {
  Box, Typography, Tabs, Tab, Grid, Paper, Chip,
  CircularProgress, Alert, Badge,
} from '@mui/material'
import PeopleIcon from '@mui/icons-material/People'
import ReceiptIcon from '@mui/icons-material/Receipt'
import PersonIcon from '@mui/icons-material/Person'
import DashboardIcon from '@mui/icons-material/Dashboard'
import LockIcon from '@mui/icons-material/Lock'
import NotificationsIcon from '@mui/icons-material/Notifications'
import { useCitizenProfile, useInvoices } from '@/hooks/useCitizenData'
import { useNotificationSSE } from '@/hooks/useNotificationSSE'
import type { AlertNotification } from '@/hooks/useNotificationSSE'
import InvoicePage from '@/components/citizen/InvoicePage'
import CitizenProfilePage from '@/components/citizen/CitizenProfilePage'
import CitizenNotificationsPage from '@/components/citizen/CitizenNotificationsPage'
import { useAuth } from '@/hooks/useAuth'

function CitizenDashboard() {
  const { data: profile, isLoading, error } = useCitizenProfile()
  const { data: invoicePage } = useInvoices({
    year: new Date().getFullYear(),
    month: new Date().getMonth() + 1,
  })

  if (isLoading) return <CircularProgress />
  if (error) return <Alert severity="warning">Could not load citizen data.</Alert>

  const thisMonthInvoices = invoicePage?.content ?? []
  const totalAmount = thisMonthInvoices.reduce((sum, inv) => sum + Number(inv.amount), 0)
  const unpaidCount = thisMonthInvoices.filter((inv) => inv.status === 'UNPAID').length

  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Welcome back, <strong>{profile?.fullName ?? 'Citizen'}</strong>
      </Typography>
      <Grid container spacing={2} mt={0.5}>
        <Grid item xs={12} sm={4}>
          <Paper variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">This month's invoices</Typography>
            <Typography variant="h4" fontWeight={700} color="primary">
              {thisMonthInvoices.length}
            </Typography>
            {unpaidCount > 0 && (
              <Chip label={`${unpaidCount} unpaid`} color="warning" size="small" sx={{ mt: 1 }} />
            )}
          </Paper>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Paper variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">Total this month (VND)</Typography>
            <Typography variant="h5" fontWeight={700} color="primary">
              {totalAmount.toLocaleString('vi-VN')}
            </Typography>
          </Paper>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Paper variant="outlined" sx={{ p: 2, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">Household</Typography>
            <Typography variant="body1" fontWeight={600}>
              {profile?.household?.buildingName ?? 'Not linked'}
            </Typography>
            {profile?.household && (
              <Typography variant="caption" color="text.secondary">
                Floor {profile.household.floor} — Unit {profile.household.unitNumber}
              </Typography>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  )
}

export default function CitizenPage() {
  const [tab, setTab] = useState(0)
  const [newAlertCount, setNewAlertCount] = useState(0)
  const [liveAlerts, setLiveAlerts] = useState<AlertNotification[]>([])
  const { user } = useAuth()

  const handleLiveAlert = useCallback((alert: AlertNotification) => {
    setLiveAlerts((prev) => [alert, ...prev].slice(0, 50))
    setNewAlertCount((c) => c + 1)
  }, [])

  useNotificationSSE(handleLiveAlert)

  // Admin and Operator accounts do not have citizen profiles
  if (user && user.role !== 'ROLE_CITIZEN') {
    return (
      <Box>
        <Box display="flex" alignItems="center" gap={1} mb={3}>
          <PeopleIcon color="primary" />
          <Typography variant="h5">Citizen Portal</Typography>
        </Box>
        <Alert severity="info" icon={<LockIcon />}>
          <Typography variant="body2">
            Citizen Portal chỉ dành cho tài khoản <strong>ROLE_CITIZEN</strong>.
            Tài khoản <strong>{user.username}</strong> ({user.role}) không có hồ sơ citizen.
          </Typography>
          <Typography variant="body2" mt={1}>
            Đăng ký tài khoản citizen mới tại <strong>/register</strong> để trải nghiệm portal.
          </Typography>
        </Alert>
      </Box>
    )
  }

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={3}>
        <PeopleIcon color="primary" />
        <Typography variant="h5">Citizen Portal</Typography>
      </Box>

      <Tabs
        value={tab}
        onChange={(_, v: number) => {
          setTab(v)
          if (v === 3) setNewAlertCount(0)
        }}
        sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}
      >
        <Tab icon={<DashboardIcon />} iconPosition="start" label="Dashboard" />
        <Tab icon={<ReceiptIcon />} iconPosition="start" label="My Bills" />
        <Tab icon={<PersonIcon />} iconPosition="start" label="Profile" />
        <Tab
          icon={
            <Badge badgeContent={newAlertCount || undefined} color="error" max={99}>
              <NotificationsIcon />
            </Badge>
          }
          iconPosition="start"
          label="Notifications"
        />
      </Tabs>

      {tab === 0 && <CitizenDashboard />}
      {tab === 1 && <InvoicePage />}
      {tab === 2 && <CitizenProfilePage />}
      {tab === 3 && (
        <Box>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            Cảnh báo môi trường trong 48 giờ qua (HIGH / CRITICAL)
          </Typography>
          <CitizenNotificationsPage liveAlerts={liveAlerts} />
        </Box>
      )}
    </Box>
  )
}
