import { useEffect } from 'react'
import { StatusBar } from 'expo-status-bar'
import { NavigationContainer } from '@react-navigation/native'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import * as Notifications from 'expo-notifications'
import { AuthProvider, useAuth } from './src/context/AuthContext'
import { usePushToken } from './src/hooks/usePushToken'
import DashboardScreen from './src/screens/dashboard/DashboardScreen'
import AlertsScreen from './src/screens/alerts/AlertsScreen'
import ControlsScreen from './src/screens/controls/ControlsScreen'
import ProfileScreen from './src/screens/profile/ProfileScreen'
import LoginScreen from './src/screens/auth/LoginScreen'
import TenantSelectionScreen from './src/screens/auth/TenantSelectionScreen'

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
})

const Tab = createBottomTabNavigator()
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
    mutations: {
      retry: 0,
    },
  },
})

function AppNavigator() {
  const { isAuthenticated, isLoading, selectedTenant, selectTenant, token } = useAuth()

  usePushToken(token ?? null, selectedTenant ?? null)

  useEffect(() => {
    const sub = Notifications.addNotificationReceivedListener(notification => {
      const data = notification.request.content.data as Record<string, unknown>
      console.log('[Push] foreground alert:', data?.severity, data?.module)
    })
    return () => sub.remove()
  }, [])

  if (isLoading) return null

  if (!selectedTenant) {
    return <TenantSelectionScreen onSelect={selectTenant} />
  }

  if (!isAuthenticated) {
    return <LoginScreen />
  }

  return (
    <Tab.Navigator
      initialRouteName="Dashboard"
      screenOptions={{ tabBarActiveTintColor: '#1565C0' }}
    >
      <Tab.Screen name="Dashboard" component={DashboardScreen} />
      <Tab.Screen name="Alerts" component={AlertsScreen} />
      <Tab.Screen name="Controls" component={ControlsScreen} />
      <Tab.Screen name="Profile" component={ProfileScreen} />
    </Tab.Navigator>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <NavigationContainer>
          <StatusBar style="auto" />
          <AppNavigator />
        </NavigationContainer>
      </AuthProvider>
    </QueryClientProvider>
  )
}
