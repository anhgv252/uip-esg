import { lazy } from 'react'
import { type RouteObject } from 'react-router-dom'
import AppShell from '@/components/AppShell'
import ProtectedRoute from '@/routes/ProtectedRoute'

// Lazily loaded page components
const LoginPage = lazy(() => import('@/pages/LoginPage'))
const DashboardPage = lazy(() => import('@/pages/DashboardPage'))
const EnvironmentPage = lazy(() => import('@/pages/EnvironmentPage'))
const EsgPage = lazy(() => import('@/pages/EsgPage'))
const TrafficPage = lazy(() => import('@/pages/TrafficPage'))
const AlertsPage = lazy(() => import('@/pages/AlertsPage'))
const CitizenPage = lazy(() => import('@/pages/CitizenPage'))
const AdminPage = lazy(() => import('@/pages/AdminPage'))

export const routes: RouteObject[] = [
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: (
      <ProtectedRoute>
        <AppShell />
      </ProtectedRoute>
    ),
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/dashboard', element: <DashboardPage /> },
      { path: '/environment', element: <EnvironmentPage /> },
      { path: '/esg', element: <EsgPage /> },
      { path: '/traffic', element: <TrafficPage /> },
      { path: '/alerts', element: <AlertsPage /> },
      { path: '/citizen', element: <CitizenPage /> },
      {
        path: '/admin',
        element: (
          <ProtectedRoute requiredRole="ROLE_ADMIN">
            <AdminPage />
          </ProtectedRoute>
        ),
      },
    ],
  },
]
