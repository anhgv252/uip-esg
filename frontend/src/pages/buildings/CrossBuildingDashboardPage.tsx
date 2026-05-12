import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Alert, Box, Typography } from '@mui/material'
import { BuildingDashboardSkeleton } from '@/components/buildings/BuildingDashboardSkeleton'
import { MultiBuildingSelector } from '@/components/buildings/MultiBuildingSelector'
import { useBuildingSelectionStore } from '@/stores/buildingSelectionStore'
import { useBuildings } from '@/hooks/useBuildings'

export function CrossBuildingDashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { selectedBuildings, addBuilding, clearSelection, isSelected } =
    useBuildingSelectionStore()
  const { data: allBuildings = [] } = useBuildings()

  // Sync URL → Zustand on mount
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

  // Sync Zustand → URL on selection change
  useEffect(() => {
    const ids = selectedBuildings.map((b) => b.id)
    if (ids.length > 0) {
      setSearchParams({ ids: ids.join(',') }, { replace: true })
    } else {
      setSearchParams({}, { replace: true })
    }
  }, [selectedBuildings, setSearchParams])

  return (
    <Box>
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          mb: 2,
        }}
      >
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

      {selectedBuildings.length === 0 ? (
        <Alert severity="info" sx={{ mt: 2 }}>
          Select up to 5 buildings from the button above to compare analytics across your cluster.
        </Alert>
      ) : (
        <Box>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Showing analytics for{' '}
            <strong>
              {selectedBuildings.length} building{selectedBuildings.length > 1 ? 's' : ''}
            </strong>
            :{' '}
            {selectedBuildings.map((b) => b.buildingCode).join(', ')}
          </Typography>
          {/* Sprint 2: v3-FE-03 Analytics charts go here */}
          <BuildingDashboardSkeleton />
        </Box>
      )}
    </Box>
  )
}
