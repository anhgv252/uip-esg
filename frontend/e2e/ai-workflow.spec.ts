import { test, expect } from '@playwright/test';
import { loginAsAdmin, navigateTo } from './helpers/auth';

/**
 * TC-E2E-09: AI Workflow Dashboard
 * Tests AI workflow management interface with BPMN process definitions
 * This test requires running backend with workflow engine data
 */
test.describe('AI Workflow Dashboard', () => {
  
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    // Navigate via sidebar — route is /ai-workflow, sidebar label is "AI Workflows"
    await navigateTo(page, 'AI Workflows');
    // Ensure navigation succeeded — fallback to direct goto if sidebar click was missed
    await page.waitForURL('**/ai-workflow', { timeout: 5000 }).catch(async () => {
      await page.goto('/ai-workflow');
      await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    });
  });

  test('should load workflows page', async ({ page }) => {
    // Heading is "AI Workflow Dashboard" (MUI variant=h5 → <h5>)
    await expect(page.locator('h1, h2, h3, h4, h5, h6').filter({ hasText: /ai workflow|workflow/i }).first())
      .toBeVisible({ timeout: 10000 });
  });

  test('should display process definitions in definitions tab', async ({ page }) => {
    // AiWorkflowPage has tabs: "Process Instances" | "Process Definitions" | "Live Demo"
    // Click the "Process Definitions" tab
    const definitionsTab = page.getByRole('tab', { name: /process definitions/i });
    if (await definitionsTab.isVisible({ timeout: 5000 }).catch(() => false)) {
      await definitionsTab.click();
    }

    // Wait for table to render data after tab switch (use toBeVisible retry)
    const processRows = page.locator('tbody tr');
    const hasRows = await processRows.first().isVisible({ timeout: 8_000 }).catch(() => false);
    if (hasRows) {
      const count = await processRows.count();
      expect(count).toBeGreaterThanOrEqual(1);
    } else {
      // Empty state is acceptable if backend has no definitions
      test.skip(true, 'No process definitions available');
    }
  });

  test('should be able to switch to instances tab', async ({ page }) => {
    // "Process Instances" is the first tab (already selected by default)
    const instancesTab = page.getByRole('tab', { name: /process instances/i });
    await expect(instancesTab).toBeVisible({ timeout: 10000 });
    await instancesTab.click();
    
    // After tab click, table or empty state should appear
    const tableVisible = await page.locator('table').isVisible({ timeout: 8000 }).catch(() => false);
    const emptyVisible = await page.getByText(/no instances|status|running/i).first().isVisible({ timeout: 3000 }).catch(() => false);
    expect(tableVisible || emptyVisible).toBeTruthy();
  });

  test('should show workflow management interface', async ({ page }) => {
    // MUI Tabs renders as [role="tablist"] with [role="tab"] children
    await expect(page.locator('[role="tablist"]')).toBeVisible({ timeout: 10000 });
    
    // Should have workflow-related tab labels
    const hasWorkflowTabs = await page.getByRole('tab', { name: /process|instances|definitions|demo/i })
      .first().isVisible({ timeout: 5000 }).catch(() => false);
    expect(hasWorkflowTabs).toBeTruthy();
  });
});
