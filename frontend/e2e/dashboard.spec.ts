import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers/auth';

/**
 * TC-E2E-02: City Operations Dashboard
 * Tests main dashboard functionality and navigation
 */
test.describe('City Operations Dashboard', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('should display map container', async ({ page }) => {
    // Map container should be visible (Leaflet map)
    const mapContainer = page.locator('.leaflet-container, [class*="map"], #map-container');
    await expect(mapContainer.first()).toBeVisible({ timeout: 10000 });
  });

  test('should display recent alerts panel', async ({ page }) => {
    // Recent alerts section should exist
    const alertsPanel = page.locator('text=/recent alerts|latest alerts|alert/i').first();
    await expect(alertsPanel).toBeVisible({ timeout: 10000 });
  });

  test('should have sidebar navigation with all main menu items', async ({ page }) => {
    // Check for main navigation links
    await expect(page.locator('nav, [role="navigation"]')).toBeVisible();
    
    // Verify key menu items exist
    const menuItems = [
      /dashboard/i,
      /environment/i,
      /esg/i,
      /traffic/i,
      /alert/i,
    ];
    
    for (const item of menuItems) {
      await expect(page.locator(`a:has-text("${item.source}")`).first()).toBeVisible();
    }
  });
});
