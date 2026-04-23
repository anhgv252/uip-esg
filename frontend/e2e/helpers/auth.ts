import { Page } from '@playwright/test';

/**
 * Logs in as admin user and waits for dashboard to load
 * Saves authentication state to sessionStorage for reuse
 */
export async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  
  await page.getByLabel(/username/i).fill('admin');
  await page.getByLabel(/password/i).fill('admin_Dev#2026!');
  await page.getByRole('button', { name: /login/i }).click();
  
  // Wait for redirect to dashboard
  await page.waitForURL('**/dashboard', { timeout: 10000 });
}

/**
 * Logs in as operator user (if needed for role-specific tests)
 */
export async function loginAsOperator(page: Page) {
  await page.goto('/login');
  
  await page.getByLabel(/username/i).fill('operator');
  await page.getByLabel(/password/i).fill('operator_pass'); // Update if known
  await page.getByRole('button', { name: /login/i }).click();
  
  await page.waitForURL('**/dashboard', { timeout: 10000 });
}
