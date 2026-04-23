import { test, expect } from '@playwright/test';

/**
 * TC-E2E-01: Authentication Flow
 * Tests login, logout, and protected route access
 */
test.describe('Authentication', () => {
  
  test('should login with valid credentials and redirect to dashboard', async ({ page }) => {
    await page.goto('/login');
    
    await page.getByLabel(/username/i).fill('admin');
    await page.getByLabel(/password/i).fill('admin_Dev#2026!');
    await page.getByRole('button', { name: /login/i }).click();
    
    // Should redirect to dashboard after successful login
    await expect(page).toHaveURL(/.*dashboard/);
    
    // Dashboard should be visible
    await expect(page.locator('text=/city operations|dashboard/i')).toBeVisible({ timeout: 10000 });
  });

  test('should show error message with invalid credentials', async ({ page }) => {
    await page.goto('/login');
    
    await page.getByLabel(/username/i).fill('wronguser');
    await page.getByLabel(/password/i).fill('wrongpass');
    await page.getByRole('button', { name: /login/i }).click();
    
    // Should show error message (could be alert, toast, or inline error)
    await expect(page.locator('text=/invalid|error|failed|incorrect/i')).toBeVisible({ timeout: 5000 });
  });

  test('should redirect unauthenticated user to login page', async ({ page }) => {
    // Try to access protected route without login
    await page.goto('/dashboard');
    
    // Should redirect to login
    await expect(page).toHaveURL(/.*login/);
  });
});
