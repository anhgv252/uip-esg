import { View, Text, FlatList, StyleSheet, TouchableOpacity } from 'react-native'

interface Tenant {
  id: string
  name: string
}

const TENANTS: Tenant[] = [
  { id: 'hcm', name: 'Ho Chi Minh City' },
  { id: 'hanoi', name: 'Ha Noi' },
  { id: 'danang', name: 'Da Nang' },
]

interface TenantSelectionScreenProps {
  onSelect: (tenantId: string) => void
}

export default function TenantSelectionScreen({ onSelect }: TenantSelectionScreenProps) {
  const renderItem = ({ item }: { item: Tenant }) => (
    <TouchableOpacity style={styles.tenantCard} onPress={() => onSelect(item.id)}>
      <Text style={styles.tenantName}>{item.name}</Text>
      <Text style={styles.tenantId}>{item.id}</Text>
    </TouchableOpacity>
  )

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Select City</Text>
      <Text style={styles.subtitle}>Choose your city authority</Text>
      <FlatList
        data={TENANTS}
        keyExtractor={item => item.id}
        renderItem={renderItem}
        contentContainerStyle={styles.list}
      />
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
  title: { fontSize: 24, fontWeight: 'bold', color: '#1565C0' },
  subtitle: { fontSize: 14, color: '#666', marginTop: 4, marginBottom: 24 },
  list: { gap: 8 },
  tenantCard: { backgroundColor: '#fff', borderRadius: 8, padding: 16, marginBottom: 8, elevation: 1 },
  tenantName: { fontSize: 18, fontWeight: '500', color: '#333' },
  tenantId: { fontSize: 12, color: '#999', marginTop: 4 },
})
