import { Box, Skeleton, Tooltip, Typography } from '@mui/material'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'

interface SafetyScoreGaugeProps {
  score: number
  offline?: boolean
  buildingName?: string
  lastUpdated?: string
}

const SCORE_MAX = 100

// Color zones per sprint 7 spec
function getScoreConfig(score: number, offline: boolean) {
  if (offline) return { color: '#9CA3AF', status: 'OFFLINE', label: 'Ngoại tuyến' }
  if (score <= 40) return { color: '#EF4444', status: 'CRITICAL', label: 'Nguy hiểm' }
  if (score <= 70) return { color: '#F59E0B', status: 'WARNING', label: 'Cảnh báo' }
  return { color: '#22C55E', status: 'SAFE', label: 'An toàn' }
}

// Arc segments matching color zones
const ARC_SEGMENTS = [
  { limit: 40, color: '#EF4444' },  // 0-40 critical red
  { limit: 30, color: '#F59E0B' },  // 41-70 amber
  { limit: 30, color: '#22C55E' },  // 71-100 green
]

function toAngle(score: number): number {
  return -180 + Math.min(score / SCORE_MAX, 1) * 180
}

export function SafetyScoreGauge({ score, offline = false, buildingName, lastUpdated }: SafetyScoreGaugeProps) {
  const { color, status, label } = getScoreConfig(score, offline)
  const displayScore = offline ? '--' : score
  const angle = toAngle(offline ? 0 : score)
  const needleX = 50 + 32 * Math.cos(((angle - 90) * Math.PI) / 180)
  const needleY = 48 + 32 * Math.sin(((angle - 90) * Math.PI) / 180)

  const arcData = offline
    ? [{ value: 100, color: '#9CA3AF' }]
    : ARC_SEGMENTS.map(s => ({ value: s.limit, color: s.color }))

  return (
    <Box sx={{ textAlign: 'center', p: 1 }}>
      {buildingName && (
        <Typography variant="subtitle2" fontWeight={600} noWrap>
          {buildingName}
        </Typography>
      )}

      <Box sx={{ position: 'relative', width: '100%', height: 130 }}>
        <ResponsiveContainer width="100%" height={130}>
          <PieChart>
            <Pie
              data={arcData}
              cx="50%"
              cy="80%"
              startAngle={180}
              endAngle={0}
              innerRadius="55%"
              outerRadius="80%"
              dataKey="value"
              isAnimationActive={!offline}
              stroke="none"
            >
              {arcData.map((entry, i) => (
                <Cell key={i} fill={entry.color} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>

        {/* SVG needle overlay */}
        <Box
          component="svg"
          viewBox="0 0 100 60"
          aria-hidden="true"
          sx={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%' }}
        >
          <circle cx="50" cy="48" r="4" fill="#374151" />
          <line
            x1="50"
            y1="48"
            x2={needleX}
            y2={needleY}
            stroke="#374151"
            strokeWidth="2"
            strokeLinecap="round"
          />
        </Box>
      </Box>

      {/* Score badge */}
      <Box
        sx={{
          display: 'inline-block',
          bgcolor: color,
          color: '#fff',
          borderRadius: 2,
          px: 2,
          py: 0.5,
          mt: -1,
          mb: 0.5,
        }}
        role="status"
        aria-label={`Safety score: ${displayScore}, status: ${label}`}
      >
        <Typography variant="h5" fontWeight={700} lineHeight={1}>
          {displayScore}
        </Typography>
        <Typography variant="caption">{label}</Typography>
      </Box>

      {lastUpdated && !offline && (
        <Tooltip title={`Cập nhật lúc: ${new Date(lastUpdated).toLocaleTimeString('vi-VN')}`}>
          <Typography
            variant="caption"
            display="block"
            color="text.secondary"
            mt={0.5}
            sx={{ cursor: 'default' }}
          >
            {status}
          </Typography>
        </Tooltip>
      )}
    </Box>
  )
}

export function SafetyScoreGaugeSkeleton() {
  return (
    <Box sx={{ textAlign: 'center', p: 1 }}>
      <Skeleton variant="text" width={120} sx={{ mx: 'auto' }} />
      <Skeleton variant="circular" width={130} height={80} sx={{ mx: 'auto', mt: 1 }} />
      <Skeleton variant="rounded" width={80} height={36} sx={{ mx: 'auto', mt: 1 }} />
    </Box>
  )
}
