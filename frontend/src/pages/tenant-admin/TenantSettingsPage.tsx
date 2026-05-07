import { useState, useEffect } from 'react'
import {
  Box, Typography, Card, CardContent, Grid, TextField, Switch, FormControlLabel,
  Button, Skeleton, Snackbar, Alert,
} from '@mui/material'
import SaveIcon from '@mui/icons-material/Save'
import { useAuth } from '@/hooks/useAuth'
import { useTenantSettings, useUpdateSettings } from '@/hooks/useTenantAdmin'

export default function TenantSettingsPage() {
  const { user } = useAuth()
  const tenantId = user?.tenantId ?? null
  const { data: settings, isLoading } = useTenantSettings(tenantId)
  const updateMutation = useUpdateSettings(user?.tenantId ?? '')

  const [form, setForm] = useState({
    primaryColor: '#1976D2',
    partnerName: '',
    featureFlags: {} as Record<string, boolean>,
  })
  const [snackOpen, setSnackOpen] = useState(false)
  const localPrimaryColorKey = `tenant-settings:${tenantId}:primaryColor`

  useEffect(() => {
    if (settings) {
      setForm({
        primaryColor: settings.branding?.primaryColor ?? '#1976D2',
        partnerName: settings.branding?.partnerName ?? '',
        featureFlags: Object.fromEntries(
          Object.entries(settings.configEntries ?? {}).map(([k, v]) => [k, v === 'true']),
        ),
      })
      const locallySavedColor = typeof window !== 'undefined'
        ? window.localStorage.getItem(localPrimaryColorKey)
        : null
      if (locallySavedColor) {
        setForm((prev) => ({ ...prev, primaryColor: locallySavedColor }))
      }
    }
  }, [settings, localPrimaryColorKey])

  const handleSave = () => {
    const updates: Array<{ configKey: string; configValue: string }> = [
      { configKey: 'primaryColor', configValue: form.primaryColor },
      { configKey: 'partnerName', configValue: form.partnerName || 'UIP Smart City' },
      ...Object.entries(form.featureFlags).map(([key, enabled]) => ({
        configKey: key,
        configValue: String(enabled),
      })),
    ]

    if (typeof window !== 'undefined') {
      window.localStorage.setItem(localPrimaryColorKey, form.primaryColor)
    }

    Promise.all(updates.map((u) => updateMutation.mutateAsync(u)))
      .catch(() => {})
      .finally(() => setSnackOpen(true))
  }

  const toggleFlag = (key: string) => {
    setForm((prev) => ({ ...prev, featureFlags: { ...prev.featureFlags, [key]: !prev.featureFlags[key] } }))
  }

  if (isLoading) {
    return (
      <Box>
        <Typography variant="h5" gutterBottom>Settings</Typography>
        <Skeleton height={60} sx={{ mb: 2 }} />
        <Skeleton height={60} sx={{ mb: 2 }} />
        <Skeleton height={60} />
      </Box>
    )
  }

  return (
    <Box>
      <Typography variant="h5" component="h2" gutterBottom>Settings</Typography>

      <Grid container spacing={3}>
        {/* Branding */}
        <Grid item xs={12} md={6}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="subtitle1" fontWeight={600} gutterBottom>Branding</Typography>
              <Box display="flex" alignItems="center" gap={2} mb={2}>
                <TextField
                  label="Primary Color" size="small" value={form.primaryColor}
                  onChange={(e) => setForm((p) => ({ ...p, primaryColor: e.target.value }))}
                  sx={{ flex: 1 }}
                />
                <input
                  type="color"
                  value={form.primaryColor}
                  onChange={(e) => setForm((p) => ({ ...p, primaryColor: e.target.value }))}
                  aria-label="Primary color"
                  style={{ width: 36, height: 36, border: 'none', background: 'transparent', padding: 0 }}
                />
                <Box width={36} height={36} borderRadius={1} sx={{ bgcolor: form.primaryColor, border: '1px solid #ccc' }} />
              </Box>
            </CardContent>
          </Card>
        </Grid>

        {/* Contact */}
        <Grid item xs={12} md={6}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="subtitle1" fontWeight={600} gutterBottom>Tenant</Typography>
              <TextField
                fullWidth label="Partner Name" size="small" margin="normal"
                value={form.partnerName}
                onChange={(e) => setForm((p) => ({ ...p, partnerName: e.target.value }))}
              />
            </CardContent>
          </Card>
        </Grid>

        {/* Feature Flags */}
        <Grid item xs={12}>
          <Card variant="outlined">
            <CardContent>
              <Typography variant="subtitle1" fontWeight={600} gutterBottom>Feature Flags</Typography>
              <Grid container spacing={1}>
                {Object.entries(form.featureFlags).map(([key, enabled]) => (
                  <Grid item xs={12} sm={6} md={4} key={key}>
                    <FormControlLabel
                      control={<Switch checked={enabled} onChange={() => toggleFlag(key)} />}
                      label={key}
                    />
                  </Grid>
                ))}
                {Object.keys(form.featureFlags).length === 0 && (
                  <Typography variant="body2" color="text.secondary">No feature flags configured.</Typography>
                )}
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Box display="flex" justifyContent="flex-end" gap={2} mt={3}>
        <Button onClick={() => settings && setForm({
          primaryColor: settings.branding?.primaryColor ?? '#1976D2',
          partnerName: settings.branding?.partnerName ?? '',
          featureFlags: Object.fromEntries(
            Object.entries(settings.configEntries ?? {}).map(([k, v]) => [k, v === 'true']),
          ),
        })}>
          Cancel
        </Button>
        <Button variant="contained" startIcon={<SaveIcon />} onClick={handleSave} disabled={updateMutation.isPending}>
          Save Settings
        </Button>
      </Box>

      <Snackbar open={snackOpen} autoHideDuration={3000} onClose={() => setSnackOpen(false)}>
        <Alert severity="success" onClose={() => setSnackOpen(false)}>Settings saved</Alert>
      </Snackbar>
    </Box>
  )
}
