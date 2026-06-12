import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import WorkflowWizard from '@/components/workflow/WorkflowWizard';
import type { WorkflowTemplate } from '@/types/workflowTemplate';

// TemplateGallery renders the WORKFLOW_TEMPLATES list — no need to mock it.
// But we stub the heavy TemplateGallery to a simpler version to keep tests fast
// and deterministic, while still testing the wizard flow.

const MOCK_TEMPLATE: WorkflowTemplate = {
  id: 'aqi-threshold-v1',
  name: 'AQI Critical Threshold Alert',
  description: 'Test description',
  category: 'AIR_QUALITY',
  icon: 'Air',
  bpmnKey: 'aqi-threshold-alert',
  tags: ['air-quality'],
  estimatedDurationMinutes: 2,
  params: [
    {
      key: 'aqiThreshold',
      label: 'AQI Threshold',
      type: 'number',
      required: true,
      defaultValue: 200,
    },
    {
      key: 'severity',
      label: 'Severity',
      type: 'select',
      required: true,
      options: ['WARNING', 'HIGH', 'CRITICAL'],
      defaultValue: 'HIGH',
    },
    {
      key: 'sensorId',
      label: 'Sensor ID',
      type: 'string',
      required: false,
    },
  ],
};

const TEMPLATE_WITH_BOOL: WorkflowTemplate = {
  id: 'noise-alert-v1',
  name: 'Noise Alert',
  description: 'Noise description',
  category: 'AIR_QUALITY',
  icon: 'VolumeUp',
  bpmnKey: 'noise-alert-workflow',
  tags: ['noise'],
  estimatedDurationMinutes: 5,
  params: [
    {
      key: 'noiseThresholdDb',
      label: 'Noise Threshold (dB)',
      type: 'number',
      required: true,
      defaultValue: 70,
    },
    {
      key: 'notifyResident',
      label: 'Notify Resident',
      type: 'boolean',
      required: false,
      defaultValue: true,
    },
  ],
};

// Stub TemplateGallery to avoid rendering all 10 templates in tests.
// The stub calls onSelectTemplate with MOCK_TEMPLATE when the test button is clicked.
vi.mock('@/components/workflow/TemplateGallery', () => ({
  default: ({ onSelectTemplate }: { onSelectTemplate: (t: WorkflowTemplate) => void }) => (
    <div data-testid="template-gallery">
      <button
        onClick={() => onSelectTemplate(MOCK_TEMPLATE)}
        aria-label="Select AQI template"
      >
        Select AQI template
      </button>
      <button
        onClick={() => onSelectTemplate(TEMPLATE_WITH_BOOL)}
        aria-label="Select Noise Alert template"
      >
        Select Noise Alert template
      </button>
    </div>
  ),
}));

// Mock react-router-dom so useNavigate is available without a Router context.
// vi.hoisted ensures mockNavigate is initialised before vi.mock hoisting runs.
const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

function renderWizard(
  overrides: Partial<{
    open: boolean;
    onClose: () => void;
    onDeploy: (t: WorkflowTemplate, v: Record<string, unknown>) => Promise<void>;
    isPending: boolean;
  }> = {},
) {
  const defaults = {
    open: true,
    onClose: vi.fn(),
    onDeploy: vi.fn().mockResolvedValue(undefined) as (
      t: WorkflowTemplate,
      v: Record<string, unknown>,
    ) => Promise<void>,
    isPending: false,
    ...overrides,
  };
  render(
    <WorkflowWizard
      open={defaults.open}
      onClose={defaults.onClose}
      onDeploy={defaults.onDeploy}
      isPending={defaults.isPending}
    />,
  );
  return defaults;
}

/** Navigate the wizard to step 3 (Review & Deploy) using MOCK_TEMPLATE. */
async function reachStep3(onDeploy?: (t: WorkflowTemplate, v: Record<string, unknown>) => Promise<void>) {
  const mocks = renderWizard(onDeploy ? { onDeploy } : {});
  await userEvent.click(screen.getByRole('button', { name: 'Select AQI template' }));
  await waitFor(() => expect(screen.getByLabelText('AQI Threshold')).toBeDefined());
  await userEvent.click(screen.getByRole('button', { name: /continue to review step/i }));
  await waitFor(() => expect(screen.getByText('Ready to deploy')).toBeDefined());
  return mocks;
}

describe('WorkflowWizard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Test 1
  it('renders step 1 with template gallery on open', () => {
    renderWizard();
    expect(screen.getByText('New Workflow')).toBeDefined();
    expect(screen.getByText('Choose Template')).toBeDefined();
    expect(screen.getByTestId('template-gallery')).toBeDefined();
  });

  // Test 2
  it('shows stepper with 3 steps', () => {
    renderWizard();
    expect(screen.getByText('Choose Template')).toBeDefined();
    expect(screen.getByText('Customize')).toBeDefined();
    expect(screen.getByText('Review & Deploy')).toBeDefined();
  });

  // Test 3
  it('advances to step 2 when a template is selected', async () => {
    renderWizard();
    const selectBtn = screen.getByRole('button', { name: 'Select AQI template' });
    await userEvent.click(selectBtn);

    await waitFor(() => {
      expect(screen.getByText(/Selected:/)).toBeDefined();
      expect(screen.getByText('AQI Critical Threshold Alert')).toBeDefined();
    });
  });

  // Test 4
  it('step 2 renders form fields for template params', async () => {
    renderWizard();
    await userEvent.click(screen.getByRole('button', { name: 'Select AQI template' }));

    await waitFor(() => {
      expect(screen.getByLabelText('AQI Threshold')).toBeDefined();
      expect(screen.getByLabelText('Severity')).toBeDefined();
      expect(screen.getByLabelText('Sensor ID')).toBeDefined();
    });
  });

  // Test 5
  it('step 2 renders boolean param as Switch', async () => {
    renderWizard();
    await userEvent.click(screen.getByRole('button', { name: 'Select Noise Alert template' }));

    await waitFor(() => {
      const switchEl = screen.getByRole('checkbox', { name: 'Notify Resident' });
      expect(switchEl).toBeDefined();
    });
  });

  // Test 6
  it('can advance from step 2 to step 3 with all required fields filled', async () => {
    renderWizard();
    await userEvent.click(screen.getByRole('button', { name: 'Select AQI template' }));

    await waitFor(() => {
      expect(screen.getByLabelText('AQI Threshold')).toBeDefined();
    });

    // aqiThreshold already has defaultValue=200, severity defaultValue='HIGH' — click Next
    const nextBtn = screen.getByRole('button', { name: /continue to review step/i });
    await userEvent.click(nextBtn);

    await waitFor(() => {
      expect(screen.getByText('Ready to deploy')).toBeDefined();
    });
  });

  // Test 7
  it('step 3 shows review summary with template name and variables', async () => {
    renderWizard();
    await userEvent.click(screen.getByRole('button', { name: 'Select AQI template' }));

    await waitFor(() => {
      expect(screen.getByLabelText('AQI Threshold')).toBeDefined();
    });

    const nextBtn = screen.getByRole('button', { name: /continue to review step/i });
    await userEvent.click(nextBtn);

    await waitFor(() => {
      expect(screen.getByText('AQI Critical Threshold Alert')).toBeDefined();
      expect(screen.getByText('AQI Threshold')).toBeDefined();
    });
  });

  // Test 8
  it('can go back from step 2 to step 1', async () => {
    renderWizard();
    await userEvent.click(screen.getByRole('button', { name: 'Select AQI template' }));

    await waitFor(() => {
      expect(screen.getByLabelText('AQI Threshold')).toBeDefined();
    });

    const backBtn = screen.getByRole('button', { name: /go back to previous step/i });
    await userEvent.click(backBtn);

    await waitFor(() => {
      expect(screen.getByTestId('template-gallery')).toBeDefined();
    });
  });

  // Test 9 — updated: must click through confirmation dialog before onDeploy fires
  it('deploy button calls onDeploy with correct template and variables', async () => {
    const onDeploy = vi.fn().mockResolvedValue(undefined) as (
      t: WorkflowTemplate,
      v: Record<string, unknown>,
    ) => Promise<void>;
    await reachStep3(onDeploy);

    const deployBtn = screen.getByRole('button', { name: /deploy workflow/i });
    await userEvent.click(deployBtn);

    // Confirmation dialog opens — click Confirm Deploy
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /confirm deploy/i })).toBeDefined(),
    );
    await userEvent.click(screen.getByRole('button', { name: /confirm deploy/i }));

    await waitFor(() => {
      expect(onDeploy).toHaveBeenCalledWith(
        expect.objectContaining({ bpmnKey: 'aqi-threshold-alert' }),
        expect.objectContaining({ aqiThreshold: 200, severity: 'HIGH' }),
      );
    });
  });

  // Test 10
  it('required field validation prevents advancing to step 3', async () => {
    renderWizard();
    // Use Noise Alert template: noiseThresholdDb is required
    await userEvent.click(screen.getByRole('button', { name: 'Select Noise Alert template' }));

    await waitFor(() => {
      expect(screen.getByLabelText('Noise Threshold (dB)')).toBeDefined();
    });

    // Clear the required field
    const thresholdField = screen.getByLabelText('Noise Threshold (dB)');
    await userEvent.clear(thresholdField);
    // Leave it empty and click Next — should stay on step 2
    const nextBtn = screen.getByRole('button', { name: /continue to review step/i });
    await userEvent.click(nextBtn);

    // Step 3 should not be shown
    await waitFor(() => {
      expect(screen.queryByText('Ready to deploy')).toBeNull();
    });
  });

  // Test 11
  it('shows "Deploying…" label and disabled deploy button when isPending is true', async () => {
    renderWizard({ isPending: true });
    await userEvent.click(screen.getByRole('button', { name: 'Select AQI template' }));
    await waitFor(() => expect(screen.getByLabelText('AQI Threshold')).toBeDefined());

    await userEvent.click(screen.getByRole('button', { name: /continue to review step/i }));
    await waitFor(() => expect(screen.getByText('Ready to deploy')).toBeDefined());

    const deployBtn = screen.getByRole('button', { name: /deploy workflow/i });
    expect(deployBtn).toHaveProperty('disabled', true);
    expect(screen.getByText('Deploying…')).toBeDefined();
  });

  // Test 12
  it('calls onClose when Cancel is clicked', async () => {
    const onClose = vi.fn();
    renderWizard({ onClose });

    const cancelBtn = screen.getByRole('button', { name: /cancel wizard/i });
    await userEvent.click(cancelBtn);

    expect(onClose).toHaveBeenCalledOnce();
  });

  // ── New tests — Task #22 ───────────────────────────────────────────────────

  // Test 13
  it('shows deploy confirmation dialog when Deploy clicked', async () => {
    await reachStep3();

    await userEvent.click(screen.getByRole('button', { name: /deploy workflow/i }));

    await waitFor(() => {
      expect(screen.getByText('Confirm Deployment')).toBeDefined();
      expect(screen.getByText(/Are you sure\? This will deploy/)).toBeDefined();
      expect(screen.getByText('AQI Critical Threshold Alert')).toBeDefined();
    });
  });

  // Test 14
  it('calls onDeploy only after confirmation, not on Cancel', async () => {
    const onDeploy = vi.fn().mockResolvedValue(undefined) as (
      t: WorkflowTemplate,
      v: Record<string, unknown>,
    ) => Promise<void>;
    await reachStep3(onDeploy);

    await userEvent.click(screen.getByRole('button', { name: /deploy workflow/i }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /confirm deploy/i })).toBeDefined(),
    );

    // Cancel — onDeploy must NOT have been called
    await userEvent.click(screen.getByRole('button', { name: /cancel deployment/i }));
    expect(onDeploy).not.toHaveBeenCalled();

    // Open confirmation again and confirm this time
    await userEvent.click(screen.getByRole('button', { name: /deploy workflow/i }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /confirm deploy/i })).toBeDefined(),
    );
    await userEvent.click(screen.getByRole('button', { name: /confirm deploy/i }));

    await waitFor(() => expect(onDeploy).toHaveBeenCalledOnce());
  });

  // Test 15
  it('shows success state after successful deploy', async () => {
    const onDeploy = vi.fn().mockResolvedValue(undefined) as (
      t: WorkflowTemplate,
      v: Record<string, unknown>,
    ) => Promise<void>;
    await reachStep3(onDeploy);

    await userEvent.click(screen.getByRole('button', { name: /deploy workflow/i }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /confirm deploy/i })).toBeDefined(),
    );
    await userEvent.click(screen.getByRole('button', { name: /confirm deploy/i }));

    await waitFor(() => {
      expect(screen.getByText('Workflow deployed successfully!')).toBeDefined();
      expect(screen.getByText('AQI Critical Threshold Alert')).toBeDefined();
      expect(
        screen.getByRole('button', { name: /view in process instances/i }),
      ).toBeDefined();
      expect(
        screen.getByRole('button', { name: /close wizard after success/i }),
      ).toBeDefined();
    });
  });

  // Test 16
  it('shows error state after failed deploy', async () => {
    const onDeploy = vi.fn().mockRejectedValue(new Error('Network timeout')) as (
      t: WorkflowTemplate,
      v: Record<string, unknown>,
    ) => Promise<void>;
    await reachStep3(onDeploy);

    await userEvent.click(screen.getByRole('button', { name: /deploy workflow/i }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /confirm deploy/i })).toBeDefined(),
    );
    await userEvent.click(screen.getByRole('button', { name: /confirm deploy/i }));

    await waitFor(() => {
      expect(screen.getByText(/Deployment failed: Network timeout/)).toBeDefined();
    });
  });

  // Test 17
  it('try again button clears error and returns to review step', async () => {
    const onDeploy = vi.fn().mockRejectedValue(new Error('Server error')) as (
      t: WorkflowTemplate,
      v: Record<string, unknown>,
    ) => Promise<void>;
    await reachStep3(onDeploy);

    await userEvent.click(screen.getByRole('button', { name: /deploy workflow/i }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /confirm deploy/i })).toBeDefined(),
    );
    await userEvent.click(screen.getByRole('button', { name: /confirm deploy/i }));
    await waitFor(() => expect(screen.getByText(/Deployment failed/)).toBeDefined());

    await userEvent.click(screen.getByRole('button', { name: /try deploy again/i }));

    await waitFor(() => {
      expect(screen.queryByText(/Deployment failed/)).toBeNull();
      expect(screen.getByText('Ready to deploy')).toBeDefined();
    });
  });

  // Test 18
  it("'View in Process Instances' button navigates and closes wizard", async () => {
    const onClose = vi.fn();
    const onDeploy = vi.fn().mockResolvedValue(undefined) as (
      t: WorkflowTemplate,
      v: Record<string, unknown>,
    ) => Promise<void>;
    renderWizard({ onDeploy, onClose });

    await userEvent.click(screen.getByRole('button', { name: 'Select AQI template' }));
    await waitFor(() => expect(screen.getByLabelText('AQI Threshold')).toBeDefined());
    await userEvent.click(screen.getByRole('button', { name: /continue to review step/i }));
    await waitFor(() => expect(screen.getByText('Ready to deploy')).toBeDefined());

    await userEvent.click(screen.getByRole('button', { name: /deploy workflow/i }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /confirm deploy/i })).toBeDefined(),
    );
    await userEvent.click(screen.getByRole('button', { name: /confirm deploy/i }));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /view in process instances/i })).toBeDefined(),
    );

    await userEvent.click(
      screen.getByRole('button', { name: /view in process instances/i }),
    );

    expect(mockNavigate).toHaveBeenCalledWith('/ai-workflow');
    expect(onClose).toHaveBeenCalledOnce();
  });
});
