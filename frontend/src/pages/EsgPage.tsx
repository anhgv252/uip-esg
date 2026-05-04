import { useState } from 'react';
import { Box, Typography, Grid, Paper, Divider, ToggleButton, ToggleButtonGroup } from '@mui/material';
import NaturePeopleIcon from '@mui/icons-material/NaturePeople';
import { useQuery } from '@tanstack/react-query';
import { getEsgSummary, getEsgEnergy, getEsgCarbon } from '../api/esg';
import EsgKpiCard from '../components/esg/EsgKpiCard';
import EsgBarChart from '../components/esg/EsgBarChart';
import ReportGenerationPanel from '../components/esg/ReportGenerationPanel';
import { useAuth } from '../hooks/useAuth';

type ChartView = 'energy' | 'carbon';

export default function EsgPage() {
  const [chartView, setChartView] = useState<ChartView>('energy');
  const { user } = useAuth();
  const tenantId = user?.tenantId ?? 'default';

  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['esg-summary', tenantId],
    queryFn: () => getEsgSummary(),
    staleTime: 5 * 60_000,
  });

  const { data: energyData = [] } = useQuery({
    queryKey: ['esg-energy', tenantId],
    queryFn: () => getEsgEnergy(),
    enabled: chartView === 'energy',
    staleTime: 5 * 60_000,
  });

  const { data: carbonData = [] } = useQuery({
    queryKey: ['esg-carbon', tenantId],
    queryFn: () => getEsgCarbon(),
    enabled: chartView === 'carbon',
    staleTime: 5 * 60_000,
  });

  return (
    <Box>
      {/* Header */}
      <Box display="flex" alignItems="center" gap={1} mb={2}>
        <NaturePeopleIcon color="primary" />
        <Typography variant="h5">ESG Metrics</Typography>
        {summary?.period && (
          <Typography variant="body2" color="text.secondary" ml={1}>
            Period: {summary.period}
          </Typography>
        )}
      </Box>

      {/* KPI Cards */}
      <Grid container spacing={2} mb={2}>
        <Grid item xs={12} sm={4}>
          <EsgKpiCard
            label="Energy Consumption"
            value={summary?.totalEnergyKwh}
            unit="kWh"
            trend={summary?.energyTrend ?? undefined}
            loading={summaryLoading}
            higherIsBad
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <EsgKpiCard
            label="Water Usage"
            value={summary?.totalWaterM3}
            unit="m³"
            trend={summary?.waterTrend ?? undefined}
            loading={summaryLoading}
            higherIsBad
          />
        </Grid>
        <Grid item xs={12} sm={4}>
          <EsgKpiCard
            label="Carbon Footprint"
            value={summary?.totalCarbonTco2e}
            unit="tCO₂e"
            trend={summary?.carbonTrend ?? undefined}
            loading={summaryLoading}
            higherIsBad
          />
        </Grid>
      </Grid>

      {/* Chart + Report Panel */}
      <Grid container spacing={2}>
        <Grid item xs={12} md={8}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
              <Typography variant="subtitle1" fontWeight={600}>Trend by Building</Typography>
              <ToggleButtonGroup
                value={chartView}
                exclusive
                size="small"
                onChange={(_, v) => v && setChartView(v)}
              >
                <ToggleButton value="energy">Energy</ToggleButton>
                <ToggleButton value="carbon">Carbon</ToggleButton>
              </ToggleButtonGroup>
            </Box>
            <Divider sx={{ mb: 1 }} />
            {chartView === 'energy' ? (
              <EsgBarChart data={energyData} metricLabel="Energy" unit="kWh" />
            ) : (
              <EsgBarChart data={carbonData} metricLabel="Carbon" unit="kg CO₂" />
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
            <ReportGenerationPanel />
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
