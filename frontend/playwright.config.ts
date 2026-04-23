import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for UIP Smart City E2E tests
 * Run with: npm run e2e
 * 
 * To add to CI pipeline, see commented YAML example below.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0, // No retries in CI to keep it fast
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list']
  ],
  
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 5000,
  },

  timeout: 30000, // 30s per test

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  /* Start dev server only if not already running */
  webServer: {
    command: 'npm run preview',
    url: 'http://localhost:3000',
    reuseExistingServer: true,
    timeout: 120000,
  },

  outputDir: 'playwright-report/test-results',
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
        run: npx playwright install --with-deps chromium
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
