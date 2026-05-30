import { StatusBar } from 'expo-status-bar'
import { StyleSheet } from 'react-native'
import { NavigationContainer } from '@react-navigation/native'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider, useAuth } from './src/context/AuthContext'
import DashboardScreen from './src/screens/dashboard/DashboardScreen'
import AlertsScreen from './src/screens/alerts/AlertsScreen'
import ControlsScreen from './src/screens/controls/ControlsScreen'
import ProfileScreen from './src/screens/profile/ProfileScreen'
import LoginScreen from './src/screens/auth/LoginScreen'

const Tab = createBottomTabNavigator()
const queryClient = new QueryClient()

function AppNavigator() {
  const { isAuthenticated, isLoading } = useAuth()

  if (isLoading) return null

  if (!isAuthenticated) return <LoginScreen />

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

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
})
