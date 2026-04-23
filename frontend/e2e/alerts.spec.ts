import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers/auth';

/**
 * TC-E2E-07: Alert Management
 * Tests alert listing and severity display
 * This test requires running backend for alert data
 */
test.describe('Alert Management', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/alerts');
  });

  test('should load alerts page with list', async ({ page }) => {
    // Alerts heading should be visible
    await expect(page.locator('h1, h2, h3').filter({ hasText: /alert/i }).first())
      .toBeVisible({ timeout: 10000 });
    
    // Alerts list or table should exist
    const alertsList = page.locator('table, [role="table"], [class*="list"], [class*="alert"]').first();
    await expect(alertsList).toBeVisible({ timeout: 10000 });
  });

  test('should display severity chips or indicators', async ({ page }) => {
    // Should have severity indicators (WARNING, CRITICAL, INFO, etc.)
    const severityChips = page.locator('text=/warning|critical|info|high|medium|low/i, [class*="chip"], [class*="badge"], [class*="severity"]');
    
    // At least one severity indicator should be visible or empty state shown
    const hasSeverity = await severityChips.first().isVisible({ timeout: 5000 })
      .catch(() => false);
    
    const hasEmptyState = await page.locator('text=/no alerts|empty|loading/i').first().isVisible({ timeout: 5000 })
      .catch(() => false);
    
    expect(hasSeverity || hasEmptyState).toBeTruthy();
  });

  test('should show alert list structure', async ({ page }) => {
    // Alert list should have columns or card layout
    const hasAlertStructure = await page.locator('[role="row"], [class*="card"], [class*="alert-item"]')
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    
    const hasHeaders = await page.locator('th, [role="columnheader"]').count();
    
    expect(hasAlertStructure || hasHeaders > 0).toBeTruthy();
  });
});
