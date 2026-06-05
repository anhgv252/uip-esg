/**
 * @uip/hooks - Shared React hooks for UIP Smart City
 * 
 * This package provides reusable React hooks for common API interactions
 * across frontend (web) and operator-mobile (React Native) applications.
 * 
 * All hooks accept an optional `client` parameter to customize the axios instance.
 * By default, they use `defaultApiClient` which can be configured via:
 * - window.__UIP_API_BASE_URL__ (browser)
 * - process.env.API_BASE_URL (Node.js/React Native)
 * 
 * @example
 * ```tsx
 * import { useDashboard, useAlerts, useSensors } from '@uip/hooks'
 * 
 * function DashboardScreen() {
 *   const { data: stats } = useDashboard()
 *   const { data: alerts } = useAlerts({ status: 'OPEN' })
 *   const { data: sensors } = useSensors({ status: 'ACTIVE' })
 *   
 *   return <View>...</View>
 * }
 * ```
 */

export { defaultApiClient } from './apiClient'

export {
  useDashboard,
  type DashboardStats,
} from './useDashboard'

export {
  useAlerts,
  useAcknowledgeAlert,
  useEscalateAlert,
  useResolveAlert,
  useCitizenNotifications,
  type AlertEvent,
  type AlertEventsPage,
} from './useAlerts'

export {
  useSensors,
  useSensor,
  type Sensor,
  type SensorsPage,
} from './useSensors'
