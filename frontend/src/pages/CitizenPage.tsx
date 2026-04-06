import { useState } from 'react'
import {
  Box, Typography, Tabs, Tab, Grid, Paper, Chip,
  CircularProgress, Alert,
} from '@mui/material'
import PeopleIcon from '@mui/icons-material/People'
import ReceiptIcon from '@mui/icons-material/Receipt'
import PersonIcon from '@mui/icons-material/Person'
import DashboardIcon from '@mui/icons-material/Dashboard'
import { useCitizenProfile, useInvoices } from '@/hooks/useCitizenData'
import InvoicePage from '@/components/citizen/InvoicePage'
import CitizenProfilePage from '@/components/citizen/CitizenProfilePage'

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

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={3}>
        <PeopleIcon color="primary" />
        <Typography variant="h5">Citizen Portal</Typography>
      </Box>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}>
        <Tab icon={<DashboardIcon />} iconPosition="start" label="Dashboard" />
        <Tab icon={<ReceiptIcon />} iconPosition="start" label="My Bills" />
        <Tab icon={<PersonIcon />} iconPosition="start" label="Profile" />
      </Tabs>

      {tab === 0 && <CitizenDashboard />}
      {tab === 1 && <InvoicePage />}
      {tab === 2 && <CitizenProfilePage />}
    </Box>
  )
}
