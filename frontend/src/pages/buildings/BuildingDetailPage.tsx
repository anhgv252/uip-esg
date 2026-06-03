import { useParams, useSearchParams } from 'react-router-dom'
import { Box, Grid, Paper, Skeleton, Tab, Tabs, Typography } from '@mui/material'
import { useBuildings } from '@/hooks/useBuildings'
import { useSafetyScore } from '@/hooks/useSafetyScore'
import { useAlerts } from '@/hooks/useAlertManagement'
import { SafetyScoreGauge, SafetyScoreGaugeSkeleton } from '@/components/safety/SafetyScoreGauge'
import { SafetyTrendChart } from '@/components/safety/SafetyTrendChart'
import { SafetySensorStatusGrid } from '@/components/safety/SafetySensorStatusGrid'
import { SafetyAlertBanner } from '@/components/safety/SafetyAlertBanner'
import { SafetyAlertHistory } from '@/components/safety/SafetyAlertHistory'

type SafetyTab = 'safety'

export function BuildingDetailPage() {
  const { id: buildingId } = useParams<{ id: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  const activeTab = (searchParams.get('tab') as SafetyTab) ?? 'safety'

  const { data: buildings = [] } = useBuildings()
  const building = buildings.find(b => b.id === buildingId)

  const { data: safetyScore, isLoading: scoreLoading } = useSafetyScore(buildingId)

  // Fetch structural alerts for this building (filter by module client-side for Wave 2)
  const { data: alertsPage, isLoading: alertsLoading } = useAlerts({
    status: 'OPEN',
    size: 20,
  })
  const structuralAlerts = (alertsPage?.content ?? []).filter(a => a.module === 'STRUCTURAL')
  const criticalAlerts = structuralAlerts.filter(a => a.severity === 'CRITICAL' || a.severity === 'HIGH')

  function handleTabChange(_: React.SyntheticEvent, value: SafetyTab) {
    setSearchParams({ tab: value }, { replace: true })
  }

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      {/* Page header */}
      <Box sx={{ mb: 2 }}>
        {building ? (
          <Typography variant="h5" fontWeight={700}>
            {building.buildingName}
          </Typography>
        ) : (
          <Skeleton variant="text" width={240} height={36} />
        )}
        <Typography variant="body2" color="text.secondary">
          {buildingId}
        </Typography>
      </Box>

      {/* Tab navigation — URL state: /buildings/:id?tab=safety */}
      <Tabs
        value={activeTab}
        onChange={handleTabChange}
        sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}
        aria-label="Building detail tabs"
      >
        <Tab value="safety" label="An toàn kết cấu" aria-label="Tab an toàn kết cấu" />
      </Tabs>

      {/* Safety tab content */}
      {activeTab === 'safety' && (
        <Box>
          {/* P0 alert banners — non-dismissible per BR-010 */}
          <SafetyAlertBanner alerts={criticalAlerts} loading={alertsLoading} />

          <Grid container spacing={2}>
            {/* Safety score gauge */}
            <Grid item xs={12} sm={4} md={3}>
              <Paper sx={{ p: 2, height: '100%' }}>
                {scoreLoading ? (
                  <SafetyScoreGaugeSkeleton />
                ) : (
                  <SafetyScoreGauge
                    score={safetyScore?.score ?? 0}
                    offline={safetyScore?.status === 'OFFLINE'}
                    buildingName={building?.buildingName}
                    lastUpdated={safetyScore?.lastUpdated}
                  />
                )}
              </Paper>
            </Grid>

            {/* Vibration trend chart */}
            <Grid item xs={12} sm={8} md={9}>
              <Paper sx={{ p: 2 }}>
                <Typography variant="subtitle2" fontWeight={600} mb={1}>
                  Biểu đồ rung động 24h
                </Typography>
                <SafetyTrendChart buildingId={buildingId} />
              </Paper>
            </Grid>

            {/* Sensor status grid */}
            <Grid item xs={12} md={6}>
              <Paper sx={{ p: 2 }}>
                <Typography variant="subtitle2" fontWeight={600} mb={1}>
                  Trạng thái cảm biến
                </Typography>
                <SafetySensorStatusGrid sensors={[]} loading={false} />
              </Paper>
            </Grid>

            {/* Alert history */}
            <Grid item xs={12} md={6}>
              <Paper sx={{ p: 2 }}>
                <SafetyAlertHistory
                  alerts={structuralAlerts}
                  loading={alertsLoading}
                />
              </Paper>
            </Grid>
          </Grid>
        </Box>
      )}
    </Box>
  )
}
