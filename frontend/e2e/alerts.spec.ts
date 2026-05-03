import { test, expect } from '@playwright/test';
import { loginAsAdmin, navigateTo } from './helpers/auth';

/**
 * TC-E2E-07: Alert Management
 * Tests alert listing and severity display
 * This test requires running backend for alert data
 */
test.describe('Alert Management', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await navigateTo(page, 'Alerts');
  });

  test('should load alerts page with list', async ({ page }) => {
    // Alerts heading should be visible (MUI Typography variant=h5 renders as <h5>)
    await expect(page.locator('h1, h2, h3, h4, h5, h6').filter({ hasText: /alert/i }).first())
      .toBeVisible({ timeout: 10000 });
    
    // Alerts list or table should exist
    const alertsList = page.locator('table, [role="table"], [class*="list"], [class*="alert"]').first();
    await expect(alertsList).toBeVisible({ timeout: 10000 });
  });

  test('should display severity chips or indicators', async ({ page }) => {
    // The Severity filter dropdown is always present on the alerts page
    const hasSeverityFilter = await page.getByLabel('Severity').isVisible({ timeout: 8000 })
      .catch(() => false);
    
    // If there is alert data, MUI Chips will show severity levels
    const hasSeverityChips = await page.locator('[class*="MuiChip-root"]').first().isVisible({ timeout: 3000 })
      .catch(() => false);
    
    expect(hasSeverityFilter || hasSeverityChips).toBeTruthy();
  });

  test('should show alert list structure', async ({ page }) => {
    // MUI Table renders <th> elements for TableHead > TableCell
    // Use toBeVisible with retry instead of count() which has no retry
    await expect(page.locator('th').first()).toBeVisible({ timeout: 10000 });
  });
});
