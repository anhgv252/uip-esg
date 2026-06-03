import {
  Brush,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { Box, Skeleton, Tab, Tabs, Typography } from '@mui/material'
import { useState } from 'react'
import { format } from 'date-fns'
import { useVibrationReadings, VibrationReading } from '@/hooks/useVibrationReadings'

type SensorTab = 'STRUCTURAL_VIBRATION' | 'STRUCTURAL_TILT' | 'STRUCTURAL_CRACK'

interface SafetyTrendChartProps {
  buildingId: string | undefined
}

// Per TCVN 9386:2012 + ISO 4866
const SENSOR_CONFIG: Record<SensorTab, { warning: number; critical: number; unit: string; label: string }> = {
  STRUCTURAL_VIBRATION: { warning: 10, critical: 50, unit: 'mm/s', label: 'Rung động' },
  STRUCTURAL_TILT:      { warning: 3,  critical: 10, unit: 'mrad', label: 'Nghiêng' },
  STRUCTURAL_CRACK:     { warning: 0.3, critical: 2, unit: 'mm',   label: 'Vết nứt' },
}

const TABS: SensorTab[] = ['STRUCTURAL_VIBRATION', 'STRUCTURAL_TILT', 'STRUCTURAL_CRACK']

interface TooltipPayload {
  active?: boolean
  payload?: Array<{ value: number; payload: VibrationReading & { displayTime: string } }>
}

function CustomTooltip({ active, payload }: TooltipPayload) {
  if (!active || !payload?.length) return null
  const { timestamp, value, unit, sensorType } = payload[0].payload as VibrationReading & { unit: string }
  const cfg = SENSOR_CONFIG[sensorType as SensorTab] ?? { warning: 0, critical: 0, unit: '' }
  const status = value >= cfg.critical ? 'CRITICAL' : value >= cfg.warning ? 'WARNING' : 'NORMAL'
  const statusColor = status === 'CRITICAL' ? '#EF4444' : status === 'WARNING' ? '#F59E0B' : '#22C55E'
  return (
    <Box sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', p: 1, borderRadius: 1 }}>
      <Typography variant="caption" display="block">
        {format(new Date(timestamp), 'dd/MM HH:mm:ss')}
      </Typography>
      <Typography variant="body2" fontWeight={700}>
        {value} {unit ?? cfg.unit}
      </Typography>
      <Typography variant="caption" sx={{ color: statusColor }}>
        {status}
      </Typography>
    </Box>
  )
}

function TrendLine({ buildingId, tab }: { buildingId: string; tab: SensorTab }) {
  const { data, isLoading, isError } = useVibrationReadings(buildingId, tab)
  const cfg = SENSOR_CONFIG[tab]

  if (isLoading) {
    return <Skeleton variant="rectangular" height={220} />
  }

  if (isError || !data?.length) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 220 }}>
        <Typography color="text.secondary" variant="body2">
          {isError ? 'Không thể tải dữ liệu' : 'Chưa có readings trong 24h qua'}
        </Typography>
      </Box>
    )
  }

  const chartData = data.map(d => ({
    ...d,
    displayTime: format(new Date(d.timestamp), 'HH:mm'),
  }))

  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.08)" />
        <XAxis dataKey="displayTime" tick={{ fontSize: 11 }} />
        <YAxis tick={{ fontSize: 11 }} unit={` ${cfg.unit}`} />
        <Tooltip content={<CustomTooltip />} />
        <Legend />
        <ReferenceLine
          y={cfg.warning}
          stroke="#F59E0B"
          strokeDasharray="4 4"
          label={{ value: `Cảnh báo ${cfg.warning}${cfg.unit}`, position: 'insideRight', fontSize: 10, fill: '#F59E0B' }}
        />
        <ReferenceLine
          y={cfg.critical}
          stroke="#EF4444"
          strokeDasharray="4 4"
          label={{ value: `Nguy hiểm ${cfg.critical}${cfg.unit}`, position: 'insideRight', fontSize: 10, fill: '#EF4444' }}
        />
        <Line
          type="monotone"
          dataKey="value"
          name={`${cfg.label} (${cfg.unit})`}
          stroke="#60a5fa"
          dot={false}
          strokeWidth={2}
          activeDot={{ r: 4 }}
        />
        <Brush dataKey="displayTime" height={20} stroke="#4b5563" />
      </LineChart>
    </ResponsiveContainer>
  )
}

export function SafetyTrendChart({ buildingId }: SafetyTrendChartProps) {
  const [activeTab, setActiveTab] = useState<SensorTab>('STRUCTURAL_VIBRATION')

  return (
    <Box>
      <Tabs
        value={activeTab}
        onChange={(_, v: SensorTab) => setActiveTab(v)}
        sx={{ mb: 1, borderBottom: 1, borderColor: 'divider' }}
        aria-label="Chọn loại cảm biến kết cấu"
      >
        {TABS.map(tab => (
          <Tab
            key={tab}
            value={tab}
            label={SENSOR_CONFIG[tab].label}
            aria-label={`Biểu đồ ${SENSOR_CONFIG[tab].label}`}
          />
        ))}
      </Tabs>

      {buildingId ? (
        <TrendLine buildingId={buildingId} tab={activeTab} />
      ) : (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 220 }}>
          <Typography color="text.secondary" variant="body2">
            Chọn tòa nhà để xem biểu đồ
          </Typography>
        </Box>
      )}
    </Box>
  )
}
