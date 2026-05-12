import { Box, Chip, Stack, Tooltip, Typography } from '@mui/material'
import BusinessIcon from '@mui/icons-material/Business'
import { useBuildingSelectionStore } from '@/stores/buildingSelectionStore'

export function BuildingContextBar() {
  const { selectedBuildings, removeBuilding } = useBuildingSelectionStore()

  if (selectedBuildings.length === 0) return null

  return (
    <Box
      sx={{
        px: 2,
        py: 1,
        bgcolor: 'primary.50',
        borderBottom: 1,
        borderColor: 'divider',
        background: (theme) =>
          theme.palette.mode === 'dark' ? 'rgba(144,202,249,0.08)' : '#EFF6FF',
      }}
    >
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" gap={1}>
        <BusinessIcon fontSize="small" color="primary" />
        <Typography variant="caption" color="primary.main" fontWeight={600}>
          Selected Buildings:
        </Typography>
        {selectedBuildings.map((b) => (
          <Chip
            key={b.id}
            label={b.buildingCode}
            size="small"
            color="primary"
            variant="outlined"
            onDelete={() => removeBuilding(b.id)}
          />
        ))}
        {selectedBuildings.length === 5 && (
          <Tooltip title="Maximum 5 buildings can be compared at once">
            <Typography variant="caption" color="warning.main" fontWeight={500}>
              Max reached
            </Typography>
          </Tooltip>
        )}
      </Stack>
    </Box>
  )
}
