import { View, Text, FlatList, StyleSheet, RefreshControl, TouchableOpacity } from 'react-native'
import { useBuildingList } from '../../hooks/useBuildingList'
import type { Building } from '../../hooks/useBuildingList'

export default function ControlsScreen() {
  const { data: buildings, isLoading, isError, refetch } = useBuildingList()

  const renderItem = ({ item }: { item: Building }) => (
    <View style={styles.buildingCard}>
      <Text style={styles.buildingName}>{item.name}</Text>
      <Text style={styles.buildingAddress}>{item.address}</Text>
      <View style={styles.buildingMeta}>
        <Text style={styles.buildingStatus}>{item.status}</Text>
        <Text style={styles.buildingDevices}>{item.deviceCount} devices</Text>
      </View>
    </View>
  )

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Building Controls</Text>

      {isError ? (
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>Failed to load buildings</Text>
          <TouchableOpacity style={styles.retryButton} onPress={() => refetch()}>
            <Text style={styles.retryText}>Retry</Text>
          </TouchableOpacity>
        </View>
      ) : isLoading ? (
        <Text style={styles.empty}>Loading buildings...</Text>
      ) : !buildings?.length ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.empty}>No buildings found</Text>
          <TouchableOpacity style={styles.retryButton} onPress={() => refetch()}>
            <Text style={styles.retryText}>Refresh</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <FlatList
          data={buildings}
          keyExtractor={item => item.id}
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
  list: { paddingHorizontal: 16, paddingBottom: 16 },
  buildingCard: { backgroundColor: '#fff', borderRadius: 8, padding: 16, marginBottom: 8, elevation: 1 },
  buildingName: { fontSize: 16, fontWeight: '600', color: '#333' },
  buildingAddress: { fontSize: 13, color: '#666', marginTop: 4 },
  buildingMeta: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 },
  buildingStatus: { fontSize: 12, color: '#1565C0', fontWeight: '500' },
  buildingDevices: { fontSize: 12, color: '#999' },
  empty: { textAlign: 'center', color: '#999', marginTop: 16 },
  emptyContainer: { alignItems: 'center', marginTop: 24 },
  errorContainer: { alignItems: 'center', marginTop: 40 },
  errorText: { fontSize: 14, color: '#B71C1C', marginBottom: 12 },
  retryButton: { backgroundColor: '#1565C0', borderRadius: 8, paddingHorizontal: 24, paddingVertical: 10 },
  retryText: { color: '#fff', fontSize: 14, fontWeight: '600' },
})
