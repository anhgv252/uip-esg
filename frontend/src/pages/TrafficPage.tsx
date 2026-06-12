import { useState } from 'react'
import {
  Box, Typography, Paper, Grid, MenuItem, TextField, Chip, Skeleton,
  useTheme, useMediaQuery,
} from '@mui/material'
import TrafficIcon from '@mui/icons-material/Traffic'
import WarningIcon from '@mui/icons-material/Warning'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import TrafficBarChart from '@/components/traffic/TrafficBarChart'
import IncidentTable from '@/components/traffic/IncidentTable'
import { useTrafficCounts, useTrafficIncidents, useTrafficFlow } from '@/hooks/useTrafficData'
import type { TrafficFlowDto } from '@/api/traffic'

const INTERSECTIONS = ['INT-001', 'INT-002', 'INT-003', 'INT-004', 'INT-005']

const CONGESTION_COLOR: Record<string, 'success' | 'warning' | 'error' | 'default'> = {
  FREE: 'success',
  MODERATE: 'warning',
  HEAVY: 'error',
  STANDSTILL: 'error',
}

function FlowSummaryCard({ flow, loading }: { flow: TrafficFlowDto[]; loading: boolean }) {
  if (loading) {
    return (
      <Grid container spacing={2}>
        {[1, 2, 3].map((i) => (
          <Grid item xs={12} sm={4} key={i}>
            <Skeleton variant="rectangular" height={80} />
          </Grid>
        ))}
      </Grid>
    )
  }

  if (flow.length === 0) {
    return (
      <Typography color="text.secondary" variant="body2">
        No real-time flow data available
      </Typography>
    )
  }

  const latest = flow[0]
  const avgSpeed = flow.reduce((sum, f) => sum + f.avgSpeed, 0) / flow.length
  const totalVehicles = flow.reduce((sum, f) => sum + f.vehicleCount, 0)

  return (
    <Grid container spacing={2}>
      <Grid item xs={12} sm={4}>
        <Paper variant="outlined" sx={{ p: 1.5, textAlign: 'center' }}>
          <Typography variant="caption" color="text.secondary" display="block">Congestion</Typography>
          <Chip
            label={latest.congestionLevel}
            size="small"
            color={CONGESTION_COLOR[latest.congestionLevel] ?? 'default'}
            sx={{ mt: 0.5 }}
          />
        </Paper>
      </Grid>
      <Grid item xs={12} sm={4}>
        <Paper variant="outlined" sx={{ p: 1.5, textAlign: 'center' }}>
          <Typography variant="caption" color="text.secondary" display="block">Avg Speed</Typography>
          <Typography variant="h6" fontWeight={700}>
            {avgSpeed.toFixed(1)}
            <Typography component="span" variant="caption" color="text.secondary"> km/h</Typography>
          </Typography>
        </Paper>
      </Grid>
      <Grid item xs={12} sm={4}>
        <Paper variant="outlined" sx={{ p: 1.5, textAlign: 'center' }}>
          <Typography variant="caption" color="text.secondary" display="block">Total Vehicles</Typography>
          <Typography variant="h6" fontWeight={700}>
            {totalVehicles.toLocaleString()}
          </Typography>
        </Paper>
      </Grid>
    </Grid>
  )
}

export default function TrafficPage() {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const [intersection, setIntersection] = useState('INT-001')
  const [statusFilter, setStatusFilter] = useState('OPEN')

  const { data: counts = [], isLoading: countsLoading } = useTrafficCounts({ intersection })
  const { data: incidentPage, isLoading: incidentsLoading } = useTrafficIncidents(statusFilter)
  const { data: flowData = [], isLoading: flowLoading } = useTrafficFlow({ intersection })

  const incidents = incidentPage?.content ?? []
  const openCount = incidents.filter((i) => i.status === 'OPEN').length

  const selectSx = isMobile
    ? { width: '100%', '& .MuiSelect-select': { minHeight: 44 } }
    : { minWidth: 130 }

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={isMobile ? 2 : 3} flexWrap="wrap">
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
        {/* Real-time Flow Summary (GAP-029) */}
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
              <Box display="flex" alignItems="center" gap={1}>
                <Typography variant="subtitle1" fontWeight={600}>
                  Real-Time Traffic Flow
                </Typography>
                <Chip
                  icon={<InfoOutlinedIcon />}
                  label={flowData.length > 0 ? 'LIVE' : 'No data'}
                  size="small"
                  color={flowData.length > 0 ? 'success' : 'default'}
                  variant="outlined"
                />
              </Box>
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
            <FlowSummaryCard flow={flowData} loading={flowLoading} />
          </Paper>
        </Grid>

        {/* Vehicle Counts Chart */}
        <Grid item xs={12}>
          <Paper variant="outlined" sx={{ p: isMobile ? 1.5 : 2 }}>
            <Typography variant="subtitle1" fontWeight={600} mb={2}>
              Vehicle Counts by Hour
            </Typography>
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
