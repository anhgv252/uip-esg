import { useMemo } from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts'
import { Box, Typography, CircularProgress } from '@mui/material'
import type { TrafficCountDto } from '@/api/traffic'

interface TrafficBarChartProps {
  counts: TrafficCountDto[]
  loading?: boolean
}

export default function TrafficBarChart({ counts, loading }: TrafficBarChartProps) {
  const chartData = useMemo(() => {
    // Group by hour
    const byHour: Record<string, number> = {}
    counts.forEach((c) => {
      const hour = new Date(c.recordedAt).getHours().toString().padStart(2, '0') + ':00'
      byHour[hour] = (byHour[hour] ?? 0) + c.vehicleCount
    })
    return Object.entries(byHour)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([hour, count]) => ({ hour, count }))
  }, [counts])

  if (loading) {
    return <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
  }

  if (chartData.length === 0) {
    return <Typography color="text.secondary" variant="body2">No traffic count data available</Typography>
  }

  return (
    <ResponsiveContainer width="100%" height={280}>
      <BarChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="hour" tick={{ fontSize: 12 }} />
        <YAxis tick={{ fontSize: 12 }} />
        <Tooltip formatter={(v: number) => [v.toLocaleString(), 'Vehicles']} />
        <Legend />
        <Bar dataKey="count" name="Vehicle count" fill="#1976D2" radius={[3, 3, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}
