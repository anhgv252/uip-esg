import { Box, Card, CardContent, Typography, Skeleton } from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import TrendingFlatIcon from '@mui/icons-material/TrendingFlat';

interface EsgKpiCardProps {
  label: string;
  value: number | undefined;
  unit: string;
  trend?: number; // % change vs previous period; positive = increase
  loading?: boolean;
  /** When set, a positive trend is bad (e.g. carbon emissions higher = worse) */
  higherIsBad?: boolean;
}

function TrendIndicator({ trend, higherIsBad }: { trend: number; higherIsBad?: boolean }) {
  const isNeutral = Math.abs(trend) < 0.1;
  const isPositive = trend > 0;
  // For energy/water/carbon: lower is better → positive trend = bad
  const isBad = higherIsBad ? isPositive : !isPositive;

  if (isNeutral) {
    return <TrendingFlatIcon sx={{ fontSize: 16, color: 'text.secondary' }} />;
  }

  const color = isBad ? 'error.main' : 'success.main';
  return (
    <Box display="flex" alignItems="center" gap={0.5} sx={{ color }}>
      {isPositive ? (
        <TrendingUpIcon sx={{ fontSize: 16 }} />
      ) : (
        <TrendingDownIcon sx={{ fontSize: 16 }} />
      )}
      <Typography variant="caption" fontWeight={600}>
        {Math.abs(trend).toFixed(1)}%
      </Typography>
    </Box>
  );
}

export function EsgKpiCard({ label, value, unit, trend, loading, higherIsBad }: EsgKpiCardProps) {
  if (loading) {
    return (
      <Card variant="outlined" sx={{ height: '100%' }}>
        <CardContent>
          <Skeleton width="60%" height={20} />
          <Skeleton width="80%" height={40} sx={{ mt: 1 }} />
          <Skeleton width="40%" height={20} sx={{ mt: 0.5 }} />
        </CardContent>
      </Card>
    );
  }

  const displayValue = value !== undefined ? value.toLocaleString('vi-VN', { maximumFractionDigits: 1 }) : '—';

  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="subtitle2" color="text.secondary" gutterBottom>
          {label}
        </Typography>
        <Box display="flex" alignItems="baseline" gap={1}>
          <Typography variant="h4" fontWeight={700}>{displayValue}</Typography>
          <Typography variant="body2" color="text.secondary">{unit}</Typography>
        </Box>
        {trend !== undefined && (
          <Box mt={0.5}>
            <TrendIndicator trend={trend} higherIsBad={higherIsBad} />
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

export default EsgKpiCard;
