import { useState } from 'react'
import {
  Box,
  Typography,
  Drawer,
  Divider,
  Chip,
  Button,
  TextField,
  MenuItem,
  Grid,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Paper,
  Checkbox,
  Tooltip,
  CircularProgress,
  Alert as MuiAlert,
  Card,
  CardContent,
  Stack,
  useTheme,
  useMediaQuery,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive'
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward'
import { formatDistanceToNow, format } from 'date-fns'
import type { AlertEvent } from '@/api/alerts'
import { useAlerts, useAcknowledgeAlert, useEscalateAlert } from '@/hooks/useAlertManagement'
import { useScope } from '@/hooks/useScope'

const SEVERITY_COLORS = {
  LOW: '#4caf50',
  MEDIUM: '#ff9800',
  HIGH: '#f44336',
  CRITICAL: '#b71c1c',
} as const

type SeverityKey = keyof typeof SEVERITY_COLORS

function SeverityBadge({ severity }: { severity: string }) {
  const color = SEVERITY_COLORS[severity as SeverityKey] ?? '#9e9e9e'
  return (
    <Chip
      label={severity}
      size="small"
      sx={{ bgcolor: color, color: '#fff', fontWeight: 700, fontSize: 11, height: 22 }}
    />
  )
}

function StatusBadge({ status }: { status: string }) {
  const color = status === 'ACKNOWLEDGED' ? 'success' : status === 'ESCALATED' ? 'error' : 'warning'
  return <Chip label={status} size="small" variant="outlined" color={color} />
}

interface AlertDetailDrawerProps {
  alert: AlertEvent | null
  onClose: () => void
  onAcknowledge: (id: string, note?: string) => void
  onEscalate: (id: string, note?: string) => void
  acknowledging: boolean
  escalating: boolean
  isMobile: boolean
}

function AlertDetailDrawer({ alert, onClose, onAcknowledge, onEscalate, acknowledging, escalating, isMobile }: AlertDetailDrawerProps) {
  const [note, setNote] = useState('')
  const actionLabel = alert?.status === 'ACKNOWLEDGED' ? 'Acknowledged by' : 'Escalated by'
  return (
    <Drawer
      anchor={isMobile ? 'bottom' : 'right'}
      open={!!alert}
      onClose={onClose}
      PaperProps={{ sx: { width: isMobile ? '100%' : 420, maxHeight: isMobile ? '90vh' : '100%' } }}
    >
      {alert && (
        <Box sx={{ p: 3, height: '100%', overflowY: 'auto' }}>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h6">Alert Detail</Typography>
            <IconButton onClick={onClose} aria-label="Close drawer"><CloseIcon /></IconButton>
          </Box>
          <Box display="flex" gap={1} mb={2} flexWrap="wrap">
            <SeverityBadge severity={alert.severity} />
            <StatusBadge status={alert.status} />
          </Box>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>{alert.ruleName ?? alert.ruleId ?? '—'}</Typography>
          <Divider sx={{ my: 1.5 }} />
          <Grid container spacing={1}>
            {([
              ['Module', alert.module],
              ['Measure', alert.measureType],
              ['Value', String(alert.value)],
              ['Threshold', String(alert.threshold)],
              ['Sensor', alert.sensorId ?? '—'],
              ['Detected', format(new Date(alert.detectedAt), 'dd/MM/yyyy HH:mm:ss')],
              ...(alert.acknowledgedBy
                ? [[actionLabel, alert.acknowledgedBy],
                   ['At', alert.acknowledgedAt
                     ? format(new Date(alert.acknowledgedAt), 'dd/MM/yyyy HH:mm') : '—']]
                : []),
            ] as [string, string][]).map(([label, value]) => (
              <Grid item xs={12} key={label}>
                <Box display="flex" gap={1}>
                  <Typography variant="body2" color="text.secondary" sx={{ minWidth: 130 }}>{label}:</Typography>
                  <Typography variant="body2">{value}</Typography>
                </Box>
              </Grid>
            ))}
          </Grid>
          {alert.note && (
            <Box mt={2} p={1.5} bgcolor="background.default" borderRadius={1}>
              <Typography variant="body2">{alert.note}</Typography>
            </Box>
          )}
          {(alert.status === 'OPEN' || alert.status === 'ACKNOWLEDGED') && (
            <Box mt={3}>
              <Divider sx={{ mb: 2 }} />
              <TextField
                fullWidth size="small" label="Note (optional)" value={note}
                onChange={(e) => setNote(e.target.value)} multiline rows={2} sx={{ mb: 1.5 }}
              />
              <Box display="flex" gap={1.5}>
                {alert.status === 'OPEN' && (
                  <Button
                    variant="contained" fullWidth startIcon={<CheckCircleIcon />}
                    disabled={acknowledging || escalating} onClick={() => onAcknowledge(String(alert.id), note)}
                    sx={{ minHeight: 44 }}
                  >
                    {acknowledging ? <CircularProgress size={20} /> : 'Acknowledge'}
                  </Button>
                )}
                <Button
                  variant="outlined" fullWidth startIcon={<ArrowUpwardIcon />}
                  disabled={acknowledging || escalating}
                  onClick={() => onEscalate(String(alert.id), note)}
                  color="warning"
                  sx={{ minHeight: 44 }}
                >
                  {escalating ? <CircularProgress size={20} /> : 'Escalate'}
                </Button>
              </Box>
            </Box>
          )}
        </Box>
      )}
    </Drawer>
  )
}

function MobileAlertCard({ alert, onAck, onEscalate, canAck, acknowledging, escalating }: {
  alert: AlertEvent
  onAck: (id: string) => void
  onEscalate: (id: string) => void
  canAck: boolean
  acknowledging: boolean
  escalating: boolean
}) {
  return (
    <Card variant="outlined" sx={{ mb: 1 }}>
      <CardContent sx={{ '&:last-child': { pb: 1.5 } }}>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={0.5}>
          <Box display="flex" gap={0.5}>
            <SeverityBadge severity={alert.severity} />
            <StatusBadge status={alert.status} />
          </Box>
          <Typography variant="caption" color="text.secondary">
            {formatDistanceToNow(new Date(alert.detectedAt), { addSuffix: true })}
          </Typography>
        </Box>
        <Typography variant="body2" fontWeight={600} noWrap>{alert.ruleName ?? '—'}</Typography>
        <Stack direction="row" spacing={2} mt={0.5}>
          <Typography variant="caption" color="text.secondary">{alert.module}</Typography>
          <Typography variant="caption" color="text.secondary">{alert.sensorId ?? '—'}</Typography>
          <Typography variant="caption" color="text.secondary">Value: {alert.value}</Typography>
        </Stack>
        <Box display="flex" gap={0.5} mt={1} justifyContent="flex-end">
          {alert.status === 'OPEN' && (
            <Button
              size="small" color="success" startIcon={<CheckCircleIcon />}
              onClick={() => onAck(String(alert.id))} disabled={!canAck || acknowledging}
              sx={{ minHeight: 44 }}
            >
              Ack
            </Button>
          )}
          {(alert.status === 'OPEN' || alert.status === 'ESCALATED') && (
            <Button
              size="small" color="warning" startIcon={<ArrowUpwardIcon />}
              onClick={() => onEscalate(String(alert.id))} disabled={escalating}
              sx={{ minHeight: 44 }}
            >
              Escalate
            </Button>
          )}
        </Box>
      </CardContent>
    </Card>
  )
}

export default function AlertsPage() {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))

  const [selectedAlert, setSelectedAlert] = useState<AlertEvent | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState({ status: '', severity: '' })
  const canAck = useScope('alert:ack')

  const { data, isLoading, error } = useAlerts({
    status: filters.status || undefined,
    severity: filters.severity || undefined,
    page,
    size: 20,
  })

  const { mutate: acknowledge, isPending: acknowledging } = useAcknowledgeAlert()
  const { mutate: escalate, isPending: escalating } = useEscalateAlert()

  const handleAck = (id: string, note?: string) => {
    acknowledge({ id, note }, { onSuccess: () => { setSelectedAlert(null) } })
  }

  const handleEscalate = (id: string, note?: string) => {
    escalate({ id, note }, { onSuccess: () => { setSelectedAlert(null) } })
  }

  const handleBulkAck = () => {
    selected.forEach((id) => acknowledge({ id }))
    setSelected(new Set())
  }

  const alerts = data?.content ?? []

  return (
    <Box sx={{ overflow: 'hidden' }}>
      <Box display="flex" alignItems="center" gap={1} mb={3}>
        <NotificationsActiveIcon color="primary" />
        <Typography variant="h5">Alert Management</Typography>
        {data && <Chip label={`${data.totalElements} total`} size="small" />}
      </Box>

      {/* Filters */}
      <Box display="flex" gap={2} mb={2} flexWrap="wrap">
        <TextField select size="small" label="Status" value={filters.status}
          onChange={(e) => { setFilters((f) => ({ ...f, status: e.target.value })); setPage(0) }}
          sx={{ minWidth: isMobile ? '100%' : 140 }}>
          <MenuItem value="">All</MenuItem>
          {['NEW', 'OPEN', 'ACKNOWLEDGED', 'ESCALATED'].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Severity" value={filters.severity}
          onChange={(e) => { setFilters((f) => ({ ...f, severity: e.target.value })); setPage(0) }}
          sx={{ minWidth: isMobile ? '100%' : 140 }}>
          <MenuItem value="">All</MenuItem>
          {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
        {selected.size > 0 && (
          <Tooltip title={!canAck ? 'You need alert:ack scope' : ''}>
            <span>
              <Button variant="outlined" size="small" onClick={handleBulkAck} startIcon={<CheckCircleIcon />} disabled={!canAck} sx={{ minHeight: 44 }}>
                Acknowledge selected ({selected.size})
              </Button>
            </span>
          </Tooltip>
        )}
      </Box>

      {error && <MuiAlert severity="error" sx={{ mb: 2 }}>Failed to load alerts.</MuiAlert>}

      {isMobile ? (
        /* Mobile: Card list */
        <Box>
          {isLoading && <Box textAlign="center" py={3}><CircularProgress /></Box>}
          {!isLoading && alerts.length === 0 && (
            <Typography color="text.secondary" variant="body2" textAlign="center" py={3}>No alerts found</Typography>
          )}
          {alerts.map((alert) => (
            <Box key={alert.id} onClick={() => setSelectedAlert(alert)} sx={{ cursor: 'pointer' }}>
              <MobileAlertCard
                alert={alert}
                onAck={(id) => handleAck(id)}
                onEscalate={(id) => handleEscalate(id)}
                canAck={canAck}
                acknowledging={acknowledging}
                escalating={escalating}
              />
            </Box>
          ))}
        </Box>
      ) : (
        /* Desktop: Table */
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell padding="checkbox">
                  <Checkbox
                    indeterminate={selected.size > 0 && selected.size < alerts.length}
                    checked={alerts.length > 0 && selected.size === alerts.length}
                    onChange={(e) => setSelected(e.target.checked ? new Set(alerts.map((a) => String(a.id))) : new Set())}
                  />
                </TableCell>
                <TableCell>Severity</TableCell>
                <TableCell>Rule</TableCell>
                <TableCell>Module</TableCell>
                <TableCell>Sensor</TableCell>
                <TableCell>Value</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Detected</TableCell>
                <TableCell>Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading && (
                <TableRow><TableCell colSpan={9} align="center"><CircularProgress size={24} /></TableCell></TableRow>
              )}
              {!isLoading && alerts.length === 0 && (
                <TableRow>
                  <TableCell colSpan={9} align="center">
                    <Typography color="text.secondary" variant="body2">No alerts found</Typography>
                  </TableCell>
                </TableRow>
              )}
              {alerts.map((alert) => (
                <TableRow key={alert.id} hover sx={{ cursor: 'pointer' }} onClick={() => setSelectedAlert(alert)}>
                  <TableCell padding="checkbox" onClick={(e) => e.stopPropagation()}>
                    <Checkbox
                      checked={selected.has(String(alert.id))}
                      onChange={(e) => setSelected((prev) => {
                        const next = new Set(prev)
                        e.target.checked ? next.add(String(alert.id)) : next.delete(String(alert.id))
                        return next
                      })}
                    />
                  </TableCell>
                  <TableCell><SeverityBadge severity={alert.severity} /></TableCell>
                  <TableCell><Typography variant="body2" noWrap sx={{ maxWidth: 180 }}>{alert.ruleName ?? '—'}</Typography></TableCell>
                  <TableCell><Typography variant="body2">{alert.module}</Typography></TableCell>
                  <TableCell><Typography variant="body2" noWrap sx={{ maxWidth: 120 }}>{alert.sensorId ?? '—'}</Typography></TableCell>
                  <TableCell><Typography variant="body2">{alert.value}</Typography></TableCell>
                  <TableCell><StatusBadge status={alert.status} /></TableCell>
                  <TableCell>
                    <Tooltip title={format(new Date(alert.detectedAt), 'dd/MM/yyyy HH:mm:ss')}>
                      <Typography variant="caption" color="text.secondary">
                        {formatDistanceToNow(new Date(alert.detectedAt), { addSuffix: true })}
                      </Typography>
                    </Tooltip>
                  </TableCell>
                  <TableCell onClick={(e) => e.stopPropagation()}>
                    <Box display="flex" gap={0.5}>
                      {alert.status === 'OPEN' && (
                        <Tooltip title={!canAck ? 'You need alert:ack scope' : 'Acknowledge'}>
                          <span>
                            <IconButton size="small" color="success" onClick={() => handleAck(String(alert.id))} disabled={!canAck}>
                              <CheckCircleIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      )}
                      {(alert.status === 'OPEN' || alert.status === 'ACKNOWLEDGED') && (
                        <Tooltip title="Escalate">
                          <IconButton size="small" color="warning" onClick={() => handleEscalate(String(alert.id))}>
                            <ArrowUpwardIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {data && (
        <TablePagination
          component="div" count={data.totalElements} page={page}
          onPageChange={(_, p) => setPage(p)} rowsPerPage={20} rowsPerPageOptions={[20]}
        />
      )}

      <AlertDetailDrawer
        alert={selectedAlert} onClose={() => setSelectedAlert(null)}
        onAcknowledge={handleAck} onEscalate={handleEscalate}
        acknowledging={acknowledging} escalating={escalating}
        isMobile={isMobile}
      />
    </Box>
  )
}
