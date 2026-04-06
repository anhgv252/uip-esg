import { useState } from 'react'
import {
  Box, Typography, Stepper, Step, StepLabel, Button, TextField,
  MenuItem, Grid, Alert, CircularProgress, Paper,
} from '@mui/material'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate } from 'react-router-dom'
import { useBuildings, useRegister, useLinkHousehold } from '@/hooks/useCitizenData'
import { tokenStore } from '@/api/client'
import type { CitizenProfileDto } from '@/api/citizen'

const personalSchema = z.object({
  fullName: z.string().min(2, 'Full name required'),
  email: z.string().email('Invalid email'),
  phone: z.string().regex(/^(\+84|0)[35789][0-9]{8}$/, 'Invalid VN phone number'),
  cccd: z.string().regex(/^[0-9]{9}$|^[0-9]{12}$/, 'CCCD must be 9 or 12 digits'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
})

const householdSchema = z.object({
  buildingId: z.string().uuid('Please select a building'),
  floor: z.string().min(1, 'Floor required'),
  unitNumber: z.string().min(1, 'Unit number required'),
})

const STEPS = ['Personal Info', 'Household', 'Done']

export default function CitizenRegisterPage() {
  const navigate = useNavigate()
  const [activeStep, setActiveStep] = useState(0)
  const [registered, setRegistered] = useState<CitizenProfileDto | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)

  const { data: buildings = [] } = useBuildings()
  const { mutate: register, isPending: registering } = useRegister()
  const { mutate: linkHousehold, isPending: linkingHousehold } = useLinkHousehold()

  const personalForm = useForm({
    resolver: zodResolver(personalSchema),
    defaultValues: { fullName: '', email: '', phone: '', cccd: '', password: '' },
  })

  const householdForm = useForm({
    resolver: zodResolver(householdSchema),
    defaultValues: { buildingId: '', floor: '', unitNumber: '' },
  })

  const handlePersonalSubmit = personalForm.handleSubmit((data) => {
    setServerError(null)
    register(data, {
      onSuccess: (res) => {
        // Store the JWT so Step 2 (linkHousehold) can authenticate
        tokenStore.set(res.accessToken)
        if (res.refreshToken) tokenStore.setRefresh(res.refreshToken)
        setRegistered(res.profile)
        setActiveStep(1)
      },
      onError: (err: any) => {
        setServerError(err?.response?.data?.message ?? err.message ?? 'Registration failed')
      },
    })
  })

  const handleHouseholdSubmit = householdForm.handleSubmit((data) => {
    setServerError(null)
    linkHousehold(data, {
      onSuccess: () => setActiveStep(2),
      onError: (err: any) => {
        setServerError(err?.response?.data?.message ?? 'Failed to link household')
      },
    })
  })

  return (
    <Box maxWidth={560} mx="auto" mt={4}>
      <Typography variant="h5" gutterBottom>Create Citizen Account</Typography>
      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {STEPS.map((label) => (
          <Step key={label}><StepLabel>{label}</StepLabel></Step>
        ))}
      </Stepper>

      {serverError && <Alert severity="error" sx={{ mb: 2 }}>{serverError}</Alert>}

      {/* Step 0: Personal Info */}
      {activeStep === 0 && (
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <Controller name="fullName" control={personalForm.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} fullWidth label="Full name *" size="small"
                    error={!!fieldState.error} helperText={fieldState.error?.message} />
                )} />
            </Grid>
            <Grid item xs={12}>
              <Controller name="email" control={personalForm.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} fullWidth label="Email *" size="small" type="email"
                    error={!!fieldState.error} helperText={fieldState.error?.message} />
                )} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller name="phone" control={personalForm.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} fullWidth label="Phone (VN) *" size="small"
                    placeholder="0912345678"
                    error={!!fieldState.error} helperText={fieldState.error?.message} />
                )} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <Controller name="cccd" control={personalForm.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} fullWidth label="CCCD/CMND *" size="small"
                    placeholder="9 or 12 digits"
                    error={!!fieldState.error} helperText={fieldState.error?.message} />
                )} />
            </Grid>
            <Grid item xs={12}>
              <Controller name="password" control={personalForm.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} fullWidth label="Password *" size="small"
                    type="password" placeholder="At least 8 characters"
                    error={!!fieldState.error} helperText={fieldState.error?.message} />
                )} />
            </Grid>
          </Grid>
          <Button
            variant="contained" fullWidth sx={{ mt: 3 }}
            onClick={handlePersonalSubmit} disabled={registering}
          >
            {registering ? <CircularProgress size={22} /> : 'Next: Household'}
          </Button>
          <Button fullWidth sx={{ mt: 1 }} onClick={() => navigate('/login')}>
            Already have an account? Login
          </Button>
        </Paper>
      )}

      {/* Step 1: Household */}
      {activeStep === 1 && registered && (
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Typography variant="body2" color="text.secondary" mb={2}>
            Welcome, <strong>{registered.fullName}</strong>! Link your household to receive utility bills and alerts.
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12}>
              <Controller name="buildingId" control={householdForm.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} select fullWidth label="Building *" size="small"
                    error={!!fieldState.error} helperText={fieldState.error?.message}>
                    {buildings.map((b) => (
                      <MenuItem key={b.id} value={b.id}>{b.name} — {b.district}</MenuItem>
                    ))}
                  </TextField>
                )} />
            </Grid>
            <Grid item xs={6}>
              <Controller name="floor" control={householdForm.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} fullWidth label="Floor *" size="small"
                    error={!!fieldState.error} helperText={fieldState.error?.message} />
                )} />
            </Grid>
            <Grid item xs={6}>
              <Controller name="unitNumber" control={householdForm.control}
                render={({ field, fieldState }) => (
                  <TextField {...field} fullWidth label="Unit number *" size="small"
                    error={!!fieldState.error} helperText={fieldState.error?.message} />
                )} />
            </Grid>
          </Grid>
          <Button
            variant="contained" fullWidth sx={{ mt: 3 }}
            onClick={handleHouseholdSubmit} disabled={linkingHousehold}
          >
            {linkingHousehold ? <CircularProgress size={22} /> : 'Complete Registration'}
          </Button>
          <Button fullWidth sx={{ mt: 1 }} onClick={() => setActiveStep(2)}>
            Skip for now
          </Button>
        </Paper>
      )}

      {/* Step 2: Done */}
      {activeStep === 2 && (
        <Paper variant="outlined" sx={{ p: 3, textAlign: 'center' }}>
          <Typography variant="h6" gutterBottom color="success.main">
            Account created successfully!
          </Typography>
          <Typography variant="body2" color="text.secondary" mb={3}>
            You can now log in with your email and password to manage your utilities and receive alerts.
          </Typography>
          <Button variant="contained" fullWidth onClick={() => navigate('/login')}>
            Go to Login
          </Button>
        </Paper>
      )}
    </Box>
  )
}
