import { useLocation, useNavigate } from 'react-router-dom'
import { BottomNavigation, BottomNavigationAction, Paper } from '@mui/material'
import HomeIcon from '@mui/icons-material/Home'
import ReceiptIcon from '@mui/icons-material/Receipt'
import AirIcon from '@mui/icons-material/Air'
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive'
import PersonIcon from '@mui/icons-material/Person'

const TABS = [
  { label: 'Home', path: '/citizen', icon: <HomeIcon /> },
  { label: 'Bills', path: '/citizen/bills', icon: <ReceiptIcon /> },
  { label: 'AQI', path: '/citizen/aqi', icon: <AirIcon /> },
  { label: 'Alerts', path: '/citizen/alerts', icon: <NotificationsActiveIcon /> },
  { label: 'Profile', path: '/citizen/profile', icon: <PersonIcon /> },
]

export default function MobileNav() {
  const location = useLocation()
  const navigate = useNavigate()

  const currentTab = TABS.findIndex((t) =>
    t.path === '/citizen'
      ? location.pathname === '/citizen'
      : location.pathname.startsWith(t.path)
  )

  return (
    <Paper
      sx={{ position: 'sticky', bottom: 0, left: 0, right: 0, zIndex: 1100 }}
      elevation={3}
    >
      <BottomNavigation
        data-testid="bottom-nav"
        value={currentTab >= 0 ? currentTab : 0}
        onChange={(_, newValue: number) => navigate(TABS[newValue].path)}
        showLabels
      >
        {TABS.map((tab) => (
          <BottomNavigationAction key={tab.path} label={tab.label} icon={tab.icon} />
        ))}
      </BottomNavigation>
    </Paper>
  )
}
