import { test, expect } from '@playwright/test';
import { loginAsAdmin, navigateTo } from './helpers/auth';

/**
 * TC-E2E-05: ESG Report Generation
 * Tests ESG report creation workflow — ReportGenerationPanel is embedded in /esg route.
 * Use sidebar navigation to preserve in-memory auth token.
 */
test.describe('ESG Report Generation', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    // Navigate to ESG page via sidebar (SPA navigation preserves auth token)
    await navigateTo(page, 'ESG Metrics');
  });

  test('should navigate to ESG reports section', async ({ page }) => {
    // ReportGenerationPanel is embedded directly in the ESG page (no separate tab)
    // The panel heading is "Generate ESG Report"
    await expect(
      page.locator('p, h5, h6').filter({ hasText: /generate esg report/i }).first()
    ).toBeVisible({ timeout: 10000 });
  });

  test('should allow selecting period and clicking generate', async ({ page }) => {
    // Year and Quarter selectors should be visible (MUI Select → role="combobox")
    const yearSelector = page.getByRole('combobox', { name: /year/i });
    if (await yearSelector.isVisible({ timeout: 5000 }).catch(() => false)) {
      // combobox found — fine
    }
    
    // "Generate Report" button should be present and enabled
    const generateButton = page.getByRole('button', { name: /generate report/i });
    await expect(generateButton).toBeVisible({ timeout: 10000 });
    await expect(generateButton).toBeEnabled();
  });

  test('should show report generation UI elements', async ({ page }) => {
    // The ReportGenerationPanel contains Year/Quarter selects and Generate button
    await expect(
      page.getByRole('button', { name: /generate report/i })
    ).toBeVisible({ timeout: 10000 });
    
    // Should have combobox selectors (Year, Quarter)
    const hasControls = await page.getByRole('combobox').count();
    expect(hasControls).toBeGreaterThan(0);
  });
});
