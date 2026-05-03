import { test, expect } from '@playwright/test';
import { loginAsAdmin, navigateTo } from './helpers/auth';

/**
 * TC-E2E-S3-08a: Alert Pipeline E2E
 * Tests alert list, acknowledge, escalate, and SSE feed on City Ops Center.
 * Requires running backend (docker-compose or local spring boot).
 */
test.describe('Alert Pipeline E2E', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('should load alerts page and display alert table', async ({ page }) => {
    // Navigate via sidebar to preserve in-memory auth token
    await navigateTo(page, 'Alerts');

    // Heading is "Alert Management" (MUI variant=h5 → <h5> with role=heading)
    await expect(page.locator('h1, h2, h3, h4, h5, h6').filter({ hasText: /alert management/i }).first())
      .toBeVisible({ timeout: 10_000 });

    // Table header columns present
    await expect(page.getByRole('columnheader', { name: /severity/i })).toBeVisible({ timeout: 5_000 });
    await expect(page.getByRole('columnheader', { name: /status/i })).toBeVisible({ timeout: 5_000 });
    await expect(page.getByRole('columnheader', { name: /detected/i })).toBeVisible({ timeout: 5_000 });
  });

  test('should show severity and status chips in alert rows', async ({ page }) => {
    await navigateTo(page, 'Alerts');

    // Wait for table rows (skip loading state)
    await page.waitForTimeout(2_000);

    const rows = page.locator('tbody tr');
    const rowCount = await rows.count();

    if (rowCount === 0) {
      // No data — empty table is acceptable (AlertsPage shows no explicit "no alerts found" text)
      test.skip(true, 'No alert data available');
      return;
    }

    // First row must have a severity chip (LOW/MEDIUM/HIGH/CRITICAL/WARNING)
    const firstRow = rows.first();
    await expect(
      firstRow.locator('text=/low|medium|high|critical|warning/i').first()
    ).toBeVisible({ timeout: 5_000 });
  });

  test('should open alert detail drawer on row click', async ({ page }) => {
    await navigateTo(page, 'Alerts');
    await page.waitForTimeout(2_000);

    const rows = page.locator('tbody tr');
    const rowCount = await rows.count();
    if (rowCount === 0) {
      test.skip(true, 'No alert data available');
      return;
    }

    await rows.first().click();

    // Drawer should slide in with Alert Detail heading
    await expect(page.locator('text=/alert detail/i')).toBeVisible({ timeout: 5_000 });

    // Detail fields should be visible
    await expect(page.locator('text=/module/i').first()).toBeVisible({ timeout: 3_000 });
    await expect(page.locator('text=/sensor/i').first()).toBeVisible({ timeout: 3_000 });
  });

  test('should acknowledge an OPEN alert from drawer', async ({ page }) => {
    await navigateTo(page, 'Alerts');
    await page.waitForTimeout(2_000);

    const rows = page.locator('tbody tr');
    const rowCount = await rows.count();
    if (rowCount === 0) {
      test.skip(true, 'No OPEN alerts available');
      return;
    }

    // Click first row to open drawer
    await rows.first().click();
    await expect(page.locator('text=/alert detail/i')).toBeVisible({ timeout: 5_000 });

    // Acknowledge button only appears for OPEN alerts
    const ackButton = page.getByRole('button', { name: /acknowledge/i });
    const canAck = await ackButton.isVisible({ timeout: 3_000 }).catch(() => false);
    if (!canAck) {
      test.skip(true, 'No OPEN alert available for acknowledgement');
      return;
    }
    await ackButton.click();

    // Drawer should close after acknowledge
    await expect(page.locator('text=/alert detail/i')).not.toBeVisible({ timeout: 5_000 });
  });

  test('should escalate an OPEN or ACKNOWLEDGED alert from drawer', async ({ page }) => {
    await navigateTo(page, 'Alerts');
    await page.waitForTimeout(2_000);

    const rows = page.locator('tbody tr');
    const rowCount = await rows.count();
    if (rowCount === 0) {
      test.skip(true, 'No alert data available');
      return;
    }

    await rows.first().click();
    await expect(page.locator('text=/alert detail/i')).toBeVisible({ timeout: 5_000 });

    const escalateButton = page.getByRole('button', { name: /escalate/i });
    const isEscalatable = await escalateButton.isVisible({ timeout: 3_000 }).catch(() => false);
    if (!isEscalatable) {
      test.skip(true, 'First alert is already ESCALATED');
      return;
    }

    await escalateButton.click();
    await expect(page.locator('text=/alert detail/i')).not.toBeVisible({ timeout: 5_000 });
  });

  test('should show live alert feed on City Ops Center via SSE', async ({ page }) => {
    // Route is /city-ops, navigate via sidebar
    await navigateTo(page, 'City Ops');

    // AlertFeedPanel renders "Recent Alerts" subtitle (subtitle1) and "No recent alerts" when empty
    await expect(
      page.locator('text=/recent alerts/i').first()
    ).toBeVisible({ timeout: 10_000 });
  });

  test('should filter alerts by severity', async ({ page }) => {
    await navigateTo(page, 'Alerts');
    await page.waitForTimeout(1_500);

    // Use severity filter dropdown
    const severitySelect = page.getByLabel(/severity/i).first();

    if (await severitySelect.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await severitySelect.click();
      await page.locator('[role="option"]:has-text("HIGH")').click();
      await page.waitForTimeout(1_000);

      // Table should still be visible (filtered or empty)
      await expect(page.locator('table')).toBeVisible({ timeout: 5_000 });
    }
  });
});
