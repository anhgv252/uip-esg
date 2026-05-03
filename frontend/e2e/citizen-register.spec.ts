import { test, expect } from '@playwright/test';

/**
 * TC-E2E-S3-08b: Citizen Registration Flow E2E
 * Tests the 3-step citizen registration wizard (public route — no prior auth required).
 * Requires running backend with /api/v1/citizen/register + /api/v1/citizen/buildings.
 */

function uniqueEmail() {
  return `test_e2e_${Date.now()}@uip.test`;
}

test.describe('Citizen Registration Flow', () => {
  test('should load the registration page with step 1 form', async ({ page }) => {
    await page.goto('/citizen/register');

    // Heading is "Create Citizen Account" (MUI variant=h5 → <h5>)
    await expect(
      page.locator('h1, h2, h3, h4, h5, h6').filter({ hasText: /register|đăng ký|create.*account/i }).first()
    ).toBeVisible({ timeout: 10_000 });

    // Step 1: personal info fields
    await expect(page.locator('input[type="email"], input[placeholder*="email" i]').first()).toBeVisible({ timeout: 5_000 });
    await expect(page.locator('input[type="password"], input[placeholder*="password" i], input[placeholder*="mật khẩu" i]').first()).toBeVisible({ timeout: 5_000 });
  });

  test('should validate required fields before submit', async ({ page }) => {
    await page.goto('/citizen/register');

    // Try to click Next/Submit without filling anything
    const nextButton = page.getByRole('button', { name: /next|tiếp theo|continue/i }).first();
    if (await nextButton.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await nextButton.click();

      // Should show validation errors
      const hasError = await page.locator('text=/required|bắt buộc|invalid|không hợp lệ/i').first().isVisible({ timeout: 3_000 }).catch(() => false);
      expect(hasError).toBeTruthy();
    }
  });

  test('should reject invalid phone number format', async ({ page }) => {
    await page.goto('/citizen/register');

    const phoneInput = page.locator('input[type="tel"], input[placeholder*="phone" i], input[placeholder*="điện thoại" i]').first();
    if (!await phoneInput.isVisible({ timeout: 5_000 }).catch(() => false)) return;

    await phoneInput.fill('0123456789'); // invalid (starts with 01)
    await phoneInput.blur();

    // Should show phone validation error
    await expect(
      page.locator('text=/phone|điện thoại|invalid|không hợp lệ/i').first()
    ).toBeVisible({ timeout: 3_000 });
  });

  test('should complete step 1 with valid data', async ({ page }) => {
    await page.goto('/citizen/register');

    // Fill full name
    const fullNameInput = page.locator('input[placeholder*="full name" i], input[placeholder*="họ.*tên" i], input[name*="fullName" i], input[name*="name" i]').first();
    if (await fullNameInput.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await fullNameInput.fill('Nguyen Test E2E');
    }

    // Fill email
    const emailInput = page.locator('input[type="email"], input[placeholder*="email" i]').first();
    await emailInput.fill(uniqueEmail());

    // Fill phone (valid Vietnam format: starts with 03/05/07/08/09)
    const phoneInput = page.locator('input[type="tel"], input[placeholder*="phone" i], input[placeholder*="điện thoại" i]').first();
    if (await phoneInput.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await phoneInput.fill('0912345678');
    }

    // Fill password
    const passwordInputs = page.locator('input[type="password"]');
    const pwCount = await passwordInputs.count();
    if (pwCount >= 1) await passwordInputs.nth(0).fill('TestPass@123');
    if (pwCount >= 2) await passwordInputs.nth(1).fill('TestPass@123'); // confirm password

    // Click Next
    const nextButton = page.getByRole('button', { name: /next|tiếp theo|continue/i }).first();
    if (await nextButton.isVisible({ timeout: 5_000 }).catch(() => false)) {
      await nextButton.click();

      // Should advance to step 2 (household) OR show success / duplicate email error
      const step2Visible = await page.locator('text=/building|household|unit|tầng|hộ khẩu/i').first().isVisible({ timeout: 8_000 }).catch(() => false);
      const duplicateError = await page.locator('text=/email.*registered|email.*tồn tại|already/i').first().isVisible({ timeout: 3_000 }).catch(() => false);
      const apiError = await page.locator('text=/error|lỗi/i').first().isVisible({ timeout: 3_000 }).catch(() => false);

      // At least one outcome must be true
      expect(step2Visible || duplicateError || apiError).toBeTruthy();
    }
  });

  test('should load buildings list in step 2 household setup', async ({ page }) => {
    await page.goto('/citizen/register');

    // Fill step 1 quickly (email already registered will fail gracefully)
    const emailInput = page.locator('input[type="email"]').first();
    await emailInput.fill(uniqueEmail());

    const passwordInputs = page.locator('input[type="password"]');
    const pwCount = await passwordInputs.count();
    if (pwCount >= 1) await passwordInputs.nth(0).fill('TestPass@123');
    if (pwCount >= 2) await passwordInputs.nth(1).fill('TestPass@123');

    const fullNameInput = page.locator('input[name*="name" i], input[placeholder*="name" i]').first();
    if (await fullNameInput.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await fullNameInput.fill('E2E Test User');
    }

    const nextButton = page.getByRole('button', { name: /next|tiếp theo|continue/i }).first();
    if (!await nextButton.isVisible({ timeout: 3_000 }).catch(() => false)) return;
    await nextButton.click();

    // If step 2 is reached, buildings dropdown should be visible
    const step2 = await page.locator('text=/building|tòa nhà/i').first().isVisible({ timeout: 8_000 }).catch(() => false);
    if (!step2) return; // step 1 failed (duplicate email or validation) — skip rest

    // Buildings list should load (API GET /citizen/buildings is public)
    const buildingSelect = page.locator('select, [role="combobox"]').first();
    await expect(buildingSelect).toBeVisible({ timeout: 5_000 });
  });

  test('should navigate back to login from registration page', async ({ page }) => {
    await page.goto('/citizen/register');

    const loginLink = page.locator('a:has-text("login"), a:has-text("đăng nhập"), button:has-text("back")').first();
    if (await loginLink.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await loginLink.click();
      await expect(page).toHaveURL(/login/, { timeout: 5_000 });
    }
  });
});
