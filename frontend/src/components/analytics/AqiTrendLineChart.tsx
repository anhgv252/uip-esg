import { Card, CardContent, Typography } from '@mui/material'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import type { AqiDataPoint } from '@/api/analytics'

interface Props {
  dataPoints: AqiDataPoint[]
  loading?: boolean
}

const COLORS = ['#1976d2', '#e65100', '#2e7d32', '#9c27b0', '#c62828']

export function AqiTrendLineChart({ dataPoints, loading }: Props) {
  const buildings = [...new Set(dataPoints.map((d) => d.buildingId))]

  const timeMap = new Map<string, Record<string, number>>()
  for (const dp of dataPoints) {
    const key = new Date(dp.timestampEpoch * 1000).toLocaleDateString()
    const existing = timeMap.get(key) ?? {}
    existing[dp.buildingId] = Math.round(dp.avgAqi)
    timeMap.set(key, existing)
  }

  const data = Array.from(timeMap.entries()).map(([date, values]) => ({
    date,
    ...values,
  }))

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>
          Air Quality Trend
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Average AQI by hour per building
        </Typography>
        {loading ? (
          <Typography sx={{ mt: 4, textAlign: 'center' }} color="text.secondary">Loading...</Typography>
        ) : data.length === 0 ? (
          <Typography sx={{ mt: 4, textAlign: 'center' }} color="text.secondary">No data</Typography>
        ) : (
          <ResponsiveContainer width="100%" height={250}>
            <LineChart data={data}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" fontSize={12} />
              <YAxis fontSize={12} />
              <Tooltip />
              <Legend />
              {buildings.map((bld, i) => (
                <Line key={bld} type="monotone" dataKey={bld} stroke={COLORS[i % COLORS.length]} dot={false} />
              ))}
            </LineChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  )
}
