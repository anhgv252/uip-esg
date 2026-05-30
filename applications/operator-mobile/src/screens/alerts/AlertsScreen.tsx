import { View, Text, FlatList, StyleSheet } from 'react-native'
import { useAlerts } from '../../hooks/useAlerts'
import type { AlertItem } from '../../hooks/useAlerts'

const severityColors: Record<string, string> = {
  CRITICAL: '#B71C1C',
  HIGH: '#E65100',
  WARNING: '#F57F17',
  INFO: '#1565C0',
}

export default function AlertsScreen() {
  const { data: alerts, isLoading } = useAlerts()

  const renderItem = ({ item }: { item: AlertItem }) => (
    <View style={styles.alertCard}>
      <View style={[styles.severityDot, { backgroundColor: severityColors[item.severity] ?? '#999' }]} />
      <View style={styles.alertContent}>
        <Text style={styles.alertSeverity}>{item.severity}</Text>
        <Text style={styles.alertMessage}>{item.message}</Text>
        <Text style={styles.alertMeta}>
          {item.module} · {item.location ?? 'N/A'} · {item.status}
        </Text>
      </View>
    </View>
  )

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Alerts</Text>
      {isLoading ? (
        <Text style={styles.empty}>Loading alerts...</Text>
      ) : !alerts?.length ? (
        <Text style={styles.empty}>No alerts found</Text>
      ) : (
        <FlatList
          data={alerts}
          keyExtractor={item => String(item.id)}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
        />
      )}
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  title: { fontSize: 24, fontWeight: 'bold', padding: 16, color: '#1565C0' },
  list: { paddingHorizontal: 16, paddingBottom: 16 },
  alertCard: { flexDirection: 'row', backgroundColor: '#fff', borderRadius: 8, padding: 12, marginBottom: 8, elevation: 1 },
  severityDot: { width: 12, height: 12, borderRadius: 6, marginTop: 4, marginRight: 12 },
  alertContent: { flex: 1 },
  alertSeverity: { fontSize: 13, fontWeight: 'bold', color: '#333' },
  alertMessage: { fontSize: 14, color: '#555', marginTop: 2 },
  alertMeta: { fontSize: 11, color: '#999', marginTop: 4 },
  empty: { textAlign: 'center', color: '#999', marginTop: 40 },
})
