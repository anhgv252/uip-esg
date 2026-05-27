import { useMemo } from 'react';
import {
  ComposedChart,
  Line,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
  Scatter,
} from 'recharts';
import type { ForecastPoint } from '@/api/forecast';
import { ForecastTooltip } from './ForecastTooltip';

interface ForecastChartProps {
  points: ForecastPoint[];
  isFallback?: boolean;
  mape?: number | null;
  height?: number;
}

function toChartPoint(p: ForecastPoint) {
  return {
    timestamp: new Date(p.timestamp).getTime(),
    actual: p.actualValue,
    predicted: p.predictedValue,
    upper: p.confidenceUpper,
    lower: p.confidenceLower,
    isAnomaly: p.isAnomaly,
  };
}

export function ForecastChart({ points, isFallback, mape, height = 400 }: ForecastChartProps) {
  const data = useMemo(() => points.map(toChartPoint), [points]);
  const anomalies = useMemo(() => data.filter((d) => d.isAnomaly), [data]);

  if (data.length === 0) {
    return (
      <div style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#888' }}>
        No forecast data available
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: 16, marginBottom: 8, fontSize: 13, color: '#666' }}>
        {mape != null && <span>MAPE: {(mape * 100).toFixed(1)}%</span>}
        {isFallback && <span style={{ color: '#f59e0b' }}>Fallback mode (naive)</span>}
        {anomalies.length > 0 && <span style={{ color: '#ef4444' }}>{anomalies.length} anomalies</span>}
      </div>
      <ResponsiveContainer width="100%" height={height}>
        <ComposedChart data={data} margin={{ top: 5, right: 20, bottom: 5, left: 10 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
          <XAxis
            dataKey="timestamp"
            type="number"
            domain={['dataMin', 'dataMax']}
            tickFormatter={(v: number) => new Date(v).toLocaleDateString()}
            tick={{ fontSize: 11 }}
          />
          <YAxis tick={{ fontSize: 11 }} label={{ value: 'kWh', angle: -90, position: 'insideLeft' }} />
          <Tooltip content={<ForecastTooltip />} />
          <Legend />

          {/* Confidence band */}
          <Area
            type="monotone"
            dataKey="upper"
            stroke="none"
            fill="#3b82f6"
            fillOpacity={0.1}
            name="Upper CI"
            legendType="none"
          />
          <Area
            type="monotone"
            dataKey="lower"
            stroke="none"
            fill="#3b82f6"
            fillOpacity={0.1}
            name="Lower CI"
            legendType="none"
          />

          {/* Actual line */}
          <Line
            type="monotone"
            dataKey="actual"
            stroke="#6b7280"
            strokeWidth={1.5}
            dot={false}
            connectNulls={false}
            name="Actual"
          />

          {/* Predicted line */}
          <Line
            type="monotone"
            dataKey="predicted"
            stroke="#3b82f6"
            strokeWidth={2}
            dot={false}
            name="Forecast"
          />

          {/* Anomaly markers */}
          {anomalies.length > 0 && (
            <Scatter
              data={anomalies}
              dataKey="actual"
              fill="#ef4444"
              shape="diamond"
              name="Anomaly"
            />
          )}
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}
