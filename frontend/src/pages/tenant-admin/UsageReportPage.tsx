import { useState } from 'react'
import { Box, Typography, TextField, Grid, Card, CardContent, Skeleton } from '@mui/material'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { useAuth } from '@/hooks/useAuth'
import { useTenantUsage } from '@/hooks/useTenantAdmin'

export default function UsageReportPage() {
  const { user } = useAuth()
  const tenantId = user?.tenantId ?? null
  const now = new Date()

  const [from, setFrom] = useState(`${now.getFullYear()}-${String(now.getMonth() - 2).padStart(2, '0')}-01`)
  const [to, setTo] = useState(now.toISOString().slice(0, 10))

  const { data: usage, isLoading } = useTenantUsage(tenantId, from, to)
  const chartData = usage ? [{ month: 'Readings', count: usage.readingCount }] : []

  return (
    <Box>
      <Typography variant="h5" component="h2" gutterBottom>
        Usage Report
      </Typography>

      <Box display="flex" gap={2} mb={3}>
        <TextField label="From" type="date" size="small" value={from} onChange={(e) => setFrom(e.target.value)} InputLabelProps={{ shrink: true }} />
        <TextField label="To" type="date" size="small" value={to} onChange={(e) => setTo(e.target.value)} InputLabelProps={{ shrink: true }} />
      </Box>

      {/* Summary cards */}
      <Grid container spacing={2} mb={3}>
        {[
          { label: 'Total Readings', value: usage?.readingCount },
          { label: 'Total Reports', value: '-' },
          { label: 'Avg/Day', value: usage ? Math.round(usage.readingCount / Math.max(1, 30)) : 0 },
        ].map((card) => (
          <Grid item xs={12} sm={4} key={card.label}>
            <Card variant="outlined">
              <CardContent sx={{ textAlign: 'center', '&:last-child': { pb: 2 } }}>
                <Typography variant="caption" color="text.secondary">{card.label}</Typography>
                {isLoading ? (
                  <Skeleton width={60} sx={{ margin: '0 auto' }} />
                ) : (
                  <Typography variant="h5" fontWeight={700}>{card.value ?? 0}</Typography>
                )}
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Charts */}
      <Grid container spacing={2} data-testid="usage-chart">
        <Grid item xs={12} md={6}>
          <Typography variant="subtitle2" gutterBottom>Monthly Sensor Readings</Typography>
          {isLoading ? (
            <Skeleton variant="rounded" height={250} />
          ) : (
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" fontSize={12} />
                <YAxis fontSize={12} />
                <Tooltip />
                <Bar dataKey="count" fill="#1976D2" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Grid>
        <Grid item xs={12} md={6}>
          <Typography variant="subtitle2" gutterBottom>Monthly ESG Reports</Typography>
          {isLoading ? (
            <Skeleton variant="rounded" height={250} />
          ) : (
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" fontSize={12} />
                <YAxis fontSize={12} />
                <Tooltip />
                <Bar dataKey="count" fill="#4CAF50" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Grid>
      </Grid>
    </Box>
  )
}
