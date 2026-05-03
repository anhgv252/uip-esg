import { describe, it, expect } from 'vitest'
import type { UserRole } from '@/contexts/AuthContext'

interface NavItem {
  label: string
  path: string
  roles?: string[]
  featureFlag?: string
  scope?: string
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', path: '/dashboard' },
  { label: 'City Ops', path: '/city-ops' },
  { label: 'Environment', path: '/environment' },
  { label: 'ESG Metrics', path: '/esg' },
  { label: 'Traffic', path: '/traffic' },
  { label: 'Alerts', path: '/alerts' },
  { label: 'Citizens', path: '/citizen' },
  { label: 'AI Workflows', path: '/ai-workflow', roles: ['ROLE_ADMIN', 'ROLE_OPERATOR'] },
  { label: 'Trigger Config', path: '/workflow-config', roles: ['ROLE_ADMIN'] },
  { label: 'Admin', path: '/admin', roles: ['ROLE_ADMIN'] },
  { label: 'Tenant Admin', path: '/tenant-admin', roles: ['ROLE_TENANT_ADMIN'], featureFlag: 'tenant_management' },
  { label: 'Feature X', path: '/feature-x', featureFlag: 'feature_x' },
]

function filterNavItems(
  items: NavItem[],
  userRole: UserRole | null,
  isFeatureEnabled: (flag: string) => boolean,
): NavItem[] {
  const user = userRole ? { role: userRole } : null
  return items.filter((item) => {
    if (item.roles && (!user || !item.roles.includes(user.role))) return false
    if (item.featureFlag && !isFeatureEnabled(item.featureFlag)) return false
    return true
  })
}

describe('Nav filtering logic', () => {
  const allFlagsEnabled = (_flag: string) => true
  const allFlagsDisabled = (_flag: string) => false
  const featureXEnabled = (flag: string) => flag === 'feature_x'
  const tenantMgmtEnabled = (flag: string) => flag === 'tenant_management'

  it('shows public items when no user (not authenticated)', () => {
    const visible = filterNavItems(NAV_ITEMS, null, allFlagsEnabled)
    const labels = visible.map((i) => i.label)
    expect(labels).toContain('Dashboard')
    expect(labels).toContain('Environment')
    expect(labels).not.toContain('AI Workflows')
    expect(labels).not.toContain('Admin')
    expect(labels).not.toContain('Tenant Admin')
  })

  it('shows all items for ROLE_ADMIN when all flags enabled', () => {
    const visible = filterNavItems(NAV_ITEMS, 'ROLE_ADMIN', allFlagsEnabled)
    const labels = visible.map((i) => i.label)
    expect(labels).toContain('Dashboard')
    expect(labels).toContain('AI Workflows')
    expect(labels).toContain('Trigger Config')
    expect(labels).toContain('Admin')
    expect(labels).not.toContain('Tenant Admin') // ROLE_ADMIN not in roles for Tenant Admin
  })

  it('shows AI Workflows for ROLE_OPERATOR', () => {
    const visible = filterNavItems(NAV_ITEMS, 'ROLE_OPERATOR', allFlagsEnabled)
    const labels = visible.map((i) => i.label)
    expect(labels).toContain('AI Workflows')
    expect(labels).not.toContain('Admin')
    expect(labels).not.toContain('Trigger Config')
  })

  it('hides AI Workflows from ROLE_CITIZEN', () => {
    const visible = filterNavItems(NAV_ITEMS, 'ROLE_CITIZEN', allFlagsEnabled)
    const labels = visible.map((i) => i.label)
    expect(labels).not.toContain('AI Workflows')
    expect(labels).not.toContain('Admin')
    expect(labels).toContain('Dashboard')
    expect(labels).toContain('Environment')
  })

  it('shows Tenant Admin for ROLE_TENANT_ADMIN when feature flag enabled', () => {
    const visible = filterNavItems(NAV_ITEMS, 'ROLE_TENANT_ADMIN', tenantMgmtEnabled)
    const labels = visible.map((i) => i.label)
    expect(labels).toContain('Tenant Admin')
  })

  it('hides Tenant Admin for ROLE_TENANT_ADMIN when feature flag disabled', () => {
    const visible = filterNavItems(NAV_ITEMS, 'ROLE_TENANT_ADMIN', allFlagsDisabled)
    const labels = visible.map((i) => i.label)
    expect(labels).not.toContain('Tenant Admin')
  })

  it('hides feature-flagged items when flag disabled but role matches', () => {
    const visible = filterNavItems(NAV_ITEMS, 'ROLE_TENANT_ADMIN', (_f) => false)
    const labels = visible.map((i) => i.label)
    // Tenant Admin has both role + flag requirement; flag disabled → hidden
    expect(labels).not.toContain('Tenant Admin')
  })

  it('shows feature-flagged items when flag enabled and no role restriction', () => {
    const visible = filterNavItems(NAV_ITEMS, 'ROLE_CITIZEN', featureXEnabled)
    const labels = visible.map((i) => i.label)
    expect(labels).toContain('Feature X')
  })

  it('hides feature-flagged items when flag disabled and no role restriction', () => {
    const visible = filterNavItems(NAV_ITEMS, 'ROLE_CITIZEN', allFlagsDisabled)
    const labels = visible.map((i) => i.label)
    expect(labels).not.toContain('Feature X')
  })

  it('admin sees everything except Tenant Admin (wrong role)', () => {
    const visible = filterNavItems(NAV_ITEMS, 'ROLE_ADMIN', allFlagsEnabled)
    expect(visible.length).toBe(NAV_ITEMS.length - 1) // Tenant Admin hidden
  })

  it('ROLE_TENANT_ADMIN sees public items + Tenant Admin when flag enabled', () => {
    const visible = filterNavItems(NAV_ITEMS, 'ROLE_TENANT_ADMIN', tenantMgmtEnabled)
    const labels = visible.map((i) => i.label)
    expect(labels).toContain('Dashboard')
    expect(labels).toContain('Tenant Admin')
    expect(labels).not.toContain('Admin')
    expect(labels).not.toContain('AI Workflows')
  })
})
