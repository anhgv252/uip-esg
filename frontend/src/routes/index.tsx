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
const CityOpsPage = lazy(() => import('@/pages/CityOpsPage'))
const CitizenPage = lazy(() => import('@/pages/CitizenPage'))
const CitizenRegisterPage = lazy(() => import('@/components/citizen/CitizenRegisterPage'))
const AdminPage = lazy(() => import('@/pages/AdminPage'))
const AiWorkflowPage = lazy(() => import('@/pages/AiWorkflowPage'))
const WorkflowConfigPage = lazy(() => import('@/pages/WorkflowConfigPage'))
const TenantAdminPage = lazy(() => import('@/pages/TenantAdminPage'))

export const routes: RouteObject[] = [
  {
    path: '/login',
    element: <LoginPage />,
  },
  // Public citizen registration (no auth required)
  {
    path: '/citizen/register',
    element: <CitizenRegisterPage />,
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
      { path: '/city-ops', element: <CityOpsPage /> },
      { path: '/citizen', element: <CitizenPage /> },
      { path: '/ai-workflow', element: (
          <ProtectedRoute requiredRoles={['ROLE_ADMIN', 'ROLE_OPERATOR']}>
            <AiWorkflowPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/workflow-config',
        element: (
          <ProtectedRoute requiredRole="ROLE_ADMIN">
            <WorkflowConfigPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/admin',
        element: (
          <ProtectedRoute requiredRole="ROLE_ADMIN">
            <AdminPage />
          </ProtectedRoute>
        ),
      },
      {
        path: '/tenant-admin',
        element: (
          <ProtectedRoute requiredRoles={['ROLE_TENANT_ADMIN']}>
            <TenantAdminPage />
          </ProtectedRoute>
        ),
      },
    ],
  },
]
