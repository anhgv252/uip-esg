import { View, Text, StyleSheet, TouchableOpacity } from 'react-native'

interface ProfileScreenProps {
  onLogout?: () => void
}

export default function ProfileScreen({ onLogout }: ProfileScreenProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Profile</Text>

      <View style={styles.section}>
        <Text style={styles.label}>Name</Text>
        <Text style={styles.value}>UIP Operator</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.label}>Role</Text>
        <Text style={styles.value}>Operator</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.label}>Tenant</Text>
        <Text style={styles.value}>Ho Chi Minh City</Text>
      </View>

      {onLogout && (
        <TouchableOpacity style={styles.logoutButton} onPress={onLogout}>
          <Text style={styles.logoutText}>Sign Out</Text>
        </TouchableOpacity>
      )}
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
  title: { fontSize: 24, fontWeight: 'bold', marginBottom: 24, color: '#1565C0' },
  section: { backgroundColor: '#fff', borderRadius: 8, padding: 16, marginBottom: 8 },
  label: { fontSize: 12, color: '#999', textTransform: 'uppercase' },
  value: { fontSize: 16, color: '#333', marginTop: 4 },
  logoutButton: { backgroundColor: '#B71C1C', borderRadius: 8, padding: 16, marginTop: 32, alignItems: 'center' },
  logoutText: { color: '#fff', fontSize: 16, fontWeight: '600' },
})
