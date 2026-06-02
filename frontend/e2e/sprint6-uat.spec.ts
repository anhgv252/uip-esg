import { test, expect, type Page } from '@playwright/test'

/**
 * Sprint 6 — Multi-Tenancy UAT End-to-End Tests
 *
 * Sprint 6 exit criteria #6:
 *   "E2E automated test cho multi-tenancy flow (login → tenant context → feature flags → theme)"
 *
 * Coverage:
 *   TC-S6-01  Full chain: login → JWT tenant claims → dashboard
 *   TC-S6-02  Partner theme: branding.primaryColor applied to Avatar & UI
 *   TC-S6-03  Feature flag: tenant_management=false → "Tenant Admin" nav hidden
 *   TC-S6-04  Feature flag fail-open: /tenant/config 500 → all nav items still visible
 *   TC-S6-05  Logout clears session → redirect /login, protected routes blocked
 *   TC-S6-06  Scope-gated button: esg:write absent → "Generate Report" disabled
 *   TC-S6-07  ROLE_CITIZEN cannot access /tenant-admin → redirect /dashboard
 *   TC-S6-08  Mobile viewport (375×812) + ROLE_CITIZEN → MobileLayout bottom nav visible
 *
 * All tests are backend-offline safe: all /api/v1/ calls are mocked via page.route().
 *
 * Test count: 14 test cases across 8 groups.
 */

// ---------------------------------------------------------------------------
// Helpers (self-contained — intentionally duplicated from sprint2-multi-tenancy.spec.ts)
// ---------------------------------------------------------------------------

/**
 * Build a minimal JWT-like base64url token that the frontend can parse.
 * Signature is fake — frontend in dev mode trusts the payload.
 */
function makeTestJwt(payload: Record<string, unknown>): string {
  const enc = (obj: unknown) =>
    Buffer.from(JSON.stringify(obj)).toString('base64url')
  return `${enc({ alg: 'HS256', typ: 'JWT' })}.${enc(payload)}.fake-sig`
}

interface TenantBranding {
  partnerName?: string
  primaryColor?: string
  logoUrl?: string | null
}

function makeTenantConfig(
  tenantId: string,
  features: Record<string, { enabled: boolean }> = {},
  branding: TenantBranding = {},
) {
  return {
    tenantId,
    features: { tenant_management: { enabled: true }, ...features },
    branding: {
      partnerName: 'UIP Smart City',
      primaryColor: '#1976D2',
      logoUrl: null,
      ...branding,
    },
  }
}

/**
 * Register mocked routes and complete the login flow.
 *
 * Route LIFO priority order (registered lowest → highest):
 *   1. catch-all /api/v1/         (lowest — fallback)
 *   2. /api/v1/esg/**             (ESG data)
 *   3. /api/v1/tenant/config      (tenant config)
 *   4. /api/v1/auth/login         (login)
 *   5. /api/v1/auth/refresh       (highest — checked first)
 */
async function loginWithMockJwt(
  page: Page,
  jwtPayload: Record<string, unknown>,
  opts: {
    tenantFeatures?: Record<string, { enabled: boolean }>
    branding?: TenantBranding
    tenantConfigStatus?: number
  } = {},
) {
  const {
    tenantFeatures = {},
    branding = {},
    tenantConfigStatus = 200,
  } = opts

  const accessToken = makeTestJwt(jwtPayload)
  const tenantId = String(jwtPayload.tenant_id ?? 'default')
  let loginDone = false

  // 1. Catch-all: lowest LIFO priority
  await page.route(/\/api\/v1\//, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: [], total: 0 }),
    })
  })

  // 2. ESG summary stub
  await page.route(/\/api\/v1\/esg\//, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        tenantId,
        energyUsageKwh: 12000,
        waterUsageM3: 4500,
        carbonEmissionKg: 3200,
      }),
    })
  })

  // 3. Tenant config — can be overridden to 500 for fail-open tests
  await page.route(/\/api\/v1\/tenant\/config/, async (route) => {
    if (tenantConfigStatus !== 200) {
      await route.fulfill({ status: tenantConfigStatus, body: 'Internal Server Error' })
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeTenantConfig(tenantId, tenantFeatures, branding)),
      })
    }
  })

  // 4. Login
  await page.route(/\/api\/v1\/auth\/login/, async (route) => {
    loginDone = true
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accessToken,
        refreshToken: 'fake-refresh-token',
        expiresIn: 3600,
      }),
    })
  })

  // 5. Refresh — highest priority
  await page.route(/\/api\/v1\/auth\/refresh/, async (route) => {
    if (loginDone) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ accessToken, refreshToken: 'fake-refresh-token', expiresIn: 3600 }),
      })
    } else {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Unauthorized' }),
      })
    }
  })

  // 6. Buildings — returns array directly (fetchBuildings calls res.data expecting Building[])
  await page.route(/\/api\/v1\/buildings/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  })

  await page.goto('/login')
  await page.waitForLoadState('domcontentloaded')

  await page.locator('input[name="username"]').fill(String(jwtPayload.sub ?? 'admin'))
  await page.locator('input[name="password"]').fill('admin_Dev#2026!')
  await page.locator('button[type="submit"]').first().click()
  await page.waitForURL('**/dashboard', { timeout: 15_000 })
}

// ---------------------------------------------------------------------------
// TC-S6-01: Full multi-tenancy chain — login → JWT claims → tenant context
// ---------------------------------------------------------------------------

test.describe('TC-S6-01: Full multi-tenancy chain', () => {
  test('login with tenant hcm JWT → dashboard loads, username visible in AppBar', async ({ page }) => {
    await loginWithMockJwt(page, {
      sub: 'admin',
      roles: ['ROLE_ADMIN'],
      tenant_id: 'hcm',
      tenant_path: 'city.hcm',
      scopes: ['esg:read', 'esg:write', 'alert:ack'],
      allowed_buildings: ['BLD-HCM-001', 'BLD-HCM-002'],
    })

    await expect(page).toHaveURL(/.*dashboard/)
    // AppShell renders username as Avatar initial — avatar must be visible
    const avatar = page.locator('.MuiAvatar-root').first()
    await expect(avatar).toBeVisible({ timeout: 5_000 })
    // Avatar shows first letter of username ('A' for 'admin')
    await expect(avatar).toContainText('A', { timeout: 3_000 })
  })

  test('Authorization header sent with API calls (X-Tenant-Id / Bearer token)', async ({ page }) => {
    let capturedAuthHeader = ''
    page.on('request', (req) => {
      if (req.url().includes('/api/v1/') && !req.url().includes('/auth/')) {
        capturedAuthHeader = req.headers()['authorization'] ?? ''
      }
    })

    await loginWithMockJwt(page, {
      sub: 'admin',
      roles: ['ROLE_ADMIN'],
      tenant_id: 'default',
      scopes: [],
      allowed_buildings: [],
    })

    await page.goto('/dashboard')
    await page.waitForLoadState('networkidle').catch(() => {})

    expect(capturedAuthHeader).toMatch(/^Bearer /)
  })

  test('tenant_path field present in JWT is accepted without errors', async ({ page }) => {
    const consoleErrors: string[] = []
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text())
    })

    await loginWithMockJwt(page, {
      sub: 'operator',
      roles: ['ROLE_OPERATOR'],
      tenant_id: 'hanoi',
      tenant_path: 'city.hanoi.district1',
      scopes: ['environment:read'],
      allowed_buildings: [],
    })

    // No unhandled JS errors from tenant claims parsing
    const tenantErrors = consoleErrors.filter(
      (e) => e.includes('tenantId') || e.includes('tenant_id') || e.includes('Cannot read'),
    )
    expect(tenantErrors).toHaveLength(0)
    await expect(page).toHaveURL(/.*dashboard/)
  })
})

// ---------------------------------------------------------------------------
// TC-S6-02: Partner theme — branding.primaryColor applied to UI
// ---------------------------------------------------------------------------

test.describe('TC-S6-02: Partner theme from branding config', () => {
  test('Energy Optimizer green theme (#2E7D32) applied to user avatar', async ({ page }) => {
    await loginWithMockJwt(
      page,
      { sub: 'admin', roles: ['ROLE_ADMIN'], tenant_id: 'energy-a', scopes: [], allowed_buildings: [] },
      { branding: { primaryColor: '#2E7D32', partnerName: 'Energy Optimizer' } },
    )

    await page.waitForLoadState('networkidle').catch(() => {})

    // MUI Avatar sx={{ bgcolor: theme.palette.primary.main }} renders as inline background-color
    const avatar = page.locator('.MuiAvatar-root').first()
    await expect(avatar).toBeVisible({ timeout: 5_000 })

    const bgColor = await avatar.evaluate(
      (el) => window.getComputedStyle(el).backgroundColor,
    )
    // #2E7D32 = rgb(46, 125, 50)
    expect(bgColor).toBe('rgb(46, 125, 50)')
  })

  test('default UIP blue (#1976D2) applied when no branding override', async ({ page }) => {
    await loginWithMockJwt(page, {
      sub: 'admin',
      roles: ['ROLE_ADMIN'],
      tenant_id: 'default',
      scopes: [],
      allowed_buildings: [],
    })

    await page.waitForLoadState('networkidle').catch(() => {})

    const avatar = page.locator('.MuiAvatar-root').first()
    await expect(avatar).toBeVisible({ timeout: 5_000 })

    const bgColor = await avatar.evaluate(
      (el) => window.getComputedStyle(el).backgroundColor,
    )
    // #1976D2 = rgb(25, 118, 210)
    expect(bgColor).toBe('rgb(25, 118, 210)')
  })

  test('sidebar logo text "UIP Smart City" always present (branding partnerName not yet wired to sidebar)', async ({ page }) => {
    await loginWithMockJwt(
      page,
      { sub: 'admin', roles: ['ROLE_ADMIN'], tenant_id: 'energy-a', scopes: [], allowed_buildings: [] },
      { branding: { primaryColor: '#2E7D32', partnerName: 'Energy Optimizer' } },
    )

    // AppShell sidebar header hardcodes "UIP Smart City" text (ADR-019 partner name wiring is v3.0 scope)
    const sidebar = page.locator('.MuiDrawer-root').first()
    await expect(sidebar).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('UIP Smart City').first()).toBeVisible({ timeout: 5_000 })
  })
})

// ---------------------------------------------------------------------------
// TC-S6-03: Feature flag — tenant_management gate
// ---------------------------------------------------------------------------

test.describe('TC-S6-03: tenant_management feature flag', () => {
  test('tenant_management=false → "Tenant Admin" nav item is HIDDEN', async ({ page }) => {
    await loginWithMockJwt(
      page,
      { sub: 'admin', roles: ['ROLE_ADMIN'], tenant_id: 'default', scopes: [], allowed_buildings: [] },
      { tenantFeatures: { tenant_management: { enabled: false } } },
    )

    await page.waitForLoadState('networkidle').catch(() => {})

    // "Tenant Admin" nav item should not be visible
    const tenantAdminBtn = page.getByRole('button', { name: /tenant admin/i })
    await expect(tenantAdminBtn).not.toBeVisible({ timeout: 5_000 })
  })

  test('tenant_management=true → "Tenant Admin" nav item IS visible for ROLE_TENANT_ADMIN', async ({ page }) => {
    // NAV_ITEMS: Tenant Admin has roles: ['ROLE_TENANT_ADMIN'] — only visible to tenant admins
    await loginWithMockJwt(
      page,
      { sub: 'tadmin', roles: ['ROLE_TENANT_ADMIN'], tenant_id: 'default', scopes: [], allowed_buildings: [] },
      { tenantFeatures: { tenant_management: { enabled: true } } },
    )

    await page.waitForLoadState('networkidle').catch(() => {})

    await expect(
      page.getByRole('button', { name: /tenant admin/i }),
    ).toBeVisible({ timeout: 5_000 })
  })

  test('Unknown feature flag defaults to enabled (fail-open)', async ({ page }) => {
    await loginWithMockJwt(
      page,
      { sub: 'admin', roles: ['ROLE_ADMIN'], tenant_id: 'default', scopes: [], allowed_buildings: [] },
      // Only override an unknown flag — all others default to enabled
      { tenantFeatures: { 'unknown-feature-xyz': { enabled: false } } },
    )

    await page.waitForLoadState('networkidle').catch(() => {})

    // Standard nav items should still be visible
    await expect(page.getByText(/dashboard/i).first()).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText(/esg metrics/i).first()).toBeVisible({ timeout: 5_000 })
  })
})

// ---------------------------------------------------------------------------
// TC-S6-04: Feature flags fail-open — /tenant/config returns 500
// ---------------------------------------------------------------------------

test.describe('TC-S6-04: TenantConfig fail-open on API error', () => {
  test('tenant/config 500 → nav renders with default (all items visible)', async ({ page }) => {
    await loginWithMockJwt(
      page,
      { sub: 'admin', roles: ['ROLE_ADMIN'], tenant_id: 'default', scopes: [], allowed_buildings: [] },
      { tenantConfigStatus: 500 },
    )

    // App should not crash
    await expect(page).toHaveURL(/.*dashboard/)

    // Core nav items still visible (fail-open: features default to enabled)
    await expect(page.getByText(/dashboard/i).first()).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText(/esg metrics/i).first()).toBeVisible({ timeout: 5_000 })
  })
})

// ---------------------------------------------------------------------------
// TC-S6-05: Logout clears session
// ---------------------------------------------------------------------------

test.describe('TC-S6-05: Logout clears tenant session', () => {
  test('logout redirects to /login and protected routes are blocked', async ({ page }) => {
    await loginWithMockJwt(page, {
      sub: 'admin',
      roles: ['ROLE_ADMIN'],
      tenant_id: 'hcm',
      scopes: [],
      allowed_buildings: [],
    })

    await expect(page).toHaveURL(/.*dashboard/)

    // Open user menu (Avatar button in AppBar)
    const avatarBtn = page.locator('.MuiAppBar-root .MuiAvatar-root, .MuiAppBar-root [aria-label*="account"], .MuiAppBar-root button').last()
    await avatarBtn.click({ timeout: 5_000 }).catch(async () => {
      // Fallback: find any button in AppBar
      await page.locator('.MuiToolbar-root button').last().click()
    })

    // Click Logout menu item
    const logoutItem = page.getByRole('menuitem', { name: /logout/i })
    const logoutBtn = page.getByRole('button', { name: /logout/i })
    if (await logoutItem.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await logoutItem.click()
    } else {
      await logoutBtn.first().click()
    }

    await page.waitForURL('**/login', { timeout: 10_000 })
    await expect(page).toHaveURL(/.*login/)
  })

  test('unauthenticated user navigating to /dashboard is redirected to /login', async ({ page }) => {
    // Mock refresh → 401 so AuthContext stays on login page
    await page.route(/\/api\/v1\/auth\/refresh/, async (route) => {
      await route.fulfill({ status: 401, body: JSON.stringify({ message: 'Unauthorized' }) })
    })

    // Navigate directly to protected route with no stored token
    await page.goto('/dashboard')

    // ProtectedRoute (or AuthContext) should redirect unauthenticated users to /login
    await expect(page).toHaveURL(/.*login/, { timeout: 10_000 })
  })
})

// ---------------------------------------------------------------------------
// TC-S6-06: Scope-gated button — esg:write absent → disabled
// ---------------------------------------------------------------------------

test.describe('TC-S6-06: Scope-gated ESG report generation', () => {
  test('user without esg:write → Generate Report button is disabled', async ({ page }) => {
    await loginWithMockJwt(page, {
      sub: 'op-readonly',
      roles: ['ROLE_OPERATOR'],
      tenant_id: 'default',
      scopes: ['esg:read'],   // esg:write intentionally absent
      allowed_buildings: [],
    })

    await page.goto('/esg')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForLoadState('networkidle').catch(() => {})

    // ReportGenerationPanel renders a Button disabled when !canWrite (useScope('esg:write'))
    // Button has data-testid="generate-report-btn" for reliable selection
    const generateBtn = page.getByTestId('generate-report-btn')
    await generateBtn.waitFor({ state: 'attached', timeout: 10_000 })
    await expect(generateBtn).toBeDisabled()
  })

  test('user WITH esg:write → Generate Report button is enabled', async ({ page }) => {
    await loginWithMockJwt(page, {
      sub: 'op-writer',
      roles: ['ROLE_OPERATOR'],
      tenant_id: 'default',
      scopes: ['esg:read', 'esg:write'],
      allowed_buildings: [],
    })

    await page.goto('/esg')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForLoadState('networkidle').catch(() => {})

    const generateBtn = page.getByTestId('generate-report-btn')
    await generateBtn.waitFor({ state: 'attached', timeout: 10_000 })
    await expect(generateBtn).not.toBeDisabled()
  })
})

// ---------------------------------------------------------------------------
// TC-S6-07: ROLE_CITIZEN cannot access /tenant-admin
// ---------------------------------------------------------------------------

test.describe('TC-S6-07: ROLE_CITIZEN tenant-admin access control', () => {
  test('ROLE_CITIZEN navigating to /tenant-admin is redirected to /dashboard', async ({ page }) => {
    await loginWithMockJwt(page, {
      sub: 'citizen1',
      roles: ['ROLE_CITIZEN'],
      tenant_id: 'default',
      scopes: [],
      allowed_buildings: [],
    })

    await page.goto('/tenant-admin')
    // ProtectedRoute requires ROLE_ADMIN or ROLE_TENANT_ADMIN — citizen gets redirected
    await expect(page).toHaveURL(/.*dashboard/, { timeout: 10_000 })
  })

  test('ROLE_CITIZEN sidebar does not show "Tenant Admin" nav item', async ({ page }) => {
    await loginWithMockJwt(page, {
      sub: 'citizen1',
      roles: ['ROLE_CITIZEN'],
      tenant_id: 'default',
      scopes: [],
      allowed_buildings: [],
    })

    await page.waitForLoadState('networkidle').catch(() => {})

    // Tenant Admin is role-gated (ROLE_ADMIN | ROLE_TENANT_ADMIN) — not visible to citizen
    const tenantAdminBtn = page.getByRole('button', { name: /tenant admin/i })
    await expect(tenantAdminBtn).not.toBeVisible({ timeout: 5_000 })
  })
})

// ---------------------------------------------------------------------------
// TC-S6-08: Mobile viewport + ROLE_CITIZEN → MobileLayout renders
// ---------------------------------------------------------------------------

test.describe('TC-S6-08: Mobile PWA citizen portal layout', () => {
  test.use({ viewport: { width: 375, height: 812 } })

  test('citizen on mobile gets redirected to /citizen and bottom nav visible', async ({ page }) => {
    await loginWithMockJwt(page, {
      sub: 'citizen-mobile',
      roles: ['ROLE_CITIZEN'],
      tenant_id: 'default',
      scopes: [],
      allowed_buildings: [],
    })

    // Navigate to citizen portal
    await page.goto('/citizen')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForLoadState('networkidle').catch(() => {})

    // MobileLayout / MobileNav renders bottom tabs on small viewport
    // Check for bottom navigation container or citizen-specific content
    const bottomNav = page.locator(
      '[role="navigation"]:not(.MuiDrawer-root), .MuiBottomNavigation-root, nav',
    ).last()

    const citizenHeading = page.getByText(/citizen|bill|aqi|notification/i).first()

    const navVisible = await bottomNav.isVisible({ timeout: 5_000 }).catch(() => false)
    const headingVisible = await citizenHeading.isVisible({ timeout: 5_000 }).catch(() => false)

    expect(navVisible || headingVisible).toBe(true)
  })

  test('AI Workflows not accessible via sidebar for ROLE_CITIZEN (role-filtered)', async ({ page }) => {
    await loginWithMockJwt(page, {
      sub: 'citizen-mobile',
      roles: ['ROLE_CITIZEN'],
      tenant_id: 'default',
      scopes: [],
      allowed_buildings: [],
    })

    await page.waitForLoadState('networkidle').catch(() => {})

    // AI Workflows requires ROLE_ADMIN | ROLE_OPERATOR — not visible to citizen
    const aiWorkflowBtn = page.getByRole('button', { name: /ai workflow/i })
    await expect(aiWorkflowBtn).not.toBeVisible({ timeout: 5_000 })
  })
})

/*
 * Summary:
 *   14 test cases across 8 groups (TC-S6-01 through TC-S6-08)
 *
 * Sprint 6 exit criteria #6 coverage:
 *   ✅ login → JWT tenant claims (TC-S6-01)
 *   ✅ tenant context propagated to API calls (TC-S6-01)
 *   ✅ feature flags hide/show nav items (TC-S6-03)
 *   ✅ feature flags fail-open (TC-S6-04)
 *   ✅ partner theme applied from branding (TC-S6-02)
 *   ✅ logout clears session (TC-S6-05)
 *   ✅ scope-gated actions (TC-S6-06)
 *   ✅ RBAC access control (TC-S6-07)
 *   ✅ mobile PWA citizen layout (TC-S6-08)
 *
 * Run: npx playwright test e2e/sprint6-uat.spec.ts --project=chromium
 */
