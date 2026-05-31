import * as SecureStore from 'expo-secure-store'

// Safe wrapper: SecureStore fails on BlueStacks (x86, no Keystore hardware)
// All functions return null/void instead of throwing.

export async function storageGet(key: string): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(key)
  } catch {
    return null
  }
}

export async function storageSet(key: string, value: string): Promise<void> {
  try {
    await SecureStore.setItemAsync(key, value)
  } catch {
    // Silent fail in emulators without hardware Keystore
  }
}

export async function storageDelete(key: string): Promise<void> {
  try {
    await SecureStore.deleteItemAsync(key)
  } catch {
    // Silent fail
  }
}
