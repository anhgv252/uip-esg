import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers/auth';

/**
 * TC-E2E-06: Traffic Management
 * Tests traffic incidents dashboard
 * This test requires running backend for actual traffic data
 */
test.describe('Traffic Management', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/traffic');
  });

  test('should load traffic management page', async ({ page }) => {
    // Page heading should be visible
    await expect(page.locator('h1, h2, h3').filter({ hasText: /traffic/i }).first())
      .toBeVisible({ timeout: 10000 });
  });

  test('should display incidents table or list', async ({ page }) => {
    // Should have table or list container for incidents
    const incidentsList = page.locator('table, [role="table"], [class*="list"], [class*="grid"]').first();
    await expect(incidentsList).toBeVisible({ timeout: 10000 });
    
    // Table headers or list items should exist
    const hasIncidentContent = await page.locator('th, [role="columnheader"], [class*="incident"], text=/location|status|type|severity/i')
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    
    expect(hasIncidentContent).toBeTruthy();
  });

  test('should show traffic data or empty state', async ({ page }) => {
    // Either incidents are loaded or empty/loading state shown
    const hasContent = await page.locator('text=/incident|congestion|accident|location|no traffic|loading/i')
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    
    expect(hasContent).toBeTruthy();
  });
});
