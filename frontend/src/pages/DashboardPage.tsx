import { Typography, Box, Grid, Card, CardContent, CircularProgress } from '@mui/material'
import DashboardIcon from '@mui/icons-material/Dashboard'
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline'
import { useQuery } from '@tanstack/react-query'
import { getSensors, getCurrentAqi } from '@/api/environment'
import { getAlerts } from '@/api/alerts'
import { getEsgSummary } from '@/api/esg'

type StatValue = { text: string } | { error: true } | null

export default function DashboardPage() {
  const { data: sensors, isError: sensorsError } = useQuery({ queryKey: ['sensors'], queryFn: () => getSensors() })
  const { data: aqi, isError: aqiError } = useQuery({ queryKey: ['aqi-current'], queryFn: () => getCurrentAqi() })
  const { data: openAlerts, isError: alertsError } = useQuery({
    queryKey: ['alerts-open-count'],
    queryFn: () => getAlerts({ status: 'OPEN', size: 1 }),
    refetchInterval: 30_000,
  })
  const { data: esg, isError: esgError } = useQuery({ queryKey: ['esg-summary'], queryFn: () => getEsgSummary() })

  const toStat = (hasData: boolean, isError: boolean, value: string | null): StatValue => {
    if (isError) return { error: true }
    if (hasData && value !== null) return { text: value }
    return null
  }

  const stats = [
    { label: 'Active Sensors', stat: toStat(!!sensors, sensorsError, sensors ? String(sensors.length) : null), color: '#1976D2' },
    { label: 'AQI Current',    stat: toStat(!!aqi && aqi.length > 0, aqiError, aqi && aqi.length > 0 ? String(Math.round(aqi[0].aqi)) : null), color: '#2E7D32' },
    { label: 'Open Alerts',    stat: toStat(!!openAlerts, alertsError, openAlerts ? String(openAlerts.totalElements) : null), color: '#D32F2F' },
    { label: 'Carbon (tCO₂e)', stat: toStat(!!esg, esgError, esg ? String(Math.round(esg.totalCarbonTco2e)) + ' t' : null), color: '#009688' },
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
                  {s.stat && 'text' in s.stat
                    ? s.stat.text
                    : s.stat && 'error' in s.stat
                      ? <Box display="flex" alignItems="center" gap={0.5}><ErrorOutlineIcon sx={{ color: 'text.disabled', fontSize: 28 }} /><Typography variant="h6" color="text.disabled">N/A</Typography></Box>
                      : <CircularProgress size={28} sx={{ color: s.color }} />}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Box>
  )
}
