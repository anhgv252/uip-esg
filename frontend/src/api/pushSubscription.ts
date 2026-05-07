import { apiClient } from './client'

export interface PushSubscriptionParams {
  endpoint: string
  platform: string
  p256dh?: string
  authKey?: string
  deviceToken?: string
  userAgent?: string
}

export interface PushSubscriptionResponse {
  id: string
  platform: string
  endpoint: string
  active: boolean
  createdAt: string
}

export const subscribeToPush = (params: PushSubscriptionParams) =>
  apiClient.post<PushSubscriptionResponse>('/push/subscribe', params).then((r) => r.data)

export const unsubscribeFromPush = (subscriptionId: string) =>
  apiClient.delete(`/push/subscriptions/${subscriptionId}`).then((r) => r.data)

export const listPushSubscriptions = () =>
  apiClient.get<PushSubscriptionResponse[]>('/push/subscriptions').then((r) => r.data)
