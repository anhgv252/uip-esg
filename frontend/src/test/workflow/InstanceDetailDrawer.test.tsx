import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import InstanceDetailDrawer from '@/components/workflow/InstanceDetailDrawer'
import type { ProcessInstance } from '@/api/workflow'

// Mock the hook, not the API — avoids unhandled rejection from rejected promises
vi.mock('@/hooks/useWorkflowData', () => ({
  useInstanceVariables: vi.fn(),
}))

import { useInstanceVariables } from '@/hooks/useWorkflowData'

const INSTANCE: ProcessInstance = {
  id: 'inst-001',
  processDefinitionId: 'def-1',
  processDefinitionKey: 'aqi-alert-process',
  businessKey: 'BK-2026-001',
  state: 'ACTIVE',
  startTime: '2026-04-20T10:00:00',
  variables: {},
}

function stubVars(overrides: Partial<ReturnType<typeof useInstanceVariables>> = {}) {
  vi.mocked(useInstanceVariables).mockReturnValue({
    data: {},
    isLoading: false,
    error: null,
    ...overrides,
  } as ReturnType<typeof useInstanceVariables>)
}

describe('InstanceDetailDrawer', () => {
  beforeEach(() => stubVars())

  it('renders nothing visible when instance is null', () => {
    render(<InstanceDetailDrawer instance={null} onClose={vi.fn()} />)
    expect(screen.queryByText('Process Instance')).toBeNull()
  })

  it('renders drawer title and instance id when open', () => {
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText('Process Instance')).toBeDefined()
    expect(screen.getByText('inst-001')).toBeDefined()
  })

  it('shows process key and business key', () => {
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText('aqi-alert-process')).toBeDefined()
    expect(screen.getByText('BK-2026-001')).toBeDefined()
  })

  it('shows ACTIVE state chip', () => {
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText('ACTIVE')).toBeDefined()
  })

  it('shows COMPLETED state chip', () => {
    render(<InstanceDetailDrawer instance={{ ...INSTANCE, state: 'COMPLETED' }} onClose={vi.fn()} />)
    expect(screen.getByText('COMPLETED')).toBeDefined()
  })

  it('shows AI Decision section when AI variables present', () => {
    stubVars({
      data: { aiDecision: 'P1_WARNING', aiConfidence: 0.92, zone: 'QUAN_1' },
    })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText('AI Decision')).toBeDefined()
    expect(screen.getByText('aiDecision')).toBeDefined()
    expect(screen.getByText('P1_WARNING')).toBeDefined()
  })

  it('does not show AI Decision section when no AI variables', () => {
    stubVars({ data: { zone: 'QUAN_1', sensorId: 'SENSOR-001' } })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.queryByText('AI Decision')).toBeNull()
  })

  it('shows All Variables accordion with the variable count', () => {
    stubVars({ data: { zone: 'QUAN_1', sensorId: 'SENSOR-AIR-001' } })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText(/All Variables \(2\)/)).toBeDefined()
  })

  it('renders variable keys and values in All Variables section', () => {
    stubVars({ data: { zone: 'QUAN_1', sensorId: 'SENSOR-AIR-001' } })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText('zone')).toBeDefined()
    expect(screen.getByText('QUAN_1')).toBeDefined()
  })

  it('shows loading spinner while fetching variables', () => {
    stubVars({ data: undefined, isLoading: true })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByRole('progressbar')).toBeDefined()
  })

  it('shows error alert when variables fetch fails', () => {
    stubVars({ data: undefined, isLoading: false, error: new Error('Network error') })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText('Failed to load variables')).toBeDefined()
  })

  it('calls onClose when close button is clicked', async () => {
    const onClose = vi.fn()
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={onClose} />)

    const closeBtn = screen.getByRole('button', { name: /close/i })
    await userEvent.click(closeBtn)
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('renders boolean variable as chip', () => {
    stubVars({ data: { notified: true } })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText('true')).toBeDefined()
  })

  it('renders null variable as dash', () => {
    stubVars({ data: { optionalField: null } })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(1)
  })

  it('renders numeric variable as string', () => {
    stubVars({ data: { aqiValue: 250 } })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText('250')).toBeDefined()
  })

  it('renders object variable as formatted JSON', () => {
    stubVars({ data: { metadata: { key: 'value', nested: true } } })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText(/"key"/)).toBeDefined()
    expect(screen.getByText(/"value"/)).toBeDefined()
  })

  it('shows formatted start date', () => {
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText('20/04/2026 10:00:00')).toBeDefined()
  })

  it('shows "No additional variables" when only AI vars present', () => {
    stubVars({ data: { aiDecision: 'P0_EMERGENCY', aiConfidence: 0.99 } })
    render(<InstanceDetailDrawer instance={INSTANCE} onClose={vi.fn()} />)
    expect(screen.getByText('No additional variables')).toBeDefined()
  })

  it('renders EXTERNALLY_TERMINATED state chip', () => {
    render(
      <InstanceDetailDrawer
        instance={{ ...INSTANCE, state: 'EXTERNALLY_TERMINATED' }}
        onClose={vi.fn()}
      />,
    )
    expect(screen.getByText('EXTERNALLY_TERMINATED')).toBeDefined()
  })
})
