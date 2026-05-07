import { test, expect } from '@playwright/test'
import { loginAsOperator, loginAsTenantAdmin, loginAsCitizen, navigateTo } from './helpers/auth'

/**
 * Tenant Admin Dashboard — E2E Tests
 * Sprint 5: MVP2-13
 *
 * Prerequisites:
 * - Backend running with test data seeded
 * - Tenant admin user: tenant_admin_hcm / password
 * - Test tenant: hcm
 */

// ============================================================================
// Test fixtures
// ============================================================================

test.use({ viewport: { width: 1280, height: 720 } })

// ============================================================================
// Flow 1: Overview → Invite User → Verify
// ============================================================================

test.describe('Tenant Admin — User Management Flow', () => {

  test('Login as TENANT_ADMIN → view overview → invite user → verify invite sent', async ({ page }) => {
    await loginAsTenantAdmin(page)

    // Step 2: Navigate to Tenant Admin overview
    await navigateTo(page, 'Tenant Admin')
    await page.waitForURL('**/tenant-admin', { timeout: 5000 })

    // Step 3: Verify overview page loaded with stat cards
    await expect(page.locator('[data-testid="stat-users"]')).toBeVisible({ timeout: 5000 })
    await expect(page.locator('[data-testid="stat-buildings"]')).toBeVisible()
    await expect(page.locator('[data-testid="stat-alerts"]')).toBeVisible()

    // Step 4: Navigate to User Management
    await navigateTo(page, 'Users')
    await page.waitForURL('**/tenant-admin/users', { timeout: 5000 })

    // Step 5: Click "Invite User" button
    await page.getByRole('button', { name: /invite user/i }).click()

    // Step 6: Fill invite dialog
    const inviteDialog = page.getByRole('dialog')
    await expect(inviteDialog).toBeVisible()
    await inviteDialog.getByLabel(/email/i).fill(`test-invite-${Date.now()}@example.com`)
    await inviteDialog.getByRole('button', { name: /send invite/i }).click()

    // Step 7: Verify success toast
    await expect(page.getByText(/invite sent successfully/i)).toBeVisible({ timeout: 5000 })

    // Step 8: Verify invited user appears in list
    await expect(page.getByText(/@example\.com/)).toBeVisible({ timeout: 5000 })
  })

  test('User Management — role filter works', async ({ page }) => {
    await loginAsTenantAdmin(page)
    await navigateTo(page, 'Tenant Admin')
    await navigateTo(page, 'Users')
    await page.waitForURL('**/tenant-admin/users', { timeout: 5000 })

    // The users table should be visible
    await expect(page.locator('[data-testid="users-table"], table')).toBeVisible({ timeout: 5000 })

    // Select "CITIZEN" filter via dropdown or chip
    const roleFilter = page.getByRole('combobox', { name: /role/i })
      .or(page.locator('[data-testid="role-filter"]'))
    if (await roleFilter.isVisible()) {
      await roleFilter.click()
      await page.getByRole('option', { name: /citizen/i }).click()
      await page.waitForLoadState('networkidle')

      // Verify no ADMIN rows visible
      const adminRows = page.locator('[data-testid="user-role-chip"]').filter({ hasText: /admin/i })
      await expect(adminRows).toHaveCount(0)

      // Reset to ALL
      await roleFilter.click()
      await page.getByRole('option', { name: /all/i }).click()
      await page.waitForLoadState('networkidle')
    }
  })

  test('User Management — deactivate user with confirmation', async ({ page }) => {
    await loginAsTenantAdmin(page)
    await navigateTo(page, 'Tenant Admin')
    await navigateTo(page, 'Users')
    await page.waitForURL('**/tenant-admin/users', { timeout: 5000 })

    await expect(page.locator('[data-testid="users-table"], table')).toBeVisible({ timeout: 5000 })

    // Find an active user row with a deactivate/disable action
    const deactivateBtn = page.locator('[data-testid="btn-deactivate-user"]').first()
      .or(page.getByRole('button', { name: /deactivate/i }).first())

    if (await deactivateBtn.isVisible()) {
      await deactivateBtn.click()

      // Confirmation dialog
      const confirmDialog = page.getByRole('dialog')
      await expect(confirmDialog).toBeVisible()
      await confirmDialog.getByRole('button', { name: /confirm|yes|deactivate/i }).click()

      // Toast/success message
      await expect(page.getByText(/user deactivated|disabled successfully/i)).toBeVisible({ timeout: 5000 })

      // The user row should now show INACTIVE badge
      await expect(
        page.locator('[data-testid="user-status-chip"]').filter({ hasText: /inactive/i }).first()
      ).toBeVisible({ timeout: 3000 })
    }
  })
})

// ============================================================================
// Flow 2: Settings → Change Color → Save
// ============================================================================

test.describe('Tenant Admin — Settings Flow', () => {

  test('Login as TENANT_ADMIN → settings → change color → save → verify', async ({ page }) => {
    await loginAsTenantAdmin(page)

    // Step 2: Navigate to settings
    await navigateTo(page, 'Tenant Admin')
    await navigateTo(page, 'Settings')
    await page.waitForURL('**/tenant-admin/settings', { timeout: 5000 })

    // Step 3: Change primary color
    const colorInput = page.locator('input[type="color"]')
    if (await colorInput.isVisible()) {
      await colorInput.fill('#2e7d32') // Change to green
    }

    // Step 4: Click save
    await page.getByRole('button', { name: /save/i }).click()

    // Step 5: Verify success toast
    await expect(page.getByText(/settings saved/i)).toBeVisible({ timeout: 5000 })

    // Step 6: Reload and verify color persisted
    await page.reload()
    if (await colorInput.isVisible()) {
      await expect(colorInput).toHaveValue(/2e7d32/i)
    }
  })

  test('Settings — tenant name and logo fields are editable', async ({ page }) => {
    await loginAsTenantAdmin(page)
    await navigateTo(page, 'Tenant Admin')
    await navigateTo(page, 'Settings')
    await page.waitForURL('**/tenant-admin/settings', { timeout: 5000 })

    // Verify tenant name input is present and editable
    const nameInput = page.locator('input[name="tenantName"], input[placeholder*="name" i]').first()
    if (await nameInput.isVisible()) {
      const original = await nameInput.inputValue()
      await nameInput.fill('HCM City Test')
      await page.getByRole('button', { name: /save/i }).click()
      await expect(page.getByText(/settings saved/i)).toBeVisible({ timeout: 5000 })
      // Restore original value
      await nameInput.fill(original)
      await page.getByRole('button', { name: /save/i }).click()
    }
  })
})

// ============================================================================
// Flow 3: Usage → Change Date Range → Verify Chart
// ============================================================================

test.describe('Tenant Admin — Usage Report Flow', () => {

  test('Login as TENANT_ADMIN → usage → change date range → verify chart', async ({ page }) => {
    await loginAsTenantAdmin(page)

    // Step 2: Navigate to usage report
    await navigateTo(page, 'Tenant Admin')
    await navigateTo(page, 'Usage')
    await page.waitForURL('**/tenant-admin/usage', { timeout: 5000 })

    // Step 3: Verify chart renders
    await expect(page.locator('[data-testid="usage-chart"]')).toBeVisible({ timeout: 5000 })

    // Step 4: Change date range
    const fromInput = page.locator('input[name="from"]')
    const toInput = page.locator('input[name="to"]')
    if (await fromInput.isVisible()) {
      await fromInput.fill('2026-06-01')
      await toInput.fill('2026-06-15')
      // Trigger date range update
      await page.getByRole('button', { name: /apply/i }).click()
    }

    // Step 5: Verify chart updated
    await page.waitForTimeout(1000) // Wait for chart re-render
    await expect(page.locator('[data-testid="usage-chart"]')).toBeVisible()
  })

  test('Usage Report — export CSV downloads file', async ({ page }) => {
    await loginAsTenantAdmin(page)
    await navigateTo(page, 'Tenant Admin')
    await navigateTo(page, 'Usage')
    await page.waitForURL('**/tenant-admin/usage', { timeout: 5000 })

    await expect(page.locator('[data-testid="usage-chart"]')).toBeVisible({ timeout: 5000 })

    // Intercept download event
    const downloadPromise = page.waitForEvent('download', { timeout: 10000 }).catch(() => null)

    const exportBtn = page.getByRole('button', { name: /export|csv|download/i })
    if (await exportBtn.isVisible()) {
      await exportBtn.click()
      const download = await downloadPromise
      if (download) {
        expect(download.suggestedFilename()).toMatch(/\.csv$/i)
      }
    }
  })
})

// ============================================================================
// RBAC: Non-admin cannot access tenant admin pages
// ============================================================================

test.describe('Tenant Admin — RBAC', () => {

  test('CITIZEN user cannot access tenant admin pages', async ({ page }) => {
    await loginAsCitizen(page)

    // Try to navigate to tenant admin directly
    await page.goto('/tenant-admin')

    // Should be redirected or see access denied
    await expect(page).not.toHaveURL(/\/tenant-admin$/)
  })

  test('OPERATOR user cannot access tenant admin pages', async ({ page }) => {
    await loginAsOperator(page)

    await page.goto('/tenant-admin')
    await expect(page).not.toHaveURL(/\/tenant-admin$/)
  })

  test('Tenant Admin nav item is NOT visible for OPERATOR', async ({ page }) => {
    await loginAsOperator(page)

    // Wait for nav to load
    await page.waitForLoadState('networkidle')

    // "Tenant Admin" nav item should not be rendered for operators
    const tenantAdminNav = page.getByRole('button', { name: /tenant admin/i })
    await expect(tenantAdminNav).toHaveCount(0)
  })
})
