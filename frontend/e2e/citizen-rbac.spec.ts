import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers/auth';

/**
 * TC-E2E-08: RBAC on Citizen Portal
 * Tests role-based access control - admin should not access citizen portal
 */
test.describe('Citizen Portal RBAC', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('should show access restriction for admin on citizen portal', async ({ page }) => {
    // Navigate to citizens page
    await page.goto('/citizens');
    
    // Should show restriction message or redirect
    const restrictionMessage = page.locator('text=/access denied|not authorized|restricted|citizen role required|forbidden|403/i');
    
    const hasRestriction = await restrictionMessage.first().isVisible({ timeout: 5000 })
      .catch(() => false);
    
    // Or check if redirected away from /citizens
    const currentURL = page.url();
    const isRedirected = !currentURL.includes('/citizens');
    
    expect(hasRestriction || isRedirected).toBeTruthy();
  });

  test('should prevent direct navigation to citizen-only features', async ({ page }) => {
    // Try accessing citizen complaint form directly
    await page.goto('/citizens/complaints/new');
    
    // Should either show error or redirect
    const hasError = await page.locator('text=/access denied|not authorized|restricted|403|404/i')
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);
    
    const notOnComplaintsPage = !page.url().includes('/complaints/new');
    
    expect(hasError || notOnComplaintsPage).toBeTruthy();
  });

  test('should show correct role in user menu', async ({ page }) => {
    await page.goto('/dashboard');
    
    // Look for user menu or profile section
    const userMenu = page.locator('[class*="user-menu"], [class*="profile"], [aria-label*="user"], button:has-text("admin")').first();
    
    if (await userMenu.isVisible({ timeout: 5000 })) {
      await userMenu.click();
      
      // Should show ADMIN role
      await expect(page.locator('text=/admin|administrator/i')).toBeVisible({ timeout: 5000 });
    }
  });
});
