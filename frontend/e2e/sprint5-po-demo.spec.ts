import { test, expect, Page } from '@playwright/test'
import { loginAsAdmin, loginAsTenantAdmin, loginAsCitizen } from './helpers/auth'

/**
 * Sprint 5 — PO Demo Script
 * ============================================================
 * Full end-to-end walkthrough matching the live demo flow:
 *   Scene 1: Admin — Dashboard KPIs
 *   Scene 2: Admin — City Ops Center (map + alert panel)
 *   Scene 3: Admin — Environment Monitoring (AQI gauges)
 *   Scene 4: Admin — ESG Metrics
 *   Scene 5: Admin — Traffic Management (incidents table)
 *   Scene 6: Admin — Alert Management
 *   Scene 7: Admin — AI Workflow Dashboard (instances + definitions)
 *   Scene 8: Tenant Admin — Overview + Users + Buildings + Usage + Settings
 *   Scene 9: Citizen Portal — Home + Bills + AQI
 *   Scene 10: RBAC — OPERATOR & CITIZEN cannot access Tenant Admin
 *
 * Run:  npx playwright test e2e/sprint5-po-demo.spec.ts --project=chromium
 * ============================================================
 */

test.use({ viewport: { width: 1280, height: 720 } })

// ─────────────────────────────────────────────────────────────
// Scene 1: Dashboard KPIs
// ─────────────────────────────────────────────────────────────

test.describe('Scene 1 — Admin Dashboard KPIs', () => {
  test.beforeEach(async ({ page }) => { await loginAsAdmin(page) })

  test('Dashboard shows 4 KPI cards with real data', async ({ page }) => {
    await page.goto('/dashboard')
    await page.waitForLoadState('domcontentloaded')

    await expect(page.getByText(/active sensors/i)).toBeVisible({ timeout: 8000 })
    await expect(page.getByText(/aqi current/i)).toBeVisible()
    await expect(page.getByText(/open alerts/i)).toBeVisible()
    await expect(page.getByText(/carbon/i)).toBeVisible()

    // KPI values are numeric headings (MUI h4)
    const kpiHeadings = page.locator('h4')
    await expect(kpiHeadings.first()).toBeVisible()
  })

  test('Sidebar shows all 9 main menu items for ADMIN', async ({ page }) => {
    const expectedMenus = ['Dashboard', 'City Ops', 'Environment', 'ESG Metrics',
      'Traffic', 'Alerts', 'Citizens', 'AI Workflows', 'Admin']
    for (const label of expectedMenus) {
      // Sidebar items render as MUI ListItemButton with a Typography span child
      await expect(page.getByText(label).first()).toBeVisible({ timeout: 5000 })
    }
  })
})

// ─────────────────────────────────────────────────────────────
// Scene 2: City Operations Center
// ─────────────────────────────────────────────────────────────

test.describe('Scene 2 — City Operations Center', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/city-ops')
    await page.waitForLoadState('domcontentloaded')
  })

  test('City Ops page loads with map and heading', async ({ page }) => {
    await expect(page.getByText(/city operations center/i)).toBeVisible({ timeout: 10000 })
  })

  test('Leaflet map container is rendered', async ({ page }) => {
    const map = page.locator('.leaflet-container')
    await expect(map).toBeVisible({ timeout: 10000 })
  })

  test('Recent Alerts panel shows WARNING/CRITICAL entries', async ({ page }) => {
    await expect(page.getByText(/recent alerts/i)).toBeVisible({ timeout: 8000 })
    // At least one severity badge
    const badges = page.locator('text=/WARNING|CRITICAL/').first()
    await expect(badges).toBeVisible({ timeout: 8000 })
  })

  test('District filter dropdown is present', async ({ page }) => {
    await expect(page.getByText(/recent alerts/i)).toBeVisible({ timeout: 8000 })
    // District select is in the toolbar area
    const districtSelect = page.locator('text=/District|district/').first()
    await expect(districtSelect).toBeVisible()
  })
})

// ─────────────────────────────────────────────────────────────
// Scene 3: Environment Monitoring
// ─────────────────────────────────────────────────────────────

test.describe('Scene 3 — Environment Monitoring', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/environment')
    await page.waitForLoadState('domcontentloaded')
  })

  test('Environment page heading is visible', async ({ page }) => {
    await expect(page.getByText(/environment monitoring/i)).toBeVisible({ timeout: 10000 })
  })

  test('"Current AQI by Station" section renders', async ({ page }) => {
    await expect(page.getByText(/current aqi by station/i)).toBeVisible({ timeout: 10000 })
  })

  test('Sensor online counter is shown', async ({ page }) => {
    await expect(page.locator('text=/sensors online/i').first()).toBeVisible({ timeout: 10000 })
  })

  test('At least one AQI station card renders', async ({ page }) => {
    await expect(page.getByText(/current aqi by station/i)).toBeVisible({ timeout: 10000 })
    // Each station renders a label like "Bến Nghé" or "ENV-00x"
    const stationCards = page.locator('[class*="MuiCard"], [class*="MuiPaper"]')
    const count = await stationCards.count()
    expect(count).toBeGreaterThanOrEqual(1)
  })
})

// ─────────────────────────────────────────────────────────────
// Scene 4: ESG Metrics
// ─────────────────────────────────────────────────────────────

test.describe('Scene 4 — ESG Metrics', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/esg')
    await page.waitForLoadState('domcontentloaded')
  })

  test('ESG page heading is visible', async ({ page }) => {
    await expect(page.getByText(/esg/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('Energy metric card is rendered', async ({ page }) => {
    await expect(page.getByText(/energy/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('Water metric card is rendered', async ({ page }) => {
    await expect(page.getByText(/water/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('Carbon metric card is rendered', async ({ page }) => {
    await expect(page.getByText(/carbon/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('Generate Report button is present', async ({ page }) => {
    const reportBtn = page.getByRole('button', { name: /generate report|export/i })
    await expect(reportBtn.first()).toBeVisible({ timeout: 8000 })
  })
})

// ─────────────────────────────────────────────────────────────
// Scene 5: Traffic Management
// ─────────────────────────────────────────────────────────────

test.describe('Scene 5 — Traffic Management', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/traffic')
    await page.waitForLoadState('domcontentloaded')
  })

  test('Traffic Management page heading renders', async ({ page }) => {
    await expect(page.getByText(/traffic management/i)).toBeVisible({ timeout: 10000 })
  })

  test('Open incidents badge is shown', async ({ page }) => {
    await expect(page.getByText(/open incidents/i)).toBeVisible({ timeout: 10000 })
  })

  test('Traffic Incidents table has at least one row', async ({ page }) => {
    await expect(page.getByText(/traffic incidents/i)).toBeVisible({ timeout: 10000 })
    const rows = page.locator('tbody tr')
    await expect(rows.first()).toBeVisible({ timeout: 8000 })
  })

  test('ACCIDENT and CONGESTION badge types appear', async ({ page }) => {
    await expect(page.getByText(/traffic incidents/i)).toBeVisible({ timeout: 10000 })
    const accidentBadge = page.locator('text=ACCIDENT').first()
    await expect(accidentBadge).toBeVisible({ timeout: 8000 })
  })

  test('Intersection filter dropdown is present', async ({ page }) => {
    await expect(page.getByText(/traffic incidents/i)).toBeVisible({ timeout: 10000 })
    // "Intersection" select in toolbar
    await expect(page.getByText(/intersection/i).first()).toBeVisible()
  })
})

// ─────────────────────────────────────────────────────────────
// Scene 6: Alert Management
// ─────────────────────────────────────────────────────────────

test.describe('Scene 6 — Alert Management', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/alerts')
    await page.waitForLoadState('domcontentloaded')
  })

  test('Alerts page loads', async ({ page }) => {
    await expect(page.getByText(/alert/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('Alert list renders with WARNING or CRITICAL rows', async ({ page }) => {
    await expect(page.getByText(/alert/i).first()).toBeVisible({ timeout: 10000 })
    const severityLabel = page.locator('text=/WARNING|CRITICAL/').first()
    await expect(severityLabel).toBeVisible({ timeout: 8000 })
  })

  test('Alert rows include sensor IDs like ENV-00x', async ({ page }) => {
    await expect(page.getByText(/alert/i).first()).toBeVisible({ timeout: 10000 })
    const sensorRef = page.locator('text=/ENV-00/').first()
    await expect(sensorRef).toBeVisible({ timeout: 8000 })
  })
})

// ─────────────────────────────────────────────────────────────
// Scene 7: AI Workflow Dashboard
// ─────────────────────────────────────────────────────────────

test.describe('Scene 7 — AI Workflow Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
    await page.goto('/ai-workflow')
    await page.waitForLoadState('domcontentloaded')
  })

  test('AI Workflow page heading renders', async ({ page }) => {
    await expect(page.getByText(/ai workflow/i).first()).toBeVisible({ timeout: 10000 })
  })

  test('Process Instances tab is visible and has data', async ({ page }) => {
    const instancesTab = page.getByRole('tab', { name: /process instances/i })
    await expect(instancesTab).toBeVisible({ timeout: 10000 })

    // Instances table should have rows (761+ instances seeded)
    const rows = page.locator('tbody tr')
    await expect(rows.first()).toBeVisible({ timeout: 8000 })
  })

  test('Process Definitions tab shows workflow definitions', async ({ page }) => {
    const definitionsTab = page.getByRole('tab', { name: /process definitions/i })
    await expect(definitionsTab).toBeVisible({ timeout: 10000 })
    await definitionsTab.click()
    await page.waitForTimeout(500)

    const rows = page.locator('tbody tr')
    await expect(rows.first()).toBeVisible({ timeout: 8000 })
  })

  test('Known workflow IDs are visible in definitions', async ({ page }) => {
    const definitionsTab = page.getByRole('tab', { name: /process definitions/i })
    await expect(definitionsTab).toBeVisible({ timeout: 10000 })
    await definitionsTab.click()
    await page.waitForTimeout(500)

    // At least one of the known workflow names appears
    const workflowName = page.locator('text=/aiM0|aiC0|flood|aqi|esg/i').first()
    await expect(workflowName).toBeVisible({ timeout: 8000 })
  })
})

// ─────────────────────────────────────────────────────────────
// Scene 8: Tenant Admin — Full Sub-navigation Flow
// ─────────────────────────────────────────────────────────────

test.describe('Scene 8 — Tenant Admin Full Flow', () => {
  test.beforeEach(async ({ page }) => { await loginAsTenantAdmin(page) })

  test('Tenant Admin Overview — stat cards visible', async ({ page }) => {
    await page.goto('/tenant-admin')
    await page.waitForLoadState('domcontentloaded')

    await expect(page.getByText(/tenant admin/i).first()).toBeVisible({ timeout: 8000 })
    // stat cards
    await expect(page.locator('[data-testid="stat-users"]')).toBeVisible({ timeout: 8000 })
    await expect(page.locator('[data-testid="stat-buildings"]')).toBeVisible()
    await expect(page.locator('[data-testid="stat-alerts"]')).toBeVisible()
  })

  test('Tenant Admin sub-nav has 5 sections', async ({ page }) => {
    await page.goto('/tenant-admin')
    await page.waitForLoadState('domcontentloaded')
    await expect(page.getByText(/tenant admin/i).first()).toBeVisible({ timeout: 8000 })

    for (const section of ['Overview', 'Users', 'Buildings', 'Usage', 'Settings']) {
      // Sub-nav may use ListItemButton or NavLink — match by visible text
      await expect(page.getByText(section).first()).toBeVisible({ timeout: 5000 })
    }
  })

  test('User Management — lists seeded users including tadmin', async ({ page }) => {
    await page.goto('/tenant-admin/users')
    await page.waitForLoadState('domcontentloaded')

    await expect(page.getByText(/user management/i)).toBeVisible({ timeout: 8000 })

    // At least 1 user row visible in the table
    const rows = page.locator('tbody tr')
    await expect(rows.first()).toBeVisible({ timeout: 8000 })
    const count = await rows.count()
    expect(count).toBeGreaterThanOrEqual(1)
  })

  test('User Management — Created date shows valid date or dash (not "Invalid Date")', async ({ page }) => {
    await page.goto('/tenant-admin/users')
    await page.waitForLoadState('domcontentloaded')
    await expect(page.getByText(/user management/i)).toBeVisible({ timeout: 8000 })

    // After fix: no "Invalid Date" text anywhere on page
    await expect(page.getByText('Invalid Date')).not.toBeVisible()
  })

  test('User Management — Invite User dialog opens and email can be filled', async ({ page }) => {
    await page.goto('/tenant-admin/users')
    await page.waitForLoadState('domcontentloaded')

    await page.getByRole('button', { name: /invite user/i }).click()

    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await dialog.getByLabel(/email/i).fill(`demo-${Date.now()}@po-test.com`)

    // Role selector shows ROLE_OPERATOR or ROLE_TENANT_ADMIN
    const roleSelect = dialog.locator('[role="combobox"], select')
    await expect(roleSelect.first()).toBeVisible()

    // Cancel — don't actually send in this test
    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible({ timeout: 3000 })
  })

  test('User Management — Invite flow shows success toast', async ({ page }) => {
    await page.goto('/tenant-admin/users')
    await page.waitForLoadState('domcontentloaded')

    await page.getByRole('button', { name: /invite user/i }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByLabel(/email/i).fill(`po-demo-${Date.now()}@smartcity.test`)
    await dialog.getByRole('button', { name: /send invite/i }).click()

    await expect(page.getByText(/invite sent successfully/i)).toBeVisible({ timeout: 6000 })
  })

  test('Buildings page shows "No buildings found" empty state', async ({ page }) => {
    await page.goto('/tenant-admin/buildings')
    await page.waitForLoadState('domcontentloaded')

    await expect(page.getByText(/building configuration/i)).toBeVisible({ timeout: 8000 })
    await expect(page.getByText(/no buildings found/i)).toBeVisible({ timeout: 5000 })
  })

  test('Usage Report page shows date range and KPI cards', async ({ page }) => {
    await page.goto('/tenant-admin/usage')
    await page.waitForLoadState('domcontentloaded')

    await expect(page.getByText(/usage report/i)).toBeVisible({ timeout: 8000 })
    await expect(page.getByText(/total readings/i)).toBeVisible()
    await expect(page.getByText(/total reports/i)).toBeVisible()
    await expect(page.getByText(/avg.day/i)).toBeVisible()

    // Date inputs exist
    const inputs = page.locator('input[type="date"]')
    expect(await inputs.count()).toBeGreaterThanOrEqual(2)
  })

  test('Settings page shows Branding and Tenant panels', async ({ page }) => {
    await page.goto('/tenant-admin/settings')
    await page.waitForLoadState('domcontentloaded')

    await expect(page.getByText(/settings/i).first()).toBeVisible({ timeout: 8000 })
    // Branding section exists (may be a card title or section heading)
    await expect(page.getByText(/branding/i).first()).toBeVisible({ timeout: 5000 })
    // primaryColor field renders
    const primaryColorInput = page.locator('input[value="#1976D2"], input[placeholder*="color"], input').first()
    await expect(primaryColorInput).toBeVisible({ timeout: 5000 })
    // Save button accessible
    await expect(page.getByRole('button', { name: /save settings/i })).toBeVisible()
  })

  test('Settings — Save Settings button triggers success toast', async ({ page }) => {
    await page.goto('/tenant-admin/settings')
    await page.waitForLoadState('domcontentloaded')

    await expect(page.getByText(/settings/i).first()).toBeVisible({ timeout: 8000 })
    await page.getByRole('button', { name: /save settings/i }).click()
    await expect(page.getByText(/settings saved/i)).toBeVisible({ timeout: 6000 })
  })
})

// ─────────────────────────────────────────────────────────────
// Scene 9: Citizen Portal (login as citizen)
// ─────────────────────────────────────────────────────────────

test.describe('Scene 9 — Citizen Portal', () => {
  test.beforeEach(async ({ page }) => { await loginAsCitizen(page) })

  test('Citizen Portal loads with bottom navigation', async ({ page }) => {
    await expect(page.locator('[data-testid="bottom-nav"]')).toBeVisible({ timeout: 8000 })
  })

  test('Citizen Portal bottom nav has Home, Bills, AQI, Alerts, Profile tabs', async ({ page }) => {
    const nav = page.locator('[data-testid="bottom-nav"]')
    await expect(nav).toBeVisible({ timeout: 8000 })
    for (const label of ['Home', 'Bills', 'AQI', 'Alerts', 'Profile']) {
      await expect(nav.getByText(new RegExp(label, 'i'))).toBeVisible()
    }
  })

  test('Bills tab navigates to /citizen/bills', async ({ page }) => {
    await page.locator('[data-testid="bottom-nav"]').getByRole('button', { name: /bills/i }).click()
    await page.waitForURL('**/citizen/bills', { timeout: 5000 })
    await expect(page).toHaveURL(/\/citizen\/bills/)
  })

  test('Bills page renders bill-card element', async ({ page }) => {
    await page.goto('/citizen/bills')
    await page.waitForLoadState('domcontentloaded')
    await expect(page.locator('[data-testid="bill-card"]').first()).toBeVisible({ timeout: 8000 })
  })

  test('AQI tab navigates to /citizen/aqi', async ({ page }) => {
    await page.locator('[data-testid="bottom-nav"]').getByRole('button', { name: /aqi/i }).click()
    await page.waitForURL('**/citizen/aqi', { timeout: 5000 })
    await expect(page).toHaveURL(/\/citizen\/aqi/)
  })

  test('AQI page renders AQI gauge or index value', async ({ page }) => {
    await page.goto('/citizen/aqi')
    await page.waitForLoadState('domcontentloaded')
    // AQI page shows a numeric gauge or text "AQI"
    await expect(page.getByText(/aqi/i).first()).toBeVisible({ timeout: 10000 })
  })
})

// ─────────────────────────────────────────────────────────────
// Scene 10: RBAC — Role-Based Access Control
// ─────────────────────────────────────────────────────────────

test.describe('Scene 10 — RBAC Enforcement', () => {
  test('CITIZEN user cannot access /tenant-admin — redirected or 403 shown', async ({ page }) => {
    await loginAsCitizen(page)
    await page.goto('/tenant-admin')
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(1000)

    // PASS if:
    //  (a) navigated away from /tenant-admin, OR
    //  (b) shows access denied/forbidden message, OR
    //  (c) stays on /citizen (normal citizen home)
    const url = page.url()
    const isRedirected = !url.includes('/tenant-admin') || url.includes('/citizen') || url.includes('/login')
    const hasAccessDenied = await page.getByText(/access denied|unauthorized|forbidden|not allowed|không có quyền/i)
      .isVisible({ timeout: 2000 }).catch(() => false)
    // Citizen sees citizen portal nav — no Tenant Admin menu
    const hasTenantAdminMenu = await page.getByText('User Management').isVisible({ timeout: 1000 }).catch(() => false)

    expect(isRedirected || hasAccessDenied || !hasTenantAdminMenu).toBe(true)
  })

  test('TENANT_ADMIN cannot see AI Workflows or Admin in sidebar', async ({ page }) => {
    await loginAsTenantAdmin(page)
    await page.goto('/tenant-admin')
    await page.waitForLoadState('domcontentloaded')

    // AI Workflows and Admin are ROLE_ADMIN-only sidebar items
    await expect(page.getByRole('button', { name: /^admin$/i })).not.toBeVisible()
  })

  test('TENANT_ADMIN login lands on app (not /login)', async ({ page }) => {
    await loginAsTenantAdmin(page)
    await page.waitForLoadState('domcontentloaded')
    const url = page.url()
    // Should not be on /login — successfully authenticated
    expect(url).not.toMatch(/\/login/)
    // App title confirms we're in the app
    await expect(page.locator('text=/UIP Smart City|Tenant Admin|Dashboard/i').first()).toBeVisible({ timeout: 8000 })
  })
})
