import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers/auth';

/**
 * TC-E2E-04: ESG Metrics Dashboard
 * Tests ESG metrics overview with KPI cards and charts
 * This test requires running backend for actual ESG data
 */
test.describe('ESG Metrics', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/esg');
  });

  test('should display ESG KPI cards', async ({ page }) => {
    // Page should have ESG heading
    await expect(page.locator('h1, h2, h3').filter({ hasText: /esg|environmental|social|governance/i }).first())
      .toBeVisible({ timeout: 10000 });
    
    // Should have at least 3 KPI cards (carbon, energy, waste, etc.)
    const kpiCards = page.locator('[class*="card"], [class*="metric"], [data-testid*="kpi"]');
    const cardCount = await kpiCards.count();
    expect(cardCount).toBeGreaterThanOrEqual(3);
  });

  test('should render chart visualization', async ({ page }) => {
    // Chart container should be present (recharts/d3)
    const chartElement = page.locator('[class*="recharts"], svg[class*="chart"], canvas');
    await expect(chartElement.first()).toBeVisible({ timeout: 10000 });
  });

  test('should display metric values or loading state', async ({ page }) => {
    // Either metrics are loaded or loading indicator shown
    const hasMetrics = await page.locator('text=/carbon|emission|kwh|energy|waste|score|loading/i').first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    
    expect(hasMetrics).toBeTruthy();
  });
});
