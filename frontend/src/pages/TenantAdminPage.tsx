import { Box, Typography } from '@mui/material'

export default function TenantAdminPage() {
  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" component="h1">
        Tenant Admin
      </Typography>
      <Typography variant="body1" sx={{ mt: 2, color: 'text.secondary' }}>
        Tenant administration panel — manage tenant settings, feature flags, and branding.
      </Typography>
    </Box>
  )
}
