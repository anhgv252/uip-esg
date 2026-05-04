export interface BasePalette {
  primary: { main: string; light: string; dark: string; contrastText: string }
  secondary: { main: string; light: string; dark: string }
  error: { main: string }
  warning: { main: string }
  success: { main: string }
  background: { default: string; paper: string }
  sidebar: {
    background: string
    text: string
    activeItem: string
    activeBg: string
    hover: string
  }
}

export const defaultPalette: BasePalette = {
  primary: {
    main: '#1976D2',
    light: '#42A5F5',
    dark: '#1565C0',
    contrastText: '#FFFFFF',
  },
  secondary: {
    main: '#009688',
    light: '#4DB6AC',
    dark: '#00796B',
  },
  error: { main: '#D32F2F' },
  warning: { main: '#ED6C02' },
  success: { main: '#2E7D32' },
  background: {
    default: '#F5F7FA',
    paper: '#FFFFFF',
  },
  sidebar: {
    background: '#0A1929',
    text: '#B2BAC2',
    activeItem: '#FFFFFF',
    activeBg: 'rgba(25, 118, 210, 0.25)',
    hover: 'rgba(255, 255, 255, 0.06)',
  },
}

export const baseTypography = {
  fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
  h4: { fontWeight: 700 },
  h5: { fontWeight: 600 },
  h6: { fontWeight: 600 },
  subtitle1: { fontWeight: 500 },
}

export const baseShape = { borderRadius: 8 }

export const baseComponents = {
  MuiButton: {
    styleOverrides: {
      root: { textTransform: 'none', fontWeight: 600 },
    },
  },
  MuiCard: {
    styleOverrides: {
      root: { boxShadow: '0 1px 4px rgba(0,0,0,0.08)' },
    },
  },
  MuiAppBar: {
    styleOverrides: {
      root: { boxShadow: '0 1px 3px rgba(0,0,0,0.12)' },
    },
  },
}
