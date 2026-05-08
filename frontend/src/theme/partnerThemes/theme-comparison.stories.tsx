import type { Meta, StoryObj } from '@storybook/react'
import { ThemeProvider, CssBaseline, Card, CardContent, Typography, Button, Chip, Box, Stack, Divider } from '@mui/material'
import { createPartnerTheme } from '@/theme'
import { defaultThemeConfig, energyOptimizerThemeConfig, citizenFirstThemeConfig } from './index'
import type { PartnerThemeConfig } from '@/theme'

const ALL_THEMES: PartnerThemeConfig[] = [
  defaultThemeConfig,
  energyOptimizerThemeConfig,
  citizenFirstThemeConfig,
]

function ThemeCard({ config }: { config: PartnerThemeConfig }) {
  const theme = createPartnerTheme(config)
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Card variant="outlined" sx={{ minWidth: 240 }}>
        <Box
          sx={{
            bgcolor: config.sidebarBg ?? config.primaryColor,
            p: 2,
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
        >
          <Typography variant="subtitle2" sx={{ color: '#fff', fontWeight: 700 }}>
            {config.partnerName}
          </Typography>
        </Box>
        <CardContent>
          <Stack spacing={1.5}>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label="Primary" color="primary" size="small" />
              <Chip label="Success" color="success" size="small" />
              <Chip label="Warning" color="warning" size="small" />
            </Stack>
            <Stack direction="row" spacing={1}>
              <Button variant="contained" size="small">Action</Button>
              <Button variant="outlined" size="small">Cancel</Button>
            </Stack>
            <Divider />
            <Typography variant="caption" color="text.secondary">
              Primary: {config.primaryColor}
            </Typography>
            {config.sidebarBg && (
              <Typography variant="caption" color="text.secondary">
                Sidebar: {config.sidebarBg}
              </Typography>
            )}
          </Stack>
        </CardContent>
      </Card>
    </ThemeProvider>
  )
}

const meta: Meta = {
  title: 'Themes/Comparison',
}

export default meta
type Story = StoryObj

export const AllThemes: Story = {
  render: () => (
    <Box p={3}>
      <Typography variant="h5" gutterBottom fontWeight={600}>
        Partner Theme Comparison
      </Typography>
      <Typography variant="body2" color="text.secondary" gutterBottom>
        All registered partner themes — branding configured per tenant via tenant_config.
      </Typography>
      <Box display="flex" gap={2} flexWrap="wrap" mt={2}>
        {ALL_THEMES.map((config) => (
          <ThemeCard key={config.partnerName} config={config} />
        ))}
      </Box>
    </Box>
  ),
}
