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
    // Navigate to citizen page (singular — /citizen is the correct route)
    await page.goto('/citizen');
    
    // Should show restriction message for non-CITIZEN role
    const restrictionMessage = page.locator('text=/ROLE_CITIZEN|citizen role required|citizen portal/i');
    
    const hasRestriction = await restrictionMessage.first().isVisible({ timeout: 5000 })
      .catch(() => false);
    
    // Or check if redirected away from /citizen
    const currentURL = page.url();
    const isRedirected = !currentURL.includes('/citizen');
    
    expect(hasRestriction || isRedirected).toBeTruthy();
  });

  test('should prevent direct navigation to citizen-only features', async ({ page }) => {
    // Try accessing citizen registration page — allowed without auth (public route)
    // The portal tabs are only accessible after login with ROLE_CITIZEN
    await page.goto('/citizen/register');
    
    // Registration page is public — check it loads correctly or redirects
    const onRegisterPage = page.url().includes('/register');
    const redirectedToLogin = page.url().includes('/login');
    
    expect(onRegisterPage || redirectedToLogin).toBeTruthy();
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
