import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native'
import { useAuth } from '../../context/AuthContext'

export default function LoginScreen() {
  const { login, isLoading, error } = useAuth()

  return (
    <View style={styles.container}>
      <Text style={styles.logo}>UIP</Text>
      <Text style={styles.subtitle}>Smart City Operations</Text>

      <Text style={styles.description}>
        Sign in to access city monitoring, alerts, and building controls.
      </Text>

      {error && <Text style={styles.error}>{error}</Text>}

      <TouchableOpacity
        style={styles.loginButton}
        onPress={login}
        disabled={isLoading}
      >
        {isLoading ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.loginButtonText}>Sign in with Keycloak</Text>
        )}
      </TouchableOpacity>
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#1565C0', padding: 32 },
  logo: { fontSize: 48, fontWeight: 'bold', color: '#fff' },
  subtitle: { fontSize: 18, color: '#E3F2FD', marginTop: 8 },
  description: { fontSize: 14, color: '#BBDEFB', textAlign: 'center', marginTop: 24, lineHeight: 20 },
  error: { fontSize: 13, color: '#FFCDD2', textAlign: 'center', marginTop: 16, backgroundColor: 'rgba(0,0,0,0.2)', padding: 12, borderRadius: 8 },
  loginButton: { backgroundColor: '#fff', borderRadius: 8, paddingVertical: 14, paddingHorizontal: 32, marginTop: 32, width: '100%', alignItems: 'center' },
  loginButtonText: { color: '#1565C0', fontSize: 16, fontWeight: '600' },
})
