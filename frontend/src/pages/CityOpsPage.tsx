import { useState, useCallback } from 'react'
import { Box, Typography, CircularProgress, Alert } from '@mui/material'
import LocationCityIcon from '@mui/icons-material/LocationCity'
import { useQuery } from '@tanstack/react-query'
import { getSensorsForMap, getRecentAlerts } from '@/api/cityops'
import type { SensorWithAqi } from '@/api/cityops'
import type { AlertEvent } from '@/api/alerts'
import SensorMap from '@/components/cityops/SensorMap'
import AlertFeedPanel from '@/components/cityops/AlertFeedPanel'
import DistrictFilter from '@/components/cityops/DistrictFilter'
import { useMapSSE } from '@/hooks/useMapSSE'

export default function CityOpsPage() {
  const [districtFilter, setDistrictFilter] = useState<string | null>(null)
  const [liveAlerts, setLiveAlerts] = useState<AlertEvent[]>([])

  const { data: sensors = [], isLoading: sensorsLoading, error: sensorsError } = useQuery({
    queryKey: ['sensors-for-map'],
    queryFn: getSensorsForMap,
    refetchInterval: 60_000,
  })

  const { data: alertPage, isLoading: alertsLoading } = useQuery({
    queryKey: ['recent-alerts'],
    queryFn: getRecentAlerts,
    refetchInterval: 30_000,
  })

  const [sensorOverrides, setSensorOverrides] = useState<Record<string, SensorWithAqi>>({})

  const handleSensorUpdate = useCallback((sensor: SensorWithAqi) => {
    setSensorOverrides((prev) => ({ ...prev, [sensor.id]: sensor }))
  }, [])

  const handleNewAlert = useCallback((alert: AlertEvent) => {
    setLiveAlerts((prev) => [alert, ...prev].slice(0, 20))
  }, [])

  useMapSSE({ onSensorUpdate: handleSensorUpdate, onAlert: handleNewAlert })

  // Merge live SSE sensor overrides into sensor list
  const displayedSensors = sensors.map((s) => sensorOverrides[s.id] ?? s)

  // Merge live alerts into API-fetched alerts (live takes priority up front)
  const feedAlerts = [
    ...liveAlerts,
    ...(alertPage?.content ?? []),
  ].slice(0, 20)

  return (
    <Box sx={{ height: 'calc(100vh - 84px)', display: 'flex', flexDirection: 'column', gap: 1 }}>
      {/* Header */}
      <Box display="flex" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={1}>
        <Box display="flex" alignItems="center" gap={1}>
          <LocationCityIcon color="primary" />
          <Typography variant="h5">City Operations Center</Typography>
          <Typography variant="body2" color="text.secondary">
            ({displayedSensors.filter((s) => s.status === 'ONLINE').length} sensors online)
          </Typography>
        </Box>
        <DistrictFilter
          sensors={displayedSensors}
          value={districtFilter}
          onChange={setDistrictFilter}
        />
      </Box>

      {sensorsError && (
        <Alert severity="warning">Could not load sensor data. Map may be incomplete.</Alert>
      )}

      {/* Main 2-panel layout */}
      <Box sx={{ flex: 1, display: 'flex', gap: 1.5, minHeight: 0 }}>
        {/* Map (70%) */}
        <Box sx={{ flex: '0 0 70%', position: 'relative', minHeight: 0 }}>
          {sensorsLoading ? (
            <Box
              display="flex"
              alignItems="center"
              justifyContent="center"
              height="100%"
              bgcolor="background.paper"
              borderRadius={2}
            >
              <CircularProgress />
            </Box>
          ) : (
            <SensorMap sensors={displayedSensors} districtFilter={districtFilter} />
          )}
        </Box>

        {/* Alert Feed (30%) */}
        <Box sx={{ flex: 1, minHeight: 0, maxHeight: '100%' }}>
          <AlertFeedPanel alerts={feedAlerts} loading={alertsLoading && liveAlerts.length === 0} />
        </Box>
      </Box>
    </Box>
  )
}
