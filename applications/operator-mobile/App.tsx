import { StatusBar } from 'expo-status-bar'
import { StyleSheet } from 'react-native'
import { NavigationContainer } from '@react-navigation/native'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import DashboardScreen from './src/screens/dashboard/DashboardScreen'
import AlertsScreen from './src/screens/alerts/AlertsScreen'
import ControlsScreen from './src/screens/controls/ControlsScreen'
import ProfileScreen from './src/screens/profile/ProfileScreen'

const Tab = createBottomTabNavigator()
const queryClient = new QueryClient()

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <NavigationContainer>
        <StatusBar style="auto" />
        <Tab.Navigator
          initialRouteName="Dashboard"
          screenOptions={{ tabBarActiveTintColor: '#1565C0' }}
        >
          <Tab.Screen name="Dashboard" component={DashboardScreen} />
          <Tab.Screen name="Alerts" component={AlertsScreen} />
          <Tab.Screen name="Controls" component={ControlsScreen} />
          <Tab.Screen name="Profile" component={ProfileScreen} />
        </Tab.Navigator>
      </NavigationContainer>
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
