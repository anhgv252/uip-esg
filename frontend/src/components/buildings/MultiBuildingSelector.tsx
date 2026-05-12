import { useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  InputAdornment,
  List,
  ListItemButton,
  ListItemText,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import AddBusinessIcon from '@mui/icons-material/AddBusiness'
import SearchIcon from '@mui/icons-material/Search'
import { useBuildings } from '@/hooks/useBuildings'
import { useBuildingSelectionStore } from '@/stores/buildingSelectionStore'
import { useDebounce } from '@/hooks/useDebounce'

export function MultiBuildingSelector() {
  const [open, setOpen] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const debouncedSearch = useDebounce(searchTerm, 300)

  const { data: buildings = [], isLoading, error } = useBuildings()
  const { selectedBuildings, addBuilding, removeBuilding, isSelected, maxReached } =
    useBuildingSelectionStore()

  const filtered = buildings.filter(
    (b) =>
      b.buildingName.toLowerCase().includes(debouncedSearch.toLowerCase()) ||
      b.buildingCode.toLowerCase().includes(debouncedSearch.toLowerCase())
  )

  const handleToggle = (building: (typeof buildings)[0]) => {
    if (isSelected(building.id)) {
      removeBuilding(building.id)
    } else if (!maxReached()) {
      addBuilding({
        id: building.id,
        buildingCode: building.buildingCode,
        buildingName: building.buildingName,
        tenantId: building.tenantId,
      })
    }
  }

  return (
    <>
      <Button
        variant="outlined"
        startIcon={<AddBusinessIcon />}
        onClick={() => setOpen(true)}
        size="small"
      >
        Select Buildings ({selectedBuildings.length}/5)
      </Button>

      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Select Buildings to Compare</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            size="small"
            placeholder="Search by name or code..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            sx={{ mb: 2, mt: 1 }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            }}
          />

          {maxReached() && (
            <Alert severity="warning" sx={{ mb: 1 }}>
              Maximum 5 buildings selected. Remove one to add another.
            </Alert>
          )}

          {isLoading && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
              <CircularProgress size={24} />
            </Box>
          )}

          {error instanceof Error && (
            <Alert severity="error">Failed to load buildings: {error.message}</Alert>
          )}

          <List dense disablePadding>
            {filtered.map((building) => {
              const selected = isSelected(building.id)
              const disabled = maxReached() && !selected
              return (
                <Tooltip
                  key={building.id}
                  title={disabled ? 'Remove a building first to add another' : ''}
                  placement="right"
                >
                  <span>
                    <ListItemButton
                      onClick={() => !disabled && handleToggle(building)}
                      disabled={disabled}
                      sx={{ borderRadius: 1 }}
                    >
                      <Checkbox
                        checked={selected}
                        disabled={disabled}
                        tabIndex={-1}
                        disableRipple
                        size="small"
                      />
                      <ListItemText
                        primary={building.buildingName}
                        secondary={`${building.buildingCode}${building.floorCount ? ` · ${building.floorCount}F` : ''}${building.totalAreaM2 ? ` · ${building.totalAreaM2.toLocaleString()} m²` : ''}`}
                      />
                    </ListItemButton>
                  </span>
                </Tooltip>
              )
            })}
            {filtered.length === 0 && !isLoading && !error && (
              <Box sx={{ py: 3, textAlign: 'center' }}>
                <Typography variant="body2" color="text.secondary">
                  No buildings found
                </Typography>
              </Box>
            )}
          </List>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)} variant="contained">
            Done
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
