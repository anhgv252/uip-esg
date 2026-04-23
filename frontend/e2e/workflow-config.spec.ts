import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers/auth';

/**
 * TC-E2E-10: Workflow Trigger Configuration
 * Tests admin workflow trigger config management
 * This test requires running backend with 8 workflow configs
 */
test.describe('Workflow Trigger Configuration', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/workflow-configs');
  });

  test('should load workflow config page', async ({ page }) => {
    // Config page heading should be visible
    await expect(page.locator('h1, h2, h3').filter({ hasText: /workflow|trigger|config/i }).first())
      .toBeVisible({ timeout: 10000 });
  });

  test('should display 8 configuration rows in table', async ({ page }) => {
    // Table should be present
    const configTable = page.locator('table, [role="table"]').first();
    await expect(configTable).toBeVisible({ timeout: 10000 });

    // Count table rows (excluding header)
    // Requires running backend with exactly 8 workflow configs (7 seeded + 1 smoke test)
    const dataRows = page.locator('tbody tr');
    await expect(dataRows).toHaveCount(8, { timeout: 10000 });
  });

  test('should allow toggling enabled/disabled state', async ({ page }) => {
    // Look for toggle switches or checkboxes
    const toggleControls = page.locator('input[type="checkbox"], [role="switch"], button[aria-label*="toggle"], button[aria-label*="enable"]');
    
    const toggleCount = await toggleControls.count();
    expect(toggleCount).toBeGreaterThan(0);
    
    // First toggle should be interactive
    if (toggleCount > 0) {
      const firstToggle = toggleControls.first();
      await expect(firstToggle).toBeVisible();
      
      // Should be enabled for interaction
      const isEnabled = await firstToggle.isEnabled();
      expect(isEnabled).toBeTruthy();
    }
  });

  test('should display config table with action columns', async ({ page }) => {
    // Should have table headers for config properties
    const hasHeaders = await page.locator('th, [role="columnheader"]').count();
    expect(hasHeaders).toBeGreaterThan(0);
    
    // Should show enabled/disabled state column
    const hasStatusColumn = await page.locator('text=/enabled|disabled|status|active/i').first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    
    expect(hasStatusColumn).toBeTruthy();
  });
});
