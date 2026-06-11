import { View, Text, FlatList, StyleSheet, RefreshControl, TouchableOpacity, Alert } from 'react-native'
import { useBuildingList } from '../../hooks/useBuildingList'
import { useConfirmation } from '../../hooks/useConfirmation'
import { useAuthMobile } from '../../hooks/useAuthMobile'
import ControlConfirmModal from '../../components/ControlConfirmModal'
import HighDangerConfirmModal from '../../components/HighDangerConfirmModal'
import type { Building } from '../../hooks/useBuildingList'
import type { BmsDeviceCommand } from '../../types/bms'
import { classifyDangerLevel, COMMAND_LABELS } from '../../types/bms'

/** Sample device commands for demo / pilot — replaced by API in v3.1 */
const DEMO_COMMANDS: BmsDeviceCommand[] = [
  {
    commandId: 'cmd-hvac',
    deviceId: 'dev-hvac-001',
    deviceName: 'HVAC Unit A1',
    actuatorName: 'HVAC-A1-Setpoint',
    commandType: 'HVAC_SETPOINT',
    value: 24,
    unit: '°C',
    dangerLevel: 'NORMAL',
    requiresConfirmation: true,
  },
  {
    commandId: 'cmd-light',
    deviceId: 'dev-light-001',
    deviceName: 'Floor 3 Lights',
    actuatorName: 'LIGHT-F3-Switch',
    commandType: 'LIGHT_SWITCH',
    value: true,
    dangerLevel: 'NORMAL',
    requiresConfirmation: false,
  },
  {
    commandId: 'cmd-fan',
    deviceId: 'dev-fan-001',
    deviceName: 'Exhaust Fan B2',
    actuatorName: 'FAN-B2-Speed',
    commandType: 'FAN_SPEED',
    value: 75,
    unit: '%',
    dangerLevel: 'NORMAL',
    requiresConfirmation: true,
  },
  {
    commandId: 'cmd-emergency',
    deviceId: 'dev-door-001',
    deviceName: 'Main Entrance Door',
    actuatorName: 'DOOR-MAIN-EMERGENCY',
    commandType: 'EMERGENCY_DOOR',
    value: 'OPEN',
    dangerLevel: 'HIGH',
    requiresConfirmation: true,
  },
  {
    commandId: 'cmd-shutdown',
    deviceId: 'dev-hvac-002',
    deviceName: 'HVAC System',
    actuatorName: 'HVAC-SYSTEM-SHUTDOWN',
    commandType: 'SHUTDOWN',
    value: 'STOP',
    dangerLevel: 'HIGH',
    requiresConfirmation: true,
  },
]

export default function ControlsScreen() {
  const { data: buildings, isLoading, isError, refetch } = useBuildingList()
  const { token } = useAuthMobile()
  const {
    isVisible: confirmVisible,
    command: pendingCommand,
    isSubmitting,
    error: confirmError,
    requestConfirmation,
    cancelConfirmation,
    confirmAndSend,
    lastResult,
  } = useConfirmation()

  const handleCommandPress = (command: BmsDeviceCommand) => {
    if (!command.requiresConfirmation) {
      // Low-risk commands: direct execute with simple alert
      Alert.alert(
        'Execute Command',
        `Send ${COMMAND_LABELS[command.commandType]} to ${command.deviceName}?`,
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Execute',
            onPress: () => {
              // Direct send — no safety confirmation needed
              // Will be wired to API in v3.1
            },
          },
        ]
      )
      return
    }

    // High-risk or normal-risk with confirmation: open modal
    requestConfirmation(command)
  }

  const handleConfirm = async (reason: string) => {
    if (!pendingCommand) return
    try {
      const username = 'operator' // TODO: extract from JWT token in v3.1
      await confirmAndSend(reason, username)
      Alert.alert('Success', 'Command executed successfully')
    } catch {
      // Error is shown in modal via confirmError state
    }
  }

  const isHighDanger = pendingCommand?.dangerLevel === 'HIGH'

  const renderBuilding = ({ item }: { item: Building }) => (
    <View style={styles.buildingCard}>
      <Text style={styles.buildingName}>{item.name}</Text>
      <Text style={styles.buildingAddress}>{item.address}</Text>
      <View style={styles.buildingMeta}>
        <Text style={styles.buildingStatus}>{item.status}</Text>
        <Text style={styles.buildingDevices}>{item.deviceCount} devices</Text>
      </View>

      {/* Device Commands */}
      <View style={styles.commandsSection}>
        <Text style={styles.commandsTitle}>Commands</Text>
        <View style={styles.commandsGrid}>
          {DEMO_COMMANDS.map(cmd => (
            <TouchableOpacity
              key={cmd.commandId}
              style={[
                styles.commandButton,
                cmd.dangerLevel === 'HIGH' && styles.commandButtonDanger,
              ]}
              onPress={() => handleCommandPress(cmd)}
            >
              <Text
                style={[
                  styles.commandButtonText,
                  cmd.dangerLevel === 'HIGH' && styles.commandButtonTextDanger,
                ]}
              >
                {COMMAND_LABELS[cmd.commandType]}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
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
          renderItem={renderBuilding}
          contentContainerStyle={styles.list}
          refreshControl={
            <RefreshControl refreshing={isLoading} onRefresh={refetch} tintColor="#1565C0" />
          }
        />
      )}

      {/* Standard Confirmation Modal (NORMAL danger) */}
      <ControlConfirmModal
        visible={confirmVisible && !isHighDanger}
        command={pendingCommand}
        isSubmitting={isSubmitting}
        error={confirmError}
        onConfirm={handleConfirm}
        onCancel={cancelConfirmation}
      />

      {/* High-Danger Confirmation Modal (EMERGENCY_DOOR, SHUTDOWN) */}
      <HighDangerConfirmModal
        visible={confirmVisible && isHighDanger}
        command={pendingCommand}
        isSubmitting={isSubmitting}
        error={confirmError}
        onConfirm={handleConfirm}
        onCancel={cancelConfirmation}
      />
    </View>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  title: { fontSize: 24, fontWeight: 'bold', padding: 16, color: '#1565C0' },
  list: { paddingHorizontal: 16, paddingBottom: 16 },
  buildingCard: {
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 16,
    marginBottom: 12,
    elevation: 1,
  },
  buildingName: { fontSize: 16, fontWeight: '600', color: '#333' },
  buildingAddress: { fontSize: 13, color: '#666', marginTop: 4 },
  buildingMeta: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 8 },
  buildingStatus: { fontSize: 12, color: '#1565C0', fontWeight: '500' },
  buildingDevices: { fontSize: 12, color: '#999' },
  commandsSection: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#EEE',
  },
  commandsTitle: {
    fontSize: 13,
    fontWeight: '600',
    color: '#666',
    marginBottom: 8,
    textTransform: 'uppercase',
  },
  commandsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  commandButton: {
    backgroundColor: '#E3F2FD',
    borderRadius: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: '#90CAF9',
  },
  commandButtonDanger: {
    backgroundColor: '#FFEBEE',
    borderColor: '#EF9A9A',
  },
  commandButtonText: {
    fontSize: 12,
    fontWeight: '500',
    color: '#1565C0',
  },
  commandButtonTextDanger: {
    color: '#C62828',
    fontWeight: '700',
  },
  empty: { textAlign: 'center', color: '#999', marginTop: 16 },
  emptyContainer: { alignItems: 'center', marginTop: 24 },
  errorContainer: { alignItems: 'center', marginTop: 40 },
  errorText: { fontSize: 14, color: '#B71C1C', marginBottom: 12 },
  retryButton: {
    backgroundColor: '#1565C0',
    borderRadius: 8,
    paddingHorizontal: 24,
    paddingVertical: 10,
  },
  retryText: { color: '#fff', fontSize: 14, fontWeight: '600' },
})
