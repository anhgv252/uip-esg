// Centralized tenant-related types — single source of truth across AuthContext,
// TenantConfigContext, theme, and route guards.

export type UserRole =
  | 'ROLE_ADMIN'
  | 'ROLE_OPERATOR'
  | 'ROLE_CITIZEN'
  | 'ROLE_TENANT_ADMIN'

export interface AuthUser {
  username: string
  role: UserRole
  tenantId: string
  tenantPath: string
  scopes: string[]
  allowedBuildings: string[]
}

export interface FeatureFlag {
  enabled: boolean
}

export interface TenantBranding {
  partnerName: string
  primaryColor: string
  logoUrl: string | null
}

export interface TenantConfig {
  tenantId: string
  features: Record<string, FeatureFlag>
  branding: TenantBranding
}

export interface PartnerThemeConfig {
  primaryColor?: string
  secondaryColor?: string
  sidebarBg?: string
  partnerLogoUrl?: string
  partnerName?: string
}
