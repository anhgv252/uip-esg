import { View, Text, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from 'react-native'
import { useNavigation } from '@react-navigation/native'
import { BottomTabNavigationProp } from '@react-navigation/bottom-tabs'
import { useAlerts } from '../../hooks/useAlerts'
import { useSensors } from '../../hooks/useSensors'
import { useDashboard, getSafetyColor, getAqiColor, getAqiLabel } from '../../hooks/useDashboard'
import KpiCard from '../../components/KpiCard'

type TabParamList = {
  Dashboard: undefined
  Alerts: undefined
  Controls: undefined
  Profile: undefined
}

type NavigationProp = BottomTabNavigationProp<TabParamList>

export default function DashboardScreen() {
  const navigation = useNavigation<NavigationProp>()
  const alertsQuery = useAlerts()
  const sensorsQuery = useSensors()
  const dashboardQuery = useDashboard()

  const alerts = alertsQuery.data
  const sensors = sensorsQuery.data
  const dashboard = dashboardQuery.data

  const isRefreshing =
    alertsQuery.isFetching ||
    sensorsQuery.isFetching ||
    dashboardQuery.isFetching

  const hasError = alertsQuery.isError || sensorsQuery.isError || dashboardQuery.isError

  const refreshAll = () => {
    alertsQuery.refetch()
    sensorsQuery.refetch()
    dashboardQuery.refetch()
  }

  // Derive KPI values
  const activeAlerts = alerts?.filter(a => a.status === 'OPEN').length ?? 0
  const p0Alerts = alerts?.filter(a => a.status === 'OPEN' && a.severity === 'P0').length ?? 0
  const energyKwh = dashboard?.energyKwh ?? 0
  const safetyScore = dashboard?.safetyScore ?? 0
  const aqi = dashboard?.aqi ?? 0
  const onlineSensors = dashboard?.onlineSensors ?? sensors?.filter(s => s.status === 'ONLINE').length ?? 0
  const totalSensors = dashboard?.totalSensors ?? sensors?.length ?? 0

  // Trend arrow
  const energyTrendIcon = dashboard?.energyTrend === 'up' ? '📈' : dashboard?.energyTrend === 'down' ? '📉' : '➡️'

  return (
    <ScrollView
      style={styles.container}
      refreshControl={
        <RefreshControl refreshing={isRefreshing} onRefresh={refreshAll} tintColor="#1565C0" />
      }
    >
      <Text style={styles.title}>Dashboard</Text>
      <Text style={styles.subtitle}>
        {new Date().toLocaleDateString('vi-VN', { weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric' })}
      </Text>

      {hasError && (
        <View style={styles.errorBanner}>
          <Text style={styles.errorBannerText}>⚠️ Một số dữ liệu chưa tải được. Kéo xuống để thử lại.</Text>
        </View>
      )}

      {/* ─── 4 KPI Cards ─── */}
      <View style={styles.kpiGrid}>
        <View style={styles.kpiRow}>
          {/* Energy KPI */}
          <KpiCard
            value={`${energyKwh.toLocaleString('vi-VN')} kWh`}
            label="Năng lượng"
            subtitle={`${energyTrendIcon} Hôm nay`}
            color="#FF9800"
            icon="⚡"
            isLoading={dashboardQuery.isLoading}
            onPress={() => {}}
          />
          {/* Safety Score KPI */}
          <KpiCard
            value={dashboardQuery.isLoading ? '—' : `${safetyScore}`}
            label="An toàn"
            subtitle={safetyScore >= 80 ? 'Tốt' : safetyScore >= 50 ? 'Cảnh báo' : 'Nguy hiểm'}
            color={getSafetyColor(safetyScore)}
            icon="🛡️"
            isLoading={dashboardQuery.isLoading}
            onPress={() => navigation.navigate('Controls')}
          />
        </View>

        <View style={styles.kpiRow}>
          {/* AQI KPI */}
          <KpiCard
            value={dashboardQuery.isLoading ? '—' : `${aqi}`}
            label="Chỉ số AQI"
            subtitle={getAqiLabel(aqi)}
            color={getAqiColor(aqi)}
            icon="🌬️"
            isLoading={dashboardQuery.isLoading}
            onPress={() => {}}
          />
          {/* Active Alerts KPI */}
          <KpiCard
            value={activeAlerts}
            label="Cảnh báo"
            subtitle={p0Alerts > 0 ? `🔴 ${p0Alerts} P0` : 'Không có P0'}
            color={activeAlerts > 0 ? '#F44336' : '#4CAF50'}
            icon="🔔"
            isLoading={alertsQuery.isLoading}
            onPress={() => navigation.navigate('Alerts')}
          />
        </View>
      </View>

      {/* ─── Sensor Status ─── */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Sensors</Text>
        <View style={styles.sensorRow}>
          <Text style={styles.sensorValue}>
            {onlineSensors}/{totalSensors}
          </Text>
          <Text style={styles.sensorLabel}>Online</Text>
          <View style={styles.sensorBar}>
            <View
              style={[
                styles.sensorBarFill,
                { width: totalSensors > 0 ? `${(onlineSensors / totalSensors) * 100}%` : '0%' },
              ]}
            />
          </View>
        </View>
      </View>

      {/* ─── Recent Alerts ─── */}
      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Cảnh báo gần đây</Text>
          {alerts && alerts.length > 3 && (
            <TouchableOpacity onPress={() => navigation.navigate('Alerts')}>
              <Text style={styles.seeAll}>Xem tất cả →</Text>
            </TouchableOpacity>
          )}
        </View>

        {alerts?.slice(0, 5).map(alert => (
          <View
            key={alert.id}
            style={[
              styles.alertItem,
              { borderLeftColor: alert.severity === 'P0' ? '#F44336' : alert.severity === 'P1' ? '#FF9800' : '#FFC107' },
            ]}
          >
            <View style={styles.alertHeader}>
              <Text style={[styles.alertSeverity, { color: alert.severity === 'P0' ? '#F44336' : '#FF9800' }]}>
                {alert.severity}
              </Text>
              <Text style={styles.alertModule}>{alert.module}</Text>
            </View>
            <Text style={styles.alertMessage} numberOfLines={2}>{alert.message}</Text>
            <Text style={styles.alertTime}>{alert.createdAt}</Text>
          </View>
        ))}

        {alerts && alerts.length === 0 && (
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>✅ Không có cảnh báo</Text>
          </View>
        )}

        {alertsQuery.isError && (
          <TouchableOpacity style={styles.retryButton} onPress={() => alertsQuery.refetch()}>
            <Text style={styles.retryText}>Thử lại</Text>
          </TouchableOpacity>
        )}
      </View>
    </ScrollView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  title: { fontSize: 28, fontWeight: 'bold', color: '#1565C0', paddingHorizontal: 16, paddingTop: 16 },
  subtitle: { fontSize: 14, color: '#666', paddingHorizontal: 16, marginBottom: 16 },

  errorBanner: {
    backgroundColor: '#FFF3E0', borderRadius: 8, padding: 12, marginHorizontal: 16,
    marginBottom: 12, borderLeftWidth: 4, borderLeftColor: '#E65100',
  },
  errorBannerText: { fontSize: 13, color: '#E65100' },

  // KPI Grid
  kpiGrid: { paddingHorizontal: 16, gap: 10 },
  kpiRow: { flexDirection: 'row', gap: 10 },

  // Sensor section
  section: { marginTop: 20, paddingHorizontal: 16 },
  sectionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  sectionTitle: { fontSize: 18, fontWeight: '600', color: '#333' },
  seeAll: { fontSize: 14, color: '#1565C0', fontWeight: '500' },

  sensorRow: { flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: '#fff', borderRadius: 8, padding: 14, elevation: 1 },
  sensorValue: { fontSize: 18, fontWeight: 'bold', color: '#1565C0' },
  sensorLabel: { fontSize: 12, color: '#666' },
  sensorBar: { flex: 1, height: 6, backgroundColor: '#E0E0E0', borderRadius: 3, overflow: 'hidden' },
  sensorBarFill: { height: '100%', backgroundColor: '#4CAF50', borderRadius: 3 },

  // Alert items
  alertItem: {
    backgroundColor: '#fff', borderRadius: 8, padding: 12, marginBottom: 8,
    borderLeftWidth: 4, elevation: 1,
  },
  alertHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  alertSeverity: { fontSize: 12, fontWeight: 'bold' },
  alertModule: { fontSize: 11, color: '#999', backgroundColor: '#F5F5F5', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4 },
  alertMessage: { fontSize: 14, color: '#333', marginTop: 6 },
  alertTime: { fontSize: 11, color: '#999', marginTop: 4 },

  emptyState: { backgroundColor: '#fff', borderRadius: 8, padding: 24, alignItems: 'center' },
  emptyText: { fontSize: 16, color: '#4CAF50' },

  retryButton: {
    backgroundColor: '#1565C0', borderRadius: 8, paddingHorizontal: 24, paddingVertical: 10,
    alignItems: 'center', marginTop: 12,
  },
  retryText: { color: '#fff', fontSize: 14, fontWeight: '600' },
})
