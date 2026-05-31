import { View, Text, StyleSheet, TouchableOpacity } from 'react-native'
import { useAuth } from '../../context/AuthContext'

function parseJwtPayload(jwt: string | null): Record<string, unknown> | null {
  if (!jwt) return null
  try {
    const b64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(b64))
  } catch {
    return null
  }
}

export default function ProfileScreen() {
  const { logout, selectedTenant, token } = useAuth()

  const claims = parseJwtPayload(token)
  const displayName = (claims?.preferred_username as string) ?? 'UIP Operator'
  const roles: string[] = (claims?.realm_access as { roles?: string[] })?.roles ?? []
  const displayRole = roles.includes('admin') ? 'Admin'
    : roles.includes('operator') ? 'Operator'
    : roles.length > 0 ? roles[0] : 'User'

  const tenantLabel: Record<string, string> = {
    hcm: 'Ho Chi Minh City',
    hanoi: 'Ha Noi',
    danang: 'Da Nang',
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Profile</Text>

      <View style={styles.section}>
        <Text style={styles.label}>Name</Text>
        <Text style={styles.value}>{displayName}</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.label}>Role</Text>
        <Text style={styles.value}>{displayRole}</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.label}>Tenant</Text>
        <Text style={styles.value}>
          {selectedTenant ? (tenantLabel[selectedTenant] ?? selectedTenant) : '—'}
        </Text>
      </View>

      <TouchableOpacity style={styles.logoutButton} onPress={logout}>
        <Text style={styles.logoutText}>Sign Out</Text>
      </TouchableOpacity>
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
