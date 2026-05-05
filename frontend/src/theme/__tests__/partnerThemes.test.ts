import { describe, it, expect } from 'vitest'
import { createPartnerTheme } from '../index'
import { energyOptimizerThemeConfig } from '../partnerThemes/energy-optimizer.theme'
import { citizenFirstThemeConfig } from '../partnerThemes/citizen-first.theme'
import { defaultThemeConfig } from '../partnerThemes/default.theme'
import type { PartnerThemeConfig } from '@/types/tenant'

describe('createPartnerTheme', () => {
  it('produces the default theme when no config is provided', () => {
    const theme = createPartnerTheme()
    expect(theme.palette.primary.main).toBe('#1976D2')
  })

  it('applies the default config primary color', () => {
    const theme = createPartnerTheme(defaultThemeConfig)
    expect(theme.palette.primary.main).toBe('#1976D2')
  })

  it('applies Energy Optimizer primary color', () => {
    const theme = createPartnerTheme(energyOptimizerThemeConfig)
    expect(theme.palette.primary.main).toBe('#2E7D32')
  })

  it('applies Energy Optimizer sidebar background', () => {
    const theme = createPartnerTheme(energyOptimizerThemeConfig)
    expect(theme.palette.sidebar.background).toBe('#1B3A1D')
  })

  it('applies Citizen First primary color', () => {
    const theme = createPartnerTheme(citizenFirstThemeConfig)
    expect(theme.palette.primary.main).toBe('#E65100')
  })

  it('applies Citizen First sidebar background', () => {
    const theme = createPartnerTheme(citizenFirstThemeConfig)
    expect(theme.palette.sidebar.background).toBe('#2A1506')
  })

  it('uses default sidebar background when sidebarBg is omitted', () => {
    const config: PartnerThemeConfig = { primaryColor: '#7B1FA2' }
    const theme = createPartnerTheme(config)
    expect(theme.palette.sidebar.background).toBe('#0A1929')
  })

  it('applies secondary color when provided', () => {
    const config: PartnerThemeConfig = {
      primaryColor: '#1976D2',
      secondaryColor: '#FF5722',
    }
    const theme = createPartnerTheme(config)
    expect(theme.palette.secondary.main).toBe('#FF5722')
  })

  it('always sets light and dark variants for primary', () => {
    const theme = createPartnerTheme(energyOptimizerThemeConfig)
    expect(theme.palette.primary.light).toBeTruthy()
    expect(theme.palette.primary.dark).toBeTruthy()
  })
})
