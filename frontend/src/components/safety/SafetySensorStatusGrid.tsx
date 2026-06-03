import { Box, Chip, Skeleton, Typography } from '@mui/material'

export interface SensorStatus {
  sensorId: string
  sensorType: 'STRUCTURAL_VIBRATION' | 'STRUCTURAL_TILT' | 'STRUCTURAL_CRACK' | string
  currentValue: number
  unit: string
  status: 'NORMAL' | 'WARNING' | 'CRITICAL' | 'OFFLINE'
  lastReading?: string
}

interface SafetySensorStatusGridProps {
  sensors: SensorStatus[]
  loading?: boolean
}

const STATUS_COLORS: Record<SensorStatus['status'], 'success' | 'warning' | 'error' | 'default'> = {
  NORMAL: 'success',
  WARNING: 'warning',
  CRITICAL: 'error',
  OFFLINE: 'default',
}

const SENSOR_TYPE_LABELS: Record<string, string> = {
  STRUCTURAL_VIBRATION: 'Rung động',
  STRUCTURAL_TILT: 'Nghiêng',
  STRUCTURAL_CRACK: 'Vết nứt',
}

function getSensorLabel(type: string): string {
  return SENSOR_TYPE_LABELS[type] ?? type
}

function SensorRow({ sensor }: { sensor: SensorStatus }) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        py: 0.75,
        px: 1,
        borderRadius: 1,
        '&:hover': { bgcolor: 'action.hover' },
      }}
      role="row"
    >
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="body2" fontWeight={500} noWrap>
          {getSensorLabel(sensor.sensorType)}
        </Typography>
        <Typography variant="caption" color="text.secondary" noWrap>
          {sensor.sensorId}
        </Typography>
      </Box>

      <Box sx={{ mx: 2, textAlign: 'right' }}>
        <Typography variant="body2" fontWeight={600}>
          {sensor.status === 'OFFLINE' ? '--' : `${sensor.currentValue} ${sensor.unit}`}
        </Typography>
      </Box>

      <Chip
        label={sensor.status}
        color={STATUS_COLORS[sensor.status]}
        size="small"
        aria-label={`Status: ${sensor.status}`}
      />
    </Box>
  )
}

function SkeletonRows() {
  return (
    <>
      {[0, 1, 2].map(i => (
        <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 0.75, px: 1 }}>
          <Box sx={{ flex: 1 }}>
            <Skeleton variant="text" width="60%" />
            <Skeleton variant="text" width="40%" />
          </Box>
          <Skeleton variant="text" width={60} />
          <Skeleton variant="rounded" width={70} height={24} />
        </Box>
      ))}
    </>
  )
}

export function SafetySensorStatusGrid({ sensors, loading = false }: SafetySensorStatusGridProps) {
  if (loading) {
    return (
      <Box role="grid" aria-label="Sensor status grid">
        <SkeletonRows />
      </Box>
    )
  }

  if (sensors.length === 0) {
    return (
      <Box sx={{ py: 3, textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          Không có dữ liệu cảm biến
        </Typography>
      </Box>
    )
  }

  // Overflow scroll for large lists (>50 sensors) — @tanstack/react-virtual not in deps
  const isLargeList = sensors.length > 50

  return (
    <Box
      role="grid"
      aria-label="Sensor status grid"
      sx={isLargeList ? { maxHeight: 400, overflow: 'auto' } : undefined}
    >
      {sensors.map(sensor => (
        <SensorRow key={sensor.sensorId} sensor={sensor} />
      ))}
    </Box>
  )
}
