import { Typography, Box } from '@mui/material'
import PeopleIcon from '@mui/icons-material/People'

export default function CitizenPage() {
  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={3}>
        <PeopleIcon color="primary" />
        <Typography variant="h5">Citizen Services</Typography>
      </Box>
      <Typography color="text.secondary">
        Citizen portal coming in Sprint 3 (S3-07).
      </Typography>
    </Box>
  )
}
