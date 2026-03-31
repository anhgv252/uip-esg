import { Box, Typography, CircularProgress } from '@mui/material';
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts';

interface AqiGaugeProps {
  aqi: number;
  category: string;
  color: string;
  sensorName: string;
  district: string;
  dominantPollutant: string;
}

const AQI_MAX = 500;
const NEEDLE_COLOR = '#374151';

function toDeg(value: number): number {
  return -180 + Math.min(value / AQI_MAX, 1) * 180;
}

export function AqiGauge({ aqi, category, color, sensorName, district, dominantPollutant }: AqiGaugeProps) {
  // Build arc segments matching EPA breakpoints
  const segments = [
    { limit: 50, color: '#00E400' },    // Good
    { limit: 100, color: '#FFFF00' },   // Moderate
    { limit: 150, color: '#FF7E00' },   // USG
    { limit: 200, color: '#FF0000' },   // Unhealthy
    { limit: 300, color: '#8F3F97' },   // Very Unhealthy
    { limit: 500, color: '#7E0023' },   // Hazardous
  ];

  const arcData = segments.map((seg, i) => ({
    value: i === 0 ? seg.limit : seg.limit - segments[i - 1].limit,
    color: seg.color,
  }));

  // Needle angle
  const angle = toDeg(aqi);
  const needleX2 = 50 + 32 * Math.cos(((angle - 90) * Math.PI) / 180);
  const needleY2 = 48 + 32 * Math.sin(((angle - 90) * Math.PI) / 180);

  return (
    <Box sx={{ textAlign: 'center', p: 1 }}>
      <Typography variant="subtitle2" fontWeight={600} noWrap>{sensorName}</Typography>
      <Typography variant="caption" color="text.secondary">{district}</Typography>

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
              isAnimationActive={false}
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
          sx={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%' }}
        >
          {/* Pivot */}
          <circle cx="50" cy="48" r="4" fill={NEEDLE_COLOR} />
          {/* Needle line: from pivot toward arc, rotated by `angle` across the 180-0 range */}
          <line
            x1="50"
            y1="48"
            x2={needleX2}
            y2={needleY2}
            stroke={NEEDLE_COLOR}
            strokeWidth="2"
            strokeLinecap="round"
          />
        </Box>
      </Box>

      {/* AQI value + label */}
      <Box
        sx={{
          display: 'inline-block',
          bgcolor: color,
          color: aqi <= 100 ? '#000' : '#fff',
          borderRadius: 2,
          px: 2,
          py: 0.5,
          mt: -1,
          mb: 0.5,
        }}
      >
        <Typography variant="h5" fontWeight={700} lineHeight={1}>{aqi}</Typography>
        <Typography variant="caption">{category}</Typography>
      </Box>

      <Typography variant="caption" display="block" color="text.secondary" mt={0.5}>
        Dominant: {dominantPollutant}
      </Typography>
    </Box>
  );
}

export function AqiGaugeSkeleton() {
  return (
    <Box sx={{ textAlign: 'center', p: 2 }}>
      <CircularProgress size={60} />
    </Box>
  );
}

// Re-export for easy usage
export default AqiGauge;
