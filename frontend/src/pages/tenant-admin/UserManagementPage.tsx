import { useState } from 'react'
import {
  Box, Typography, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, Button, Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  Select, MenuItem, FormControl, InputLabel, Chip, Skeleton, Snackbar, Alert,
  Card, CardContent, useTheme, useMediaQuery,
} from '@mui/material'
import PersonAddIcon from '@mui/icons-material/PersonAdd'
import { useAuth } from '@/hooks/useAuth'
import { useTenantUsers, useInviteUser, useUpdateUserRole } from '@/hooks/useTenantAdmin'

const INVITE_ROLES = ['ROLE_OPERATOR', 'ROLE_TENANT_ADMIN']
const EDITABLE_ROLES = ['ROLE_OPERATOR', 'ROLE_CITIZEN', 'ROLE_TENANT_ADMIN']

export default function UserManagementPage() {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const { user } = useAuth()
  const tenantId = user?.tenantId ?? ''
  const { data: users, isLoading } = useTenantUsers(user?.tenantId ?? null)
  const inviteMutation = useInviteUser(tenantId)
  const roleMutation = useUpdateUserRole(tenantId)

  const [inviteOpen, setInviteOpen] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState('ROLE_OPERATOR')
  const [inviteSuccessOpen, setInviteSuccessOpen] = useState(false)
  const [optimisticInvites, setOptimisticInvites] = useState<Array<{ email: string; role: string }>>([])

  const handleInvite = () => {
    const email = inviteEmail
    const role = inviteRole
    inviteMutation.mutate(
      { email: inviteEmail, role: inviteRole },
      {
        onSettled: () => {
          setInviteOpen(false)
          setInviteEmail('')
          setOptimisticInvites((prev) => [...prev, { email, role }])
          setInviteSuccessOpen(true)
        },
      },
    )
  }

  const handleRoleChange = (userId: string, role: string) => {
    roleMutation.mutate({ userId, body: { role } })
  }

  const allUsers = [
    ...(users ?? []),
    ...optimisticInvites.map((u, idx) => ({
      id: `optimistic-${idx}`,
      username: 'pending-invite',
      email: u.email,
      role: u.role,
      active: false,
      lastLoginAt: null,
      createdAt: new Date().toISOString(),
      _optimistic: true,
    })),
  ]

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h5" component="h2">User Management</Typography>
        <Button variant="contained" startIcon={<PersonAddIcon />} onClick={() => setInviteOpen(true)}>
          Invite User
        </Button>
      </Box>

      {isLoading ? (
        Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} height={48} sx={{ mb: 1 }} />)
      ) : isMobile ? (
        /* Mobile: card layout */
        <Box display="flex" flexDirection="column" gap={1.5}>
          {allUsers.map((u) => (
            <Card key={u.id} variant="outlined">
              <CardContent sx={{ '&:last-child': { pb: 1.5 } }}>
                <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                  <Box>
                    <Typography variant="subtitle2" fontWeight={600}>{u.username}</Typography>
                    <Typography variant="caption" color="text.secondary">{u.email}</Typography>
                  </Box>
                  <Chip
                    label={'_optimistic' in u ? 'Invited' : u.active ? 'Active' : 'Inactive'}
                    color={'_optimistic' in u ? 'info' : u.active ? 'success' : 'default'}
                    size="small"
                  />
                </Box>
                <Box mt={1} display="flex" justifyContent="space-between" alignItems="center">
                  {!('_optimistic' in u) ? (
                    <FormControl size="small" sx={{ minWidth: 120 }}>
                      <Select
                        value={u.role}
                        onChange={(e) => handleRoleChange(u.id, e.target.value)}
                        disabled={roleMutation.isPending}
                      >
                        {EDITABLE_ROLES.map((r) => (
                          <MenuItem key={r} value={r}>{r.replace('ROLE_', '')}</MenuItem>
                        ))}
                        <MenuItem value={u.role}>{u.role.replace('ROLE_', '')}</MenuItem>
                      </Select>
                    </FormControl>
                  ) : (
                    <Typography variant="body2">{u.role.replace('ROLE_', '')}</Typography>
                  )}
                  <Typography variant="caption" color="text.secondary">
                    {new Date(u.createdAt).toLocaleDateString('vi-VN')}
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          ))}
        </Box>
      ) : (
        /* Desktop: table layout */
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Username</TableCell>
                <TableCell>Email</TableCell>
                <TableCell>Role</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Created</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {allUsers.map((u) => (
                <TableRow key={u.id}>
                  <TableCell>{u.username}</TableCell>
                  <TableCell>{u.email}</TableCell>
                  <TableCell>
                    {'_optimistic' in u ? (
                      <Typography variant="body2">{u.role.replace('ROLE_', '')}</Typography>
                    ) : (
                      <FormControl size="small" sx={{ minWidth: 120 }}>
                        <Select
                          value={u.role}
                          onChange={(e) => handleRoleChange(u.id, e.target.value)}
                          disabled={roleMutation.isPending}
                        >
                          {EDITABLE_ROLES.map((r) => (
                            <MenuItem key={r} value={r}>{r.replace('ROLE_', '')}</MenuItem>
                          ))}
                          <MenuItem value={u.role}>{u.role.replace('ROLE_', '')}</MenuItem>
                        </Select>
                      </FormControl>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={'_optimistic' in u ? 'Invited' : u.active ? 'Active' : 'Inactive'}
                      color={'_optimistic' in u ? 'info' : u.active ? 'success' : 'default'}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>{u.createdAt ? new Date(u.createdAt).toLocaleDateString('vi-VN') : '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Invite Dialog */}
      <Dialog open={inviteOpen} onClose={() => setInviteOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Invite User</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth label="Email" type="email" margin="normal"
            value={inviteEmail}
            onChange={(e) => setInviteEmail(e.target.value)}
          />
          <FormControl fullWidth margin="normal">
            <InputLabel>Role</InputLabel>
            <Select value={inviteRole} label="Role" onChange={(e) => setInviteRole(e.target.value)}>
              {INVITE_ROLES.map((r) => (
                <MenuItem key={r} value={r}>{r.replace('ROLE_', '')}</MenuItem>
              ))}
            </Select>
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setInviteOpen(false)}>Cancel</Button>
          <Button onClick={handleInvite} variant="contained" disabled={!inviteEmail || inviteMutation.isPending}>
            Send Invite
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={inviteSuccessOpen} autoHideDuration={3000} onClose={() => setInviteSuccessOpen(false)}>
        <Alert severity="success" onClose={() => setInviteSuccessOpen(false)}>
          Invite sent successfully
        </Alert>
      </Snackbar>
    </Box>
  )
}
