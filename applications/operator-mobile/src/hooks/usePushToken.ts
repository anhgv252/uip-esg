import { useEffect } from 'react'
import * as Notifications from 'expo-notifications'
import { Platform } from 'react-native'
import Constants from 'expo-constants'
import { storageGet, storageSet } from '../storage/secureStorage'

const PUSH_TOKEN_KEY = 'push_device_token'

async function registerPushToken(
  token: string,
  tenantId: string,
  apiBase: string,
  authToken: string,
): Promise<void> {
  const platform = Platform.OS === 'ios' ? 'apns' : 'fcm'
  await fetch(`${apiBase}/api/v1/push/subscribe`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${authToken}`,
    },
    body: JSON.stringify({
      platform,
      deviceToken: token,
      tenantId,
    }),
  })
}

export function usePushToken(authToken: string | null, tenantId: string | null) {
  useEffect(() => {
    if (!authToken || !tenantId) return

    async function setup() {
      const { status } = await Notifications.requestPermissionsAsync()
      if (status !== 'granted') return

      const tokenData = await Notifications.getExpoPushTokenAsync({
        projectId: Constants.expoConfig?.extra?.eas?.projectId,
      })
      const token = tokenData.data

      const stored = await storageGet(PUSH_TOKEN_KEY)
      if (stored === token) return

      const apiBase = Constants.expoConfig?.extra?.apiBaseUrl ?? 'http://localhost:8080'
      await registerPushToken(token, tenantId!, apiBase, authToken!)
      await storageSet(PUSH_TOKEN_KEY, token)
    }

    setup().catch(() => {})
  }, [authToken, tenantId])
}
