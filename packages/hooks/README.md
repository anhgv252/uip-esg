# @uip/hooks

Shared React hooks for UIP Smart City applications (web + mobile).

## Overview

This package provides reusable React hooks that work across:
- `frontend/` (React web app with Vite)
- `applications/operator-mobile/` (React Native / Expo mobile app)

All hooks use `@tanstack/react-query` for data fetching and caching.

## Installation

This package is part of the UIP monorepo workspace. To use it:

```json
{
  "dependencies": {
    "@uip/hooks": "*"
  }
}
```

Then run `npm install` at the monorepo root.

## Usage

### Configure API Base URL

The hooks use a configurable axios client. Set the base URL in your app:

**Web (frontend):**
```tsx
// In your app entry point (main.tsx or App.tsx)
if (typeof window !== 'undefined') {
  window.__UIP_API_BASE_URL__ = 'https://api.uip.example.com/api/v1'
}
```

**React Native (operator-mobile):**
```tsx
// Use environment variable or pass custom client
import { defaultApiClient } from '@uip/hooks'

// Configure globally:
defaultApiClient.defaults.baseURL = 'https://api.uip.example.com/api/v1'

// Or pass a custom client to each hook:
const customClient = axios.create({
  baseURL: 'https://api.uip.example.com/api/v1',
  headers: { Authorization: `Bearer ${token}` }
})
const { data } = useDashboard(customClient)
```

### Available Hooks

#### useDashboard
```tsx
import { useDashboard } from '@uip/hooks'

function DashboardScreen() {
  const { data, isLoading, error } = useDashboard()
  
  if (isLoading) return <Text>Loading...</Text>
  if (error) return <Text>Error: {error.message}</Text>
  
  return (
    <View>
      <Text>Total Buildings: {data.totalBuildings}</Text>
      <Text>Active Sensors: {data.activeSensors}</Text>
      <Text>Open Alerts: {data.openAlerts}</Text>
    </View>
  )
}
```

#### useAlerts
```tsx
import { useAlerts, useAcknowledgeAlert } from '@uip/hooks'

function AlertsScreen() {
  const { data: alertsPage } = useAlerts({
    status: 'OPEN',
    severity: 'HIGH',
    page: 0,
    size: 20
  })
  
  const ackMutation = useAcknowledgeAlert()
  
  const handleAcknowledge = async (alertId: string) => {
    await ackMutation.mutateAsync({
      id: alertId,
      note: 'Investigating'
    })
  }
  
  return (
    <FlatList
      data={alertsPage?.content}
      renderItem={({ item }) => (
        <AlertCard
          alert={item}
          onAcknowledge={() => handleAcknowledge(item.id)}
        />
      )}
    />
  )
}
```

#### useSensors
```tsx
import { useSensors, useSensor } from '@uip/hooks'

function SensorsScreen() {
  const { data: sensorsPage } = useSensors({
    type: 'AIR_QUALITY',
    status: 'ACTIVE'
  })
  
  return (
    <FlatList
      data={sensorsPage?.content}
      renderItem={({ item }) => <SensorCard sensor={item} />}
    />
  )
}

function SensorDetailScreen({ sensorId }: { sensorId: string }) {
  const { data: sensor } = useSensor(sensorId)
  
  return <SensorDetails sensor={sensor} />
}
```

## Exports

### Hooks
- `useDashboard()` - Fetch dashboard statistics
- `useAlerts()` - Fetch paginated alerts with filters
- `useAcknowledgeAlert()` - Mutation to acknowledge an alert
- `useEscalateAlert()` - Mutation to escalate an alert
- `useResolveAlert()` - Mutation to resolve an alert
- `useCitizenNotifications()` - Fetch public citizen notifications
- `useSensors()` - Fetch paginated sensors with filters
- `useSensor()` - Fetch a single sensor by ID

### Types
- `DashboardStats`
- `AlertEvent`
- `AlertEventsPage`
- `Sensor`
- `SensorsPage`

### Utilities
- `defaultApiClient` - Pre-configured axios instance

## Development

```bash
# Type check
cd packages/hooks
npm run type-check
```

## Migration from Frontend Hooks

This package extracts core hooks from `frontend/src/hooks/` for reuse in mobile.

The existing frontend hooks remain unchanged for backward compatibility. 
Mobile apps can import from `@uip/hooks` directly.
