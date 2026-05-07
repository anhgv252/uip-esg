import { useState } from 'react'
import {
  Box, Typography, Paper, Grid, MenuItem, TextField, Chip,
  useTheme, useMediaQuery,
} from '@mui/material'
import TrafficIcon from '@mui/icons-material/Traffic'
import WarningIcon from '@mui/icons-material/Warning'
import TrafficBarChart from '@/components/traffic/TrafficBarChart'
import IncidentTable from '@/components/traffic/IncidentTable'
import { useTrafficCounts, useTrafficIncidents } from '@/hooks/useTrafficData'

const INTERSECTIONS = ['INT-001', 'INT-002', 'INT-003', 'INT-004', 'INT-005']

export default function TrafficPage() {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const [intersection, setIntersection] = useState('INT-001')
  const [statusFilter, setStatusFilter] = useState('OPEN')

  const { data: counts = [], isLoading: countsLoading } = useTrafficCounts({ intersection })
  const { data: incidentPage, isLoading: incidentsLoading } = useTrafficIncidents(statusFilter)

  const incidents = incidentPage?.content ?? []
  const openCount = incidents.filter((i) => i.status === 'OPEN').length

  const selectSx = isMobile
    ? { width: '100%', '& .MuiSelect-select': { minHeight: 44 } }
    : { minWidth: 130 }

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={isMobile ? 2 : 3}>
        <TrafficIcon color="primary" />
        <Typography variant="h5">Traffic Management</Typography>
        {openCount > 0 && (
          <Chip
            icon={<WarningIcon />}
            label={`${openCount} open incidents`}
            color="warning"
            size="small"
          />
        )}
      </Box>

      <Grid container spacing={isMobile ? 2 : 3}>
        {/* Vehicle Counts Chart */}
        <Grid item xs={12}>
          <Paper variant="outlined" sx={{ p: isMobile ? 1.5 : 2 }}>
            <Box
              display="flex"
              flexDirection={isMobile ? 'column' : 'row'}
              justifyContent="space-between"
              alignItems={isMobile ? 'stretch' : 'center'}
              gap={isMobile ? 1.5 : 0}
              mb={2}
            >
              <Typography variant="subtitle1" fontWeight={600}>
                Vehicle Counts by Hour
              </Typography>
              <TextField
                select size="small" label="Intersection" value={intersection}
                onChange={(e) => setIntersection(e.target.value)}
                sx={selectSx}
              >
                {INTERSECTIONS.map((id) => (
                  <MenuItem key={id} value={id} sx={{ minHeight: 44 }}>{id}</MenuItem>
                ))}
              </TextField>
            </Box>
            <TrafficBarChart counts={counts} loading={countsLoading} />
          </Paper>
        </Grid>

        {/* Incident Table */}
        <Grid item xs={12}>
          <Paper variant="outlined" sx={{ p: isMobile ? 1.5 : 2 }}>
            <Box
              display="flex"
              flexDirection={isMobile ? 'column' : 'row'}
              justifyContent="space-between"
              alignItems={isMobile ? 'stretch' : 'center'}
              gap={isMobile ? 1.5 : 0}
              mb={2}
            >
              <Typography variant="subtitle1" fontWeight={600}>
                Traffic Incidents
              </Typography>
              <TextField
                select size="small" label="Status" value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                sx={selectSx}
              >
                {['OPEN', 'RESOLVED', 'ESCALATED'].map((s) => (
                  <MenuItem key={s} value={s} sx={{ minHeight: 44 }}>{s}</MenuItem>
                ))}
              </TextField>
            </Box>
            <IncidentTable incidents={incidents} loading={incidentsLoading} />
          </Paper>
        </Grid>
      </Grid>
    </Box>
  )
}
