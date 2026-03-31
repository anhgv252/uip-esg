import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { Box, Typography } from '@mui/material';
import { EsgMetricEntry } from '../../api/esg';
import { format } from 'date-fns';

interface EsgBarChartProps {
  data: EsgMetricEntry[];
  metricLabel: string;
  unit: string;
}

interface AggregatedEntry {
  date: string;
  [building: string]: number | string;
}

export function EsgBarChart({ data, metricLabel, unit }: EsgBarChartProps) {
  if (!data.length) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 220 }}>
        <Typography color="text.secondary">No {metricLabel.toLowerCase()} data</Typography>
      </Box>
    );
  }

  // Aggregate by date + buildingId
  const byDate: Record<string, AggregatedEntry> = {};
  const buildings = new Set<string>();

  for (const entry of data) {
    const date = format(new Date(entry.timestamp), 'dd/MM');
    if (!byDate[date]) byDate[date] = { date };
    byDate[date][entry.buildingId] = (byDate[date][entry.buildingId] as number ?? 0) + entry.value;
    buildings.add(entry.buildingId);
  }

  const chartData = Object.values(byDate).slice(-14); // last 14 days
  const buildingList = Array.from(buildings);

  const COLORS = ['#60a5fa', '#34d399', '#f472b6', '#fb923c', '#a78bfa'];

  return (
    <Box>
      <Typography variant="subtitle2" mb={1}>{metricLabel} ({unit})</Typography>
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
          <XAxis dataKey="date" tick={{ fontSize: 11 }} />
          <YAxis tick={{ fontSize: 11 }} unit={` ${unit}`} />
          <Tooltip
            formatter={(value: number, name: string) => [`${value.toFixed(1)} ${unit}`, name]}
          />
          <Legend />
          {buildingList.map((b, i) => (
            <Bar
              key={b}
              dataKey={b}
              stackId="a"
              fill={COLORS[i % COLORS.length]}
              name={b}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </Box>
  );
}

export default EsgBarChart;
