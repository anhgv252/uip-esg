import { Page } from '@playwright/test'

async function loginWithRetry(page: Page, username: string, password: string) {
  const response = await page.request.post('/api/v1/auth/login', {
    data: { username, password },
  })

  if (!response.ok()) {
    throw new Error(`Login failed for ${username}: HTTP ${response.status()}`)
  }

  const auth = await response.json() as { accessToken: string; refreshToken: string }

  await page.context().addInitScript(
    ({ accessToken, refreshToken }) => {
      window.localStorage.setItem('uip_access_token', accessToken)
      window.localStorage.setItem('uip_refresh_token', refreshToken)
    },
    { accessToken: auth.accessToken, refreshToken: auth.refreshToken },
  )

  await page.goto('/')
  await page.waitForLoadState('domcontentloaded')
}

/**
 * Logs in as admin user and waits for dashboard to load
 */
export async function loginAsAdmin(page: Page) {
  await loginWithRetry(page, 'admin', 'admin_Dev#2026!')
}

/**
 * Logs in as operator user (if needed for role-specific tests)
 */
export async function loginAsOperator(page: Page) {
  await loginWithRetry(page, 'operator', 'operator_Dev#2026!')
}

/**
 * Logs in as TENANT_ADMIN for the HCM tenant.
 * Required for Sprint 5 MVP2-13 Tenant Admin Dashboard tests.
 */
export async function loginAsTenantAdmin(page: Page) {
  await loginWithRetry(page, 'tadmin', 'admin_Dev#2026!')
}

/**
 * Logs in as a citizen user (HCM tenant).
 * Required for Sprint 5 MVP2-12 PWA / Mobile tests.
 */
export async function loginAsCitizen(page: Page) {
  await loginWithRetry(page, 'citizen', 'citizen_Dev#2026!')
  // Navigate to citizen home so MobileLayout with bottom nav is visible
  await page.goto('/citizen')
  await page.waitForURL('**/citizen', { timeout: 15000 })
}

/**
 * Navigate to a page via sidebar click (SPA navigation — preserves in-memory auth token).
 * Use this instead of page.goto() when already logged in.
 */
export async function navigateTo(page: Page, sidebarLabel: string) {
  const routeMap: Record<string, string> = {
    'tenant admin': '/tenant-admin',
    users: '/tenant-admin/users',
    usage: '/tenant-admin/usage',
    settings: '/tenant-admin/settings',
    alerts: '/alerts',
    traffic: '/traffic',
    environment: '/environment',
    'esg metrics': '/esg',
    esg: '/esg',
    dashboard: '/dashboard',
    'city ops': '/city-ops',
    'ai workflow': '/ai-workflow',
    'ai workflows': '/ai-workflow',
    'trigger config': '/workflow-config',
    'workflow config': '/workflow-config',
    admin: '/admin',
    citizens: '/citizen',
    citizen: '/citizen',
  }

  const button = page.getByRole('button', { name: new RegExp(sidebarLabel, 'i') }).first()
  if (await button.isVisible().catch(() => false)) {
    await button.click()
  } else {
    const key = sidebarLabel.trim().toLowerCase()
    const fallbackPath = routeMap[key]
    if (fallbackPath) {
      await page.goto(fallbackPath)
    }
  }
  // Wait for network to be idle so API data is loaded before test assertions run
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {})
}
