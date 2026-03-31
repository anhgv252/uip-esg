import { createTheme, alpha } from '@mui/material/styles'

declare module '@mui/material/styles' {
  interface Palette {
    sidebar: {
      background: string
      text: string
      activeItem: string
      activeBg: string
      hover: string
    }
  }
  interface PaletteOptions {
    sidebar?: {
      background: string
      text: string
      activeItem: string
      activeBg: string
      hover: string
    }
  }
}

const SIDEBAR_BG = '#0A1929'
const SIDEBAR_TEXT = '#B2BAC2'
const PRIMARY = '#1976D2'

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: PRIMARY,
      light: '#42A5F5',
      dark: '#1565C0',
      contrastText: '#FFFFFF',
    },
    secondary: {
      main: '#009688',
      light: '#4DB6AC',
      dark: '#00796B',
    },
    error: {
      main: '#D32F2F',
    },
    warning: {
      main: '#ED6C02',
    },
    success: {
      main: '#2E7D32',
    },
    background: {
      default: '#F5F7FA',
      paper: '#FFFFFF',
    },
    sidebar: {
      background: SIDEBAR_BG,
      text: SIDEBAR_TEXT,
      activeItem: '#FFFFFF',
      activeBg: alpha(PRIMARY, 0.25),
      hover: alpha('#FFFFFF', 0.06),
    },
  },

  typography: {
    fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
    h4: { fontWeight: 700 },
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
    subtitle1: { fontWeight: 500 },
  },

  shape: {
    borderRadius: 8,
  },

  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 600,
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          boxShadow: '0 1px 3px rgba(0,0,0,0.12)',
        },
      },
    },
  },
})
