import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthContext, type AuthContextValue } from '@/contexts/AuthContext'
import AiWorkflowPage from '@/pages/AiWorkflowPage'

// Mock hooks so tests don't hit API
vi.mock('@/hooks/useWorkflowData', () => ({
  useProcessDefinitions: vi.fn(),
  useProcessInstances: vi.fn(),
  useInstanceVariables: vi.fn(),
  useProcessDefinitionXml: vi.fn(),
  useStartProcess: vi.fn(),
}))

// InstanceDetailDrawer uses useInstanceVariables internally — stub it out too
vi.mock('@/components/workflow/InstanceDetailDrawer', () => ({
  default: () => null,
}))

// Mock BpmnViewer — bpmn-js is a DOM renderer incompatible with jsdom
vi.mock('@/components/workflow/BpmnViewer', () => ({
  default: ({ xml }: { xml?: string }) => (
    <div data-testid="bpmn-viewer">{xml ? 'BPMN_RENDERED' : 'NO_XML'}</div>
  ),
}))

import {
  useProcessDefinitions,
  useProcessInstances,
  useInstanceVariables,
  useProcessDefinitionXml,
  useStartProcess,
} from '@/hooks/useWorkflowData'

const DEFINITIONS = [
  { id: 'def-1', key: 'aqi-alert', name: 'AQI Alert Process', version: 2, tenantId: null, deploymentId: 'd1', suspended: false },
  { id: 'def-2', key: 'esg-report', name: 'ESG Report Generation', version: 1, tenantId: null, deploymentId: 'd1', suspended: true },
]

const INSTANCES = [
  {
    id: 'inst-001',
    processDefinitionId: 'def-1',
    processDefinitionKey: 'aqi-alert',
    businessKey: 'BK-001',
    state: 'ACTIVE',
    startTime: '2026-04-20T10:00:00',
    variables: {},
  },
]

function makeAdminCtx(): AuthContextValue {
  return {
    user: { username: 'admin', role: 'ROLE_ADMIN' },
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
  }
}

function makeOperatorCtx(): AuthContextValue {
  return {
    user: { username: 'operator', role: 'ROLE_OPERATOR' },
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
  }
}

function renderPage(ctx: AuthContextValue = makeAdminCtx()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={ctx}>
        <MemoryRouter>
          <AiWorkflowPage />
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

describe('AiWorkflowPage', () => {
  beforeEach(() => {
    vi.mocked(useProcessInstances).mockReturnValue({
      data: { content: INSTANCES, totalElements: 1, totalPages: 1, number: 0 },
      isLoading: false,
      error: null,
    } as ReturnType<typeof useProcessInstances>)

    vi.mocked(useProcessDefinitions).mockReturnValue({
      data: DEFINITIONS,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useProcessDefinitions>)

    vi.mocked(useInstanceVariables).mockReturnValue({
      data: {},
      isLoading: false,
      error: null,
    } as ReturnType<typeof useInstanceVariables>)

    vi.mocked(useProcessDefinitionXml).mockReturnValue({
      data: undefined,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useProcessDefinitionXml>)

    vi.mocked(useStartProcess).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isSuccess: false,
      isError: false,
    } as unknown as ReturnType<typeof useStartProcess>)
  })

  it('renders page title', () => {
    renderPage()
    expect(screen.getByText('AI Workflow Dashboard')).toBeDefined()
  })

  it('renders two tabs', () => {
    renderPage()
    expect(screen.getByRole('tab', { name: /process instances/i })).toBeDefined()
    expect(screen.getByRole('tab', { name: /process definitions/i })).toBeDefined()
  })

  it('shows instances tab content by default', () => {
    renderPage()
    expect(screen.getByText('aqi-alert')).toBeDefined()
  })

  it('shows state filter dropdown in instances tab', () => {
    renderPage()
    expect(screen.getByLabelText('State')).toBeDefined()
  })

  it('shows instance count chip', () => {
    renderPage()
    expect(screen.getByText('1 instance')).toBeDefined()
  })

  it('switches to Definitions tab on click', async () => {
    renderPage()
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      expect(screen.getByText('AQI Alert Process')).toBeDefined()
    })
    expect(screen.getByText('ESG Report Generation')).toBeDefined()
  })

  it('shows Start button for each active definition when user is ADMIN', async () => {
    renderPage(makeAdminCtx())
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      const startBtns = screen.getAllByRole('button', { name: /start/i })
      // aqi-alert is active → 1 enabled button; esg-report is suspended → disabled
      expect(startBtns.length).toBeGreaterThanOrEqual(1)
    })
  })

  it('does not show Start button for OPERATOR users', async () => {
    renderPage(makeOperatorCtx())
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      expect(screen.getByText('AQI Alert Process')).toBeDefined()
    })
    expect(screen.queryByRole('button', { name: /start/i })).toBeNull()
  })

  it('shows suspended chip for suspended definition', async () => {
    renderPage()
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      expect(screen.getByText('Suspended')).toBeDefined()
    })
  })

  it('shows error alert when instances query fails', () => {
    vi.mocked(useProcessInstances).mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Network error'),
    } as ReturnType<typeof useProcessInstances>)

    renderPage()
    expect(screen.getByText('Failed to load instances')).toBeDefined()
  })

  it('shows loading spinner in instances tab while loading', () => {
    vi.mocked(useProcessInstances).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useProcessInstances>)

    renderPage()
    expect(screen.getByRole('progressbar')).toBeDefined()
  })

  it('opens StartProcess dialog when Start button is clicked (ADMIN)', async () => {
    renderPage(makeAdminCtx())
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      const startBtns = screen.getAllByRole('button', { name: /start/i })
      const enabledBtn = startBtns.find((b) => !b.hasAttribute('disabled'))
      expect(enabledBtn).toBeDefined()
    })

    const startBtns = screen.getAllByRole('button', { name: /start/i })
    const enabledBtn = startBtns.find((b) => !b.hasAttribute('disabled'))!
    await userEvent.click(enabledBtn)

    await waitFor(() => {
      expect(screen.getByText(/Start Process/)).toBeDefined()
    })
    expect(screen.getByLabelText('Variables (JSON)')).toBeDefined()
  })

  it('shows JSON error when invalid JSON entered in StartProcess dialog', async () => {
    renderPage(makeAdminCtx())
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      expect(screen.getByText('AQI Alert Process')).toBeDefined()
    })

    const startBtns = screen.getAllByRole('button', { name: /start/i })
    const enabledBtn = startBtns.find((b) => !b.hasAttribute('disabled'))!
    await userEvent.click(enabledBtn)

    await waitFor(() => {
      expect(screen.getByText(/Start Process/)).toBeDefined()
    })

    const jsonField = screen.getByLabelText('Variables (JSON)')
    await userEvent.clear(jsonField)
    await userEvent.type(jsonField, '{invalid}')

    const dialogStartBtn = screen.getAllByRole('button', { name: /^start$/i }).pop()!
    await userEvent.click(dialogStartBtn)

    await waitFor(() => {
      expect(screen.getByText('Invalid JSON')).toBeDefined()
    })
  })

  it('calls mutate with correct variables on valid JSON submit', async () => {
    const mutate = vi.fn()
    vi.mocked(useStartProcess).mockReturnValue({
      mutate,
      isPending: false,
      isSuccess: false,
      isError: false,
    } as unknown as ReturnType<typeof useStartProcess>)

    renderPage(makeAdminCtx())
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      expect(screen.getByText('AQI Alert Process')).toBeDefined()
    })

    const startBtns = screen.getAllByRole('button', { name: /start/i })
    const enabledBtn = startBtns.find((b) => !b.hasAttribute('disabled'))!
    await userEvent.click(enabledBtn)

    await waitFor(() => {
      expect(screen.getByText(/Start Process/)).toBeDefined()
    })

    const jsonField = screen.getByLabelText('Variables (JSON)')
    // Use fireEvent to set value directly (curly braces break userEvent.type)
    fireEvent.change(jsonField, { target: { value: '{"zone":"QUAN_1"}' } })

    const dialogStartBtn = screen.getAllByRole('button', { name: /^start$/i }).pop()!
    await userEvent.click(dialogStartBtn)

    await waitFor(() => {
      expect(mutate).toHaveBeenCalledWith(
        { processKey: 'aqi-alert', variables: { zone: 'QUAN_1' } },
        expect.objectContaining({ onSuccess: expect.any(Function) }),
      )
    })
  })

  it('closes StartProcess dialog when Cancel is clicked', async () => {
    renderPage(makeAdminCtx())
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      expect(screen.getByText('AQI Alert Process')).toBeDefined()
    })

    const startBtns = screen.getAllByRole('button', { name: /start/i })
    const enabledBtn = startBtns.find((b) => !b.hasAttribute('disabled'))!
    await userEvent.click(enabledBtn)

    await waitFor(() => {
      expect(screen.getByText(/Start Process/)).toBeDefined()
    })

    const cancelBtn = screen.getByRole('button', { name: /cancel/i })
    await userEvent.click(cancelBtn)

    await waitFor(() => {
      expect(screen.queryByText(/Start Process/)).toBeNull()
    })
  })

  it('shows definitions loading spinner', async () => {
    vi.mocked(useProcessDefinitions).mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useProcessDefinitions>)

    renderPage()
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      expect(screen.getByRole('progressbar')).toBeDefined()
    })
  })

  it('shows error alert when definitions query fails', async () => {
    vi.mocked(useProcessDefinitions).mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Server error'),
    } as ReturnType<typeof useProcessDefinitions>)

    renderPage()
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      expect(screen.getByText('Failed to load definitions')).toBeDefined()
    })
  })

  it('shows empty state when no definitions deployed', async () => {
    vi.mocked(useProcessDefinitions).mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useProcessDefinitions>)

    renderPage()
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      expect(screen.getByText('No definitions deployed')).toBeDefined()
    })
  })

  it('selects definition row and shows detail placeholder', async () => {
    renderPage()
    const defTab = screen.getByRole('tab', { name: /process definitions/i })
    await userEvent.click(defTab)

    await waitFor(() => {
      expect(screen.getByText('AQI Alert Process')).toBeDefined()
    })

    const defRow = screen.getByText('AQI Alert Process')
    await userEvent.click(defRow)

    await waitFor(() => {
      expect(screen.getByText('AQI Alert Process · v2')).toBeDefined()
    })
  })

  it('changes status filter and resets page', async () => {
    renderPage()

    const stateSelect = screen.getByLabelText('State')
    await userEvent.click(stateSelect)

    // Use role-based query to find the menuitem, not the chip
    const activeOption = screen.getByRole('option', { name: 'Active' })
    await userEvent.click(activeOption)

    // Filter change should trigger useProcessInstances with ACTIVE status
    expect(useProcessInstances).toHaveBeenCalledWith('ACTIVE', 0, 20)
  })

  it('shows pluralized instance count', () => {
    vi.mocked(useProcessInstances).mockReturnValue({
      data: { content: [], totalElements: 5, totalPages: 1, number: 0 },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useProcessInstances>)

    renderPage()
    expect(screen.getByText('5 instances')).toBeDefined()
  })
})
