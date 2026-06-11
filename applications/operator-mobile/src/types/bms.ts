/**
 * BMS Device Control Types — Mobile Control Panel
 * S11-MOB-02: Safety confirmations for high-risk commands
 */

/** BMS device command types */
export type BmsCommandType =
  | 'HVAC_SETPOINT'
  | 'LIGHT_SWITCH'
  | 'FAN_SPEED'
  | 'DAMPER_POSITION'
  | 'EMERGENCY_DOOR'
  | 'SHUTDOWN'
  | 'ALARM_SILENCE'
  | 'VENTILATION_MODE'
  | 'PUMP_SPEED'
  | 'VALVE_POSITION'

/** Danger level of a command — determines confirmation flow */
export type CommandDangerLevel = 'NORMAL' | 'HIGH'

/** Command with metadata for safety classification */
export interface BmsDeviceCommand {
  commandId: string
  deviceId: string
  deviceName: string
  actuatorName: string
  commandType: BmsCommandType
  value: number | string | boolean
  unit?: string
  dangerLevel: CommandDangerLevel
  requiresConfirmation: boolean
}

/** Payload sent to backend after confirmation */
export interface ConfirmedCommandPayload {
  deviceId: string
  commandType: BmsCommandType
  value: number | string | boolean
  reason: string
  confirmedBy: string
  confirmedAt: string  // ISO timestamp
  actuatorName: string
}

/** Backend response after command execution */
export interface CommandExecutionResponse {
  success: boolean
  commandId: string
  executedAt: string
  message?: string
}

/** Classify a command's danger level */
export function classifyDangerLevel(commandType: BmsCommandType): CommandDangerLevel {
  const HIGH_DANGER_COMMANDS: BmsCommandType[] = ['EMERGENCY_DOOR', 'SHUTDOWN']
  return HIGH_DANGER_COMMANDS.includes(commandType) ? 'HIGH' : 'NORMAL'
}

/** Check if a command requires typed name confirmation */
export function requiresTypedConfirmation(commandType: BmsCommandType): boolean {
  return ['EMERGENCY_DOOR', 'SHUTDOWN'].includes(commandType)
}

/** All available command types for UI display */
export const COMMAND_LABELS: Record<BmsCommandType, string> = {
  HVAC_SETPOINT: 'HVAC Setpoint',
  LIGHT_SWITCH: 'Light Switch',
  FAN_SPEED: 'Fan Speed',
  DAMPER_POSITION: 'Damper Position',
  EMERGENCY_DOOR: '⚠️ Emergency Door',
  SHUTDOWN: '⚠️ System Shutdown',
  ALARM_SILENCE: 'Alarm Silence',
  VENTILATION_MODE: 'Ventilation Mode',
  PUMP_SPEED: 'Pump Speed',
  VALVE_POSITION: 'Valve Position',
}
