import { Box, Typography, Stack } from '@mui/material';

interface Props {
  value: number;
  /** Max scale (e.g. 6.0 for water level) */
  max?: number;
  /** Unit label */
  unit?: string;
  /** Threshold markers */
  thresholds?: { label: string; value: number; color: string }[];
}

const DEFAULT_THRESHOLDS = [
  { label: 'P2', value: 2.0, color: '#1976d2' },
  { label: 'P1', value: 3.5, color: '#f57c00' },
  { label: 'P0', value: 5.0, color: '#c62828' },
];

/**
 * Vertical water level gauge with P0/P1/P2 threshold markers.
 */
export default function WaterLevelGauge({ value, max = 6.0, unit = 'm', thresholds = DEFAULT_THRESHOLDS }: Props) {
  const pct = Math.min((value / max) * 100, 100);

  return (
    <Box sx={{ width: 60, height: 180, position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      {/* Value display */}
      <Typography variant="h6" fontWeight={700} mb={0.5}>
        {value.toFixed(1)}
      </Typography>
      <Typography variant="caption" color="text.secondary" mb={1}>
        {unit}
      </Typography>

      {/* Gauge bar */}
      <Box sx={{ width: 24, flex: 1, bgcolor: 'grey.200', borderRadius: 2, position: 'relative', overflow: 'hidden' }}>
        {/* Fill */}
        <Box
          sx={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            height: `${pct}%`,
            bgcolor: pct > 83 ? '#c62828' : pct > 58 ? '#f57c00' : pct > 33 ? '#1976d2' : '#43a047',
            borderRadius: 2,
            transition: 'height 0.5s ease',
          }}
        />

        {/* Threshold markers */}
        {thresholds.map((t) => {
          const yPct = 100 - (t.value / max) * 100;
          return (
            <Box
              key={t.label}
              sx={{
                position: 'absolute',
                left: -4,
                right: -4,
                top: `${yPct}%`,
                height: 2,
                bgcolor: t.color,
                borderRadius: 1,
              }}
            />
          );
        })}
      </Box>

      {/* Threshold labels */}
      <Stack spacing={0.25} mt={0.5} alignItems="center">
        {thresholds.map((t) => (
          <Typography key={t.label} variant="caption" sx={{ color: t.color, fontSize: '0.6rem', fontWeight: 600 }}>
            {t.label}: {t.value}
          </Typography>
        ))}
      </Stack>
    </Box>
  );
}
