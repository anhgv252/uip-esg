import { useState, useCallback } from 'react'
import { apiClient } from '../api/client'
import { storageGet } from '../storage/secureStorage'
import type {
  BmsDeviceCommand,
  ConfirmedCommandPayload,
  CommandExecutionResponse,
} from '../types/bms'

const AUTH_TOKEN_KEY = 'auth_token'

interface ConfirmationState {
  isVisible: boolean
  command: BmsDeviceCommand | null
  isSubmitting: boolean
  error: string | null
  lastResult: CommandExecutionResponse | null
}

/**
 * Reusable hook for BMS device command confirmation flow.
 * S11-MOB-02: Safety confirmations for high-risk commands.
 *
 * Usage:
 *   const { requestConfirmation, ... } = useConfirmation()
 *   requestConfirmation(command)  // opens modal
 *   confirmAndSend(reason)        // sends after user confirms
 */
export function useConfirmation() {
  const [state, setState] = useState<ConfirmationState>({
    isVisible: false,
    command: null,
    isSubmitting: false,
    error: null,
    lastResult: null,
  })

  /** Open the confirmation modal for a command */
  const requestConfirmation = useCallback((command: BmsDeviceCommand) => {
    if (!command.requiresConfirmation) {
      // Non-confirmation commands can be sent directly
      return
    }
    setState({
      isVisible: true,
      command,
      isSubmitting: false,
      error: null,
      lastResult: null,
    })
  }, [])

  /** Cancel the confirmation */
  const cancelConfirmation = useCallback(() => {
    setState({
      isVisible: false,
      command: null,
      isSubmitting: false,
      error: null,
      lastResult: null,
    })
  }, [])

  /** Send confirmed command to backend */
  const confirmAndSend = useCallback(async (reason: string, confirmedBy: string) => {
    if (!state.command) return

    const command = state.command
    const payload: ConfirmedCommandPayload = {
      deviceId: command.deviceId,
      commandType: command.commandType,
      value: command.value,
      reason,
      confirmedBy,
      confirmedAt: new Date().toISOString(),
      actuatorName: command.actuatorName,
    }

    setState(prev => ({ ...prev, isSubmitting: true, error: null }))

    try {
      const token = await storageGet(AUTH_TOKEN_KEY)
      const result = await apiClient.post<CommandExecutionResponse>(
        `/api/v1/bms/devices/${command.deviceId}/commands`,
        payload,
        token ?? undefined
      )

      setState({
        isVisible: false,
        command: null,
        isSubmitting: false,
        error: null,
        lastResult: result,
      })

      return result
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Command execution failed'
      setState(prev => ({ ...prev, isSubmitting: false, error: message }))
      throw err
    }
  }, [state.command])

  /** Clear last result */
  const clearResult = useCallback(() => {
    setState(prev => ({ ...prev, lastResult: null, error: null }))
  }, [])

  return {
    /** Whether the confirmation modal is visible */
    isVisible: state.isVisible,
    /** The command pending confirmation */
    command: state.command,
    /** Whether the command is being submitted */
    isSubmitting: state.isSubmitting,
    /** Error from last submission attempt */
    error: state.error,
    /** Result from last successful submission */
    lastResult: state.lastResult,
    /** Open confirmation modal for a command */
    requestConfirmation,
    /** Close modal without confirming */
    cancelConfirmation,
    /** Confirm and send the command to backend */
    confirmAndSend,
    /** Clear result/error state */
    clearResult,
  }
}
