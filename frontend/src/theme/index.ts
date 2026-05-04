import { createTheme, alpha, lighten, darken } from '@mui/material/styles'
import { defaultPalette, baseTypography, baseShape, baseComponents } from './baseTheme'
import { meetsWcagAA } from './contrastCheck'
import { type PartnerThemeConfig } from '@/types/tenant'

export type { PartnerThemeConfig }

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


export function createPartnerTheme(config?: PartnerThemeConfig) {
  const primary = config?.primaryColor ?? defaultPalette.primary.main
  const sidebarBg = config?.sidebarBg ?? defaultPalette.sidebar.background

  // WCAG AA contrast check — warn in console if failing
  if (config?.primaryColor) {
    if (!meetsWcagAA(primary, '#FFFFFF')) {
      console.warn(
        `[UIP Theme] primaryColor "${primary}" fails WCAG AA contrast against white (buttons). ` +
        'Consider choosing a darker shade.',
      )
    }
    if (!meetsWcagAA('#FFFFFF', sidebarBg, true)) {
      console.warn(
        `[UIP Theme] sidebarBg "${sidebarBg}" fails WCAG AA contrast for sidebar text. ` +
        'Consider choosing a darker background.',
      )
    }
  }

  return createTheme({
    palette: {
      mode: 'light',
      primary: {
        main: primary,
        light: lighten(primary, 0.3),
        dark: darken(primary, 0.2),
        contrastText: '#FFFFFF',
      },
      secondary: config?.secondaryColor
        ? { main: config.secondaryColor, light: lighten(config.secondaryColor, 0.3), dark: darken(config.secondaryColor, 0.2) }
        : defaultPalette.secondary,
      error: defaultPalette.error,
      warning: defaultPalette.warning,
      success: defaultPalette.success,
      background: defaultPalette.background,
      sidebar: {
        background: sidebarBg,
        text: defaultPalette.sidebar.text,
        activeItem: defaultPalette.sidebar.activeItem,
        activeBg: alpha(primary, 0.25),
        hover: alpha('#FFFFFF', 0.06),
      },
    },
    typography: baseTypography,
    shape: baseShape,
    components: baseComponents,
  })
}

// Default theme — backward compatible
export const theme = createPartnerTheme()
