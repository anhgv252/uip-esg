import { View, Text, StyleSheet, FlatList } from 'react-native'
import { useBuildingList } from '../../hooks/useBuildingList'
import type { Building } from '../../hooks/useBuildingList'

export default function ControlsScreen() {
  const { data: buildings, isLoading } = useBuildingList()

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
      {isLoading ? (
        <Text style={styles.empty}>Loading buildings...</Text>
      ) : !buildings?.length ? (
        <Text style={styles.empty}>No buildings found</Text>
      ) : (
        <FlatList
          data={buildings}
          keyExtractor={item => item.id}
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
  buildingCard: { backgroundColor: '#fff', borderRadius: 8, padding: 16, marginBottom: 8, elevation: 1 },
  buildingName: { fontSize: 16, fontWeight: '600', color: '#333' },
  buildingAddress: { fontSize: 13, color: '#666', marginTop: 4 },
  buildingMeta: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 },
  buildingStatus: { fontSize: 12, color: '#1565C0', fontWeight: '500' },
  buildingDevices: { fontSize: 12, color: '#999' },
  empty: { textAlign: 'center', color: '#999', marginTop: 40 },
})
