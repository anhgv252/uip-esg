import { useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import {
  Box,
  Drawer,
  AppBar,
  Toolbar,
  Typography,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Avatar,
  Menu,
  MenuItem,
  Tooltip,
  useMediaQuery,
  useTheme,
  Divider,
} from '@mui/material'
import {
  Menu as MenuIcon,
  Dashboard as DashboardIcon,
  Air as EnvironmentIcon,
  NaturePeople as EsgIcon,
  Traffic as TrafficIcon,
  NotificationsActive as AlertsIcon,
  People as CitizenIcon,
  AdminPanelSettings as AdminIcon,
  Logout as LogoutIcon,
  ChevronLeft as ChevronLeftIcon,
} from '@mui/icons-material'
import LocationCityIcon from '@mui/icons-material/LocationCity'
import { useAuth } from '@/hooks/useAuth'

const DRAWER_WIDTH = 240
const DRAWER_COLLAPSED = 72

interface NavItem {
  label: string
  path: string
  icon: React.ReactNode
  roles?: string[]
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', path: '/dashboard', icon: <DashboardIcon /> },
  { label: 'Environment', path: '/environment', icon: <EnvironmentIcon /> },
  { label: 'ESG Metrics', path: '/esg', icon: <EsgIcon /> },
  { label: 'Traffic', path: '/traffic', icon: <TrafficIcon /> },
  { label: 'Alerts', path: '/alerts', icon: <AlertsIcon /> },
  { label: 'Citizens', path: '/citizen', icon: <CitizenIcon /> },
  {
    label: 'Admin',
    path: '/admin',
    icon: <AdminIcon />,
    roles: ['ROLE_ADMIN'],
  },
]

export default function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const muiTheme = useTheme()
  const isMobile = useMediaQuery(muiTheme.breakpoints.down('md'))

  const [mobileOpen, setMobileOpen] = useState(false)
  const [desktopCollapsed, setDesktopCollapsed] = useState(false)
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)

  const drawerWidth = desktopCollapsed && !isMobile ? DRAWER_COLLAPSED : DRAWER_WIDTH

  const handleNavClick = (path: string) => {
    navigate(path)
    if (isMobile) setMobileOpen(false)
  }

  const handleLogout = () => {
    setAnchorEl(null)
    logout()
    navigate('/login', { replace: true })
  }

  const sidebarSx = {
    backgroundColor: muiTheme.palette.sidebar.background,
    color: muiTheme.palette.sidebar.text,
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
  }

  const visibleItems = NAV_ITEMS.filter(
    (item) => !item.roles || (user && item.roles.includes(user.role)),
  )

  const drawerContent = (
    <Box sx={sidebarSx}>
      {/* Logo area */}
      <Toolbar
        sx={{
          justifyContent:
            desktopCollapsed && !isMobile ? 'center' : 'space-between',
          px: 2,
          minHeight: 64,
          backgroundColor: 'rgba(255,255,255,0.04)',
        }}
      >
        {(!desktopCollapsed || isMobile) && (
          <Box display="flex" alignItems="center" gap={1}>
            <LocationCityIcon sx={{ color: muiTheme.palette.primary.light }} />
            <Typography
              variant="subtitle1"
              noWrap
              sx={{ color: '#FFFFFF', fontWeight: 700 }}
            >
              UIP Smart City
            </Typography>
          </Box>
        )}
        {!isMobile && (
          <IconButton
            size="small"
            onClick={() => setDesktopCollapsed((p) => !p)}
            sx={{ color: muiTheme.palette.sidebar.text }}
          >
            {desktopCollapsed ? <MenuIcon /> : <ChevronLeftIcon />}
          </IconButton>
        )}
        {isMobile && (
          <IconButton
            size="small"
            onClick={() => setMobileOpen(false)}
            sx={{ color: muiTheme.palette.sidebar.text }}
          />
        )}
      </Toolbar>

      <Divider sx={{ borderColor: 'rgba(255,255,255,0.08)' }} />

      {/* Nav items */}
      <List sx={{ flex: 1, pt: 1 }}>
        {visibleItems.map((item) => {
          const isActive = location.pathname.startsWith(item.path)
          return (
            <ListItem key={item.path} disablePadding sx={{ display: 'block' }}>
              <Tooltip
                title={desktopCollapsed && !isMobile ? item.label : ''}
                placement="right"
              >
                <ListItemButton
                  onClick={() => handleNavClick(item.path)}
                  selected={isActive}
                  sx={{
                    minHeight: 48,
                    px: 2.5,
                    justifyContent:
                      desktopCollapsed && !isMobile ? 'center' : 'initial',
                    borderRadius: 1,
                    mx: 1,
                    mb: 0.5,
                    '&.Mui-selected': {
                      backgroundColor: muiTheme.palette.sidebar.activeBg,
                      '& .MuiListItemIcon-root': {
                        color: muiTheme.palette.sidebar.activeItem,
                      },
                      '& .MuiListItemText-primary': {
                        color: muiTheme.palette.sidebar.activeItem,
                        fontWeight: 700,
                      },
                    },
                    '&:hover': {
                      backgroundColor: muiTheme.palette.sidebar.hover,
                    },
                  }}
                >
                  <ListItemIcon
                    sx={{
                      minWidth: desktopCollapsed && !isMobile ? 'unset' : 40,
                      color: isActive
                        ? muiTheme.palette.sidebar.activeItem
                        : muiTheme.palette.sidebar.text,
                    }}
                  >
                    {item.icon}
                  </ListItemIcon>
                  {(!desktopCollapsed || isMobile) && (
                    <ListItemText
                      primary={item.label}
                      primaryTypographyProps={{ fontSize: 14 }}
                      sx={{
                        color: isActive
                          ? muiTheme.palette.sidebar.activeItem
                          : muiTheme.palette.sidebar.text,
                      }}
                    />
                  )}
                </ListItemButton>
              </Tooltip>
            </ListItem>
          )
        })}
      </List>

      {/* User info at bottom */}
      <Divider sx={{ borderColor: 'rgba(255,255,255,0.08)' }} />
      <Box
        sx={{
          p: 2,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          justifyContent:
            desktopCollapsed && !isMobile ? 'center' : 'flex-start',
        }}
      >
        <Avatar sx={{ width: 32, height: 32, bgcolor: muiTheme.palette.primary.main, fontSize: 14 }}>
          {user?.username?.[0]?.toUpperCase() ?? 'U'}
        </Avatar>
        {(!desktopCollapsed || isMobile) && (
          <Box flex={1} overflow="hidden">
            <Typography
              variant="body2"
              noWrap
              sx={{ color: '#FFFFFF', fontWeight: 600 }}
            >
              {user?.username}
            </Typography>
            <Typography variant="caption" sx={{ color: muiTheme.palette.sidebar.text }}>
              {user?.role.replace('ROLE_', '')}
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* Top AppBar (mobile only shows hamburger) */}
      <AppBar
        position="fixed"
        sx={{
          width: { md: `calc(100% - ${drawerWidth}px)` },
          ml: { md: `${drawerWidth}px` },
          bgcolor: '#FFFFFF',
          color: 'text.primary',
          transition: muiTheme.transitions.create(['width', 'margin'], {
            easing: muiTheme.transitions.easing.sharp,
            duration: muiTheme.transitions.duration.leavingScreen,
          }),
        }}
      >
        <Toolbar>
          {isMobile && (
            <IconButton
              color="inherit"
              edge="start"
              onClick={() => setMobileOpen(true)}
              sx={{ mr: 2 }}
            >
              <MenuIcon />
            </IconButton>
          )}
          <Typography variant="h6" noWrap sx={{ flexGrow: 1 }}>
            UIP Smart City Platform
          </Typography>

          {/* User avatar menu */}
          <Tooltip title="Account">
            <IconButton onClick={(e) => setAnchorEl(e.currentTarget)} size="small">
              <Avatar
                sx={{
                  width: 36,
                  height: 36,
                  bgcolor: muiTheme.palette.primary.main,
                  fontSize: 14,
                }}
              >
                {user?.username?.[0]?.toUpperCase() ?? 'U'}
              </Avatar>
            </IconButton>
          </Tooltip>
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={() => setAnchorEl(null)}
            transformOrigin={{ horizontal: 'right', vertical: 'top' }}
            anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
          >
            <MenuItem disabled>
              <Typography variant="body2">{user?.username}</Typography>
            </MenuItem>
            <Divider />
            <MenuItem onClick={handleLogout}>
              <ListItemIcon>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              Logout
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      {/* Sidebar — mobile drawer */}
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={() => setMobileOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{
          display: { xs: 'block', md: 'none' },
          '& .MuiDrawer-paper': {
            boxSizing: 'border-box',
            width: DRAWER_WIDTH,
          },
        }}
      >
        {drawerContent}
      </Drawer>

      {/* Sidebar — desktop permanent drawer */}
      <Drawer
        variant="permanent"
        sx={{
          display: { xs: 'none', md: 'block' },
          width: drawerWidth,
          flexShrink: 0,
          transition: muiTheme.transitions.create('width', {
            easing: muiTheme.transitions.easing.sharp,
            duration: muiTheme.transitions.duration.leavingScreen,
          }),
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
            transition: muiTheme.transitions.create('width', {
              easing: muiTheme.transitions.easing.sharp,
              duration: muiTheme.transitions.duration.leavingScreen,
            }),
            overflowX: 'hidden',
          },
        }}
      >
        {drawerContent}
      </Drawer>

      {/* Main content */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          bgcolor: 'background.default',
          p: 3,
          mt: '64px',
          minHeight: 'calc(100vh - 64px)',
          transition: muiTheme.transitions.create('margin', {
            easing: muiTheme.transitions.easing.sharp,
            duration: muiTheme.transitions.duration.leavingScreen,
          }),
        }}
      >
        <Outlet />
      </Box>
    </Box>
  )
}
