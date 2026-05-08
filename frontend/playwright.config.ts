import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for UIP Smart City E2E tests
 * Run with: npm run e2e
 *
 * Projects:
 *   chromium         — Desktop Chrome (primary)
 *   firefox          — Desktop Firefox (cross-browser)
 *   mobile-chrome    — Pixel 7 emulation  (Sprint 5 PWA tests)
 *   mobile-safari    — iPhone 14 emulation (Sprint 5 PWA tests)
 *
 * To add to CI pipeline, see commented YAML example below.
 */
export default defineConfig({
  tsconfig: './tsconfig.playwright.json',
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['list'],
    ...(process.env.CI ? [['github'] as ['github']] : []),
  ],

  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 8000,
    navigationTimeout: 15000,
    launchOptions: {
      slowMo: process.env.SLOW_MO ? parseInt(process.env.SLOW_MO) : 0,
    },
  },

  timeout: process.env.SLOW_MO ? 120000 : 45000,

  projects: [
    // ── Desktop browsers ────────────────────────────────────────────────────
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },

    // ── Mobile emulation (Sprint 5 PWA tests) ───────────────────────────────
    {
      name: 'mobile-chrome',
      use: {
        ...devices['Pixel 7'],
        // Grant notification permission for push-subscription tests
        permissions: ['notifications'],
      },
      // Only run specs that target mobile behavior
      testMatch: ['**/pwa-mobile.spec.ts', '**/citizen-rbac.spec.ts'],
    },
    {
      name: 'mobile-safari',
      use: {
        ...devices['iPhone 14'],
        permissions: ['notifications'],
      },
      testMatch: ['**/pwa-mobile.spec.ts'],
    },
  ],

  /* Start dev server only if not already running */
  webServer: {
    command: 'npm run dev -- --host 0.0.0.0',
    url: 'http://localhost:3000',
    reuseExistingServer: true,
    timeout: 120000,
    stdout: 'pipe',
    stderr: 'pipe',
  },

  outputDir: 'test-results',
});

/*
  CI Integration (add to .github/workflows/test.yml):

  e2e-tests:
    name: E2E (Playwright)
    runs-on: ubuntu-latest
    needs: frontend-tests
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install dependencies
        working-directory: frontend
        run: npm ci
      - name: Install Playwright browsers
        working-directory: frontend
        run: npx playwright install --with-deps chromium firefox
      - name: Build frontend for preview
        working-directory: frontend
        run: npm run build
      - name: Run E2E tests
        working-directory: frontend
        run: npm run e2e
        env:
          BASE_URL: http://localhost:3000
          CI: true
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: playwright-report
          path: frontend/playwright-report/
          retention-days: 7
*/
