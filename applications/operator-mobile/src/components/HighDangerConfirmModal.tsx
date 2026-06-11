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

interface HighDangerConfirmModalProps {
  visible: boolean
  command: BmsDeviceCommand | null
  isSubmitting: boolean
  error: string | null
  onConfirm: (reason: string) => void
  onCancel: () => void
}

const MIN_REASON_LENGTH = 10

/**
 * Extra-danger confirmation modal for EMERGENCY_DOOR and SHUTDOWN commands.
 * Requires: (1) reason field (min 10 chars), (2) typed actuator name match.
 *
 * S11-MOB-02: Safety confirmations for HIGH-danger commands.
 */
export default function HighDangerConfirmModal({
  visible,
  command,
  isSubmitting,
  error,
  onConfirm,
  onCancel,
}: HighDangerConfirmModalProps) {
  const [reason, setReason] = useState('')
  const [typedName, setTypedName] = useState('')

  if (!command) return null

  const isReasonValid = reason.trim().length >= MIN_REASON_LENGTH
  const isNameMatch = typedName.trim() === command.actuatorName
  const isValid = isReasonValid && isNameMatch

  const handleConfirm = () => {
    if (isValid && !isSubmitting) {
      onConfirm(reason.trim())
      setReason('')
      setTypedName('')
    }
  }

  const handleCancel = () => {
    setReason('')
    setTypedName('')
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
          {/* Warning banner */}
          <View style={styles.warningBanner}>
            <Text style={styles.warningIcon}>⚠️</Text>
            <Text style={styles.warningText}>HIGH RISK ACTION</Text>
          </View>

          {/* Header */}
          <Text style={styles.title}>Confirm Critical Command</Text>

          <Text style={styles.description}>
            This action may affect building safety or system availability.
            Please confirm with extra care.
          </Text>

          {/* Command info */}
          <View style={styles.infoBox}>
            <Text style={styles.label}>Device</Text>
            <Text style={styles.value}>{command.deviceName}</Text>

            <Text style={styles.label}>Command</Text>
            <Text style={styles.dangerValue}>
              {COMMAND_LABELS[command.commandType] ?? command.commandType}
            </Text>

            <Text style={styles.label}>Value</Text>
            <Text style={styles.value}>
              {String(command.value)}{command.unit ? ` ${command.unit}` : ''}
            </Text>
          </View>

          {/* Step 1: Reason */}
          <Text style={styles.stepLabel}>Step 1: Reason (min {MIN_REASON_LENGTH} chars) *</Text>
          <TextInput
            style={[styles.input, !isReasonValid && reason.length > 0 && styles.inputInvalid]}
            placeholder="e.g. Emergency evacuation triggered by fire alarm..."
            placeholderTextColor="#999"
            value={reason}
            onChangeText={setReason}
            multiline
            maxLength={500}
            editable={!isSubmitting}
          />

          {/* Step 2: Type actuator name */}
          <Text style={styles.stepLabel}>
            Step 2: Type "{command.actuatorName}" to confirm *
          </Text>
          <TextInput
            style={[
              styles.input,
              typedName.length > 0 && !isNameMatch && styles.inputInvalid,
              isNameMatch && styles.inputValid,
            ]}
            placeholder={command.actuatorName}
            placeholderTextColor="#CCC"
            value={typedName}
            onChangeText={setTypedName}
            autoCapitalize="none"
            autoCorrect={false}
            editable={!isSubmitting}
          />
          {typedName.length > 0 && !isNameMatch && (
            <Text style={styles.mismatchText}>Name does not match</Text>
          )}
          {isNameMatch && (
            <Text style={styles.matchText}>✓ Name matches</Text>
          )}

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
                <Text style={styles.confirmText}>Execute</Text>
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
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    justifyContent: 'flex-end',
  },
  container: {
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 24,
    maxHeight: '90%',
  },
  warningBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFEBEE',
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#EF5350',
  },
  warningIcon: {
    fontSize: 18,
    marginRight: 8,
  },
  warningText: {
    color: '#C62828',
    fontSize: 15,
    fontWeight: '700',
    letterSpacing: 1,
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: '#C62828',
    marginBottom: 6,
  },
  description: {
    fontSize: 13,
    color: '#666',
    marginBottom: 16,
    lineHeight: 18,
  },
  infoBox: {
    backgroundColor: '#FFF3E0',
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#FFB74D',
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
  dangerValue: {
    fontSize: 15,
    color: '#C62828',
    fontWeight: '700',
    marginBottom: 4,
  },
  stepLabel: {
    fontSize: 13,
    color: '#333',
    fontWeight: '600',
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: '#DDD',
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    color: '#333',
    marginBottom: 12,
  },
  inputInvalid: {
    borderColor: '#EF5350',
    backgroundColor: '#FFF5F5',
  },
  inputValid: {
    borderColor: '#4CAF50',
    backgroundColor: '#F1F8E9',
  },
  mismatchText: {
    color: '#EF5350',
    fontSize: 12,
    marginTop: -8,
    marginBottom: 8,
    marginLeft: 4,
  },
  matchText: {
    color: '#4CAF50',
    fontSize: 12,
    marginTop: -8,
    marginBottom: 8,
    marginLeft: 4,
    fontWeight: '500',
  },
  errorBox: {
    backgroundColor: '#FFEBEE',
    borderRadius: 6,
    padding: 10,
    marginTop: 4,
    marginBottom: 8,
  },
  errorText: {
    color: '#C62828',
    fontSize: 13,
  },
  actions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 12,
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
    backgroundColor: '#C62828',
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
