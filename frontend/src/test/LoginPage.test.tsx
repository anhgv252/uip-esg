import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthContext, type AuthContextValue } from '@/contexts/AuthContext'
import LoginPage from '@/pages/LoginPage'

const mockLogin = vi.fn()

function renderLogin(overrides: Partial<AuthContextValue> = {}) {
  const ctxValue: AuthContextValue = {
    user: null,
    isAuthenticated: false,
    isLoading: false,
    login: mockLogin,
    logout: vi.fn(),
    ...overrides,
  }
  return render(
    <AuthContext.Provider value={ctxValue}>
      <MemoryRouter initialEntries={['/login']}>
        <LoginPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    mockLogin.mockReset()
  })

  it('renders username and password fields', () => {
    renderLogin()
    expect(screen.getByLabelText('username')).toBeDefined()
    expect(screen.getByLabelText('password')).toBeDefined()
  })

  it('shows validation error for empty username', async () => {
    renderLogin()
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() => {
      expect(screen.getByText(/at least 3 characters/i)).toBeDefined()
    })
  })

  it('shows validation error for short password', async () => {
    renderLogin()
    await userEvent.type(screen.getByLabelText('username'), 'admin')
    await userEvent.type(screen.getByLabelText('password'), '123')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() => {
      expect(screen.getByText(/at least 6 characters/i)).toBeDefined()
    })
  })

  it('calls login with entered credentials', async () => {
    mockLogin.mockResolvedValue(undefined)
    renderLogin()
    await userEvent.type(screen.getByLabelText('username'), 'admin')
    await userEvent.type(screen.getByLabelText('password'), 'Admin#123')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith({
        username: 'admin',
        password: 'Admin#123',
      })
    })
  })

  it('shows error alert on 401', async () => {
    mockLogin.mockRejectedValue({ response: { status: 401 } })
    renderLogin()
    await userEvent.type(screen.getByLabelText('username'), 'admin')
    await userEvent.type(screen.getByLabelText('password'), 'wrongpass')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() => {
      expect(screen.getByText(/invalid username or password/i)).toBeDefined()
    })
  })

  it('shows rate limit error on 429', async () => {
    mockLogin.mockRejectedValue({ response: { status: 429 } })
    renderLogin()
    await userEvent.type(screen.getByLabelText('username'), 'admin')
    await userEvent.type(screen.getByLabelText('password'), 'Admin#123x')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))
    await waitFor(() => {
      expect(screen.getByText(/too many login attempts/i)).toBeDefined()
    })
  })

  it('redirects when already authenticated', () => {
    renderLogin({ isAuthenticated: true })
    // Navigate happens — page should no longer show sign-in button in same container
    expect(screen.queryByRole('button', { name: /sign in/i })).toBeNull()
  })
})
