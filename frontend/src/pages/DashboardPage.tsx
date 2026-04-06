import { Typography, Box, Grid, Card, CardContent, CircularProgress } from '@mui/material'
import DashboardIcon from '@mui/icons-material/Dashboard'
import { useQuery } from '@tanstack/react-query'
import { getSensors, getCurrentAqi } from '@/api/environment'
import { getAlerts } from '@/api/alerts'
import { getEsgSummary } from '@/api/esg'

export default function DashboardPage() {
  const { data: sensors } = useQuery({ queryKey: ['sensors'], queryFn: () => getSensors() })
  const { data: aqi } = useQuery({ queryKey: ['aqi-current'], queryFn: () => getCurrentAqi() })
  const { data: openAlerts } = useQuery({
    queryKey: ['alerts-open-count'],
    queryFn: () => getAlerts({ status: 'OPEN', size: 1 }),
    refetchInterval: 30_000,
  })
  const { data: esg } = useQuery({ queryKey: ['esg-summary'], queryFn: () => getEsgSummary() })

  const stats = [
    { label: 'Active Sensors', value: sensors ? String(sensors.length) : null, color: '#1976D2' },
    { label: 'AQI Current',    value: aqi && aqi.length > 0 ? String(Math.round(aqi[0].aqi)) : null,   color: '#2E7D32' },
    { label: 'Open Alerts',    value: openAlerts ? String(openAlerts.totalElements) : null, color: '#D32F2F' },
    { label: 'ESG Score',      value: esg ? String(Math.round(esg.totalCarbonKg)) + ' kg' : null, color: '#009688' },
  ]

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={3}>
        <DashboardIcon color="primary" />
        <Typography variant="h5">Dashboard</Typography>
      </Box>
      <Grid container spacing={3}>
        {stats.map((s) => (
          <Grid item xs={12} sm={6} md={3} key={s.label}>
            <Card>
              <CardContent>
                <Typography variant="body2" color="text.secondary">
                  {s.label}
                </Typography>
                <Typography variant="h4" fontWeight={700} sx={{ color: s.color }}>
                  {s.value ?? <CircularProgress size={28} sx={{ color: s.color }} />}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  )
}
