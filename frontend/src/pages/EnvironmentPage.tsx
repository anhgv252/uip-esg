import { useState, useCallback } from 'react';
import {
  Box,
  Typography,
  Grid,
  Paper,
  Card,
  CardContent,
  Alert,
  Snackbar,
  Divider,
  Skeleton,
} from '@mui/material';
import AirIcon from '@mui/icons-material/Air';
import { useQuery } from '@tanstack/react-query';
import { getCurrentAqi, getAqiHistory, getSensors, Sensor } from '../api/environment';
import AqiGauge from '../components/environment/AqiGauge';
import AqiTrendChart from '../components/environment/AqiTrendChart';
import SensorStatusTable from '../components/environment/SensorStatusTable';
import { useNotificationSSE, AlertNotification } from '../hooks/useNotificationSSE';

export default function EnvironmentPage() {
  const [selectedSensor, setSelectedSensor] = useState<Sensor | null>(null);
  const [liveAlert, setLiveAlert] = useState<AlertNotification | null>(null);

  const { data: sensors = [], isLoading: sensorsLoading } = useQuery({
    queryKey: ['sensors'],
    queryFn: getSensors,
    refetchInterval: 60_000,
  });

  const { data: currentAqi = [], isLoading: aqiLoading } = useQuery({
    queryKey: ['aqi-current'],
    queryFn: getCurrentAqi,
    refetchInterval: 30_000,
  });

  const { data: aqiHistory = [] } = useQuery({
    queryKey: ['aqi-history', selectedSensor?.district],
    queryFn: () =>
      selectedSensor
        ? getAqiHistory(selectedSensor.district)
        : Promise.resolve([]),
    enabled: !!selectedSensor,
  });

  const handleAlert = useCallback((alert: AlertNotification) => {
    setLiveAlert(alert);
  }, []);

  useNotificationSSE(handleAlert);

  const onlineCount = sensors.filter((s) => s.status === 'ONLINE').length;

  return (
    <Box>
      {/* Header */}
      <Box display="flex" alignItems="center" gap={1} mb={2}>
        <AirIcon color="primary" />
        <Typography variant="h5">Environment Monitoring</Typography>
        <Box flexGrow={1} />
        <Typography variant="body2" color="text.secondary">
          {onlineCount}/{sensors.length} sensors online
        </Typography>
      </Box>

      {/* Live alert banner */}
      <Snackbar
        open={!!liveAlert}
        autoHideDuration={8000}
        onClose={() => setLiveAlert(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Alert
          severity={
            liveAlert?.severity === 'CRITICAL' || liveAlert?.severity === 'HIGH'
              ? 'error'
              : 'warning'
          }
          onClose={() => setLiveAlert(null)}
          sx={{ width: '100%' }}
        >
          <strong>[{liveAlert?.severity}]</strong> {liveAlert?.message}
        </Alert>
      </Snackbar>

      {/* AQI Gauges Grid */}
      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Typography variant="subtitle1" fontWeight={600} mb={1}>
          Current AQI by Station
        </Typography>
        <Grid container spacing={1}>
          {aqiLoading
            ? [...Array(4)].map((_, i) => (
                <Grid item xs={6} sm={4} md={3} key={i}>
                  <Skeleton variant="rectangular" height={180} sx={{ borderRadius: 1 }} />
                </Grid>
              ))
            : currentAqi.map((item) => (
                <Grid item xs={6} sm={4} md={3} key={item.sensorId}>
                  {(() => {
                    const sensor = sensors.find((x) => x.sensorCode === item.sensorId);
                    return (
                  <Card
                    variant="outlined"
                    sx={{
                      cursor: 'pointer',
                      border: selectedSensor?.sensorCode === item.sensorId ? '2px solid' : undefined,
                      borderColor: selectedSensor?.sensorCode === item.sensorId ? 'primary.main' : undefined,
                    }}
                    onClick={() => {
                      const s = sensors.find((x) => x.sensorCode === item.sensorId);
                      setSelectedSensor(s ?? null);
                    }}
                  >
                    <CardContent sx={{ p: 1, '&:last-child': { pb: 1 } }}>
                      <AqiGauge
                        aqi={item.aqi}
                        category={item.category}
                        color={item.color}
                        sensorName={sensor?.name ?? item.sensorName}
                        district={sensor?.district ?? item.district}
                        dominantPollutant={item.dominantPollutant}
                      />
                    </CardContent>
                  </Card>
                    );
                  })()}
                </Grid>
              ))}
        </Grid>
      </Paper>

      {/* Trend Chart + Sensor Table */}
      <Grid container spacing={2}>
        <Grid item xs={12} md={8}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            {selectedSensor ? (
              <>
                <Typography variant="subtitle1" fontWeight={600} mb={1}>
                  {selectedSensor.name} — AQI Trend
                </Typography>
                <AqiTrendChart data={aqiHistory} />
              </>
            ) : (
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 220 }}>
                <Typography color="text.secondary">
                  Click a sensor above to view its 24h AQI trend
                </Typography>
              </Box>
            )}
          </Paper>
        </Grid>

        <Grid item xs={12} md={4}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" fontWeight={600} mb={1}>
              Sensor Status
            </Typography>
            <Divider sx={{ mb: 1 }} />
            <SensorStatusTable
              sensors={sensors}
              loading={sensorsLoading}
              selectedSensorId={selectedSensor?.id}
              onSensorSelect={(s) => setSelectedSensor(s)}
            />
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
