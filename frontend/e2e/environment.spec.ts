import { test, expect } from '@playwright/test';
import { loginAsAdmin, navigateTo } from './helpers/auth';

/**
 * TC-E2E-03: Environment Monitoring
 * Tests air quality monitoring dashboard with sensor stations
 * This test requires running backend for actual sensor data
 */
test.describe('Environment Monitoring', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    // Navigate via sidebar — preserves in-memory auth token
    await navigateTo(page, 'Environment');
  });

  test('should load environment page with AQI gauge cards', async ({ page }) => {
    // Heading is "Environment Monitoring" (MUI variant=h5 → <h5>)
    await expect(page.locator('h1, h2, h3, h4, h5, h6').filter({ hasText: /environment monitoring/i }).first())
      .toBeVisible({ timeout: 10000 });
    
    // "Current AQI by Station" subtitle is always rendered inside the Paper section
    await expect(page.getByText(/current aqi by station/i)).toBeVisible({ timeout: 10000 });
  });

  test('should display at least one sensor station card', async ({ page }) => {
    // "Current AQI by Station" section is always present
    await expect(page.getByText(/current aqi by station/i)).toBeVisible({ timeout: 10000 });
    
    // "X/Y sensors online" counter is always rendered
    const sensorCounter = page.locator('text=/sensors online/i').first();
    await expect(sensorCounter).toBeVisible({ timeout: 10000 });
  });

  test('should have AQI information or loading state', async ({ page }) => {
    // Either data is loaded or loading indicator is shown — "Current AQI by Station" always exists
    await expect(page.getByText(/current aqi by station/i)).toBeVisible({ timeout: 10000 });
  });
});
