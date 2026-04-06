import {
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Chip, Typography, CircularProgress, Box,
} from '@mui/material'
import { formatDistanceToNow } from 'date-fns'
import type { TrafficIncidentDto } from '@/api/traffic'

const TYPE_COLORS = {
  ACCIDENT: 'error',
  CONGESTION: 'warning',
  ROADWORK: 'info',
} as const

const STATUS_COLORS = {
  OPEN: 'error',
  RESOLVED: 'success',
  ESCALATED: 'warning',
} as const

interface IncidentTableProps {
  incidents: TrafficIncidentDto[]
  loading?: boolean
}

export default function IncidentTable({ incidents, loading }: IncidentTableProps) {
  if (loading) {
    return <Box display="flex" justifyContent="center" p={3}><CircularProgress size={24} /></Box>
  }

  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Type</TableCell>
            <TableCell>Description</TableCell>
            <TableCell>Intersection</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Occurred</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {incidents.length === 0 && (
            <TableRow>
              <TableCell colSpan={5} align="center">
                <Typography color="text.secondary" variant="body2">No incidents</Typography>
              </TableCell>
            </TableRow>
          )}
          {incidents.map((inc) => (
            <TableRow key={inc.id} hover>
              <TableCell>
                <Chip
                  label={inc.incidentType}
                  size="small"
                  color={TYPE_COLORS[inc.incidentType] ?? 'default'}
                />
              </TableCell>
              <TableCell>
                <Typography variant="body2" noWrap sx={{ maxWidth: 220 }}>
                  {inc.description}
                </Typography>
              </TableCell>
              <TableCell>
                <Typography variant="body2">{inc.intersectionId}</Typography>
              </TableCell>
              <TableCell>
                <Chip
                  label={inc.status}
                  size="small"
                  variant="outlined"
                  color={STATUS_COLORS[inc.status] ?? 'default'}
                />
              </TableCell>
              <TableCell>
                <Typography variant="caption" color="text.secondary">
                  {formatDistanceToNow(new Date(inc.occurredAt), { addSuffix: true })}
                </Typography>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
