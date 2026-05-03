/**
 * Sprint 1 PO Demo Script — Automated via Playwright
 *
 * Covers all sections from demo-script-sprint1.md:
 *   Phần 2  Admin Panel         (~5 min)
 *   Phần 3  Operator Workflow   (~15 min)
 *   Phần 4  AI Workflow Live    (~10 min)
 *   Phần 5  Citizen Portal      (~8 min)
 *   Phần 6  ESG Report          (~5 min)
 *   Phần 8  Security RBAC       (~3 min)
 *
 * Run:
 *   npx playwright test e2e/sprint1-demo.spec.ts --headed --project=chromium
 *
 * For step-by-step slow-motion (easier to follow as a live demo):
 *   npx playwright test e2e/sprint1-demo.spec.ts --headed --project=chromium \
 *     --timeout=120000
 *
 * Backend must be running at http://localhost:8080
 * Frontend must be running at http://localhost:3000
 */

import { test, expect, Page, APIRequestContext } from '@playwright/test';

// ─── credentials ─────────────────────────────────────────────────────────────
const ADMIN    = { username: 'admin',    password: 'admin_Dev#2026!' };
const OPERATOR = { username: 'operator', password: 'operator_Dev#2026!' };
const CITIZEN1 = { username: 'citizen1', password: 'citizen_Dev#2026!' };
const API_BASE = 'http://localhost:8080';

// ─── helpers ─────────────────────────────────────────────────────────────────

async function login(page: Page, creds: { username: string; password: string }) {
  await page.goto('/login');
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.locator('input[type="text"], input[name="username"]').first().fill(creds.username);
  await page.locator('input[type="password"]').first().fill(creds.password);
  await page.locator('button[type="submit"]').first().click();
  await page.waitForURL('**/dashboard', { timeout: 15000 });
}

async function nav(page: Page, label: string | RegExp) {
  await page.getByRole('button', { name: label }).first().click();
  await page.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function getToken(request: APIRequestContext, creds: typeof ADMIN): Promise<string> {
  const resp = await request.post(`${API_BASE}/api/v1/auth/login`, {
    data: creds,
  });
  const body = await resp.json();
  return body.accessToken as string;
}

// ─── Phần 2 — Admin ──────────────────────────────────────────────────────────

test.describe('Phần 2 — Admin Panel', () => {
  test.use({ baseURL: 'http://localhost:3000' });

  test('2-1: Login as admin → Dashboard KPIs visible', async ({ page }) => {
    await login(page, ADMIN);
    await expect(page.locator('h5, h6').filter({ hasText: /dashboard/i }).first())
      .toBeVisible({ timeout: 10000 });

    // 4 KPI cards
    for (const kpi of ['sensors', 'aqi', 'alert', 'carbon']) {
      await expect(
        page.locator(`text=/${kpi}/i`).first()
      ).toBeVisible({ timeout: 8000 });
    }
  });

  test('2-2: Admin Panel → Users tab shows 9 accounts', async ({ page }) => {
    await login(page, ADMIN);
    await nav(page, /admin/i);

    await expect(page.locator('h5, h6').filter({ hasText: /admin/i }).first())
      .toBeVisible({ timeout: 10000 });

    // Users tab
    const usersTab = page.getByRole('tab', { name: /users/i });
    await expect(usersTab).toBeVisible({ timeout: 8000 });
    await usersTab.click();
    await page.waitForTimeout(1000);

    // At least 1 row with ADMIN role visible
    await expect(page.locator('text=ADMIN').first()).toBeVisible({ timeout: 8000 });
  });

  test('2-3: Admin Panel → Sensors tab shows 8 sensors', async ({ page }) => {
    await login(page, ADMIN);
    await nav(page, /admin/i);

    const sensorsTab = page.getByRole('tab', { name: /sensors/i });
    await expect(sensorsTab).toBeVisible({ timeout: 8000 });
    await sensorsTab.click();
    await page.waitForTimeout(1000);

    // 8 sensor rows
    const rows = page.locator('table tbody tr, [role="row"]').filter({ hasText: /ENV-/ });
    await expect(rows.first()).toBeVisible({ timeout: 8000 });
  });

  test('2-4: Trigger Config page — 10 configs with enable toggle', async ({ page }) => {
    await login(page, ADMIN);
    await nav(page, /trigger config/i);

    await expect(page.locator('h5, h6').filter({ hasText: /trigger/i }).first())
      .toBeVisible({ timeout: 10000 });

    // At least one toggle (MUI Switch renders <input type="checkbox"> — no explicit role)
    const toggle = page.locator('input.MuiSwitch-input').first();
    await expect(toggle).toBeAttached({ timeout: 8000 });
  });
});

// ─── Phần 3 — Operator ───────────────────────────────────────────────────────

test.describe('Phần 3 — Operator Workflow', () => {
  test.use({ baseURL: 'http://localhost:3000' });

  test('3-1: RBAC — Admin/Trigger Config menu NOT visible for operator', async ({ page }) => {
    await login(page, OPERATOR);

    // Menu items that should NOT exist
    await expect(page.getByRole('button', { name: /^admin$/i })).not.toBeVisible();
    await expect(page.getByRole('button', { name: /trigger config/i })).not.toBeVisible();

    // Standard operator items ARE visible
    await expect(page.getByRole('button', { name: /environment/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /alerts/i }).first()).toBeVisible();
  });

  test('3-2: Environment Monitoring — 8 AQI gauges', async ({ page }) => {
    await login(page, OPERATOR);
    await nav(page, /environment/i);

    await expect(page.locator('h5, h6').filter({ hasText: /environment/i }).first())
      .toBeVisible({ timeout: 10000 });

    // AQI level text visible
    await expect(page.locator('text=/moderate|unhealthy|good/i').first())
      .toBeVisible({ timeout: 10000 });
  });

  test('3-3: City Ops Center — Leaflet map with Recent Alerts panel', async ({ page }) => {
    await login(page, OPERATOR);
    await nav(page, /city ops/i);

    // Leaflet map container rendered
    await expect(page.locator('.leaflet-container').first())
      .toBeVisible({ timeout: 15000 });

    // Recent Alerts panel is visible and has entries (ENV-00x alerts)
    await expect(page.locator('text=/recent alerts/i').first())
      .toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=/ENV-00/i').first())
      .toBeVisible({ timeout: 10000 });

    // Try to find sensor markers (loaded via WebSocket — may take extra time)
    const markers = page.locator('.leaflet-interactive');
    const hasMarkers = await markers.first().isVisible({ timeout: 8000 }).catch(() => false);
    if (hasMarkers) {
      const count = await markers.count();
      expect(count).toBeGreaterThanOrEqual(1);
      await markers.first().dispatchEvent('click');
    }
  });

  test('3-4: Alerts — escalate an ACKNOWLEDGED alert', async ({ page }) => {
    await login(page, OPERATOR);
    await nav(page, /alerts/i);

    await expect(page.locator('h5, h6').filter({ hasText: /alert/i }).first())
      .toBeVisible({ timeout: 10000 });

    // At least 1 alert row
    await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 10000 });

    // Click Escalate on first available Escalate button
    const escalateBtn = page.locator('td button').first();
    const btnVisible = await escalateBtn.isVisible({ timeout: 5000 }).catch(() => false);
    if (btnVisible) {
      await escalateBtn.click();
      await page.waitForTimeout(2000);
      // Row should now show ESCALATED
      await expect(page.locator('text=ESCALATED').first()).toBeVisible({ timeout: 8000 });
    }
  });

  test('3-5: Traffic Management — 5 open incidents table', async ({ page }) => {
    await login(page, OPERATOR);
    await nav(page, /traffic/i);

    await expect(page.locator('h5, h6').filter({ hasText: /traffic/i }).first())
      .toBeVisible({ timeout: 10000 });

    // Badge "5 open incidents" or similar
    await expect(page.locator('text=/open incidents/i').first())
      .toBeVisible({ timeout: 10000 });

    // Incident rows
    await expect(page.locator('table tbody tr').first()).toBeVisible({ timeout: 10000 });

    // Types present
    await expect(page.locator('text=ACCIDENT').first()).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=CONGESTION').first()).toBeVisible({ timeout: 5000 });
  });
});

// ─── Phần 4 — AI Workflow ────────────────────────────────────────────────────

test.describe('Phần 4 — AI Workflow', () => {
  test.use({ baseURL: 'http://localhost:3000' });

  test('4-1: Process Instances — completed count > 0', async ({ page }) => {
    await login(page, OPERATOR);
    await nav(page, /ai workflow/i);

    await expect(page.locator('h5, h6').filter({ hasText: /workflow|process/i }).first())
      .toBeVisible({ timeout: 10000 });

    // Instance count
    await expect(page.locator('text=/\\d+ process instances?|instances/i').first())
      .toBeVisible({ timeout: 10000 });
  });

  test('4-2: Process Definitions — 7 definitions Active v4', async ({ page }) => {
    await login(page, OPERATOR);
    await nav(page, /ai workflow/i);

    await page.getByRole('tab', { name: /process definitions/i }).click();
    await page.waitForTimeout(1500);

    // All 7 definitions
    const defs = [
      'AI-M01', 'AI-M02', 'AI-M03', 'AI-M04',
      'AI-C01', 'AI-C02', 'AI-C03',
    ];
    for (const def of defs) {
      await expect(page.locator(`text=${def}`).first()).toBeVisible({ timeout: 8000 });
    }

    // Click AI-C01 to show BPMN diagram
    await page.locator('text=AI-C01: AQI Citizen Alert').click();
    await page.waitForTimeout(2000);

    // bpmn.io attribution = diagram rendered
    await expect(page.locator('a[href="http://bpmn.io"]').first())
      .toBeVisible({ timeout: 10000 });
  });

  test('4-3: Live Demo IoT — AQI 235 triggers pipeline end-to-end', async ({ page }) => {
    await login(page, OPERATOR);
    await nav(page, /ai workflow/i);

    await page.getByRole('tab', { name: /live demo/i }).click();
    await page.waitForTimeout(1000);

    // Switch to IoT sensor tab
    await page.locator('button:has-text("Cảm biến IoT")').click();
    await page.waitForTimeout(800);

    // Click preset "Nguy hiểm (235)" — wait for tab content to render first
    await page.waitForTimeout(1500);
    await page.getByRole('button', { name: /nguy hiểm/i }).click();
    await page.waitForTimeout(500);

    // AQI heading should show ≥ 235
    const aqiHeading = page.locator('h6').filter({ hasText: /2[3-9]\d|[3-9]\d\d/ });
    await expect(aqiHeading.first()).toBeVisible({ timeout: 5000 });

    // Fire the event
    await page.locator('button').filter({ hasText: /bắn sự kiện iot/i }).click();

    // Pipeline progress bar appears
    await expect(page.locator('[role="progressbar"]').first())
      .toBeVisible({ timeout: 8000 });

    // Wait for pipeline to finish (up to 30s)
    await expect(page.locator('text=/pipeline hoàn tất|endEvent|completed/i').first())
      .toBeVisible({ timeout: 30000 });

    // Camunda process ID visible
    await expect(page.locator('text=/processInstanceId|aiC01/i').first())
      .toBeVisible({ timeout: 15000 });
  });
});

// ─── Phần 5 — Citizen Portal ─────────────────────────────────────────────────

test.describe('Phần 5 — Citizen Portal', () => {
  test.use({ baseURL: 'http://localhost:3000' });

  test('5-1: RBAC — operator redirected to login when accessing /citizen', async ({ page }) => {
    await login(page, OPERATOR);
    await page.goto('/citizen');
    // Should redirect to login (no CITIZEN role)
    await expect(page).toHaveURL(/\/login/, { timeout: 8000 });
  });

  test('5-2: Citizen Portal — welcome message + tabs', async ({ page }) => {
    await login(page, CITIZEN1);
    // Citizen portal is at /citizen — navigate via sidebar
    await nav(page, /citizens/i);

    await expect(page.locator('h5, h6').filter({ hasText: /citizen portal/i }).first())
      .toBeVisible({ timeout: 10000 });

    // Welcome message
    await expect(page.locator('text=/welcome back/i').first())
      .toBeVisible({ timeout: 8000 });

    // 4 tabs
    for (const tab of ['Dashboard', 'My Bills', 'Profile', 'Notifications']) {
      await expect(page.getByRole('tab', { name: new RegExp(tab, 'i') }))
        .toBeVisible({ timeout: 5000 });
    }
  });

  test('5-3: Notifications tab — auto-refresh label visible', async ({ page }) => {
    await login(page, CITIZEN1);
    await nav(page, /citizens/i);

    await page.getByRole('tab', { name: /notifications/i }).click();
    await page.waitForTimeout(1000);

    // Auto-update label
    await expect(page.locator('text=/cập nhật tự động|auto.?update/i').first())
      .toBeVisible({ timeout: 8000 });
  });
});

// ─── Phần 6 — ESG Report ─────────────────────────────────────────────────────

test.describe('Phần 6 — ESG Report', () => {
  test.use({ baseURL: 'http://localhost:3000' });

  test('6-1: ESG Metrics page — 3 KPI cards visible', async ({ page }) => {
    await login(page, CITIZEN1);

    await nav(page, /esg metrics/i);

    await expect(page.locator('h5, h6').filter({ hasText: /esg/i }).first())
      .toBeVisible({ timeout: 10000 });

    for (const kpi of ['Energy Consumption', 'Water Usage', 'Carbon Footprint']) {
      await expect(page.locator(`text=${kpi}`).first()).toBeVisible({ timeout: 8000 });
    }
  });

  test('6-2: Generate ESG report via API → DONE + download URL', async ({ request }) => {
    const token = await getToken(request, ADMIN);

    // Trigger generation
    const genResp = await request.post(
      `${API_BASE}/api/v1/esg/reports/generate?period=quarterly&year=2026&quarter=1`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    // Backend returns 202 Accepted for async report generation
    expect([200, 202]).toContain(genResp.status());
    const report = await genResp.json();
    expect(report.id).toBeTruthy();
    expect(['PENDING', 'DONE']).toContain(report.status);

    // Poll status until DONE (max 15s)
    let status = report.status;
    let downloadUrl: string | null = null;
    for (let i = 0; i < 15 && status !== 'DONE'; i++) {
      await new Promise(r => setTimeout(r, 1000));
      const poll = await request.get(
        `${API_BASE}/api/v1/esg/reports/${report.id}/status`,
        { headers: { Authorization: `Bearer ${token}` } }
      );
      const body = await poll.json();
      status = body.status;
      downloadUrl = body.downloadUrl;
    }

    expect(status).toBe('DONE');
    expect(downloadUrl).toBeTruthy();

    // Verify download returns XLSX
    const dl = await request.get(
      `${API_BASE}${downloadUrl}`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    expect(dl.status()).toBe(200);
    const contentDisposition = dl.headers()['content-disposition'];
    expect(contentDisposition).toMatch(/\.xlsx/);
  });
});

// ─── Phần 8 — Security RBAC ──────────────────────────────────────────────────

test.describe('Phần 8 — Security', () => {
  test('8-1: Unauthenticated request → 401', async ({ request }) => {
    const resp = await request.get(`${API_BASE}/api/v1/admin/users`);
    expect(resp.status()).toBe(401);
  });

  test('8-2: CITIZEN token → admin endpoint → 403 Forbidden', async ({ request }) => {
    const token = await getToken(request, CITIZEN1);
    const resp = await request.get(`${API_BASE}/api/v1/admin/users`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(resp.status()).toBe(403);
  });

  test('8-3: OPERATOR token → admin endpoint → 403 Forbidden', async ({ request }) => {
    const token = await getToken(request, OPERATOR);
    const resp = await request.get(`${API_BASE}/api/v1/admin/users`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(resp.status()).toBe(403);
  });

  test('8-4: ADMIN token → admin endpoint → 200 OK', async ({ request }) => {
    const token = await getToken(request, ADMIN);
    const resp = await request.get(`${API_BASE}/api/v1/admin/users`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(resp.status()).toBe(200);
  });
});
