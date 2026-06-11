import { useState } from 'react'
import {
  Modal,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from 'react-native'
import type { BmsDeviceCommand } from '../types/bms'
import { COMMAND_LABELS } from '../types/bms'

interface ControlConfirmModalProps {
  visible: boolean
  command: BmsDeviceCommand | null
  isSubmitting: boolean
  error: string | null
  onConfirm: (reason: string) => void
  onCancel: () => void
}

const MIN_REASON_LENGTH = 10

/**
 * Standard confirmation modal for BMS device commands.
 * Requires a reason field (min 10 chars) before allowing confirmation.
 *
 * S11-MOB-02: Safety confirmations for normal-danger commands.
 */
export default function ControlConfirmModal({
  visible,
  command,
  isSubmitting,
  error,
  onConfirm,
  onCancel,
}: ControlConfirmModalProps) {
  const [reason, setReason] = useState('')
  const isValid = reason.trim().length >= MIN_REASON_LENGTH

  if (!command) return null

  const handleConfirm = () => {
    if (isValid && !isSubmitting) {
      onConfirm(reason.trim())
      setReason('')
    }
  }

  const handleCancel = () => {
    setReason('')
    onCancel()
  }

  return (
    <Modal
      visible={visible}
      animationType="slide"
      transparent={true}
      onRequestClose={handleCancel}
    >
      <View style={styles.overlay}>
        <View style={styles.container}>
          {/* Header */}
          <Text style={styles.title}>Confirm Command</Text>

          {/* Command info */}
          <View style={styles.infoBox}>
            <Text style={styles.label}>Device</Text>
            <Text style={styles.value}>{command.deviceName}</Text>

            <Text style={styles.label}>Actuator</Text>
            <Text style={styles.value}>{command.actuatorName}</Text>

            <Text style={styles.label}>Command</Text>
            <Text style={styles.value}>
              {COMMAND_LABELS[command.commandType] ?? command.commandType}
            </Text>

            <Text style={styles.label}>Value</Text>
            <Text style={styles.value}>
              {String(command.value)}{command.unit ? ` ${command.unit}` : ''}
            </Text>
          </View>

          {/* Reason input */}
          <Text style={styles.reasonLabel}>
            Reason (min {MIN_REASON_LENGTH} characters) *
          </Text>
          <TextInput
            style={[styles.reasonInput, !isValid && reason.length > 0 && styles.reasonInvalid]}
            placeholder="e.g. Adjusting HVAC setpoint per operator review..."
            placeholderTextColor="#999"
            value={reason}
            onChangeText={setReason}
            multiline
            maxLength={500}
            editable={!isSubmitting}
          />
          <Text style={styles.charCount}>
            {reason.trim().length}/{MIN_REASON_LENGTH} min characters
          </Text>

          {/* Error */}
          {error && (
            <View style={styles.errorBox}>
              <Text style={styles.errorText}>{error}</Text>
            </View>
          )}

          {/* Actions */}
          <View style={styles.actions}>
            <TouchableOpacity
              style={[styles.cancelButton, isSubmitting && styles.buttonDisabled]}
              onPress={handleCancel}
              disabled={isSubmitting}
            >
              <Text style={styles.cancelText}>Cancel</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.confirmButton,
                (!isValid || isSubmitting) && styles.buttonDisabled,
              ]}
              onPress={handleConfirm}
              disabled={!isValid || isSubmitting}
            >
              {isSubmitting ? (
                <ActivityIndicator size="small" color="#fff" />
              ) : (
                <Text style={styles.confirmText}>Confirm</Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  )
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  container: {
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 24,
    maxHeight: '80%',
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: '#333',
    marginBottom: 16,
  },
  infoBox: {
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
  },
  label: {
    fontSize: 11,
    color: '#999',
    textTransform: 'uppercase',
    marginTop: 4,
  },
  value: {
    fontSize: 15,
    color: '#333',
    fontWeight: '500',
    marginBottom: 4,
  },
  reasonLabel: {
    fontSize: 13,
    color: '#666',
    marginBottom: 6,
    fontWeight: '500',
  },
  reasonInput: {
    borderWidth: 1,
    borderColor: '#DDD',
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    minHeight: 60,
    textAlignVertical: 'top',
    color: '#333',
  },
  reasonInvalid: {
    borderColor: '#FF9800',
  },
  charCount: {
    fontSize: 11,
    color: '#999',
    marginTop: 4,
    textAlign: 'right',
  },
  errorBox: {
    backgroundColor: '#FFEBEE',
    borderRadius: 6,
    padding: 10,
    marginTop: 8,
  },
  errorText: {
    color: '#C62828',
    fontSize: 13,
  },
  actions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 20,
    gap: 12,
  },
  cancelButton: {
    flex: 1,
    backgroundColor: '#E0E0E0',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
  },
  cancelText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#666',
  },
  confirmButton: {
    flex: 1,
    backgroundColor: '#F57C00',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
  },
  confirmText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#fff',
  },
  buttonDisabled: {
    opacity: 0.5,
  },
})
