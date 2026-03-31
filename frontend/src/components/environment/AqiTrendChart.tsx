import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
  Brush,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import { Box, Typography } from '@mui/material';
import { AqiHistory } from '../../api/environment';
import { format } from 'date-fns';

interface AqiTrendChartProps {
  data: AqiHistory[];
  sensorName?: string;
}

const AQI_THRESHOLDS = [
  { value: 50, label: 'Good', color: '#00E400' },
  { value: 100, label: 'Moderate', color: '#FFFF00' },
  { value: 150, label: 'USG', color: '#FF7E00' },
  { value: 200, label: 'Unhealthy', color: '#FF0000' },
];

function aqiColor(aqi: number): string {
  if (aqi <= 50) return '#00E400';
  if (aqi <= 100) return '#FFFF00';
  if (aqi <= 150) return '#FF7E00';
  if (aqi <= 200) return '#FF0000';
  if (aqi <= 300) return '#8F3F97';
  return '#7E0023';
}

interface TooltipProps {
  active?: boolean;
  payload?: Array<{ value: number; payload: AqiHistory }>;
  label?: string;
}

function CustomTooltip({ active, payload }: TooltipProps) {
  if (!active || !payload?.length) return null;
  const { timestamp, aqi, category } = payload[0].payload;
  return (
    <Box sx={{ bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', p: 1, borderRadius: 1 }}>
      <Typography variant="caption" display="block">{format(new Date(timestamp), 'dd/MM HH:mm')}</Typography>
      <Typography variant="body2" fontWeight={700} color={aqiColor(aqi)}>AQI: {aqi}</Typography>
      <Typography variant="caption">{category}</Typography>
    </Box>
  );
}

export function AqiTrendChart({ data, sensorName }: AqiTrendChartProps) {
  if (!data.length) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}>
        <Typography color="text.secondary">No historical data available</Typography>
      </Box>
    );
  }

  const chartData = data.map((d) => ({
    ...d,
    time: new Date(d.timestamp).getTime(),
    displayTime: format(new Date(d.timestamp), 'HH:mm'),
  }));

  return (
    <Box>
      {sensorName && (
        <Typography variant="subtitle2" mb={1}>{sensorName} — AQI Trend (24h)</Typography>
      )}
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
          <XAxis dataKey="displayTime" tick={{ fontSize: 11 }} />
          <YAxis domain={[0, 300]} tick={{ fontSize: 11 }} />
          <Tooltip content={<CustomTooltip />} />
          <Legend />
          {AQI_THRESHOLDS.map((t) => (
            <ReferenceLine
              key={t.value}
              y={t.value}
              stroke={t.color}
              strokeDasharray="4 4"
              label={{ value: t.label, position: 'insideRight', fontSize: 10, fill: t.color }}
            />
          ))}
          <Line
            type="monotone"
            dataKey="aqi"
            stroke="#60a5fa"
            dot={false}
            strokeWidth={2}
            activeDot={{ r: 4 }}
          />
          <Brush dataKey="displayTime" height={20} stroke="#4b5563" />
        </LineChart>
      </ResponsiveContainer>
    </Box>
  );
}

export default AqiTrendChart;
