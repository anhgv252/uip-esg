import { Typography, Box, Grid, Card, CardContent } from '@mui/material'
import DashboardIcon from '@mui/icons-material/Dashboard'

const stats = [
  { label: 'Active Sensors', value: '—', color: '#1976D2' },
  { label: 'AQI Current', value: '—', color: '#2E7D32' },
  { label: 'Open Alerts', value: '—', color: '#D32F2F' },
  { label: 'ESG Score', value: '—', color: '#009688' },
]

export default function DashboardPage() {
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
                  {s.value}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  )
}
