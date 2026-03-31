import { Typography, Box } from '@mui/material'
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive'

export default function AlertsPage() {
  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={3}>
        <NotificationsActiveIcon color="primary" />
        <Typography variant="h5">Alerts</Typography>
      </Box>
      <Typography color="text.secondary">
        Real-time alerts via SSE coming in Sprint 2 (S2-02, S2-03).
      </Typography>
    </Box>
  )
}
