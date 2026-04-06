import {
  Box, Typography, Grid, Paper, Chip, CircularProgress, Alert,
} from '@mui/material'
import PersonIcon from '@mui/icons-material/Person'
import HomeIcon from '@mui/icons-material/Home'
import ElectricMeterIcon from '@mui/icons-material/ElectricalServices'
import WaterIcon from '@mui/icons-material/Water'
import { useCitizenProfile, useMeters } from '@/hooks/useCitizenData'

export default function CitizenProfilePage() {
  const { data: profile, isLoading, error } = useCitizenProfile()
  const { data: meters = [] } = useMeters()

  if (isLoading) return <CircularProgress />
  if (error) return <Alert severity="error">Failed to load profile.</Alert>
  if (!profile) return null

  return (
    <Box>
      <Typography variant="h6" fontWeight={600} gutterBottom>My Profile</Typography>

      <Grid container spacing={2}>
        {/* Personal info */}
        <Grid item xs={12} md={6}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Box display="flex" alignItems="center" gap={1} mb={1.5}>
              <PersonIcon color="primary" fontSize="small" />
              <Typography variant="subtitle2" fontWeight={600}>Personal Info</Typography>
            </Box>
            {[
              ['Full name', profile.fullName],
              ['Email', profile.email],
              ['Phone', profile.phone ?? '—'],
              ['CCCD', profile.cccd ?? '—'],
              ['Role', profile.role],
            ].map(([label, value]) => (
              <Box key={label} display="flex" gap={1} mb={0.5}>
                <Typography variant="body2" color="text.secondary" sx={{ minWidth: 90 }}>{label}:</Typography>
                <Typography variant="body2">{value}</Typography>
              </Box>
            ))}
          </Paper>
        </Grid>

        {/* Household */}
        <Grid item xs={12} md={6}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Box display="flex" alignItems="center" gap={1} mb={1.5}>
              <HomeIcon color="primary" fontSize="small" />
              <Typography variant="subtitle2" fontWeight={600}>Household</Typography>
            </Box>
            {profile.household ? (
              [
                ['Building', profile.household.buildingName],
                ['Floor', profile.household.floor],
                ['Unit', profile.household.unitNumber],
              ].map(([label, value]) => (
                <Box key={label} display="flex" gap={1} mb={0.5}>
                  <Typography variant="body2" color="text.secondary" sx={{ minWidth: 90 }}>{label}:</Typography>
                  <Typography variant="body2">{value}</Typography>
                </Box>
              ))
            ) : (
              <Typography variant="body2" color="text.secondary">No household linked</Typography>
            )}
          </Paper>
        </Grid>

        {/* Meters */}
        <Grid item xs={12}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Box display="flex" alignItems="center" gap={1} mb={1.5}>
              <ElectricMeterIcon color="primary" fontSize="small" />
              <Typography variant="subtitle2" fontWeight={600}>Registered Meters</Typography>
            </Box>
            {meters.length === 0 ? (
              <Typography variant="body2" color="text.secondary">No meters registered</Typography>
            ) : (
              <Box display="flex" gap={1} flexWrap="wrap">
                {meters.map((m) => (
                  <Chip
                    key={m.id}
                    icon={m.meterType === 'WATER' ? <WaterIcon /> : <ElectricMeterIcon />}
                    label={`${m.meterCode} (${m.meterType})`}
                    variant="outlined"
                    size="small"
                  />
                ))}
              </Box>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  )
}
