import { useState } from 'react';
import { Box, Typography, Grid, Paper, Divider, ToggleButton, ToggleButtonGroup, useTheme, useMediaQuery, MenuItem, Select, FormControl, InputLabel, CircularProgress, Alert } from '@mui/material';
import LockIcon from '@mui/icons-material/Lock';
import NaturePeopleIcon from '@mui/icons-material/NaturePeople';
import { useQuery } from '@tanstack/react-query';
import { getEsgSummary, getEsgEnergy, getEsgCarbon } from '../api/esg';
import { useBuildings } from '../hooks/useBuildings';
import { useEnergyForecast } from '../hooks/useEnergyForecast';
import { useAuth } from '../hooks/useAuth';
import EsgKpiCard from '../components/esg/EsgKpiCard';
import EsgBarChart from '../components/esg/EsgBarChart';
import ReportGenerationPanel from '../components/esg/ReportGenerationPanel';
import { EsgPdfDownloadButton } from '../components/esg/EsgPdfDownloadButton';
import { ForecastChart } from '../components/forecast/ForecastChart';

type ChartView = 'energy' | 'carbon';

const CURRENT_YEAR = new Date().getFullYear();
const YEAR_OPTIONS = [CURRENT_YEAR, CURRENT_YEAR - 1, CURRENT_YEAR - 2];
const FORECAST_ROLES = new Set(['ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_TENANT_ADMIN']);

export default function EsgPage() {
  const [chartView, setChartView] = useState<ChartView>('energy');
  const [selectedBuilding, setSelectedBuilding] = useState<string>('');
  const [selectedYear, setSelectedYear] = useState<number>(CURRENT_YEAR);
  const { user } = useAuth();
  const canViewForecast = user != null && FORECAST_ROLES.has(user.role);
  const muiTheme = useTheme()
  const isMobile = useMediaQuery(muiTheme.breakpoints.down('sm'))

  const { data: summary, isLoading: summaryLoading } = useQuery({
    queryKey: ['esg-summary', selectedYear],
    queryFn: () => getEsgSummary(selectedYear),
    staleTime: 5 * 60_000,
  });

  const yearFrom = `${selectedYear}-01-01T00:00:00Z`;
  const yearTo = `${selectedYear}-12-31T23:59:59Z`;

  const { data: energyData = [] } = useQuery({
    queryKey: ['esg-energy', selectedYear],
    queryFn: () => getEsgEnergy(yearFrom, yearTo),
    enabled: chartView === 'energy',
    staleTime: 5 * 60_000,
  });

  const { data: carbonData = [] } = useQuery({
    queryKey: ['esg-carbon', selectedYear],
    queryFn: () => getEsgCarbon(yearFrom, yearTo),
    enabled: chartView === 'carbon',
    staleTime: 5 * 60_000,
  });

  const { data: buildings = [] } = useBuildings();
  const { data: forecastData, isLoading: forecastLoading, error: forecastError } = useEnergyForecast(
    selectedBuilding || undefined,
    30
  );

  return (
    <Box>
      {/* Header */}
      <Box display="flex" alignItems="center" gap={1} mb={2} flexWrap="wrap">
        <NaturePeopleIcon color="primary" />
        <Typography variant="h5">ESG Metrics</Typography>
        {summary?.period && (
          <Typography variant="body2" color="text.secondary" ml={1}>
            Period: {summary.period}
          </Typography>
        )}
        <Box ml="auto">
          <FormControl size="small" sx={{ minWidth: 100 }}>
            <InputLabel>Year</InputLabel>
            <Select
              value={selectedYear}
              label="Year"
              onChange={(e) => setSelectedYear(Number(e.target.value))}
            >
              {YEAR_OPTIONS.map((y) => (
                <MenuItem key={y} value={y}>{y}</MenuItem>
              ))}
            </Select>
          </FormControl>
        </Box>
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
          <Paper variant="outlined" sx={{ p: 2, minHeight: { xs: 260, sm: 320 } }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
              <Typography variant="subtitle1" fontWeight={600}>Trend by Building</Typography>
              <ToggleButtonGroup
                value={chartView}
                exclusive
                size="small"
                fullWidth={isMobile}
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
            <Box sx={{ mt: 2 }}>
              <EsgPdfDownloadButton year={selectedYear} quarter={Math.ceil(new Date().getMonth() / 3)} />
            </Box>
          </Paper>
        </Grid>
      </Grid>

      {/* Energy Forecast Section */}
      <Box mt={3}>
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="subtitle1" fontWeight={600} mb={2}>
            Energy Forecast
          </Typography>
          <Divider sx={{ mb: 2 }} />

          {!canViewForecast && (
            <Box display="flex" alignItems="center" justifyContent="center" gap={1} minHeight={120}>
              <LockIcon color="disabled" />
              <Typography color="text.secondary">
                Energy forecast is available to operators and administrators only.
              </Typography>
            </Box>
          )}

          {canViewForecast && (
            <>
              <Box display="flex" alignItems="center" justifyContent="flex-end" mb={2} gap={1} flexWrap="wrap">
                <FormControl size="small" sx={{ minWidth: 200 }}>
                  <InputLabel>Building</InputLabel>
                  <Select
                    value={selectedBuilding}
                    label="Building"
                    onChange={(e) => setSelectedBuilding(e.target.value)}
                  >
                    <MenuItem value="">
                      <em>Select a building</em>
                    </MenuItem>
                    {buildings.map((b) => (
                      <MenuItem key={b.id} value={b.id}>
                        {b.buildingName || b.buildingCode}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Box>

              {!selectedBuilding && (
                <Box display="flex" alignItems="center" justifyContent="center" minHeight={200}>
                  <Typography color="text.secondary">Select a building to view energy forecast</Typography>
                </Box>
              )}

              {selectedBuilding && forecastLoading && (
                <Box display="flex" alignItems="center" justifyContent="center" minHeight={200}>
                  <CircularProgress size={32} />
                  <Typography ml={2} color="text.secondary">Loading forecast...</Typography>
                </Box>
              )}

              {selectedBuilding && forecastError && (
                <Alert severity="error" sx={{ mb: 1 }}>
                  Failed to load forecast data. The service may be unavailable.
                </Alert>
              )}

              {selectedBuilding && forecastData && (
                <ForecastChart
                  points={forecastData.points}
                  isFallback={forecastData.isFallback}
                  mape={forecastData.mape}
                  height={350}
                />
              )}
            </>
          )}
        </Paper>
      </Box>
    </Box>
  );
}
