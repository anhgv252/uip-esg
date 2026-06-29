import {
  Box,
  Card,
  CardContent,
  Typography,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Stack,
} from '@mui/material';
import {
  TrendingUp as SavingsIcon,
  Co2Outlined as Co2Icon,
  Schedule as PaybackIcon,
} from '@mui/icons-material';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import type { BuildingRoiResponse } from '@/types/nlWorkflow';

interface BuildingRoiChartProps {
  roi: BuildingRoiResponse;
}

export function formatVND(amount: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

export default function BuildingRoiChart({ roi }: BuildingRoiChartProps) {
  const chartData = [
    {
      name: 'Manual\nOps',
      cost: roi.savings.manualOpsCostVnd,
    },
    {
      name: 'UIP\nAutomation',
      cost: roi.costs.totalCostVnd,
    },
  ];

  const paybackColor = roi.savings.paybackMonths < 12 ? 'success' : roi.savings.paybackMonths < 24 ? 'warning' : 'error';

  return (
    <Box>
      {/* Summary Cards */}
      <Stack direction="row" spacing={2} mb={3}>
        <Card variant="outlined" sx={{ flex: 1 }}>
          <CardContent>
            <Box display="flex" alignItems="center" gap={1} mb={1}>
              <SavingsIcon color="success" />
              <Typography variant="subtitle2" color="text.secondary">
                Monthly Savings
              </Typography>
            </Box>
            <Typography variant="h5" fontWeight={600} color="success.main">
              {formatVND(roi.savings.automationSavingsVnd)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {((roi.savings.automationSavingsVnd / roi.savings.manualOpsCostVnd) * 100).toFixed(1)}% reduction
            </Typography>
          </CardContent>
        </Card>

        <Card variant="outlined" sx={{ flex: 1 }}>
          <CardContent>
            <Box display="flex" alignItems="center" gap={1} mb={1}>
              <PaybackIcon color="primary" />
              <Typography variant="subtitle2" color="text.secondary">
                Payback Period
              </Typography>
            </Box>
            <Box display="flex" alignItems="baseline" gap={1}>
              <Typography variant="h5" fontWeight={600}>
                {roi.savings.paybackMonths}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                months
              </Typography>
              <Chip label={paybackColor === 'success' ? 'Fast' : paybackColor === 'warning' ? 'Good' : 'Slow'} color={paybackColor} size="small" />
            </Box>
          </CardContent>
        </Card>

        <Card variant="outlined" sx={{ flex: 1 }}>
          <CardContent>
            <Box display="flex" alignItems="center" gap={1} mb={1}>
              <Co2Icon color="success" />
              <Typography variant="subtitle2" color="text.secondary">
                CO₂ Saved
              </Typography>
            </Box>
            <Box display="flex" alignItems="baseline" gap={1}>
              <Typography variant="h5" fontWeight={600}>
                {roi.savings.co2SavedKg.toLocaleString()}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                kg/month
              </Typography>
            </Box>
          </CardContent>
        </Card>
      </Stack>

      {/* Cost Comparison Bar Chart */}
      <Card variant="outlined" sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            Cost Comparison
          </Typography>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis tickFormatter={(value) => `${(value / 1_000_000).toFixed(1)}M`} />
              <Tooltip formatter={(value) => formatVND(Number(value))} />
              <Legend />
              <Bar dataKey="cost" fill="#ef5350" name="Monthly Cost (VND)" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>

      {/* Detailed Breakdown Table */}
      <Card variant="outlined">
        <CardContent>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            Detailed ROI Breakdown
          </Typography>
          <TableContainer component={Paper} variant="outlined">
            <Table size="small">
              <TableHead>
                <TableRow sx={{ backgroundColor: 'grey.50' }}>
                  <TableCell>Metric</TableCell>
                  <TableCell align="right">Before (Manual)</TableCell>
                  <TableCell align="right">After (UIP)</TableCell>
                  <TableCell align="right">Unit</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {roi.comparisonChart.map((row) => (
                  <TableRow key={row.metric}>
                    <TableCell>{row.metric}</TableCell>
                    <TableCell align="right">{row.before.toLocaleString()}</TableCell>
                    <TableCell align="right">{row.after.toLocaleString()}</TableCell>
                    <TableCell align="right">{row.unit}</TableCell>
                  </TableRow>
                ))}
                <TableRow sx={{ backgroundColor: 'success.lighter', fontWeight: 600 }}>
                  <TableCell>
                    <strong>Net Savings</strong>
                  </TableCell>
                  <TableCell align="right">{formatVND(roi.savings.manualOpsCostVnd)}</TableCell>
                  <TableCell align="right">{formatVND(roi.costs.totalCostVnd)}</TableCell>
                  <TableCell align="right">
                    <Chip label={formatVND(roi.savings.automationSavingsVnd)} color="success" size="small" />
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      {/* UIP Cost Details */}
      <Card variant="outlined" sx={{ mt: 2 }}>
        <CardContent>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            UIP Cost Breakdown ({roi.month})
          </Typography>
          <Stack spacing={1}>
            <Box display="flex" justifyContent="space-between">
              <Typography variant="body2">Base Fee</Typography>
              <Typography variant="body2" fontWeight={500}>
                {formatVND(roi.costs.baseFeeVnd)}
              </Typography>
            </Box>
            <Box display="flex" justifyContent="space-between">
              <Typography variant="body2">AI Tokens Used</Typography>
              <Typography variant="body2">
                {roi.costs.aiTokensUsed.toLocaleString()} (overage: {roi.costs.aiOverageTokens.toLocaleString()})
              </Typography>
            </Box>
            {roi.costs.aiOverageCostVnd > 0 && (
              <Box display="flex" justifyContent="space-between">
                <Typography variant="body2">AI Overage Cost</Typography>
                <Typography variant="body2" fontWeight={500}>
                  {formatVND(roi.costs.aiOverageCostVnd)}
                </Typography>
              </Box>
            )}
            <Box display="flex" justifyContent="space-between">
              <Typography variant="body2">Sensor Readings</Typography>
              <Typography variant="body2">{roi.costs.sensorReadings.toLocaleString()}</Typography>
            </Box>
            <Box display="flex" justifyContent="space-between">
              <Typography variant="body2">Alerts Generated</Typography>
              <Typography variant="body2">{roi.costs.alertsGenerated.toLocaleString()}</Typography>
            </Box>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
