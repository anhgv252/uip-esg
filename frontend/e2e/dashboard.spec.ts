import { test, expect } from '@playwright/test';
import { loginAsAdmin, navigateTo } from './helpers/auth';

/**
 * TC-E2E-02: City Operations Dashboard
 * Tests main dashboard functionality and navigation
 */
test.describe('City Operations Dashboard', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('should display map container', async ({ page }) => {
    // Map is on City Ops page (CityOpsPage), not Dashboard — navigate via sidebar
    await navigateTo(page, 'City Ops');
    // Map container should be visible (Leaflet map rendered by SensorMap component)
    const mapContainer = page.locator('.leaflet-container, [class*="leaflet"]');
    await expect(mapContainer.first()).toBeVisible({ timeout: 15000 });
  });

  test('should display recent alerts panel', async ({ page }) => {
    // Dashboard KPI cards include "Open Alerts" stat
    const alertsPanel = page.locator('text=/open alerts|recent alerts|alert/i').first();
    await expect(alertsPanel).toBeVisible({ timeout: 10000 });
  });

  test('should have sidebar navigation with all main menu items', async ({ page }) => {
    // MUI Drawer sidebar uses ListItemButton (role="button"), not <nav> or <a>
    const menuLabels = ['Dashboard', 'Environment', 'ESG Metrics', 'Traffic', 'Alerts'];
    
    for (const label of menuLabels) {
      await expect(
        page.getByRole('button', { name: new RegExp(label, 'i') }).first()
      ).toBeVisible();
    }
  });
});
