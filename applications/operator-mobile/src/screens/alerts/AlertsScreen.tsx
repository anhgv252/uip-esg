import { useState, useMemo } from 'react'
import { View, Text, FlatList, StyleSheet, RefreshControl, TouchableOpacity, ScrollView } from 'react-native'
import { useAlerts } from '../../hooks/useAlerts'
import { useDashboard, getSafetyColor } from '../../hooks/useDashboard'
import type { AlertItem } from '../../hooks/useAlerts'

const severityOrder: Record<string, number> = { P0: 0, CRITICAL: 0, P1: 1, HIGH: 1, P2: 2, WARNING: 2, INFO: 3 }
const severityColors: Record<string, string> = { P0: '#B71C1C', CRITICAL: '#B71C1C', P1: '#E65100', HIGH: '#E65100', P2: '#F57F17', WARNING: '#F57F17', INFO: '#1565C0' }

const SEVERITY_FILTERS = [
  { key: '', label: 'Tất cả' },
  { key: 'P0', label: 'P0' },
  { key: 'P1', label: 'P1' },
  { key: 'P2', label: 'P2' },
] as const

const MODULE_FILTERS = [
  { key: '', label: 'Tất cả' },
  { key: 'ENVIRONMENT', label: 'Môi trường' },
  { key: 'STRUCTURAL', label: 'Kết cấu' },
  { key: 'FLOOD', label: 'Lũ lụt' },
  { key: 'TRAFFIC', label: 'Giao thông' },
  { key: 'BMS', label: 'BMS' },
] as const

export default function AlertsScreen() {
  const [selectedSeverity, setSelectedSeverity] = useState('')
  const [selectedModule, setSelectedModule] = useState('')
  const { data: alerts, isLoading, isFetching, isError, refetch } = useAlerts(selectedModule || undefined)
  const dashboardQuery = useDashboard()

  // Filter + sort alerts
  const filteredAlerts = useMemo(() => {
    if (!alerts) return []
    let filtered = alerts

    if (selectedSeverity) {
      filtered = filtered.filter(a => a.severity === selectedSeverity)
    }

    // Sort by severity (P0 first)
    return [...filtered].sort((a, b) => {
      const orderA = severityOrder[a.severity] ?? 99
      const orderB = severityOrder[b.severity] ?? 99
      return orderA - orderB
    })
  }, [alerts, selectedSeverity])

  const safetyScore = dashboardQuery.data?.safetyScore ?? 0
  const safetyStatus = dashboardQuery.data?.safetyStatus ?? 'OFFLINE'

  const renderItem = ({ item }: { item: AlertItem }) => (
    <View style={[styles.alertCard, { borderLeftColor: severityColors[item.severity] ?? '#999' }]}>
      <View style={styles.alertContent}>
        <View style={styles.alertHeader}>
          <View style={[styles.severityBadge, { backgroundColor: severityColors[item.severity] ?? '#999' }]}>
            <Text style={styles.severityBadgeText}>{item.severity}</Text>
          </View>
          <Text style={styles.alertModule}>{item.module}</Text>
          {item.status === 'OPEN' && <View style={styles.openDot} />}
        </View>
        <Text style={styles.alertMessage} numberOfLines={3}>{item.message}</Text>
        <Text style={styles.alertMeta}>
          {item.location ?? 'N/A'} · {item.createdAt}
        </Text>
      </View>
    </View>
  )

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Cảnh báo</Text>

      {/* ─── Safety Score Summary ─── */}
      <View style={styles.safetyBar}>
        <View style={styles.safetyScoreContainer}>
          <Text style={[styles.safetyScore, { color: getSafetyColor(safetyScore) }]}>
            {safetyScore}
          </Text>
          <Text style={styles.safetyLabel}>/100</Text>
        </View>
        <View style={styles.safetyInfo}>
          <Text style={styles.safetyTitle}>Điểm an toàn</Text>
          <Text style={[styles.safetyStatus, { color: getSafetyColor(safetyScore) }]}>
            {safetyStatus === 'SAFE' ? 'Tốt' : safetyStatus === 'WARNING' ? 'Cảnh báo' : safetyStatus === 'CRITICAL' ? 'Nguy hiểm' : 'Offline'}
          </Text>
        </View>
      </View>

      {/* ─── Severity Filter Chips ─── */}
      <View style={styles.filterRow}>
        {SEVERITY_FILTERS.map(f => (
          <TouchableOpacity
            key={`sev-${f.key}`}
            style={[
              styles.filterChip,
              selectedSeverity === f.key && { backgroundColor: severityColors[f.key] || '#1565C0' },
            ]}
            onPress={() => setSelectedSeverity(f.key)}
          >
            <Text style={[styles.filterChipText, selectedSeverity === f.key && styles.filterChipTextActive]}>
              {f.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* ─── Module Filter Chips ─── */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.moduleScroll}>
        {MODULE_FILTERS.map(f => (
          <TouchableOpacity
            key={`mod-${f.key}`}
            style={[styles.filterChip, selectedModule === f.key && styles.filterChipActive]}
            onPress={() => setSelectedModule(f.key)}
          >
            <Text style={[styles.filterChipText, selectedModule === f.key && styles.filterChipTextActive]}>
              {f.label}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {/* ─── Alert Count ─── */}
      <Text style={styles.alertCount}>
        {filteredAlerts.length} cảnh báo{selectedSeverity ? ` ${selectedSeverity}` : ''}{selectedModule ? ` — ${selectedModule}` : ''}
      </Text>

      {/* ─── Alert List ─── */}
      {isError ? (
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>Không tải được cảnh báo</Text>
          <TouchableOpacity style={styles.retryButton} onPress={() => refetch()}>
            <Text style={styles.retryText}>Thử lại</Text>
          </TouchableOpacity>
        </View>
      ) : isLoading ? (
        <Text style={styles.empty}>Đang tải...</Text>
      ) : !filteredAlerts.length ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyIcon}>✅</Text>
          <Text style={styles.empty}>Không có cảnh báo</Text>
        </View>
      ) : (
        <FlatList
          data={filteredAlerts}
          keyExtractor={item => String(item.id)}
          renderItem={renderItem}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl refreshing={isFetching} onRefresh={refetch} tintColor="#1565C0" />
          }
        />
      )}
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  title: { fontSize: 28, fontWeight: 'bold', paddingHorizontal: 16, paddingTop: 16, color: '#1565C0' },

  // Safety score bar
  safetyBar: {
    flexDirection: 'row', alignItems: 'center', backgroundColor: '#fff',
    marginHorizontal: 16, marginTop: 12, borderRadius: 12, padding: 16, elevation: 2,
  },
  safetyScoreContainer: { flexDirection: 'row', alignItems: 'baseline' },
  safetyScore: { fontSize: 36, fontWeight: 'bold' },
  safetyLabel: { fontSize: 14, color: '#999' },
  safetyInfo: { marginLeft: 16 },
  safetyTitle: { fontSize: 14, fontWeight: '600', color: '#333' },
  safetyStatus: { fontSize: 12, marginTop: 2 },

  // Filters
  filterRow: { flexDirection: 'row', paddingHorizontal: 16, paddingTop: 12, paddingBottom: 6, gap: 8 },
  moduleScroll: { paddingHorizontal: 16, paddingBottom: 10, maxHeight: 44 },
  filterChip: {
    paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16,
    backgroundColor: '#E0E0E0',
  },
  filterChipActive: { backgroundColor: '#1565C0' },
  filterChipText: { fontSize: 12, fontWeight: '600', color: '#666' },
  filterChipTextActive: { color: '#fff' },

  // Alert count
  alertCount: { fontSize: 12, color: '#999', paddingHorizontal: 16, paddingBottom: 8 },

  // Alert list
  list: { paddingHorizontal: 16, paddingBottom: 16 },
  alertCard: {
    flexDirection: 'row', backgroundColor: '#fff', borderRadius: 8, padding: 12,
    marginBottom: 8, borderLeftWidth: 4, elevation: 1,
  },
  alertContent: { flex: 1 },
  alertHeader: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  severityBadge: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 10 },
  severityBadgeText: { fontSize: 11, fontWeight: 'bold', color: '#fff' },
  alertModule: { fontSize: 11, color: '#666', backgroundColor: '#F5F5F5', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
  openDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#F44336' },
  alertMessage: { fontSize: 14, color: '#333', marginTop: 6 },
  alertMeta: { fontSize: 11, color: '#999', marginTop: 4 },

  // Empty / error states
  emptyContainer: { alignItems: 'center', marginTop: 40 },
  emptyIcon: { fontSize: 32, marginBottom: 8 },
  empty: { textAlign: 'center', color: '#999', marginTop: 8 },
  errorContainer: { alignItems: 'center', marginTop: 40 },
  errorText: { fontSize: 14, color: '#B71C1C', marginBottom: 12 },
  retryButton: { backgroundColor: '#1565C0', borderRadius: 8, paddingHorizontal: 24, paddingVertical: 10 },
  retryText: { color: '#fff', fontSize: 14, fontWeight: '600' },
})
