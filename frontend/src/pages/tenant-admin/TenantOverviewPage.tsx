import { Box, Card, CardContent, Grid, Typography, Skeleton, Button } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import PeopleIcon from '@mui/icons-material/People'
import SensorsIcon from '@mui/icons-material/Sensors'
import AssessmentIcon from '@mui/icons-material/Assessment'
import ApartmentIcon from '@mui/icons-material/Apartment'
import PersonAddIcon from '@mui/icons-material/PersonAdd'
import BarChartIcon from '@mui/icons-material/BarChart'
import { useAuth } from '@/hooks/useAuth'
import { useTenantUsers, useTenantUsage } from '@/hooks/useTenantAdmin'

export default function TenantOverviewPage() {
  const { user } = useAuth()
  const tenantId = user?.tenantId ?? null
  const navigate = useNavigate()

  const { data: users, isLoading: usersLoading } = useTenantUsers(tenantId)
  const now = new Date()
  const from = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`
  const to = now.toISOString().slice(0, 10)
  const { data: usage, isLoading: usageLoading } = useTenantUsage(tenantId, from, to)

  const kpis = [
    { label: 'Users', value: users?.length ?? 0, icon: <PeopleIcon />, loading: usersLoading, testId: 'stat-users' },
    { label: 'Sensors', value: usage?.readingCount ?? 0, icon: <SensorsIcon />, loading: usageLoading, testId: 'stat-sensors' },
    { label: 'ESG Reports', value: '-', icon: <AssessmentIcon />, loading: false, testId: 'stat-alerts' },
    { label: 'Buildings', value: '-', icon: <ApartmentIcon />, loading: false, testId: 'stat-buildings' },
  ]

  return (
    <Box>
      <Typography variant="h5" component="h2" gutterBottom>
        Overview
      </Typography>

      <Grid container spacing={2} mb={3}>
        {kpis.map((kpi) => (
          <Grid item xs={6} sm={3} key={kpi.label}>
            <Card variant="outlined" data-testid={kpi.testId}>
              <CardContent sx={{ textAlign: 'center', py: 2, '&:last-child': { pb: 2 } }}>
                <Box color="primary.main" mb={0.5}>{kpi.icon}</Box>
                {kpi.loading ? (
                  <Skeleton width={40} sx={{ margin: '0 auto' }} />
                ) : (
                  <Typography variant="h5" fontWeight={700}>{kpi.value}</Typography>
                )}
                <Typography variant="caption" color="text.secondary">{kpi.label}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Box display="flex" gap={2} flexWrap="wrap">
        <Button
          variant="outlined"
          startIcon={<PersonAddIcon />}
          onClick={() => navigate('/tenant-admin/users')}
        >
          Invite User
        </Button>
        <Button
          variant="outlined"
          startIcon={<BarChartIcon />}
          onClick={() => navigate('/tenant-admin/usage')}
        >
          View Usage Report
        </Button>
      </Box>
    </Box>
  )
}
