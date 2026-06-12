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
import { useAlerts, useAcknowledgeAlert, useEscalateAlert, useResolveAlert } from '@/hooks/useAlertManagement'
import { useAlertStream } from '@/hooks/useAlertStream'
import { useScope } from '@/hooks/useScope'
import AlertFeedbackButton from '@/components/alerts/AlertFeedbackButton'

/** Severity to MUI Chip color prop mapping (GAP-028: no raw hex) */
const SEVERITY_CHIP_COLOR: Record<string, 'error' | 'warning' | 'info' | 'default'> = {
  CRITICAL: 'error',
  HIGH: 'warning',
  MEDIUM: 'info',
  LOW: 'default',
}

function SeverityBadge({ severity }: { severity: string }) {
  const chipColor = SEVERITY_CHIP_COLOR[severity] ?? 'default'
  return (
    <Chip
      label={severity}
      size="small"
      color={chipColor}
      sx={{ fontWeight: 700, fontSize: 11, height: 22 }}
    />
  )
}

/** Module to MUI Chip color prop mapping (GAP-028: no raw hex) */
const MODULE_CHIP_COLOR: Record<string, 'secondary' | 'primary' | 'success' | 'warning' | 'default'> = {
  STRUCTURAL: 'secondary',
  FLOOD: 'primary',
  ENVIRONMENT: 'success',
  TRAFFIC: 'warning',
}

function ModuleBadge({ module }: { module: string }) {
  const chipColor = MODULE_CHIP_COLOR[module] ?? 'default'
  return (
    <Chip
      label={module}
      size="small"
      variant="outlined"
      color={chipColor}
      sx={{ fontWeight: 600, fontSize: 10, height: 20 }}
      aria-label={`Module: ${module}`}
    />
  )
}

function StatusBadge({ status }: { status: string }) {
  const color = status === 'ACKNOWLEDGED' ? 'success' : status === 'ESCALATED' ? 'error' : status === 'RESOLVED' ? 'default' : 'warning'
  return <Chip label={status} size="small" variant="outlined" color={color} />
}

interface AlertDetailDrawerProps {
  alert: AlertEvent | null
  onClose: () => void
  onAcknowledge: (id: string, note?: string) => void
  onEscalate: (id: string, note?: string) => void
  onResolve: (id: string, note?: string) => void
  acknowledging: boolean
  escalating: boolean
  resolving: boolean
  isMobile: boolean
  canEscalate: boolean
  onFeedback: (alertId: string, correct: boolean, comment?: string) => void
}

function AlertDetailDrawer({ alert, onClose, onAcknowledge, onEscalate, onResolve, acknowledging, escalating, resolving, isMobile, canEscalate, onFeedback }: AlertDetailDrawerProps) {
  const [note, setNote] = useState('')
  const actionLabel = alert?.status === 'ACKNOWLEDGED' ? 'Acknowledged by' : alert?.status === 'RESOLVED' ? 'Resolved by' : 'Escalated by'
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
          {(alert.status === 'OPEN' || alert.status === 'ACKNOWLEDGED' || alert.status === 'ESCALATED') && (
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
                    disabled={acknowledging || escalating || resolving} onClick={() => onAcknowledge(String(alert.id), note)}
                    sx={{ minHeight: 44 }}
                  >
                    {acknowledging ? <CircularProgress size={20} /> : 'Acknowledge'}
                  </Button>
                )}
                {(alert.status === 'OPEN' || alert.status === 'ACKNOWLEDGED') && (
                  <Button
                    variant="outlined" fullWidth startIcon={<ArrowUpwardIcon />}
                    disabled={acknowledging || escalating || resolving || !canEscalate}
                    onClick={() => onEscalate(String(alert.id), note)}
                    color="warning"
                    sx={{ minHeight: 44 }}
                  >
                    {escalating ? <CircularProgress size={20} /> : 'Escalate'}
                  </Button>
                )}
                {alert.status === 'ESCALATED' && (
                  <Button
                    variant="contained" fullWidth color="success" startIcon={<CheckCircleIcon />}
                    disabled={acknowledging || escalating || resolving}
                    onClick={() => onResolve(String(alert.id), note)}
                    sx={{ minHeight: 44 }}
                  >
                    {resolving ? <CircularProgress size={20} /> : 'Resolve'}
                  </Button>
                )}
              </Box>
            </Box>
          )}
          {/* AI Feedback — shown for all alert statuses */}
          <Box mt={3}>
            <Divider sx={{ mb: 2 }} />
            <AlertFeedbackButton
              alertId={String(alert.id)}
              onSubmit={(correct, comment) => onFeedback(String(alert.id), correct, comment)}
            />
          </Box>
        </Box>
      )}
    </Drawer>
  )
}

function MobileAlertCard({ alert, onAck, onEscalate, onResolve, canAck, canEscalate, acknowledging, escalating, resolving }: {
  alert: AlertEvent
  onAck: (id: string) => void
  onEscalate: (id: string) => void
  onResolve: (id: string) => void
  canAck: boolean
  canEscalate: boolean
  acknowledging: boolean
  escalating: boolean
  resolving: boolean
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
          <ModuleBadge module={alert.module} />
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
              onClick={() => onEscalate(String(alert.id))} disabled={!canEscalate || escalating}
              sx={{ minHeight: 44 }}
            >
              Escalate
            </Button>
          )}
          {alert.status === 'ESCALATED' && (
            <Button
              size="small" color="success" startIcon={<CheckCircleIcon />}
              onClick={() => onResolve(String(alert.id))} disabled={resolving}
              sx={{ minHeight: 44 }}
            >
              Resolve
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
  const [filters, setFilters] = useState({ status: '', severity: '', module: '' })
  const canAck = useScope('alert:ack')
  const canEscalate = useScope('alert:escalate')

  const { status: streamStatus } = useAlertStream({ severity: filters.severity || undefined })

  const { data, isLoading, error } = useAlerts({
    status: filters.status || undefined,
    severity: filters.severity || undefined,
    module: filters.module || undefined,
    page,
    size: 20,
  })

  const { mutate: acknowledge, isPending: acknowledging } = useAcknowledgeAlert()
  const { mutate: escalate, isPending: escalating } = useEscalateAlert()
  const { mutate: resolve, isPending: resolving } = useResolveAlert()

  const handleAck = (id: string, note?: string) => {
    acknowledge({ id, note }, { onSuccess: () => { setSelectedAlert(null) } })
  }

  const handleEscalate = (id: string, note?: string) => {
    escalate({ id, note }, { onSuccess: () => { setSelectedAlert(null) } })
  }

  const handleResolve = (id: string, note?: string) => {
    resolve({ id, note }, { onSuccess: () => { setSelectedAlert(null) } })
  }

  const handleFeedback = (_alertId: string, _correct: boolean, _comment?: string) => {
    // Feedback is captured locally in AlertFeedbackButton state;
    // a real implementation would POST to /api/alerts/{alertId}/feedback.
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
        <Chip
          label={streamStatus === 'connected' ? 'Live' : streamStatus === 'connecting' ? 'Connecting...' : 'Offline'}
          size="small"
          color={streamStatus === 'connected' ? 'success' : 'default'}
          variant={streamStatus === 'connected' ? 'filled' : 'outlined'}
          sx={{ ml: 'auto' }}
        />
      </Box>

      {/* Filters */}
      <Box display="flex" gap={2} mb={2} flexWrap="wrap">
        <TextField select size="small" label="Status" value={filters.status}
          onChange={(e) => { setFilters((f) => ({ ...f, status: e.target.value })); setPage(0) }}
          sx={{ minWidth: isMobile ? '100%' : 140 }}>
          <MenuItem value="">All</MenuItem>
          {['NEW', 'OPEN', 'ACKNOWLEDGED', 'ESCALATED', 'RESOLVED'].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Severity" value={filters.severity}
          onChange={(e) => { setFilters((f) => ({ ...f, severity: e.target.value })); setPage(0) }}
          sx={{ minWidth: isMobile ? '100%' : 140 }}>
          <MenuItem value="">All</MenuItem>
          {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Module" value={filters.module}
          onChange={(e) => { setFilters((f) => ({ ...f, module: e.target.value })); setPage(0) }}
          sx={{ minWidth: isMobile ? '100%' : 140 }}>
          <MenuItem value="">All</MenuItem>
          {['STRUCTURAL', 'FLOOD', 'ENVIRONMENT', 'TRAFFIC', 'BMS'].map((m) => (
            <MenuItem key={m} value={m}>{m}</MenuItem>
          ))}
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
                onResolve={(id) => handleResolve(id)}
                canAck={canAck}
                canEscalate={canEscalate}
                acknowledging={acknowledging}
                escalating={escalating}
                resolving={resolving}
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
                        <Tooltip title={!canEscalate ? 'You need alert:escalate scope' : 'Escalate'}>
                          <span>
                            <IconButton size="small" color="warning" onClick={() => handleEscalate(String(alert.id))} disabled={!canEscalate}>
                            <ArrowUpwardIcon fontSize="small" />
                          </IconButton>
                          </span>
                        </Tooltip>
                      )}
                      {alert.status === 'ESCALATED' && (
                        <Tooltip title="Resolve">
                          <IconButton size="small" color="success" onClick={() => handleResolve(String(alert.id))} disabled={resolving}>
                            <CheckCircleIcon fontSize="small" />
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
        onAcknowledge={handleAck} onEscalate={handleEscalate} onResolve={handleResolve}
        acknowledging={acknowledging} escalating={escalating} resolving={resolving}
        isMobile={isMobile} canEscalate={canEscalate}
        onFeedback={handleFeedback}
      />
    </Box>
  )
}
