import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers/auth';

/**
 * TC-E2E-03: Environment Monitoring
 * Tests air quality monitoring dashboard with sensor stations
 * This test requires running backend for actual sensor data
 */
test.describe('Environment Monitoring', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/environment');
  });

  test('should load environment page with AQI gauge cards', async ({ page }) => {
    // Page heading should be visible
    await expect(page.locator('h1, h2, h3, h4').filter({ hasText: /environment|air quality|aqi/i }).first())
      .toBeVisible({ timeout: 10000 });
    
    // Should have gauge/card components for sensor stations
    const gaugeCards = page.locator('[class*="card"], [class*="gauge"], [data-testid*="sensor"]');
    await expect(gaugeCards.first()).toBeVisible({ timeout: 10000 });
  });

  test('should display at least one sensor station card', async ({ page }) => {
    // This test requires running backend with sensor data
    // Check for sensor station card presence
    const stationCard = page.locator('text=/station|sensor|district/i').first();
    await expect(stationCard).toBeVisible({ timeout: 10000 });
    
    // AQI value or status should be present
    const aqiValue = page.locator('text=/aqi|good|moderate|unhealthy|pm2.5/i').first();
    await expect(aqiValue).toBeVisible({ timeout: 10000 });
  });

  test('should have AQI information or loading state', async ({ page }) => {
    // Either data is loaded or loading indicator is shown
    const hasContent = await page.locator('text=/aqi|good|moderate|loading|no data/i').first().isVisible({ timeout: 5000 })
      .catch(() => false);
    
    expect(hasContent).toBeTruthy();
  });
});
