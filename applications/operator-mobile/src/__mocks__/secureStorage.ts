/**
 * Mock for src/storage/secureStorage.
 *
 * Provides a simple in-memory key-value store for unit tests.
 * Tests can pre-seed values via __setMockValue(key, value).
 */

const _store: Record<string, string | null> = {}

export const storageGet = jest.fn(async (key: string): Promise<string | null> => {
  return _store[key] ?? null
})

export const storageSet = jest.fn(async (key: string, value: string): Promise<void> => {
  _store[key] = value
})

export const storageDelete = jest.fn(async (key: string): Promise<void> => {
  delete _store[key]
})

// Test helpers
export const __setMockValue = (key: string, value: string | null) => {
  _store[key] = value
}

export const __reset = () => {
  Object.keys(_store).forEach((k) => delete _store[k])
  ;(storageGet as jest.Mock).mockClear()
  ;(storageSet as jest.Mock).mockClear()
  ;(storageDelete as jest.Mock).mockClear()
}
