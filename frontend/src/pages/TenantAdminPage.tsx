import { useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  Box,
  Drawer,
  AppBar,
  Toolbar,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Typography,
  useTheme,
  useMediaQuery,
} from '@mui/material'
import MenuIcon from '@mui/icons-material/Menu'
import {
  Dashboard as OverviewIcon,
  People as UsersIcon,
  Apartment as BuildingIcon,
  BarChart as UsageIcon,
  Settings as SettingsIcon,
} from '@mui/icons-material'

const DRAWER_WIDTH = 240

const navItems = [
  { label: 'Overview', path: '/tenant-admin', icon: <OverviewIcon /> },
  { label: 'Users', path: '/tenant-admin/users', icon: <UsersIcon /> },
  { label: 'Buildings', path: '/tenant-admin/buildings', icon: <BuildingIcon /> },
  { label: 'Usage', path: '/tenant-admin/usage', icon: <UsageIcon /> },
  { label: 'Settings', path: '/tenant-admin/settings', icon: <SettingsIcon /> },
]

export default function TenantAdminPage() {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const location = useLocation()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)

  const isActive = (path: string) => {
    if (path === '/tenant-admin') return location.pathname === '/tenant-admin'
    return location.pathname.startsWith(path)
  }

  const handleNav = (path: string) => {
    navigate(path)
    if (isMobile) setMobileOpen(false)
  }

  const drawerContent = (
    <>
      <Box sx={{ p: 2 }}>
        <Typography variant="h6" noWrap>
          Tenant Admin
        </Typography>
      </Box>
      <List>
        {navItems.map((item) => (
          <ListItem key={item.path} disablePadding>
            <ListItemButton
              selected={isActive(item.path)}
              onClick={() => handleNav(item.path)}
              sx={{ minHeight: 48 }}
            >
              <ListItemIcon>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
    </>
  )

  return (
    <Box sx={{ display: 'flex', height: '100%' }}>
      {isMobile && (
        <AppBar position="fixed" sx={{ bgcolor: 'background.paper', color: 'text.primary' }}>
          <Toolbar>
            <IconButton edge="start" onClick={() => setMobileOpen(true)} sx={{ mr: 2 }}>
              <MenuIcon />
            </IconButton>
            <Typography variant="h6" noWrap>Tenant Admin</Typography>
          </Toolbar>
        </AppBar>
      )}

      {/* Mobile drawer */}
      {isMobile && (
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            '& .MuiDrawer-paper': { width: DRAWER_WIDTH },
          }}
        >
          {drawerContent}
        </Drawer>
      )}

      {/* Desktop drawer */}
      {!isMobile && (
        <Drawer
          variant="permanent"
          sx={{
            width: DRAWER_WIDTH,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              position: 'relative',
              borderRight: `1px solid ${theme.palette.divider}`,
            },
          }}
        >
          {drawerContent}
        </Drawer>
      )}

      <Box
        sx={{
          flexGrow: 1,
          p: { xs: 2, md: 3 },
          pt: isMobile ? 10 : 3,
          overflow: 'auto',
        }}
      >
        <Outlet />
      </Box>
    </Box>
  )
}
