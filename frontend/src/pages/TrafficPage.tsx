import { useState } from 'react'
import {
  Box, Typography, Paper, Grid, MenuItem, TextField, Chip,
} from '@mui/material'
import TrafficIcon from '@mui/icons-material/Traffic'
import WarningIcon from '@mui/icons-material/Warning'
import TrafficBarChart from '@/components/traffic/TrafficBarChart'
import IncidentTable from '@/components/traffic/IncidentTable'
import { useTrafficCounts, useTrafficIncidents } from '@/hooks/useTrafficData'

const INTERSECTIONS = ['INT-001', 'INT-002', 'INT-003', 'INT-004', 'INT-005']

export default function TrafficPage() {
  const [intersection, setIntersection] = useState('INT-001')
  const [statusFilter, setStatusFilter] = useState('OPEN')

  const { data: counts = [], isLoading: countsLoading } = useTrafficCounts({ intersection })
  const { data: incidentPage, isLoading: incidentsLoading } = useTrafficIncidents(statusFilter)

  const incidents = incidentPage?.content ?? []
  const openCount = incidents.filter((i) => i.status === 'OPEN').length

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={3}>
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

      <Grid container spacing={3}>
        {/* Vehicle Counts Chart */}
        <Grid item xs={12}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="subtitle1" fontWeight={600}>
                Vehicle Counts by Hour
              </Typography>
              <TextField
                select size="small" label="Intersection" value={intersection}
                onChange={(e) => setIntersection(e.target.value)}
                sx={{ minWidth: 130 }}
              >
                {INTERSECTIONS.map((id) => (
                  <MenuItem key={id} value={id}>{id}</MenuItem>
                ))}
              </TextField>
            </Box>
            <TrafficBarChart counts={counts} loading={countsLoading} />
          </Paper>
        </Grid>

        {/* Incident Table */}
        <Grid item xs={12}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="subtitle1" fontWeight={600}>
                Traffic Incidents
              </Typography>
              <TextField
                select size="small" label="Status" value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                sx={{ minWidth: 130 }}
              >
                {['OPEN', 'RESOLVED', 'ESCALATED'].map((s) => (
                  <MenuItem key={s} value={s}>{s}</MenuItem>
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
