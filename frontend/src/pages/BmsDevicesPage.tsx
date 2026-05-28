import { useState } from 'react'
import {
  Box, Typography, Chip, Button, TextField, MenuItem, Paper,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Dialog, DialogTitle, DialogContent, DialogActions, IconButton,
  CircularProgress, Alert as MuiAlert, Tooltip, Card, CardContent,
  Stack, useTheme, useMediaQuery,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import SendIcon from '@mui/icons-material/Send'
import DeviceHubIcon from '@mui/icons-material/DeviceHub'
import { formatDistanceToNow } from 'date-fns'
import { useBmsDevices, useCreateBmsDevice, useDeleteBmsDevice, useSendBmsCommand } from '@/hooks/useBmsDevices'

const PROTOCOL_COLORS: Record<string, string> = {
  MODBUS_TCP: '#1565c0',
  BACNET_IP: '#6a1b9a',
  MANUAL: '#757575',
}

function ProtocolBadge({ protocol }: { protocol: string }) {
  const color = PROTOCOL_COLORS[protocol] ?? '#757575'
  return <Chip label={protocol} size="small" sx={{ bgcolor: color, color: '#fff', fontWeight: 700, fontSize: 10 }} />
}

function StatusDot({ status }: { status: string }) {
  const color = status === 'ONLINE' ? '#4caf50' : status === 'OFFLINE' ? '#f44336' : '#ff9800'
  return <Box component="span" sx={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%', bgcolor: color, mr: 0.5 }} />
}

export default function BmsDevicesPage() {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const [showForm, setShowForm] = useState(false)

  const { data: devices = [], isLoading, error } = useBmsDevices()
  const { mutate: create, isPending: creating } = useCreateBmsDevice()
  const { mutate: remove, isPending: removing } = useDeleteBmsDevice()
  const { mutate: sendCommand } = useSendBmsCommand()

  const [form, setForm] = useState({
    deviceName: '', protocol: 'MODBUS_TCP', host: '', port: '502',
    unitId: '1', deviceId: '', pollInterval: '5000',
  })

  const handleCreate = () => {
    create({
      deviceName: form.deviceName,
      protocol: form.protocol,
      host: form.host || null,
      port: form.port ? Number(form.port) : null,
      unitId: form.unitId ? Number(form.unitId) : null,
      deviceId: form.deviceId ? Number(form.deviceId) : null,
      pollInterval: form.pollInterval ? Number(form.pollInterval) : null,
    }, { onSuccess: () => { setShowForm(false); setForm({ deviceName: '', protocol: 'MODBUS_TCP', host: '', port: '502', unitId: '1', deviceId: '', pollInterval: '5000' }) } })
  }

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={3}>
        <DeviceHubIcon color="primary" />
        <Typography variant="h5">BMS Devices</Typography>
        <Chip label={`${devices.length} devices`} size="small" />
        <Box sx={{ ml: 'auto' }}>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ minHeight: 44 }}>
            Add Device
          </Button>
        </Box>
      </Box>

      {error && <MuiAlert severity="error" sx={{ mb: 2 }}>Failed to load devices.</MuiAlert>}

      {isMobile ? (
        <Box>
          {isLoading && <Box textAlign="center" py={3}><CircularProgress /></Box>}
          {!isLoading && devices.length === 0 && (
            <Typography color="text.secondary" textAlign="center" py={3}>No BMS devices configured</Typography>
          )}
          {devices.map((device) => (
            <Card key={device.id} variant="outlined" sx={{ mb: 1 }}>
              <CardContent sx={{ '&:last-child': { pb: 1.5 } }}>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={0.5}>
                  <Typography variant="body2" fontWeight={600}>{device.deviceName}</Typography>
                  <ProtocolBadge protocol={device.protocol} />
                </Box>
                <Stack direction="row" spacing={1} mt={0.5}>
                  <Typography variant="caption" color="text.secondary">{device.host ?? '—'}:{device.port ?? ''}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    <StatusDot status={device.status} />{device.status}
                  </Typography>
                </Stack>
                <Box display="flex" gap={0.5} mt={1} justifyContent="flex-end">
                  <Tooltip title="Send command">
                    <IconButton size="small" onClick={() => sendCommand({ id: device.id, commandType: 'PING', payload: {} })}>
                      <SendIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Delete">
                    <IconButton size="small" color="error" onClick={() => remove(device.id)} disabled={removing}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Box>
              </CardContent>
            </Card>
          ))}
        </Box>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Protocol</TableCell>
                <TableCell>Host</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Last Seen</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading && (
                <TableRow><TableCell colSpan={6} align="center"><CircularProgress size={24} /></TableCell></TableRow>
              )}
              {!isLoading && devices.length === 0 && (
                <TableRow><TableCell colSpan={6} align="center"><Typography color="text.secondary" variant="body2">No BMS devices configured</Typography></TableCell></TableRow>
              )}
              {devices.map((device) => (
                <TableRow key={device.id} hover>
                  <TableCell><Typography variant="body2" fontWeight={500}>{device.deviceName}</Typography></TableCell>
                  <TableCell><ProtocolBadge protocol={device.protocol} /></TableCell>
                  <TableCell><Typography variant="body2">{device.host ?? '—'}:{device.port ?? ''}</Typography></TableCell>
                  <TableCell>
                    <Box display="flex" alignItems="center" gap={0.5}>
                      <StatusDot status={device.status} />
                      <Typography variant="body2">{device.status}</Typography>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Typography variant="caption" color="text.secondary">
                      {device.lastSeen ? formatDistanceToNow(new Date(device.lastSeen), { addSuffix: true }) : 'Never'}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    <Box display="flex" gap={0.5} justifyContent="flex-end">
                      <Tooltip title="Send command">
                        <IconButton size="small" onClick={() => {
                          sendCommand({ id: device.id, commandType: 'PING', payload: {} })
                        }}>
                          <SendIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete">
                        <IconButton size="small" color="error" onClick={() => remove(device.id)} disabled={removing}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={showForm} onClose={() => setShowForm(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add BMS Device</DialogTitle>
        <DialogContent>
          <Box display="flex" flexDirection="column" gap={2} mt={1}>
            <TextField label="Device Name" size="small" value={form.deviceName} onChange={(e) => setForm({ ...form, deviceName: e.target.value })} fullWidth />
            <TextField select label="Protocol" size="small" value={form.protocol} onChange={(e) => setForm({ ...form, protocol: e.target.value })} fullWidth>
              <MenuItem value="MODBUS_TCP">Modbus TCP</MenuItem>
              <MenuItem value="BACNET_IP">BACnet/IP</MenuItem>
              <MenuItem value="MANUAL">Manual</MenuItem>
            </TextField>
            <TextField label="Host" size="small" value={form.host} onChange={(e) => setForm({ ...form, host: e.target.value })} fullWidth />
            <TextField label="Port" size="small" type="number" value={form.port} onChange={(e) => setForm({ ...form, port: e.target.value })} fullWidth />
            <TextField label="Unit ID (Modbus)" size="small" type="number" value={form.unitId} onChange={(e) => setForm({ ...form, unitId: e.target.value })} fullWidth />
            <TextField label="Device ID (BACnet)" size="small" type="number" value={form.deviceId} onChange={(e) => setForm({ ...form, deviceId: e.target.value })} fullWidth />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowForm(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleCreate} disabled={creating || !form.deviceName}>
            {creating ? <CircularProgress size={20} /> : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
