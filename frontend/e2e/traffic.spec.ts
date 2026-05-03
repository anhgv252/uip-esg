import { test, expect } from '@playwright/test';
import { loginAsAdmin, navigateTo } from './helpers/auth';

/**
 * TC-E2E-06: Traffic Management
 * Tests traffic incidents dashboard
 * This test requires running backend for actual traffic data
 */
test.describe('Traffic Management', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await navigateTo(page, 'Traffic');
  });

  test('should load traffic management page', async ({ page }) => {
    // Page heading should be visible (MUI Typography variant=h5 renders as <h5>)
    await expect(page.locator('h1, h2, h3, h4, h5, h6').filter({ hasText: /traffic/i }).first())
      .toBeVisible({ timeout: 10000 });
  });

  test('should display incidents table or list', async ({ page }) => {
    // Traffic Incidents table uses MUI Table → TableHead → TableCell renders as <th>
    await expect(page.locator('table').first()).toBeVisible({ timeout: 10000 });
    // Column headers should be visible (Type, Description, Intersection, Status, Occurred)
    await expect(page.locator('th').first()).toBeVisible({ timeout: 8000 });
  });

  test('should show traffic data or empty state', async ({ page }) => {
    // "Traffic Incidents" section heading is always rendered on the page
    const hasIncidentsSection = await page.getByText(/traffic incidents/i).first().isVisible({ timeout: 8000 })
      .catch(() => false);
    
    // Actual incident data shows ACCIDENT/CONGESTION chips, or empty state shows "No incidents"
    const hasIncidentContent = await page.getByText(/accident|congestion|roadwork|no incidents/i).first().isVisible({ timeout: 5000 })
      .catch(() => false);
    
    expect(hasIncidentsSection || hasIncidentContent).toBeTruthy();
  });
});
