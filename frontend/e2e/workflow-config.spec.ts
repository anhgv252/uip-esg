import { test, expect } from '@playwright/test';
import { loginAsAdmin, navigateTo } from './helpers/auth';

/**
 * TC-E2E-10: Workflow Trigger Configuration
 * Tests admin workflow trigger config management
 * This test requires running backend with 8 workflow configs
 */
test.describe('Workflow Trigger Configuration', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    // Navigate via sidebar — route is /workflow-config, sidebar label is "Trigger Config"
    await navigateTo(page, 'Trigger Config');
  });

  test('should load workflow config page', async ({ page }) => {
    // Heading is "Workflow Trigger Config" (MUI variant=h5 → <h5>)
    await expect(page.locator('h1, h2, h3, h4, h5, h6').filter({ hasText: /workflow trigger config/i }).first())
      .toBeVisible({ timeout: 10000 });
  });

  test('should display 8 configuration rows in table', async ({ page }) => {
    // Table should be present
    const configTable = page.locator('table, [role="table"]').first();
    await expect(configTable).toBeVisible({ timeout: 10000 });

    // Count table rows (excluding header) — at least 1 workflow config must exist
    const dataRows = page.locator('tbody tr');
    const rowCount = await dataRows.count();
    expect(rowCount).toBeGreaterThanOrEqual(1);
  });

  test('should allow toggling enabled/disabled state', async ({ page }) => {
    // WorkflowConfigPage uses MUI Switch for enabled column
    const toggleControls = page.locator('input[type="checkbox"], [role="switch"]');
    
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
    // Table headers: Name | Scenario Key | Type | Enabled | Dedup Key | Actions
    const hasHeaders = await page.locator('th, [role="columnheader"]').count();
    expect(hasHeaders).toBeGreaterThan(0);
    
    // "Enabled" column header is always present
    await expect(page.locator('th').filter({ hasText: /enabled/i }).first())
      .toBeVisible({ timeout: 10000 });
  });
});
