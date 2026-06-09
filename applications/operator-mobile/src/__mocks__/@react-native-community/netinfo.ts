/**
 * Mock for @react-native-community/netinfo.
 *
 * Provides a controllable NetInfo mock for unit tests.
 * Tests control connectivity state via __setMockState() and can
 * trigger listeners via __triggerStateChange().
 */

type NetInfoState = {
  isConnected: boolean | null
  isInternetReachable: boolean | null
  type: string
}

type NetInfoStateChangeCallback = (state: NetInfoState) => void

let _currentState: NetInfoState = { isConnected: true, isInternetReachable: true, type: 'wifi' }
let _listeners: NetInfoStateChangeCallback[] = []

const NetInfo = {
  addEventListener: jest.fn((callback: NetInfoStateChangeCallback) => {
    _listeners.push(callback)
    return () => {
      _listeners = _listeners.filter((l) => l !== callback)
    }
  }),
  fetch: jest.fn((): Promise<NetInfoState> => Promise.resolve(_currentState)),

  // Test helpers
  __setMockState: (state: Partial<NetInfoState>) => {
    _currentState = { ..._currentState, ...state }
  },
  __triggerStateChange: (state: Partial<NetInfoState>) => {
    const next = { ..._currentState, ...state }
    _currentState = next
    _listeners.forEach((l) => l(next))
  },
  __reset: () => {
    _currentState = { isConnected: true, isInternetReachable: true, type: 'wifi' }
    _listeners = []
    ;(NetInfo.addEventListener as jest.Mock).mockClear()
    ;(NetInfo.fetch as jest.Mock).mockClear()
    ;(NetInfo.fetch as jest.Mock).mockImplementation(() => Promise.resolve(_currentState))
  },
}

export default NetInfo
export type { NetInfoState }
