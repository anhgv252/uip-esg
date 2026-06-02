import { useState, useMemo } from 'react';
import {
  Box, Typography, Tabs, Tab, Table, TableHead, TableRow, TableCell,
  TableBody, TableContainer, Paper, Chip, IconButton, Tooltip, Divider,
  Select, MenuItem, FormControl, Switch, CircularProgress,
  Alert, Button, Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, Collapse,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import PeopleIcon from '@mui/icons-material/People';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettings';
import { getErrors } from '../api/errors';
import { useQuery } from '@tanstack/react-query';
import ErrorRecordTable from '../components/admin/ErrorRecordTable';
import {
  useAdminUsers, useAdminSensors, useChangeUserRole, useDeactivateUser,
  useToggleSensorStatus, useCreateSensor, useListTenants, useCreateTenant,
  useUpdateTenantFeature, useTenantFeatures, useTenantUsers, useInviteTenantUser,
} from '@/hooks/useAdminData';
import type { UserSummaryDto, SensorRegistryDto } from '@/api/adminMgmt';
import type { TenantSummaryDto, TenantUserDto } from '@/api/tenantAdmin';

const ALL_FEATURES = [
  'environment-module',
  'esg-module',
  'traffic-module',
  'citizen-portal',
  'ai-workflow',
  'city-ops',
] as const;

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
                    onChange={(e) => {
                      const newRole = e.target.value as string
                      if (window.confirm(`Change role of ${u.username} to ${newRole.replace('ROLE_', '')}?`)) {
                        changeRole.mutate({ username: u.username, role: newRole })
                      }
                    }}
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
                      onClick={() => {
                        if (window.confirm(`Deactivate user ${u.username}? This action cannot be undone.`)) {
                          deactivate.mutate(u.username)
                        }
                      }}
                      aria-label={`Deactivate user ${u.username}`}>
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

function CreateSensorDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form, setForm] = useState({
    sensorId: '', sensorName: '', sensorType: 'AIR_QUALITY', districtCode: '', latitude: '', longitude: '',
  });
  const create = useCreateSensor();

  const handleSubmit = () => {
    create.mutate(
      {
        sensorId: form.sensorId.trim(),
        sensorName: form.sensorName.trim(),
        sensorType: form.sensorType || undefined,
        districtCode: form.districtCode || undefined,
        latitude: form.latitude ? Number(form.latitude) : undefined,
        longitude: form.longitude ? Number(form.longitude) : undefined,
      },
      {
        onSuccess: () => {
          setForm({ sensorId: '', sensorName: '', sensorType: 'AIR_QUALITY', districtCode: '', latitude: '', longitude: '' });
          onClose();
        },
      }
    );
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Add Sensor</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
        <TextField label="Sensor ID *" size="small" value={form.sensorId}
          onChange={(e) => setForm((f) => ({ ...f, sensorId: e.target.value }))} />
        <TextField label="Name *" size="small" value={form.sensorName}
          onChange={(e) => setForm((f) => ({ ...f, sensorName: e.target.value }))} />
        <FormControl size="small" fullWidth>
          <Select value={form.sensorType}
            onChange={(e) => setForm((f) => ({ ...f, sensorType: e.target.value }))}>
            <MenuItem value="AIR_QUALITY">AIR_QUALITY</MenuItem>
            <MenuItem value="WATER_QUALITY">WATER_QUALITY</MenuItem>
            <MenuItem value="NOISE">NOISE</MenuItem>
            <MenuItem value="TRAFFIC">TRAFFIC</MenuItem>
            <MenuItem value="ENERGY">ENERGY</MenuItem>
          </Select>
        </FormControl>
        <TextField label="District Code" size="small" value={form.districtCode}
          onChange={(e) => setForm((f) => ({ ...f, districtCode: e.target.value }))} />
        <Box display="flex" gap={1}>
          <TextField label="Latitude" size="small" type="number" value={form.latitude}
            onChange={(e) => setForm((f) => ({ ...f, latitude: e.target.value }))} />
          <TextField label="Longitude" size="small" type="number" value={form.longitude}
            onChange={(e) => setForm((f) => ({ ...f, longitude: e.target.value }))} />
        </Box>
        {create.isError && <Alert severity="error">Failed to create sensor.</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit}
          disabled={!form.sensorId || !form.sensorName || create.isPending}>
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function SensorsTab() {
  const { data: sensors, isLoading, error } = useAdminSensors()
  const toggle = useToggleSensorStatus()
  const [dialogOpen, setDialogOpen] = useState(false);

  if (isLoading) return <CircularProgress />
  if (error) return <Alert severity="error">Failed to load sensors.</Alert>

  return (
    <>
      <Box display="flex" justifyContent="flex-end" mb={1}>
        <Button variant="contained" size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
          Add Sensor
        </Button>
      </Box>
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
                    aria-label={`Toggle sensor ${s.sensorName}`}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <CreateSensorDialog open={dialogOpen} onClose={() => setDialogOpen(false)} />
    </>
  )
}

function CreateTenantDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [form, setForm] = useState({ tenantId: '', tenantName: '', tier: 'T1', locationPath: '' });
  const create = useCreateTenant();

  const handleSubmit = () => {
    create.mutate(
      { tenantId: form.tenantId.trim(), tenantName: form.tenantName.trim(), tier: form.tier, locationPath: form.locationPath || undefined },
      {
        onSuccess: () => {
          setForm({ tenantId: '', tenantName: '', tier: 'T1', locationPath: '' });
          onClose();
        },
      }
    );
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Create Tenant</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
        <TextField label="Tenant ID *" size="small" value={form.tenantId}
          helperText="Lowercase, alphanumeric, hyphen or underscore"
          onChange={(e) => setForm((f) => ({ ...f, tenantId: e.target.value.toLowerCase() }))} />
        <TextField label="Tenant Name *" size="small" value={form.tenantName}
          onChange={(e) => setForm((f) => ({ ...f, tenantName: e.target.value }))} />
        <FormControl size="small" fullWidth>
          <Select value={form.tier}
            onChange={(e) => setForm((f) => ({ ...f, tier: e.target.value }))}>
            <MenuItem value="T1">T1 — Standard</MenuItem>
            <MenuItem value="T2">T2 — Professional</MenuItem>
            <MenuItem value="T3">T3 — Enterprise</MenuItem>
          </Select>
        </FormControl>
        <TextField label="Location Path" size="small" value={form.locationPath}
          helperText="Optional ltree path (e.g. vn.hcmc)"
          onChange={(e) => setForm((f) => ({ ...f, locationPath: e.target.value }))} />
        {create.isError && <Alert severity="error">Failed to create tenant. ID may already exist.</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit}
          disabled={!form.tenantId || !form.tenantName || create.isPending}>
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}

interface InviteUserDialogProps {
  tenantId: string;
  open: boolean;
  onClose: () => void;
}

function InviteUserDialog({ tenantId, open, onClose }: InviteUserDialogProps) {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('ROLE_OPERATOR');
  const invite = useInviteTenantUser();

  const handleSubmit = () => {
    invite.mutate(
      { tenantId, body: { email, role } },
      { onSuccess: () => { setEmail(''); setRole('ROLE_OPERATOR'); onClose(); } },
    );
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Invite User to {tenantId}</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
        <TextField label="Email *" size="small" value={email} type="email"
          onChange={(e) => setEmail(e.target.value)} />
        <FormControl size="small" fullWidth>
          <Select value={role} onChange={(e) => setRole(e.target.value)}>
            <MenuItem value="ROLE_TENANT_ADMIN">Tenant Admin</MenuItem>
            <MenuItem value="ROLE_OPERATOR">Tenant Operator</MenuItem>
            <MenuItem value="ROLE_CITIZEN">Tenant Viewer</MenuItem>
          </Select>
        </FormControl>
        {invite.isError && <Alert severity="error">Failed to invite user.</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit}
          disabled={!email || invite.isPending}>
          Invite
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function TenantFeatureRow({ tenant }: { tenant: TenantSummaryDto }) {
  const [expanded, setExpanded] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const updateFeature = useUpdateTenantFeature();
  const { data: featureFlags, isLoading: featuresLoading } = useTenantFeatures(tenant.tenantId, expanded);
  const { data: users, isLoading: usersLoading } = useTenantUsers(tenant.tenantId, expanded);

  return (
    <>
      <TableRow hover>
        <TableCell>
          <Typography variant="caption" fontFamily="monospace">{tenant.tenantId}</Typography>
        </TableCell>
        <TableCell>{tenant.tenantName}</TableCell>
        <TableCell><Chip label={tenant.tier} size="small" variant="outlined" /></TableCell>
        <TableCell>
          <Chip size="small" label={tenant.active ? 'Active' : 'Inactive'}
            color={tenant.active ? 'success' : 'default'} />
        </TableCell>
        <TableCell>{tenant.locationPath ?? '—'}</TableCell>
        <TableCell>
          <Tooltip title={expanded ? 'Collapse' : 'Manage'}>
            <IconButton size="small" onClick={() => setExpanded((v) => !v)}
              aria-label={`Expand ${tenant.tenantId}`}>
              {expanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </TableCell>
      </TableRow>
      <TableRow>
        <TableCell colSpan={6} sx={{ p: 0, borderBottom: expanded ? undefined : 0 }}>
          <Collapse in={expanded} unmountOnExit>
            <Box px={3} py={2} bgcolor="action.hover">

              {/* Feature Flags section */}
              <Typography variant="caption" color="text.secondary" gutterBottom display="block" fontWeight={600}>
                Feature Flags
              </Typography>
              {featuresLoading ? (
                <CircularProgress size={16} />
              ) : (
                <Box display="flex" flexWrap="wrap" gap={2} mb={2}>
                  {ALL_FEATURES.map((feature) => {
                    const enabled = featureFlags?.[feature] ?? true;
                    return (
                      <Box key={feature} display="flex" alignItems="center" gap={0.5}>
                        <Switch
                          size="small"
                          checked={enabled}
                          disabled={updateFeature.isPending}
                          onChange={() =>
                            updateFeature.mutate({ tenantId: tenant.tenantId, featureKey: feature, enabled: !enabled })
                          }
                          aria-label={`Toggle ${feature} for ${tenant.tenantId}`}
                        />
                        <Typography variant="caption">{feature}</Typography>
                      </Box>
                    );
                  })}
                </Box>
              )}

              <Divider sx={{ my: 1.5 }} />

              {/* Users section */}
              <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
                <Box display="flex" alignItems="center" gap={0.5}>
                  <PeopleIcon fontSize="small" color="action" />
                  <Typography variant="caption" color="text.secondary" fontWeight={600}>
                    Users ({users?.length ?? 0})
                  </Typography>
                </Box>
                <Button size="small" variant="outlined" startIcon={<AddIcon />}
                  onClick={() => setInviteOpen(true)}>
                  Invite
                </Button>
              </Box>
              {usersLoading ? (
                <CircularProgress size={16} />
              ) : users && users.length > 0 ? (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ py: 0.5 }}>Username</TableCell>
                      <TableCell sx={{ py: 0.5 }}>Email</TableCell>
                      <TableCell sx={{ py: 0.5 }}>Role</TableCell>
                      <TableCell sx={{ py: 0.5 }}>Status</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(users as TenantUserDto[]).map((u) => (
                      <TableRow key={u.id}>
                        <TableCell sx={{ py: 0.5 }}>
                          <Typography variant="caption">{u.username}</Typography>
                        </TableCell>
                        <TableCell sx={{ py: 0.5 }}>
                          <Typography variant="caption">{u.email}</Typography>
                        </TableCell>
                        <TableCell sx={{ py: 0.5 }}>
                          <Chip label={u.role} size="small" variant="outlined" />
                        </TableCell>
                        <TableCell sx={{ py: 0.5 }}>
                          <Chip size="small" label={u.active ? 'Active' : 'Inactive'}
                            color={u.active ? 'success' : 'default'} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <Typography variant="caption" color="text.disabled">No users yet.</Typography>
              )}
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
      <InviteUserDialog tenantId={tenant.tenantId} open={inviteOpen} onClose={() => setInviteOpen(false)} />
    </>
  );
}

function TenantsTab() {
  const { data: tenants, isLoading, error } = useListTenants();
  const [dialogOpen, setDialogOpen] = useState(false);

  if (isLoading) return <CircularProgress />;
  if (error) return <Alert severity="error">Failed to load tenants.</Alert>;

  return (
    <>
      <Box display="flex" justifyContent="flex-end" mb={1}>
        <Button variant="contained" size="small" startIcon={<AddIcon />} onClick={() => setDialogOpen(true)}>
          Create Tenant
        </Button>
      </Box>
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Tenant ID</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Tier</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Location Path</TableCell>
              <TableCell>Features</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(tenants ?? []).map((t: TenantSummaryDto) => (
              <TenantFeatureRow key={t.tenantId} tenant={t} />
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <CreateTenantDialog open={dialogOpen} onClose={() => setDialogOpen(false)} />
    </>
  );
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
        <Tab label="Tenants" />
        <Tab label="Data Quality / Errors" />
      </Tabs>

      <TabPanel value={tab} index={0}>
        <UsersTab />
      </TabPanel>

      <TabPanel value={tab} index={1}>
        <SensorsTab />
      </TabPanel>

      <TabPanel value={tab} index={2}>
        <TenantsTab />
      </TabPanel>

      <TabPanel value={tab} index={3}>
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
