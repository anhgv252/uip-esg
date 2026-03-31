import { Typography, Box } from '@mui/material'
import TrafficIcon from '@mui/icons-material/Traffic'

export default function TrafficPage() {
  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={3}>
        <TrafficIcon color="primary" />
        <Typography variant="h5">Traffic Management</Typography>
      </Box>
      <Typography color="text.secondary">
        Traffic management dashboard coming in Sprint 3 (S3-05).
      </Typography>
    </Box>
  )
}
