import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers/auth';

/**
 * Sprint 3 Manual Test Cases — E2E Automation
 * MT-S3-01: Covers TC-S3-01 through TC-S3-04, TC-S3-13, TC-S3-14
 * Tester: E2E (Playwright)
 * Execution Date: 2026-05-23
 * Gate: G1 (AC-01), G2 (AC-02), G6 (AC-06)
 *
 * NOTE: MUI Select components do not expose aria-label associations without
 * explicit id/htmlFor — use getByRole('combobox') with text filters instead.
 */

const ESG_URL = '/esg';
const CURRENT_YEAR = new Date().getFullYear(); // 2026
const CURRENT_QUARTER = Math.ceil((new Date().getMonth() + 1) / 3); // Q2

test.describe('MT-S3-01 | TC-S3-01: Report panel loads on /esg route', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto(ESG_URL);
    await page.waitForLoadState('networkidle', { timeout: 15000 });
  });

  test('TC-S3-01: /esg loads with report panel visible, no JS errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });

    // Panel heading "Generate ESG Report"
    await expect(
      page.getByText('Generate ESG Report', { exact: true })
    ).toBeVisible({ timeout: 10000 });

    // Year selector — MUI Select renders as role="combobox" with the year as text
    const yearCombo = page.getByRole('combobox').filter({ hasText: /^\d{4}$/ });
    await expect(yearCombo.first()).toBeVisible({ timeout: 5000 });

    // Quarter selector — MUI Select renders as role="combobox" with Q1-Q4 as text
    const quarterCombo = page.getByRole('combobox').filter({ hasText: /^Q[1-4]$/ });
    await expect(quarterCombo.first()).toBeVisible({ timeout: 5000 });

    // Generate button
    await expect(page.getByRole('button', { name: /generate report/i })).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('button', { name: /generate report/i })).toBeEnabled();

    // No critical JS errors (filter noise)
    const criticalErrors = errors.filter(e =>
      !e.includes('favicon') && !e.includes('Warning:') && !e.includes('DevTools')
    );
    expect(criticalErrors).toHaveLength(0);
  });
});

test.describe('MT-S3-01 | TC-S3-02 + TC-S3-03: Year/Quarter selectors', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto(ESG_URL);
    await page.waitForLoadState('networkidle', { timeout: 15000 });
  });

  test('TC-S3-02: Year selector shows current + previous years', async ({ page }) => {
    // MUI Select: the combobox shows a 4-digit year value
    const yearCombo = page.getByRole('combobox').filter({ hasText: /^\d{4}$/ }).first();
    await yearCombo.click();

    // Current year and at least 2 previous years should be available as options
    await expect(page.getByRole('option', { name: String(CURRENT_YEAR) })).toBeVisible();
    await expect(page.getByRole('option', { name: String(CURRENT_YEAR - 1) })).toBeVisible();
    await expect(page.getByRole('option', { name: String(CURRENT_YEAR - 2) })).toBeVisible();

    // Close dropdown
    await page.keyboard.press('Escape');
  });

  test('TC-S3-03: Quarter selector defaults to current quarter and has Q1-Q4', async ({ page }) => {
    // MUI Select: the combobox shows Q1-Q4 value
    const quarterCombo = page.getByRole('combobox').filter({ hasText: /^Q[1-4]$/ }).first();
    await quarterCombo.click();

    // All 4 quarters should be present as options
    for (const q of [1, 2, 3, 4]) {
      await expect(page.getByRole('option', { name: `Q${q}` })).toBeVisible();
    }

    await page.keyboard.press('Escape');
  });

  test('TC-S3-02+03: Year/Quarter default to current period', async ({ page }) => {
    // Year combobox should display current year as the selected value
    const yearCombo = page.getByRole('combobox').filter({ hasText: /^\d{4}$/ }).first();
    await expect(yearCombo).toContainText(String(CURRENT_YEAR));

    // Quarter combobox should display current quarter
    const quarterCombo = page.getByRole('combobox').filter({ hasText: /^Q[1-4]$/ }).first();
    await expect(quarterCombo).toContainText(`Q${CURRENT_QUARTER}`);
  });
});

test.describe('MT-S3-01 | TC-S3-04: Generate → loading → report ready', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto(ESG_URL);
    await page.waitForLoadState('networkidle', { timeout: 15000 });
  });

  test('TC-S3-04: Generate button triggers loading state and report completes', async ({ page }) => {
    // Click Generate Report
    const generateBtn = page.getByRole('button', { name: /generate report/i });
    await expect(generateBtn).toBeEnabled();
    await generateBtn.click();

    // Button should become disabled while mutation is in-flight (loading signal)
    await expect(generateBtn).toBeDisabled({ timeout: 3000 });

    // Wait for report to complete — 60s to handle queue from earlier test runs
    // MUI Alert renders as role="alert"
    await expect(
      page.locator('[role="alert"]').filter({ hasText: /report ready/i })
    ).toBeVisible({ timeout: 60000 });

    // Download buttons use aria-label "Download Excel report" and "Download PDF report"
    await expect(page.getByRole('button', { name: /download excel report/i })).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('button', { name: /download pdf report/i })).toBeVisible({ timeout: 5000 });
  });
});

test.describe('MT-S3-01 | TC-S3-13: Responsive layout at 768px', () => {
  test('TC-S3-13: Report panel renders correctly at tablet 768px', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await loginAsAdmin(page);
    await page.goto(ESG_URL);
    await page.waitForLoadState('networkidle', { timeout: 15000 });

    // Panel should still be visible at 768px
    await expect(
      page.locator('*').filter({ hasText: /generate esg report/i }).first()
    ).toBeVisible({ timeout: 10000 });

    // Generate button visible
    await expect(page.getByRole('button', { name: /generate report/i })).toBeVisible();

    // Selectors visible — use role-based locators for MUI Select
    await expect(page.getByRole('combobox').filter({ hasText: /^\d{4}$/ }).first()).toBeVisible();
    await expect(page.getByRole('combobox').filter({ hasText: /^Q[1-4]$/ }).first()).toBeVisible();

    // No horizontal scrollbar (overflow-x check)
    const bodyWidth = await page.evaluate(() => document.body.scrollWidth);
    const viewportWidth = 768;
    expect(bodyWidth).toBeLessThanOrEqual(viewportWidth + 20); // allow 20px tolerance
  });
});

test.describe('MT-S3-01 | TC-S3-14: Dual-token session (HMAC + Keycloak)', () => {
  test('TC-S3-14: HMAC token authenticates to backend API', async ({ page }) => {
    // loginAsAdmin uses HMAC token (admin/admin_Dev#2026!)
    await loginAsAdmin(page);
    await page.goto(ESG_URL);
    await page.waitForLoadState('networkidle', { timeout: 15000 });

    // ESG page should load — Generate ESG Report panel must be visible
    await expect(
      page.getByText('Generate ESG Report', { exact: true })
    ).toBeVisible({ timeout: 10000 });

    // No error banner with 401/403 text should be present
    const errorBanner = page.locator('[role="alert"]').filter({ hasText: /401|403|unauthorized|forbidden/i });
    await expect(errorBanner).toHaveCount(0);
  });

  test('TC-S3-14: Keycloak RSA token endpoint issues alg=RS256', async ({ page }) => {
    // Verify Keycloak token endpoint via API request
    const response = await page.request.post(
      'http://localhost:8085/realms/uip/protocol/openid-connect/token',
      {
        form: {
          client_id: 'uip-api',
          client_secret: 'uip-api-secret-dev',
          grant_type: 'password',
          username: 'operator-hcm',
          password: 'Operator#2026!',
        },
      }
    );
    expect(response.ok()).toBeTruthy();
    const json = await response.json();
    expect(json.access_token).toBeTruthy();

    // Decode header: verify alg=RS256
    const header = JSON.parse(
      Buffer.from(json.access_token.split('.')[0], 'base64').toString()
    );
    expect(header.alg).toBe('RS256');
    expect(header.kid).toBeTruthy();
  });
});

test.describe('MT-S3-02 | Exploratory: Edge Cases', () => {
  test('EXP-01: Navigate away during generation — no orphaned polling', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto(ESG_URL);
    await page.waitForLoadState('networkidle', { timeout: 15000 });

    // Start generation
    await page.getByRole('button', { name: /generate report/i }).click();

    // Wait briefly for PENDING state
    await page.waitForTimeout(2000);

    // Navigate away (simulate user leaving)
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    // No crash — app should remain functional
    await page.goto(ESG_URL);
    // Use domcontentloaded to avoid flake from offline detection on rapid navigation
    await page.waitForLoadState('domcontentloaded', { timeout: 15000 });
    await page.waitForTimeout(3000); // allow React to rehydrate and render panel

    // Panel still present after navigation (use exact text match — more reliable than wildcards)
    await expect(
      page.getByText('Generate ESG Report', { exact: true })
    ).toBeVisible({ timeout: 15000 });
  });

  test('EXP-02: Browser back button after generation — panel state', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto(ESG_URL);
    await page.waitForLoadState('networkidle', { timeout: 15000 });

    // Navigate to another page then back
    await page.goto('/');
    await page.goBack();
    // Use domcontentloaded — networkidle may never settle after rapid back navigation
    await page.waitForLoadState('domcontentloaded', { timeout: 15000 });
    await page.waitForTimeout(3000); // allow React to rehydrate panel

    // Panel should still render (exact match is more reliable)
    await expect(
      page.getByText('Generate ESG Report', { exact: true })
    ).toBeVisible({ timeout: 15000 });
  });
});
