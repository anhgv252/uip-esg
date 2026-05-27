import type { TooltipProps } from 'recharts';
import type { ValueType, NameType } from 'recharts/types/component/DefaultTooltipContent';

export function ForecastTooltip({ active, payload }: TooltipProps<ValueType, NameType>) {
  if (!active || !payload?.length) return null;

  const data = payload[0]?.payload as Record<string, number | boolean | undefined>;
  if (!data) return null;

  const ts = new Date(data.timestamp as number).toLocaleString();
  const actual = data.actual as number | undefined;
  const predicted = data.predicted as number;
  const upper = data.upper as number;
  const lower = data.lower as number;
  const isAnomaly = data.isAnomaly as boolean;

  const deviation = actual != null && actual !== 0
    ? (((actual - predicted) / Math.abs(actual)) * 100).toFixed(1)
    : null;

  return (
    <div style={{
      background: '#fff',
      border: '1px solid #e5e7eb',
      borderRadius: 6,
      padding: 10,
      fontSize: 12,
      boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
    }}>
      <div style={{ fontWeight: 600, marginBottom: 4 }}>{ts}</div>
      {actual != null && (
        <div>Actual: <strong>{actual.toFixed(1)} kWh</strong></div>
      )}
      <div>Predicted: <strong>{predicted.toFixed(1)} kWh</strong></div>
      <div style={{ color: '#6b7280' }}>
        CI: [{lower.toFixed(1)}, {upper.toFixed(1)}]
      </div>
      {deviation != null && (
        <div style={{ color: parseFloat(deviation) > 10 ? '#ef4444' : '#22c55e' }}>
          Deviation: {deviation}%
        </div>
      )}
      {isAnomaly && (
        <div style={{ color: '#ef4444', fontWeight: 600, marginTop: 4 }}>
          &#9888; Anomaly detected
        </div>
      )}
    </div>
  );
}
