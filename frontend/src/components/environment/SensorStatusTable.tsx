import { useMemo } from 'react';
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Typography,
  Skeleton,
} from '@mui/material';
import { Sensor } from '../../api/environment';
import { formatDistanceToNow } from 'date-fns';

interface SensorStatusTableProps {
  sensors: Sensor[];
  loading?: boolean;
  onSensorSelect?: (sensor: Sensor) => void;
  selectedSensorId?: string;
}

const STATUS_COLORS: Record<string, 'success' | 'error' | 'warning'> = {
  ONLINE: 'success',
  OFFLINE: 'error',
  MAINTENANCE: 'warning',
};

export function SensorStatusTable({
  sensors,
  loading,
  onSensorSelect,
  selectedSensorId,
}: SensorStatusTableProps) {
  const sorted = useMemo(
    () =>
      [...sensors].sort((a, b) => {
        const leftDistrict = a.district ?? '';
        const rightDistrict = b.district ?? '';
        const districtComparison = leftDistrict.localeCompare(rightDistrict);
        return districtComparison !== 0
          ? districtComparison
          : (a.name ?? '').localeCompare(b.name ?? '');
      }),
    [sensors]
  );

  if (loading) {
    return (
      <TableContainer component={Paper} elevation={0} variant="outlined">
        {[...Array(5)].map((_, i) => (
          <Skeleton key={i} height={52} sx={{ mx: 2 }} />
        ))}
      </TableContainer>
    );
  }

  return (
    <TableContainer component={Paper} elevation={0} variant="outlined" sx={{ maxHeight: 340, overflow: 'auto' }}>
      <Table size="small" stickyHeader>
        <TableHead>
          <TableRow>
            <TableCell><strong>Sensor</strong></TableCell>
            <TableCell><strong>District</strong></TableCell>
            <TableCell><strong>Status</strong></TableCell>
            <TableCell><strong>Last Seen</strong></TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {sorted.map((sensor) => (
            <TableRow
              key={sensor.id}
              hover
              selected={sensor.id === selectedSensorId}
              onClick={() => onSensorSelect?.(sensor)}
              sx={{ cursor: onSensorSelect ? 'pointer' : 'default' }}
            >
              <TableCell>
                <Typography variant="body2" fontWeight={500}>{sensor.name}</Typography>
                <Typography variant="caption" color="text.secondary">{sensor.sensorCode}</Typography>
              </TableCell>
              <TableCell>{sensor.district}</TableCell>
              <TableCell>
                <Chip
                  label={sensor.status}
                  color={STATUS_COLORS[sensor.status] ?? 'default'}
                  size="small"
                  variant="outlined"
                />
              </TableCell>
              <TableCell>
                <Typography variant="caption" color="text.secondary">
                  {sensor.lastSeenAt
                    ? formatDistanceToNow(new Date(sensor.lastSeenAt), { addSuffix: true })
                    : '—'}
                </Typography>
              </TableCell>
            </TableRow>
          ))}
          {!sorted.length && (
            <TableRow>
              <TableCell colSpan={4} align="center">
                <Typography color="text.secondary">No sensors found</Typography>
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default SensorStatusTable;
