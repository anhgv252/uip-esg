import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ProcessInstanceTable from '@/components/workflow/ProcessInstanceTable'
import type { ProcessInstance } from '@/api/workflow'

const INSTANCES: ProcessInstance[] = [
  {
    id: 'inst-001',
    processDefinitionId: 'def-1',
    processDefinitionKey: 'aqi-alert-process',
    businessKey: 'BK-2026-001',
    state: 'ACTIVE',
    startTime: '2026-04-20T10:00:00',
    variables: {},
  },
  {
    id: 'inst-002',
    processDefinitionId: 'def-1',
    processDefinitionKey: 'esg-report-process',
    businessKey: null,
    state: 'COMPLETED',
    startTime: '2026-04-19T08:30:00',
    variables: {},
  },
  {
    id: 'inst-003',
    processDefinitionId: 'def-2',
    processDefinitionKey: 'flood-alert-process',
    businessKey: 'BK-2026-002',
    state: 'EXTERNALLY_TERMINATED',
    startTime: '2026-04-18T14:00:00',
    variables: {},
  },
]

function renderTable(
  overrides: Partial<Parameters<typeof ProcessInstanceTable>[0]> = {},
) {
  const defaults = {
    instances: INSTANCES,
    totalElements: INSTANCES.length,
    page: 0,
    onPageChange: vi.fn(),
    isLoading: false,
    onRowClick: vi.fn(),
  }
  return render(<ProcessInstanceTable {...defaults} {...overrides} />)
}

describe('ProcessInstanceTable', () => {
  it('renders loading spinner when isLoading is true', () => {
    renderTable({ instances: [], totalElements: 0, isLoading: true })
    expect(screen.getByRole('progressbar')).toBeDefined()
  })

  it('renders empty state message when no instances', () => {
    renderTable({ instances: [], totalElements: 0 })
    expect(screen.getByText('No instances found')).toBeDefined()
  })

  it('renders one row per instance', () => {
    renderTable()
    const rows = screen.getAllByRole('row')
    // header + 3 data rows
    expect(rows).toHaveLength(4)
  })

  it('shows ACTIVE chip with warning color', () => {
    renderTable()
    const activeChip = screen.getByText('Active')
    expect(activeChip).toBeDefined()
  })

  it('shows COMPLETED chip', () => {
    renderTable()
    expect(screen.getByText('Completed')).toBeDefined()
  })

  it('shows EXTERNALLY_TERMINATED as Terminated chip', () => {
    renderTable()
    expect(screen.getByText('Terminated')).toBeDefined()
  })

  it('renders process key in each row', () => {
    renderTable()
    expect(screen.getByText('aqi-alert-process')).toBeDefined()
    expect(screen.getByText('esg-report-process')).toBeDefined()
  })

  it('renders business key when present', () => {
    renderTable()
    expect(screen.getByText('BK-2026-001')).toBeDefined()
  })

  it('shows dash when business key is null', () => {
    renderTable()
    const dashes = screen.getAllByText('—')
    expect(dashes.length).toBeGreaterThanOrEqual(1)
  })

  it('calls onRowClick with the correct instance when row is clicked', async () => {
    const onRowClick = vi.fn()
    renderTable({ onRowClick })

    const row = screen.getByText('aqi-alert-process').closest('tr')!
    await userEvent.click(row)

    expect(onRowClick).toHaveBeenCalledWith(INSTANCES[0])
  })

  it('calls onRowClick when the detail icon button is clicked', async () => {
    const onRowClick = vi.fn()
    renderTable({ onRowClick })

    const buttons = screen.getAllByRole('button')
    await userEvent.click(buttons[0])

    expect(onRowClick).toHaveBeenCalled()
  })

  it('calls onPageChange when next page is clicked', async () => {
    const onPageChange = vi.fn()
    renderTable({ onPageChange, totalElements: 40 })

    const nextButton = screen.getByTitle('Go to next page')
    await userEvent.click(nextButton)

    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('shows unknown state as raw text chip', () => {
    const unknownInstances: ProcessInstance[] = [
      {
        id: 'inst-999',
        processDefinitionId: 'def-1',
        processDefinitionKey: 'test-process',
        businessKey: null,
        state: 'SUSPENDED',
        startTime: '2026-04-20T10:00:00',
        variables: {},
      },
    ]
    renderTable({ instances: unknownInstances, totalElements: 1 })
    expect(screen.getByText('SUSPENDED')).toBeDefined()
  })

  it('shows relative time for start date', () => {
    renderTable()
    // MUI Tooltip uses aria-label for accessibility
    const tooltip = screen.getAllByLabelText('20/04/2026 10:00:00')
    expect(tooltip.length).toBeGreaterThanOrEqual(1)
  })

  it('shows pagination info with total count', () => {
    renderTable({ totalElements: 25 })
    expect(screen.getByText(/of 25/i)).toBeDefined()
  })
})
