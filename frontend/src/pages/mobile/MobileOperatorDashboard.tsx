import { Box, Card, CardContent, Chip, Grid, Skeleton, Typography } from '@mui/material'
import SecurityIcon from '@mui/icons-material/Security'
import SensorsIcon from '@mui/icons-material/Sensors'
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive'
import { useAlerts } from '@/hooks/useAlertManagement'
import { useBuildings } from '@/hooks/useBuildings'
import { useSafetyScore } from '@/hooks/useSafetyScore'
import {
  Line, LineChart, ResponsiveContainer, Tooltip as RechartTooltip, XAxis,
} from 'recharts'
import { useEnergyForecast } from '@/hooks/useEnergyForecast'
import { format } from 'date-fns'

// ─── KPI Card ────────────────────────────────────────────────────────────────

interface KpiCardProps {
  label: string
  value: React.ReactNode
  icon: React.ReactNode
  color?: string
  loading?: boolean
}

function KpiCard({ label, value, icon, color = 'primary.main', loading = false }: KpiCardProps) {
  return (
    <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
      <CardContent sx={{ p: '12px !important' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
          <Box sx={{ color }}>{icon}</Box>
          <Typography variant="caption" color="text.secondary">{label}</Typography>
        </Box>
        {loading ? (
          <Skeleton variant="text" width={60} height={36} />
        ) : (
          <Typography variant="h5" fontWeight={700} sx={{ color }}>
            {value}
          </Typography>
        )}
      </CardContent>
    </Card>
  )
}

// ─── 7-day Energy Sparkline ───────────────────────────────────────────────────

function EnergySparkline({ buildingId }: { buildingId: string | undefined }) {
  const { data: forecast, isLoading } = useEnergyForecast(buildingId, 7)

  if (isLoading) return <Skeleton variant="rectangular" height={60} />
  if (!forecast?.points?.length) {
    return (
      <Typography variant="caption" color="text.secondary" sx={{ py: 1, display: 'block' }}>
        Không có dữ liệu dự báo
      </Typography>
    )
  }

  const data = forecast.points.slice(0, 7).map(p => ({
    time: format(new Date(p.timestamp), 'dd/MM'),
    value: p.predictedValue,
  }))

  return (
    <ResponsiveContainer width="100%" height={60}>
      <LineChart data={data} margin={{ top: 2, right: 4, left: 0, bottom: 2 }}>
        <XAxis dataKey="time" tick={{ fontSize: 9 }} tickLine={false} axisLine={false} />
        <RechartTooltip
          formatter={(v: number) => [`${v.toFixed(1)} kWh`, 'Dự báo']}
          contentStyle={{ fontSize: 11 }}
        />
        <Line type="monotone" dataKey="value" stroke="#60a5fa" strokeWidth={1.5} dot={false} />
      </LineChart>
    </ResponsiveContainer>
  )
}

// ─── Main Dashboard ───────────────────────────────────────────────────────────

export function MobileOperatorDashboard() {
  const { data: buildings = [], isLoading: bldLoading } = useBuildings()
  const firstBuilding = buildings[0]

  const { data: safetyScore, isLoading: scoreLoading } = useSafetyScore(firstBuilding?.id)
  const { data: alertsPage, isLoading: alertsLoading } = useAlerts({ status: 'OPEN', size: 5 })

  const openAlertCount = alertsPage?.totalElements ?? 0
  const criticalCount = (alertsPage?.content ?? []).filter(a => a.severity === 'CRITICAL').length

  const scoreColor =
    !safetyScore ? '#9CA3AF'
    : safetyScore.score <= 40 ? '#EF4444'
    : safetyScore.score <= 70 ? '#F59E0B'
    : '#22C55E'

  return (
    <Box sx={{ p: 2 }}>
      <Typography variant="h6" fontWeight={700} mb={2}>
        Bảng điều hành
      </Typography>

      {/* KPI Cards */}
      <Grid container spacing={1.5} mb={2}>
        <Grid item xs={6}>
          <KpiCard
            label="Safety Score"
            value={safetyScore ? safetyScore.score : '--'}
            icon={<SecurityIcon fontSize="small" />}
            color={scoreColor}
            loading={scoreLoading}
          />
        </Grid>
        <Grid item xs={6}>
          <KpiCard
            label="Cảnh báo mở"
            value={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                {openAlertCount}
                {criticalCount > 0 && (
                  <Chip label={`${criticalCount} P0`} size="small" color="error" sx={{ height: 18, fontSize: 10 }} />
                )}
              </Box>
            }
            icon={<NotificationsActiveIcon fontSize="small" />}
            color={criticalCount > 0 ? '#EF4444' : 'primary.main'}
            loading={alertsLoading}
          />
        </Grid>
        <Grid item xs={12}>
          <KpiCard
            label="Tòa nhà đang theo dõi"
            value={buildings.length}
            icon={<SensorsIcon fontSize="small" />}
            loading={bldLoading}
          />
        </Grid>
      </Grid>

      {/* 7-day Energy Sparkline */}
      <Card elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
        <CardContent sx={{ p: '12px !important' }}>
          <Typography variant="caption" color="text.secondary" mb={0.5} display="block">
            Dự báo điện năng 7 ngày tới
            {firstBuilding && ` — ${firstBuilding.buildingName}`}
          </Typography>
          <EnergySparkline buildingId={firstBuilding?.id} />
        </CardContent>
      </Card>
    </Box>
  )
}
