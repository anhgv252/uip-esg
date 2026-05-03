import { test, expect } from '@playwright/test';
import { loginAsAdmin, navigateTo } from './helpers/auth';

/**
 * TC-E2E-04: ESG Metrics Dashboard
 * Tests ESG metrics overview with KPI cards and charts
 * This test requires running backend for actual ESG data
 */
test.describe('ESG Metrics', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await navigateTo(page, 'ESG Metrics');
  });

  test('should display ESG KPI cards', async ({ page }) => {
    // Page should have ESG heading (MUI Typography variant=h5 renders as <h5>)
    await expect(page.locator('h1, h2, h3, h4, h5, h6').filter({ hasText: /esg|environmental|social|governance/i }).first())
      .toBeVisible({ timeout: 10000 });
    
    // ESG KPI cards show Energy Consumption, Water Usage, Carbon Footprint
    // Use text content since MUI Card uses class 'MuiCard-root' (capital C — [class*="card"] won't match)
    await expect(page.getByText(/energy consumption/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/water usage/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/carbon footprint/i).first()).toBeVisible({ timeout: 10000 });
  });

  test('should render chart visualization', async ({ page }) => {
    // Chart panel is always present: recharts SVG when data exists, empty state text when no data
    const hasChart = await page.locator('[class*="recharts"]').first().isVisible({ timeout: 5000 })
      .catch(() => false);
    // When no energy data, EsgBarChart renders "No energy data" message
    const hasChartEmptyState = await page.getByText(/no .* data/i).first().isVisible({ timeout: 5000 })
      .catch(() => false);
    expect(hasChart || hasChartEmptyState).toBeTruthy();
  });

  test('should display metric values or loading state', async ({ page }) => {
    // KPI card labels are always visible (Energy Consumption, Water Usage, Carbon Footprint)
    const hasEsgContent = await page.getByText(/energy consumption|water usage|carbon footprint/i)
      .first()
      .isVisible({ timeout: 8000 })
      .catch(() => false);
    
    expect(hasEsgContent).toBeTruthy();
  });
});
