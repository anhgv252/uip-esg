import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from 'react-native'
import { useAlerts } from '../../hooks/useAlerts'
import { useSensors } from '../../hooks/useSensors'
import { useBuildingList } from '../../hooks/useBuildingList'

export default function DashboardScreen() {
  const alertsQuery = useAlerts()
  const sensorsQuery = useSensors()
  const buildingsQuery = useBuildingList()

  const alerts = alertsQuery.data
  const sensors = sensorsQuery.data
  const buildings = buildingsQuery.data

  const isRefreshing = alertsQuery.isLoading || sensorsQuery.isLoading || buildingsQuery.isLoading
  const hasError = alertsQuery.isError || sensorsQuery.isError || buildingsQuery.isError

  const refreshAll = () => {
    alertsQuery.refetch()
    sensorsQuery.refetch()
    buildingsQuery.refetch()
  }

  const activeAlerts = alerts?.filter(a => a.status === 'OPEN').length ?? 0
  const onlineSensors = sensors?.filter(s => s.status === 'ONLINE').length ?? 0
  const totalSensors = sensors?.length ?? 0

  return (
    <ScrollView
      style={styles.container}
      refreshControl={
        <RefreshControl refreshing={isRefreshing} onRefresh={refreshAll} tintColor="#1565C0" />
      }
    >
      <Text style={styles.title}>City Operations Dashboard</Text>

      {hasError && (
        <View style={styles.errorBanner}>
          <Text style={styles.errorBannerText}>Some data failed to load. Pull down to retry.</Text>
        </View>
      )}

      <View style={styles.cardRow}>
        <View style={[styles.card, { backgroundColor: activeAlerts > 0 ? '#FFEBEE' : '#E8F5E9' }]}>
          <Text style={styles.cardValue}>{activeAlerts}</Text>
          <Text style={styles.cardLabel}>Active Alerts</Text>
        </View>
        <View style={styles.card}>
          <Text style={styles.cardValue}>{onlineSensors}/{totalSensors}</Text>
          <Text style={styles.cardLabel}>Sensors Online</Text>
        </View>
        <View style={styles.card}>
          <Text style={styles.cardValue}>{buildings?.length ?? 0}</Text>
          <Text style={styles.cardLabel}>Buildings</Text>
        </View>
      </View>

      <Text style={styles.sectionTitle}>Recent Alerts</Text>
      {alerts?.slice(0, 5).map(alert => (
        <View key={alert.id} style={styles.alertItem}>
          <Text style={styles.alertSeverity}>{alert.severity}</Text>
          <Text style={styles.alertMessage}>{alert.message}</Text>
          <Text style={styles.alertTime}>{alert.createdAt}</Text>
        </View>
      ))}

      {alertsQuery.isError && (
        <TouchableOpacity style={styles.retryButton} onPress={() => alertsQuery.refetch()}>
          <Text style={styles.retryText}>Retry Alerts</Text>
        </TouchableOpacity>
      )}
    </ScrollView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
  title: { fontSize: 24, fontWeight: 'bold', marginBottom: 16, color: '#1565C0' },
  errorBanner: { backgroundColor: '#FFF3E0', borderRadius: 8, padding: 12, marginBottom: 16, borderLeftWidth: 4, borderLeftColor: '#E65100' },
  errorBannerText: { fontSize: 13, color: '#E65100' },
  cardRow: { flexDirection: 'row', gap: 8, marginBottom: 24 },
  card: { flex: 1, backgroundColor: '#fff', borderRadius: 8, padding: 16, alignItems: 'center', elevation: 2 },
  cardValue: { fontSize: 24, fontWeight: 'bold', color: '#333' },
  cardLabel: { fontSize: 12, color: '#666', marginTop: 4 },
  sectionTitle: { fontSize: 18, fontWeight: '600', marginBottom: 8, color: '#333' },
  alertItem: { backgroundColor: '#fff', borderRadius: 8, padding: 12, marginBottom: 8, elevation: 1 },
  alertSeverity: { fontSize: 12, fontWeight: 'bold', color: '#E65100' },
  alertMessage: { fontSize: 14, color: '#333', marginTop: 4 },
  alertTime: { fontSize: 11, color: '#999', marginTop: 4 },
  retryButton: { backgroundColor: '#1565C0', borderRadius: 8, paddingHorizontal: 24, paddingVertical: 10, alignItems: 'center', marginTop: 12 },
  retryText: { color: '#fff', fontSize: 14, fontWeight: '600' },
})
