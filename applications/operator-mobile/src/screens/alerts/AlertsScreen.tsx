import { useState } from 'react'
import { View, Text, FlatList, StyleSheet, RefreshControl, TouchableOpacity } from 'react-native'
import { useAlerts } from '../../hooks/useAlerts'
import type { AlertItem } from '../../hooks/useAlerts'

const severityColors: Record<string, string> = {
  CRITICAL: '#B71C1C',
  HIGH: '#E65100',
  WARNING: '#F57F17',
  INFO: '#1565C0',
}

const MODULE_FILTERS = [
  { key: '', label: 'ALL' },
  { key: 'ENVIRONMENT', label: 'ENV' },
  { key: 'FLOOD', label: 'FLOOD' },
  { key: 'TRAFFIC', label: 'TRAFFIC' },
  { key: 'BMS', label: 'BMS' },
] as const

export default function AlertsScreen() {
  const [selectedModule, setSelectedModule] = useState('')
  const { data: alerts, isLoading, isError, refetch } = useAlerts(selectedModule || undefined)

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

      {/* Module filter chips */}
      <View style={styles.filterRow}>
        {MODULE_FILTERS.map(f => (
          <TouchableOpacity
            key={f.key}
            style={[styles.filterChip, selectedModule === f.key && styles.filterChipActive]}
            onPress={() => setSelectedModule(f.key)}
          >
            <Text style={[styles.filterChipText, selectedModule === f.key && styles.filterChipTextActive]}>
              {f.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {isError ? (
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>Failed to load alerts</Text>
          <TouchableOpacity style={styles.retryButton} onPress={() => refetch()}>
            <Text style={styles.retryText}>Retry</Text>
          </TouchableOpacity>
        </View>
      ) : isLoading ? (
        <Text style={styles.empty}>Loading alerts...</Text>
      ) : !alerts?.length ? (
        <Text style={styles.empty}>No alerts found</Text>
      ) : (
        <FlatList
          data={alerts}
          keyExtractor={item => String(item.id)}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl refreshing={isLoading} onRefresh={refetch} tintColor="#1565C0" />
          }
        />
      )}
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  title: { fontSize: 24, fontWeight: 'bold', padding: 16, color: '#1565C0' },
  filterRow: { flexDirection: 'row', paddingHorizontal: 16, paddingBottom: 12, gap: 8 },
  filterChip: {
    paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16,
    backgroundColor: '#E0E0E0',
  },
  filterChipActive: { backgroundColor: '#1565C0' },
  filterChipText: { fontSize: 12, fontWeight: '600', color: '#666' },
  filterChipTextActive: { color: '#fff' },
  list: { paddingHorizontal: 16, paddingBottom: 16 },
  alertCard: { flexDirection: 'row', backgroundColor: '#fff', borderRadius: 8, padding: 12, marginBottom: 8, elevation: 1 },
  severityDot: { width: 12, height: 12, borderRadius: 6, marginTop: 4, marginRight: 12 },
  alertContent: { flex: 1 },
  alertSeverity: { fontSize: 13, fontWeight: 'bold', color: '#333' },
  alertMessage: { fontSize: 14, color: '#555', marginTop: 2 },
  alertMeta: { fontSize: 11, color: '#999', marginTop: 4 },
  empty: { textAlign: 'center', color: '#999', marginTop: 40 },
  errorContainer: { alignItems: 'center', marginTop: 40 },
  errorText: { fontSize: 14, color: '#B71C1C', marginBottom: 12 },
  retryButton: { backgroundColor: '#1565C0', borderRadius: 8, paddingHorizontal: 24, paddingVertical: 10 },
  retryText: { color: '#fff', fontSize: 14, fontWeight: '600' },
})
