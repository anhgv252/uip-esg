/**
 * Maps feature-flag keys to the navigation paths they control.
 * Used by feature-gating logic to hide/show nav items and routes.
 * Paths must match NAV_ITEMS in AppShell.tsx.
 */
export const FEATURE_NAV_MAP: Record<string, string[]> = {
  'city-ops': ['/city-ops'],
  'environment-module': ['/environment'],
  'esg-module': ['/esg'],
  'traffic-module': ['/traffic'],
  'citizen-portal': ['/citizen'],
  'ai-workflow': ['/ai-workflow', '/workflow-config'],
  'tenant_management': ['/tenant-admin'],
}
