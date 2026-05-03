import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import ProtectedRoute from '@/routes/ProtectedRoute'
import { AuthContext, type AuthContextValue, type AuthUser } from '@/contexts/AuthContext'

const mockUser = (overrides: Partial<AuthUser> = {}): AuthUser => ({
  username: 'op',
  role: 'ROLE_OPERATOR',
  tenantId: 'default',
  tenantPath: 'city',
  scopes: [],
  allowedBuildings: [],
  ...overrides,
})

/**
 * Use createMemoryRouter so <Navigate> redirects terminate at a real route,
 * preventing the useLocation → re-render infinite loop in jsdom.
 */
function makeRouter(
  isAuthenticated: boolean,
  isLoading: boolean,
  requiredRole?: 'ROLE_ADMIN' | 'ROLE_OPERATOR' | 'ROLE_CITIZEN',
) {
  const ctxValue: AuthContextValue = {
    user: isAuthenticated ? mockUser() : null,
    isAuthenticated,
    isLoading,
    login: async () => {},
    logout: () => {},
  }

  const router = createMemoryRouter(
    [
      {
        path: '/dashboard',
        element: (
          <ProtectedRoute requiredRole={requiredRole}>
            <div>Protected Content</div>
          </ProtectedRoute>
        ),
      },
      { path: '/login', element: <div>Login Page</div> },
    ],
    { initialEntries: ['/dashboard'] },
  )

  return { router, ctxValue }
}

describe('ProtectedRoute', () => {
  it('shows spinner while loading', () => {
    const { router, ctxValue } = makeRouter(false, true)
    render(
      <AuthContext.Provider value={ctxValue}>
        <RouterProvider router={router} />
      </AuthContext.Provider>,
    )
    expect(screen.getByRole('progressbar')).toBeDefined()
  })

  it('renders children when authenticated', async () => {
    const { router, ctxValue } = makeRouter(true, false)
    render(
      <AuthContext.Provider value={ctxValue}>
        <RouterProvider router={router} />
      </AuthContext.Provider>,
    )
    expect(await screen.findByText('Protected Content')).toBeDefined()
  })

  it('redirects to /login when not authenticated', async () => {
    const { router, ctxValue } = makeRouter(false, false)
    render(
      <AuthContext.Provider value={ctxValue}>
        <RouterProvider router={router} />
      </AuthContext.Provider>,
    )
    expect(await screen.findByText('Login Page')).toBeDefined()
    expect(screen.queryByText('Protected Content')).toBeNull()
  })

  it('blocks access if requiredRole does not match', async () => {
    const ctxValue: AuthContextValue = {
      user: mockUser({ role: 'ROLE_OPERATOR' }),
      isAuthenticated: true,
      isLoading: false,
      login: async () => {},
      logout: () => {},
    }

    const router = createMemoryRouter(
      [
        {
          path: '/admin',
          element: (
            <ProtectedRoute requiredRole="ROLE_ADMIN">
              <div>Admin Content</div>
            </ProtectedRoute>
          ),
        },
        { path: '/dashboard', element: <div>Dashboard</div> },
      ],
      { initialEntries: ['/admin'] },
    )

    render(
      <AuthContext.Provider value={ctxValue}>
        <RouterProvider router={router} />
      </AuthContext.Provider>,
    )

    expect(await screen.findByText('Dashboard')).toBeDefined()
    expect(screen.queryByText('Admin Content')).toBeNull()
  })

  it('allows access when user role is in requiredRoles array', async () => {
    const ctxValue: AuthContextValue = {
      user: mockUser({ role: 'ROLE_OPERATOR' }),
      isAuthenticated: true,
      isLoading: false,
      login: async () => {},
      logout: () => {},
    }

    const router = createMemoryRouter(
      [
        {
          path: '/ai-workflow',
          element: (
            <ProtectedRoute requiredRoles={['ROLE_ADMIN', 'ROLE_OPERATOR']}>
              <div>AI Workflow Content</div>
            </ProtectedRoute>
          ),
        },
        { path: '/dashboard', element: <div>Dashboard</div> },
      ],
      { initialEntries: ['/ai-workflow'] },
    )

    render(
      <AuthContext.Provider value={ctxValue}>
        <RouterProvider router={router} />
      </AuthContext.Provider>,
    )

    expect(await screen.findByText('AI Workflow Content')).toBeDefined()
  })

  it('blocks access when user role is not in requiredRoles array', async () => {
    const ctxValue: AuthContextValue = {
      user: mockUser({ role: 'ROLE_CITIZEN' }),
      isAuthenticated: true,
      isLoading: false,
      login: async () => {},
      logout: () => {},
    }

    const router = createMemoryRouter(
      [
        {
          path: '/ai-workflow',
          element: (
            <ProtectedRoute requiredRoles={['ROLE_ADMIN', 'ROLE_OPERATOR']}>
              <div>AI Workflow Content</div>
            </ProtectedRoute>
          ),
        },
        { path: '/dashboard', element: <div>Dashboard</div> },
      ],
      { initialEntries: ['/ai-workflow'] },
    )

    render(
      <AuthContext.Provider value={ctxValue}>
        <RouterProvider router={router} />
      </AuthContext.Provider>,
    )

    expect(await screen.findByText('Dashboard')).toBeDefined()
    expect(screen.queryByText('AI Workflow Content')).toBeNull()
  })

  it('allows access when user has the required scope', async () => {
    const ctxValue: AuthContextValue = {
      user: mockUser({ scopes: ['workflow:manage', 'report:read'] }),
      isAuthenticated: true,
      isLoading: false,
      login: async () => {},
      logout: () => {},
    }

    const router = createMemoryRouter(
      [
        {
          path: '/scoped-page',
          element: (
            <ProtectedRoute requiredScope="workflow:manage">
              <div>Scoped Content</div>
            </ProtectedRoute>
          ),
        },
        { path: '/dashboard', element: <div>Dashboard</div> },
      ],
      { initialEntries: ['/scoped-page'] },
    )

    render(
      <AuthContext.Provider value={ctxValue}>
        <RouterProvider router={router} />
      </AuthContext.Provider>,
    )

    expect(await screen.findByText('Scoped Content')).toBeDefined()
  })

  it('blocks access when user lacks the required scope', async () => {
    const ctxValue: AuthContextValue = {
      user: mockUser({ scopes: ['report:read'] }),
      isAuthenticated: true,
      isLoading: false,
      login: async () => {},
      logout: () => {},
    }

    const router = createMemoryRouter(
      [
        {
          path: '/scoped-page',
          element: (
            <ProtectedRoute requiredScope="workflow:manage">
              <div>Scoped Content</div>
            </ProtectedRoute>
          ),
        },
        { path: '/dashboard', element: <div>Dashboard</div> },
      ],
      { initialEntries: ['/scoped-page'] },
    )

    render(
      <AuthContext.Provider value={ctxValue}>
        <RouterProvider router={router} />
      </AuthContext.Provider>,
    )

    expect(await screen.findByText('Dashboard')).toBeDefined()
    expect(screen.queryByText('Scoped Content')).toBeNull()
  })
})
