import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import BpmnViewer from '@/components/workflow/BpmnViewer'

// bpmn-js manipulates real DOM canvas — mock the class so jsdom doesn't choke
const mockImportXML = vi.fn()
const mockFit = vi.fn()
const mockDestroy = vi.fn()

vi.mock('bpmn-js', () => ({
  default: vi.fn().mockImplementation(() => ({
    importXML: mockImportXML,
    get: vi.fn().mockReturnValue({ fit: mockFit }),
    destroy: mockDestroy,
  })),
}))

describe('BpmnViewer', () => {
  beforeEach(() => {
    mockImportXML.mockReset()
    mockFit.mockReset()
    mockDestroy.mockReset()
  })

  it('shows placeholder text when xml is null', () => {
    render(<BpmnViewer xml={null} />)
    expect(screen.getByText(/Select a process definition/i)).toBeDefined()
  })

  it('shows placeholder text when xml is undefined', () => {
    render(<BpmnViewer xml={undefined} />)
    expect(screen.getByText(/Select a process definition/i)).toBeDefined()
  })

  it('calls importXML when xml prop is provided', async () => {
    mockImportXML.mockResolvedValue(undefined)

    await act(() => {
      render(<BpmnViewer xml='<?xml version="1.0"?><definitions/>' />)
    })

    await vi.waitFor(() => expect(mockImportXML).toHaveBeenCalledOnce())
  })

  it('calls canvas.fit after successful import', async () => {
    mockImportXML.mockResolvedValue(undefined)

    await act(() => {
      render(<BpmnViewer xml='<?xml version="1.0"?><definitions/>' />)
    })

    await vi.waitFor(() => expect(mockFit).toHaveBeenCalledOnce())
  })

  it('shows error alert when importXML rejects', async () => {
    mockImportXML.mockRejectedValue(new Error('Invalid BPMN XML'))

    await act(() => {
      render(<BpmnViewer xml='<broken/>' />)
    })

    expect(await screen.findByText('Invalid BPMN XML')).toBeDefined()
  })

  it('destroys viewer on unmount', async () => {
    mockImportXML.mockResolvedValue(undefined)

    const { unmount } = await act(() => {
      return render(<BpmnViewer xml='<?xml version="1.0"?><definitions/>' />)
    })

    unmount()

    expect(mockDestroy).toHaveBeenCalledOnce()
  })

  it('renders root container with custom height via inline style', () => {
    render(<BpmnViewer xml={null} height={600} />)
    const root = screen.getByTestId('bpmn-viewer-root')
    // MUI Box forwards `style` prop as inline style when using the style attribute directly
    expect(root.style.height).toBe('600px')
  })
})
