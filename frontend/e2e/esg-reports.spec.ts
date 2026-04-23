import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers/auth';

/**
 * TC-E2E-05: ESG Report Generation
 * Tests ESG report creation workflow
 * This test requires running backend for report generation
 */
test.describe('ESG Report Generation', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('should navigate to ESG reports section', async ({ page }) => {
    await page.goto('/esg');
    
    // Look for reports tab or link
    const reportsTab = page.locator('text=/report/i, [role="tab"]:has-text("report")').first();
    
    if (await reportsTab.isVisible({ timeout: 5000 })) {
      await reportsTab.click();
      await expect(page.locator('text=/generate|create|new report/i')).toBeVisible({ timeout: 10000 });
    } else {
      // Try direct navigation if tab not found
      await page.goto('/esg/reports');
      await expect(page.locator('text=/report|generate/i')).toBeVisible({ timeout: 10000 });
    }
  });

  test('should allow selecting period and clicking generate', async ({ page }) => {
    await page.goto('/esg/reports');
    
    // Look for period selector (dropdown or date picker)
    const periodSelector = page.locator('select, [role="combobox"], input[type="date"]').first();
    
    if (await periodSelector.isVisible({ timeout: 5000 })) {
      await periodSelector.click();
    }
    
    // Generate button should be present
    const generateButton = page.getByRole('button', { name: /generate|create|download/i });
    await expect(generateButton).toBeVisible();
    await expect(generateButton).toBeEnabled();
  });

  test('should show report generation UI elements', async ({ page }) => {
    await page.goto('/esg/reports');
    
    // Report form/interface should be present
    const reportUI = page.locator('form, [class*="report"], [data-testid*="report"]').first();
    await expect(reportUI).toBeVisible({ timeout: 10000 });
    
    // Should have some control elements
    const hasControls = await page.locator('button, select, input').count();
    expect(hasControls).toBeGreaterThan(0);
  });
});
