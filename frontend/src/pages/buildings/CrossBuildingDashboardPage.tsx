import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Alert, Box, Grid, Typography } from '@mui/material'
import { MultiBuildingSelector } from '@/components/buildings/MultiBuildingSelector'
import { useBuildingSelectionStore } from '@/stores/buildingSelectionStore'
import { useBuildings } from '@/hooks/useBuildings'
import { useEnergyAnalytics, useEmissionsAnalytics, useAqiTrend } from '@/hooks/useAnalytics'
import { EnergyBarChart } from '@/components/analytics/EnergyBarChart'
import { EmissionsBarChart } from '@/components/analytics/EmissionsBarChart'
import { AqiTrendLineChart } from '@/components/analytics/AqiTrendLineChart'
import { BuildingBreakdownTable } from '@/components/analytics/BuildingBreakdownTable'
import { AnalyticsFilterPanel } from '@/components/analytics/AnalyticsFilterPanel'
import { tenantStore } from '@/api/client'

export function CrossBuildingDashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { selectedBuildings, addBuilding, clearSelection, isSelected } =
    useBuildingSelectionStore()
  const { data: allBuildings = [] } = useBuildings()

  useEffect(() => {
    const idsParam = searchParams.get('ids')
    if (!idsParam || allBuildings.length === 0) return

    const ids = idsParam.split(',').filter(Boolean)
    clearSelection()
    ids.forEach((id) => {
      const found = allBuildings.find((b) => b.id === id || b.buildingCode === id)
      if (found && !isSelected(found.id)) {
        addBuilding({
          id: found.id,
          buildingCode: found.buildingCode,
          buildingName: found.buildingName,
          tenantId: found.tenantId,
        })
      }
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allBuildings])

  useEffect(() => {
    const ids = selectedBuildings.map((b) => b.id)
    if (ids.length > 0) {
      setSearchParams({ ids: ids.join(',') }, { replace: true })
    } else {
      setSearchParams({}, { replace: true })
    }
  }, [selectedBuildings, setSearchParams])

  const buildingIds = selectedBuildings.map((b) => b.buildingCode)

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Box>
          <Typography variant="h5" fontWeight={600}>
            Cross-Building Analytics
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Compare energy, water, and carbon metrics across your building cluster
          </Typography>
        </Box>
        <MultiBuildingSelector />
      </Box>

      {buildingIds.length === 0 ? (
        <Alert severity="info" sx={{ mt: 2 }}>
          Select up to 5 buildings from the button above to compare analytics across your cluster.
        </Alert>
      ) : (
        <Box>
          <AnalyticsFilterPanel />
          <AnalyticsPanel buildingIds={buildingIds} />
        </Box>
      )}
    </Box>
  )
}

function AnalyticsPanel({ buildingIds }: { buildingIds: string[] }) {
  const tenantId = tenantStore.get() ?? ''

  const energy = useEnergyAnalytics(tenantId, buildingIds)
  const emissions = useEmissionsAnalytics(tenantId, buildingIds)
  const aqi = useAqiTrend(tenantId, buildingIds)

  return (
    <Grid container spacing={2}>
      <Grid item xs={12} md={6}>
        <EnergyBarChart buildings={energy.data?.buildings ?? []} loading={energy.isLoading} />
      </Grid>
      <Grid item xs={12} md={6}>
        <EmissionsBarChart buildings={emissions.data?.buildings ?? []} loading={emissions.isLoading} />
      </Grid>
      <Grid item xs={12} md={6}>
        <AqiTrendLineChart dataPoints={aqi.data?.dataPoints ?? []} loading={aqi.isLoading} />
      </Grid>
      <Grid item xs={12} md={6}>
        <BuildingBreakdownTable
          energy={energy.data}
          emissions={emissions.data}
          loading={energy.isLoading || emissions.isLoading}
        />
      </Grid>
    </Grid>
  )
}
