import { lazy } from 'react'
import { type RouteObject } from 'react-router-dom'
import AppShell from '@/components/AppShell'
import MobileLayout from '@/components/mobile/MobileLayout'
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
const TenantOverviewPage = lazy(() => import('@/pages/tenant-admin/TenantOverviewPage'))
const UserManagementPage = lazy(() => import('@/pages/tenant-admin/UserManagementPage'))
const BuildingConfigPage = lazy(() => import('@/pages/tenant-admin/BuildingConfigPage'))
const UsageReportPage = lazy(() => import('@/pages/tenant-admin/UsageReportPage'))
const TenantSettingsPage = lazy(() => import('@/pages/tenant-admin/TenantSettingsPage'))
const NotificationSettingsPage = lazy(() => import('@/pages/settings/NotificationSettingsPage'))
const BmsDevicesPage = lazy(() => import('@/pages/BmsDevicesPage'))
const MobileBillsPage = lazy(() => import('@/pages/citizen/MobileBillsPage'))
const MobileBillDetailPage = lazy(() => import('@/pages/citizen/MobileBillDetailPage'))
const MobileAQIPage = lazy(() => import('@/pages/citizen/MobileAQIPage'))
const MobileNotificationsPage = lazy(() => import('@/pages/citizen/MobileNotificationsPage'))
const CrossBuildingDashboardPage = lazy(
  () => import('@/pages/buildings/CrossBuildingDashboardPage').then((m) => ({ default: m.CrossBuildingDashboardPage }))
)
const CrossBuildingShell = lazy(
  () => import('@/components/buildings/CrossBuildingShell').then((m) => ({ default: m.CrossBuildingShell }))
)

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
      { path: '/bms/devices', element: <BmsDevicesPage /> },
      { path: '/settings/notifications', element: <NotificationSettingsPage /> },
      { path: '/city-ops', element: <CityOpsPage /> },
      // Cross-Building Analytics (MVP3-Sprint1)
      {
        path: '/buildings',
        element: <CrossBuildingShell />,
        children: [
          { index: true, element: <CrossBuildingDashboardPage /> },
        ],
      },
      // Citizen routes — all wrapped in MobileLayout to render bottom navigation
      {
        path: '/citizen',
        element: <MobileLayout />,
        children: [
          { index: true, element: <CitizenPage /> },
          { path: 'bills', element: <MobileBillsPage /> },
          { path: 'bills/:billId', element: <MobileBillDetailPage /> },
          { path: 'aqi', element: <MobileAQIPage /> },
          { path: 'alerts', element: <MobileNotificationsPage /> },
          { path: 'profile', element: <CitizenPage /> },
        ],
      },
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
        children: [
          { index: true, element: <TenantOverviewPage /> },
          { path: 'users', element: <UserManagementPage /> },
          { path: 'buildings', element: <BuildingConfigPage /> },
          { path: 'usage', element: <UsageReportPage /> },
          { path: 'settings', element: <TenantSettingsPage /> },
        ],
      },
    ],
  },
]
