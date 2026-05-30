import { Box, Typography, Chip, Paper, Stack } from '@mui/material';
import WarningIcon from '@mui/icons-material/Warning';
import WaterIcon from '@mui/icons-material/Water';
import OpacityIcon from '@mui/icons-material/Opacity';
import ThermostatIcon from '@mui/icons-material/Thermostat';

interface Props {
  sensorId: string;
  sensorType: string;
  severity: string;
  value: number;
  threshold: number;
  location?: string | null;
  detectedAt: string;
}

const SEVERITY_CONFIG: Record<string, { color: 'error' | 'warning' | 'info'; label: string }> = {
  CRITICAL: { color: 'error', label: 'P0 EMERGENCY' },
  HIGH: { color: 'warning', label: 'P1 WARNING' },
  WARNING: { color: 'info', label: 'P2 ADVISORY' },
};

function sensorIcon(type: string) {
  switch (type.toUpperCase()) {
    case 'RAINFALL': return <OpacityIcon fontSize="small" />;
    case 'WATER_LEVEL': return <WaterIcon fontSize="small" />;
    case 'SOIL_MOISTURE': return <ThermostatIcon fontSize="small" />;
    default: return <WarningIcon fontSize="small" />;
  }
}

function sensorUnit(type: string) {
  switch (type.toUpperCase()) {
    case 'RAINFALL': return 'mm/h';
    case 'WATER_LEVEL': return 'm';
    case 'SOIL_MOISTURE': return '%';
    default: return '';
  }
}

/**
 * Flood alert card showing severity, sensor reading, and threshold info.
 */
export default function FloodAlertCard({ sensorId, sensorType, severity, value, threshold, location, detectedAt }: Props) {
  const config = SEVERITY_CONFIG[severity] ?? SEVERITY_CONFIG.WARNING;
  const pct = threshold > 0 ? Math.min((value / threshold) * 100, 100) : 0;

  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        borderLeft: 4,
        borderColor: config.color === 'error' ? 'error.main' : config.color === 'warning' ? 'warning.main' : 'info.main',
      }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" mb={1}>
        <Box display="flex" alignItems="center" gap={1}>
          {sensorIcon(sensorType)}
          <Typography variant="subtitle2" fontWeight={700}>
            {sensorType.replace('_', ' ')}
          </Typography>
        </Box>
        <Chip label={config.label} size="small" color={config.color} variant="filled" />
      </Stack>

      {/* Value vs Threshold */}
      <Box mb={1}>
        <Box display="flex" alignItems="baseline" gap={0.5}>
          <Typography variant="h5" fontWeight={700} color={config.color === 'error' ? 'error.main' : 'text.primary'}>
            {value.toFixed(1)}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {sensorUnit(sensorType)}
          </Typography>
        </Box>
        <Typography variant="caption" color="text.secondary">
          Threshold: {threshold.toFixed(1)} {sensorUnit(sensorType)}
        </Typography>
      </Box>

      {/* Progress bar */}
      <Box sx={{ width: '100%', height: 6, bgcolor: 'grey.200', borderRadius: 3, mb: 1.5 }}>
        <Box
          sx={{
            width: `${pct}%`,
            height: '100%',
            borderRadius: 3,
            bgcolor: config.color === 'error' ? 'error.main' : config.color === 'warning' ? 'warning.main' : 'info.main',
            transition: 'width 0.3s',
          }}
        />
      </Box>

      {/* Meta info */}
      <Stack direction="row" spacing={1.5} flexWrap="wrap">
        {location && (
          <Typography variant="caption" color="text.secondary">
            📍 {location}
          </Typography>
        )}
        <Typography variant="caption" color="text.secondary">
          🔗 {sensorId}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          🕐 {new Date(detectedAt).toLocaleTimeString('vi-VN')}
        </Typography>
      </Stack>
    </Paper>
  );
}
