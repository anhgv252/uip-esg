import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { rest } from 'msw'
import { setupServer } from 'msw/node'

/**
 * Tenant Admin Dashboard — Component Tests
 * Sprint 5: MVP2-13 (5 pages)
 *
 * Patterns:
 * - QueryClient per test (no cache sharing)
 * - MSW for API mocking
 * - RTL for rendering and interaction
 */

// ============================================================================
// Shared helpers
// ============================================================================

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })
}

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      {ui}
    </QueryClientProvider>
  )
}

// ============================================================================
// MSW handlers
// ============================================================================

const tenantAdminHandlers = [
  rest.get('/api/v1/tenant/overview', (_req, res, ctx) => {
    return res(ctx.json({
      totalUsers: 42,
      totalBuildings: 8,
      activeAlerts: 3,
      activeSensors: 15,
      recentActivity: [
        { id: '1', action: 'User invited', timestamp: '2026-06-23T10:00:00Z' },
        { id: '2', action: 'Alert acknowledged', timestamp: '2026-06-23T09:30:00Z' },
      ],
    }))
  }),

  rest.get('/api/v1/admin/users', (_req, res, ctx) => {
    return res(ctx.json({
      content: [
        { id: '1', username: 'user1', email: 'user1@example.com', role: 'CITIZEN', active: true },
        { id: '2', username: 'user2', email: 'user2@example.com', role: 'OPERATOR', active: true },
      ],
      totalElements: 2,
      totalPages: 1,
    }))
  }),

  rest.get('/api/v1/tenant/config', (_req, res, ctx) => {
    return res(ctx.json({
      features: { tenant_management: true, ai_workflow: true },
      branding: { primaryColor: '#1976d2', logo: '/logo.png' },
    }))
  }),

  rest.get('/api/v1/citizen/buildings', (_req, res, ctx) => {
    return res(ctx.json([
      { id: '1', name: 'Building A', location: 'HCM', sensorCount: 5, active: true },
      { id: '2', name: 'Building B', location: 'HN', sensorCount: 3, active: false },
    ]))
  }),

  rest.get('/api/v1/tenant/usage', (_req, res, ctx) => {
    return res(ctx.json({
      period: { from: '2026-06-01', to: '2026-06-30' },
      stats: { apiCalls: 15000, activeUsers: 12, alertsTriggered: 8 },
    }))
  }),
]

const server = setupServer(...tenantAdminHandlers)

beforeEach(() => {
  server.resetHandlers()
})

// ============================================================================
// Overview Page Tests
// ============================================================================

describe('TenantAdmin — OverviewPage', () => {
  // TODO: Import OverviewPage component when implemented
  // import OverviewPage from '@/pages/tenant-admin/OverviewPage'

  it.skip('renders 4 stat cards with correct values', async () => {
    // renderWithProviders(<OverviewPage />)
    // await waitFor(() => {
    //   expect(screen.getByText('42')).toBeInTheDocument() // totalUsers
    //   expect(screen.getByText('8')).toBeInTheDocument()  // totalBuildings
    //   expect(screen.getByText('3')).toBeInTheDocument()  // activeAlerts
    //   expect(screen.getByText('15')).toBeInTheDocument() // activeSensors
    // })
  })

  it.skip('renders recent activity feed', async () => {
    // renderWithProviders(<OverviewPage />)
    // await waitFor(() => {
    //   expect(screen.getByText(/User invited/)).toBeInTheDocument()
    //   expect(screen.getByText(/Alert acknowledged/)).toBeInTheDocument()
    // })
  })

  it.skip('quick action "Add User" navigates to user management', async () => {
    // renderWithProviders(<OverviewPage />)
    // fireEvent.click(screen.getByRole('button', { name: /add user/i }))
    // expect(mockNavigate).toHaveBeenCalledWith('/tenant-admin/users')
  })

  it.skip('fetches data on mount via React Query', async () => {
    // renderWithProviders(<OverviewPage />)
    // Verify API call was made
    // await waitFor(() => {
    //   // Check loading state resolves
    // })
  })
})

// ============================================================================
// User Management Page Tests
// ============================================================================

describe('TenantAdmin — UserManagementPage', () => {
  it.skip('renders user table with pagination', async () => {
    // renderWithProviders(<UserManagementPage />)
    // await waitFor(() => {
    //   expect(screen.getByText('user1')).toBeInTheDocument()
    //   expect(screen.getByText('user2')).toBeInTheDocument()
    // })
  })

  it.skip('invite user: valid email sends invite', async () => {
    // server.use(
    //   rest.post('/api/v1/tenant/users/invite', (_req, res, ctx) => {
    //     return res(ctx.status(200))
    //   })
    // )
    // renderWithProviders(<UserManagementPage />)
    // fireEvent.click(screen.getByRole('button', { name: /invite/i }))
    // fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'new@example.com' } })
    // fireEvent.click(screen.getByRole('button', { name: /send invite/i }))
    // await waitFor(() => {
    //   expect(screen.getByText(/invite sent/i)).toBeInTheDocument()
    // })
  })

  it.skip('invite user: invalid email shows validation error', async () => {
    // renderWithProviders(<UserManagementPage />)
    // Open invite dialog
    // Enter invalid email
    // Verify error message shown
  })

  it.skip('invite user: duplicate email shows error', async () => {
    // server.use(
    //   rest.post('/api/v1/tenant/users/invite', (_req, res, ctx) => {
    //     return res(ctx.status(409))
    //   })
    // )
    // Verify 409 error message displayed
  })

  it.skip('role filter: selecting CITIZEN shows only citizens', async () => {
    // Click role filter dropdown
    // Select CITIZEN
    // Verify table re-renders with only CITIZEN rows
  })

  it.skip('user list respects tenant isolation', async () => {
    // Verify API call includes tenant context
    // Verify no cross-tenant users visible
  })
})

// ============================================================================
// Building Config Page Tests
// ============================================================================

describe('TenantAdmin — BuildingConfigPage', () => {
  it.skip('renders building list for current tenant', async () => {
    // renderWithProviders(<BuildingConfigPage />)
    // await waitFor(() => {
    //   expect(screen.getByText('Building A')).toBeInTheDocument()
    //   expect(screen.getByText('Building B')).toBeInTheDocument()
    // })
  })

  it.skip('toggle active status updates building', async () => {
    // Click toggle on Building B (inactive → active)
    // Verify PUT request sent
    // Verify UI updates
  })

  it.skip('add building with valid data creates building', async () => {
    // server.use(
    //   rest.post('/api/v1/citizen/buildings', (_req, res, ctx) => {
    //     return res(ctx.status(201))
    //   })
    // )
  })
})

// ============================================================================
// Usage Report Page Tests
// ============================================================================

describe('TenantAdmin — UsageReportPage', () => {
  it.skip('renders usage stats with date range', async () => {
    // renderWithProviders(<UsageReportPage />)
    // await waitFor(() => {
    //   expect(screen.getByText('15,000')).toBeInTheDocument() // apiCalls
    // })
  })

  it.skip('date range picker defaults to current month', async () => {
    // Verify default date range = June 1 - June 30
  })

  it.skip('chart renders with mock data', async () => {
    // Verify recharts component renders
    // Check chart container exists
  })

  it.skip('export CSV triggers download', async () => {
    // Click export button
    // Verify download initiated (mock window.location or blob creation)
  })

  it.skip('cross-tenant data not leaked in usage stats', async () => {
    // Verify API response only contains own tenant data
  })
})

// ============================================================================
// Settings Page Tests
// ============================================================================

describe('TenantAdmin — SettingsPage', () => {
  it.skip('renders feature flags from tenant config', async () => {
    // renderWithProviders(<SettingsPage />)
    // await waitFor(() => {
    //   expect(screen.getByText(/tenant_management/i)).toBeInTheDocument()
    //   expect(screen.getByText(/ai_workflow/i)).toBeInTheDocument()
    // })
  })

  it.skip('toggle feature flag saves to backend', async () => {
    // server.use(
    //   rest.put('/api/v1/tenant/config', (_req, res, ctx) => {
    //     return res(ctx.status(200))
    //   })
    // )
    // Toggle a feature flag
    // Verify PUT request sent with updated config
  })

  it.skip('alert threshold update and save', async () => {
    // Update threshold input
    // Click save
    // Verify PUT request with new threshold
  })

  it.skip('settings changes invalidate cache', async () => {
    // Update setting
    // Verify subsequent fetch returns fresh data (no stale cache)
  })

  it.skip('form validation: required fields cannot be empty', async () => {
    // Clear required field
    // Verify validation error
  })
})
