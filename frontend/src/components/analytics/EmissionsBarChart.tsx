import { Card, CardContent, Typography } from '@mui/material'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { TenantEmissionsBreakdown } from '@/api/analytics'

interface Props {
  buildings: TenantEmissionsBreakdown[]
  loading?: boolean
}

export function EmissionsBarChart({ buildings, loading }: Props) {
  const data = buildings.map((b) => ({
    name: b.buildingId,
    'CO2 (kg)': Math.round(b.totalCo2Kg * 100) / 100,
  }))

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>
          CO2 Emissions
        </Typography>
        <Typography variant="caption" color="text.secondary">
          Total kg CO2 per building (last 30 days)
        </Typography>
        {loading ? (
          <Typography sx={{ mt: 4, textAlign: 'center' }} color="text.secondary">Loading...</Typography>
        ) : data.length === 0 ? (
          <Typography sx={{ mt: 4, textAlign: 'center' }} color="text.secondary">No data</Typography>
        ) : (
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={data}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" fontSize={12} />
              <YAxis fontSize={12} />
              <Tooltip />
              <Bar dataKey="CO2 (kg)" fill="#e65100" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  )
}
