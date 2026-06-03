import { View, Text, StyleSheet, TouchableOpacity } from 'react-native'

interface KpiCardProps {
  /** Main numeric value */
  value: string | number
  /** Label below value */
  label: string
  /** Subtitle or trend indicator */
  subtitle?: string
  /** Card accent color (left border) */
  color?: string
  /** Icon character (emoji fallback) */
  icon?: string
  /** Tap handler */
  onPress?: () => void
  /** Is loading */
  isLoading?: boolean
}

export default function KpiCard({
  value,
  label,
  subtitle,
  color = '#1565C0',
  icon,
  onPress,
  isLoading = false,
}: KpiCardProps) {
  const content = (
    <View style={[styles.card, { borderLeftColor: color }]}>
      <View style={styles.header}>
        {icon && <Text style={styles.icon}>{icon}</Text>}
        <Text style={styles.label}>{label}</Text>
      </View>
      <Text style={[styles.value, { color }]} numberOfLines={1}>
        {isLoading ? '—' : value}
      </Text>
      {subtitle && <Text style={styles.subtitle}>{subtitle}</Text>}
    </View>
  )

  if (onPress) {
    return <TouchableOpacity onPress={onPress} activeOpacity={0.7}>{content}</TouchableOpacity>
  }
  return content
}

const styles = StyleSheet.create({
  card: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    borderLeftWidth: 4,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 8,
  },
  icon: {
    fontSize: 18,
  },
  label: {
    fontSize: 12,
    color: '#666',
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  value: {
    fontSize: 28,
    fontWeight: 'bold',
    lineHeight: 34,
  },
  subtitle: {
    fontSize: 11,
    color: '#999',
    marginTop: 4,
  },
})
