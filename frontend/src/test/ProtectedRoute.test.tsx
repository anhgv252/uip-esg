import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import ProtectedRoute from '@/routes/ProtectedRoute'
import { AuthContext, type AuthContextValue } from '@/contexts/AuthContext'

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
    user: isAuthenticated ? { username: 'op', role: 'ROLE_OPERATOR' } : null,
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
      user: { username: 'op', role: 'ROLE_OPERATOR' },
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
})
