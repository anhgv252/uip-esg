import { Box } from '@mui/material'
import { Outlet } from 'react-router-dom'
import { BuildingContextBar } from './BuildingContextBar'

export function CrossBuildingShell() {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <BuildingContextBar />
      <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
        <Outlet />
      </Box>
    </Box>
  )
}
