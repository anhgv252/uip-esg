import type { Meta, StoryObj } from '@storybook/react'
import { ThemeProvider, CssBaseline, Card, CardContent, Typography, Button, Chip, Box, Stack } from '@mui/material'
import { createPartnerTheme } from '@/theme'
import { energyOptimizerThemeConfig } from './energy-optimizer.theme'

const theme = createPartnerTheme(energyOptimizerThemeConfig)

const meta: Meta = {
  title: 'Themes/Energy Optimizer',
  decorators: [
    (Story) => (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Story />
      </ThemeProvider>
    ),
  ],
}

export default meta
type Story = StoryObj

export const Showcase: Story = {
  render: () => (
    <Box p={3} maxWidth={400}>
      <Typography variant="h5" gutterBottom fontWeight={600}>
        {energyOptimizerThemeConfig.partnerName}
      </Typography>
      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="subtitle1" fontWeight={600}>Primary Color</Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Chip label="Primary" color="primary" />
              <Chip label="Secondary" color="secondary" />
              <Chip label="Success" color="success" />
              <Chip label="Warning" color="warning" />
              <Chip label="Error" color="error" />
            </Stack>
            <Stack direction="row" spacing={1}>
              <Button variant="contained">Contained</Button>
              <Button variant="outlined">Outlined</Button>
              <Button variant="text">Text</Button>
            </Stack>
            <Box bgcolor="primary.main" p={2} borderRadius={1}>
              <Typography color="primary.contrastText">
                Sidebar BG: {energyOptimizerThemeConfig.sidebarBg}
              </Typography>
            </Box>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  ),
}
