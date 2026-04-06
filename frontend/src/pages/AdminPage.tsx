import { useState, useMemo } from 'react';
import {
  Box, Typography, Tabs, Tab, Table, TableHead, TableRow, TableCell,
  TableBody, TableContainer, Paper, Chip, IconButton, Tooltip,
  Select, MenuItem, FormControl, Switch, CircularProgress,
  Alert,
} from '@mui/material';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import { useQuery } from '@tanstack/react-query';
import { getErrors } from '../api/errors';
import ErrorRecordTable from '../components/admin/ErrorRecordTable';
import { useAdminUsers, useAdminSensors, useChangeUserRole, useDeactivateUser, useToggleSensorStatus } from '@/hooks/useAdminData';
import type { UserSummaryDto, SensorRegistryDto } from '@/api/adminMgmt';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel({ children, index, value }: TabPanelProps) {
  return value === index ? <Box pt={2}>{children}</Box> : null;
}

function UsersTab() {
  const { data: users, isLoading, error } = useAdminUsers()
  const changeRole = useChangeUserRole()
  const deactivate = useDeactivateUser()

  if (isLoading) return <CircularProgress />
  if (error) return <Alert severity="error">Failed to load users.</Alert>

  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Username</TableCell>
            <TableCell>Email</TableCell>
            <TableCell>Roles</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Change Role</TableCell>
            <TableCell>Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {(users?.content ?? []).map((u: UserSummaryDto) => (
            <TableRow key={u.username} hover>
              <TableCell>{u.username}</TableCell>
              <TableCell>{u.email}</TableCell>
              <TableCell>
                <Chip label={u.role.replace('ROLE_', '')} size="small"
                  color={u.role === 'ROLE_ADMIN' ? 'error' : u.role === 'ROLE_OPERATOR' ? 'warning' : 'default'} />
              </TableCell>
              <TableCell>
                <Chip size="small"
                  label={u.active ? 'Active' : 'Inactive'}
                  color={u.active ? 'success' : 'default'} />
              </TableCell>
              <TableCell>
                <FormControl size="small" variant="outlined">
                  <Select
                    value=""
                    displayEmpty
                    renderValue={() => 'Set role…'}
                    onChange={(e) => changeRole.mutate({ username: u.username, role: e.target.value as string })}
                    disabled={changeRole.isPending}
                    sx={{ minWidth: 140, fontSize: '0.75rem' }}
                  >
                    <MenuItem value="ROLE_ADMIN">ADMIN</MenuItem>
                    <MenuItem value="ROLE_OPERATOR">OPERATOR</MenuItem>
                    <MenuItem value="ROLE_CITIZEN">CITIZEN</MenuItem>
                    <MenuItem value="ROLE_VIEWER">VIEWER</MenuItem>
                  </Select>
                </FormControl>
              </TableCell>
              <TableCell>
                <Tooltip title="Deactivate user">
                  <span>
                    <IconButton size="small" color="error"
                      disabled={!u.active || deactivate.isPending}
                      onClick={() => deactivate.mutate(u.username)}>
                      ✕
                    </IconButton>
                  </span>
                </Tooltip>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

function SensorsTab() {
  const { data: sensors, isLoading, error } = useAdminSensors()
  const toggle = useToggleSensorStatus()

  if (isLoading) return <CircularProgress />
  if (error) return <Alert severity="error">Failed to load sensors.</Alert>

  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Sensor ID</TableCell>
            <TableCell>Name</TableCell>
            <TableCell>Type</TableCell>
            <TableCell>District</TableCell>
            <TableCell>Coordinates</TableCell>
            <TableCell>Active</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {(sensors ?? []).map((s: SensorRegistryDto) => (
            <TableRow key={s.sensorId} hover>
              <TableCell><Typography variant="caption" fontFamily="monospace">{s.sensorId}</Typography></TableCell>
              <TableCell>{s.sensorName}</TableCell>
              <TableCell><Chip label={s.sensorType} size="small" variant="outlined" /></TableCell>
              <TableCell>{s.districtCode}</TableCell>
              <TableCell>
                <Typography variant="caption">{s.latitude?.toFixed(4)}, {s.longitude?.toFixed(4)}</Typography>
              </TableCell>
              <TableCell>
                <Switch
                  size="small"
                  checked={s.active}
                  disabled={toggle.isPending}
                  onChange={() => toggle.mutate({ id: s.id, active: !s.active })}
                />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

export default function AdminPage() {
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(25);
  const [statusFilter, setStatusFilter] = useState('');
  const [moduleFilter, setModuleFilter] = useState('');

  const { data: errorPage, isLoading } = useQuery({
    queryKey: ['error-records', page, rowsPerPage, statusFilter, moduleFilter],
    queryFn: () =>
      getErrors({
        page,
        size: rowsPerPage,
        status: statusFilter || undefined,
        module: moduleFilter || undefined,
      }),
    staleTime: 30_000,
  });

  const modules = useMemo(
    () => Array.from(new Set(errorPage?.content.map((r) => r.sourceModule) ?? [])),
    [errorPage]
  );

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={2}>
        <AdminPanelSettingsIcon color="primary" />
        <Typography variant="h5">Administration</Typography>
      </Box>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tab label="Users" />
        <Tab label="Sensors" />
        <Tab label="Data Quality / Errors" />
      </Tabs>

      <TabPanel value={tab} index={0}>
        <UsersTab />
      </TabPanel>

      <TabPanel value={tab} index={1}>
        <SensorsTab />
      </TabPanel>

      <TabPanel value={tab} index={2}>
        <ErrorRecordTable
          records={errorPage?.content ?? []}
          total={errorPage?.totalElements ?? 0}
          loading={isLoading}
          page={page}
          rowsPerPage={rowsPerPage}
          onPageChange={(p) => setPage(p)}
          onRowsPerPageChange={(r) => { setRowsPerPage(r); setPage(0); }}
          statusFilter={statusFilter}
          onStatusFilterChange={(s) => { setStatusFilter(s); setPage(0); }}
          moduleFilter={moduleFilter}
          onModuleFilterChange={(m) => { setModuleFilter(m); setPage(0); }}
          modules={modules}
        />
      </TabPanel>
    </Box>
  );
}
