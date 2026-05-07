import { Box, Typography } from '@mui/material'

interface AqiGaugeProps {
  value: number
  size?: number
}

const AQI_LEVELS = [
  { max: 50, label: 'Good', color: '#4CAF50' },
  { max: 100, label: 'Moderate', color: '#FFEB3B' },
  { max: 150, label: 'Unhealthy (Sensitive)', color: '#FF9800' },
  { max: 200, label: 'Unhealthy', color: '#F44336' },
  { max: 300, label: 'Very Unhealthy', color: '#9C27B0' },
  { max: 500, label: 'Hazardous', color: '#7E0023' },
]

function getAqiLevel(aqi: number) {
  return AQI_LEVELS.find((l) => aqi <= l.max) ?? AQI_LEVELS[AQI_LEVELS.length - 1]
}

export default function AqiGauge({ value, size = 200 }: AqiGaugeProps) {
  const level = getAqiLevel(value)
  const clampedValue = Math.min(value, 500)
  const percentage = clampedValue / 500
  const strokeWidth = 12
  const radius = (size - strokeWidth) / 2
  const circumference = Math.PI * radius
  const offset = circumference * (1 - percentage)

  return (
    <Box data-testid="aqi-gauge" display="flex" flexDirection="column" alignItems="center" gap={1}>
      <svg width={size} height={size / 2 + 10} viewBox={`0 0 ${size} ${size / 2 + 10}`}>
        {/* Background arc */}
        <path
          d={`M ${strokeWidth / 2} ${size / 2} A ${radius} ${radius} 0 0 1 ${size - strokeWidth / 2} ${size / 2}`}
          fill="none"
          stroke="#E0E0E0"
          strokeWidth={strokeWidth}
          strokeLinecap="round"
        />
        {/* Value arc */}
        <path
          d={`M ${strokeWidth / 2} ${size / 2} A ${radius} ${radius} 0 0 1 ${size - strokeWidth / 2} ${size / 2}`}
          fill="none"
          stroke={level.color}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          style={{ transition: 'stroke-dashoffset 0.5s ease, stroke 0.3s ease' }}
        />
        {/* Value text */}
        <text
          x={size / 2}
          y={size / 2 - 10}
          textAnchor="middle"
          dominantBaseline="middle"
          fontSize={size * 0.18}
          fontWeight={700}
          fill={level.color}
        >
          {value}
        </text>
      </svg>
      <Typography data-testid="aqi-value" variant="body2" fontWeight={600} sx={{ color: level.color }}>
        {value}
      </Typography>
      <Typography variant="caption" sx={{ color: level.color }}>
        {level.label}
      </Typography>
    </Box>
  )
}
