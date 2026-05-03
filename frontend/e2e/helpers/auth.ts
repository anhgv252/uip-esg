import { Page } from '@playwright/test';

/**
 * Logs in as admin user and waits for dashboard to load
 */
export async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  
  await page.getByLabel(/username/i).fill('admin');
  await page.locator('input[name="password"]').fill('admin_Dev#2026!');
  await page.getByRole('button', { name: /sign in/i }).click();
  
  // Wait for redirect to dashboard
  await page.waitForURL('**/dashboard', { timeout: 10000 });
}

/**
 * Logs in as operator user (if needed for role-specific tests)
 */
export async function loginAsOperator(page: Page) {
  await page.goto('/login');
  
  await page.getByLabel(/username/i).fill('operator');
  await page.locator('input[name="password"]').fill('operator_Dev#2026!');
  await page.getByRole('button', { name: /sign in/i }).click();
  
  await page.waitForURL('**/dashboard', { timeout: 10000 });
}

/**
 * Navigate to a page via sidebar click (SPA navigation — preserves in-memory auth token).
 * Use this instead of page.goto() when already logged in.
 */
export async function navigateTo(page: Page, sidebarLabel: string) {
  await page.getByRole('button', { name: new RegExp(sidebarLabel, 'i') }).first().click();
  // Wait for network to be idle so API data is loaded before test assertions run
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
}
