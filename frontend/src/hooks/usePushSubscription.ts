import { useState, useCallback, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getVapidPublicKey } from '@/pwa/vapid'
import {
  subscribeToPush,
  unsubscribeFromPush,
  listPushSubscriptions,
  type PushSubscriptionParams,
  type PushSubscriptionResponse,
} from '@/api/pushSubscription'

const PUSH_KEYS = ['push-subscriptions'] as const

export function usePushSubscriptions() {
  return useQuery({
    queryKey: PUSH_KEYS,
    queryFn: listPushSubscriptions,
    staleTime: 30_000,
  })
}

export function useSubscribeToPush() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (params: PushSubscriptionParams) => subscribeToPush(params),
    onSuccess: () => qc.invalidateQueries({ queryKey: PUSH_KEYS }),
  })
}

export function useUnsubscribeFromPush() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (subscriptionId: string) => unsubscribeFromPush(subscriptionId),
    onSuccess: () => qc.invalidateQueries({ queryKey: PUSH_KEYS }),
  })
}

export type PermissionState = 'granted' | 'denied' | 'default' | 'unsupported'

export function usePushNotificationRegistration() {
  const [permissionState, setPermissionState] = useState<PermissionState>(() => {
    if (typeof window === 'undefined' || !('Notification' in window)) return 'unsupported'
    return Notification.permission as PermissionState
  })
  const [isSubscribed, setIsSubscribed] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { data: subscriptions } = usePushSubscriptions()
  const subscribeMutation = useSubscribeToPush()
  const unsubscribeMutation = useUnsubscribeFromPush()

  useEffect(() => {
    if (subscriptions && subscriptions.length > 0) {
      setIsSubscribed(subscriptions.some((s: PushSubscriptionResponse) => s.active))
    }
  }, [subscriptions])

  const isSupported = typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window

  const subscribe = useCallback(async () => {
    setError(null)

    if (!isSupported) {
      setError('Push notifications are not supported in this browser')
      return
    }

    try {
      if (Notification.permission === 'default') {
        const perm = await Notification.requestPermission()
        setPermissionState(perm as PermissionState)
        if (perm !== 'granted') {
          setError('Notification permission denied')
          return
        }
      } else if (Notification.permission === 'denied') {
        setPermissionState('denied')
        setError('Notification permission was previously denied. Please enable in browser settings.')
        return
      }

      const registration = await navigator.serviceWorker.ready
      const vapidKey = await getVapidPublicKey()

      const pushSubscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: vapidKey,
      })

      const keys = pushSubscription.toJSON().keys
      await subscribeMutation.mutateAsync({
        endpoint: pushSubscription.endpoint,
        platform: 'web',
        p256dh: keys?.p256dh ?? undefined,
        authKey: keys?.auth ?? undefined,
        userAgent: navigator.userAgent,
      })

      setIsSubscribed(true)
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to subscribe to push notifications'
      setError(message)
    }
  }, [isSupported, subscribeMutation])

  const unsubscribe = useCallback(async () => {
    setError(null)
    try {
      const activeSub = subscriptions?.find((s: PushSubscriptionResponse) => s.active)
      if (activeSub) {
        await unsubscribeMutation.mutateAsync(activeSub.id)
      }

      const registration = await navigator.serviceWorker.ready
      const pushSub = await registration.pushManager.getSubscription()
      if (pushSub) {
        await pushSub.unsubscribe()
      }

      setIsSubscribed(false)
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to unsubscribe'
      setError(message)
    }
  }, [subscriptions, unsubscribeMutation])

  return {
    isSupported,
    isSubscribed,
    permissionState,
    error,
    subscribe,
    unsubscribe,
    isSubscribing: subscribeMutation.isPending,
    isUnsubscribing: unsubscribeMutation.isPending,
  }
}
