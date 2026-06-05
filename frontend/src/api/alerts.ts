import { apiClient } from './client';
import type { components } from '@uip/api-types';

// Use generated types from OpenAPI spec with runtime guarantees for required fields
// The backend may return nulls for some fields, but we type them as required for fields that
// are always present in practice (id, module, severity, status, detectedAt, etc.)
export type AlertEvent = Required<
  Pick<
    components['schemas']['AlertEventDto'],
    'id' | 'module' | 'measureType' | 'value' | 'threshold' | 'severity' | 'status' | 'detectedAt'
  >
> &
  Omit<
    components['schemas']['AlertEventDto'],
    'id' | 'module' | 'measureType' | 'value' | 'threshold' | 'severity' | 'status' | 'detectedAt'
  >;

export type AlertEventsPage = Omit<
  components['schemas']['PageAlertEventDto'],
  'content' | 'number' | 'totalPages' | 'totalElements'
> & {
  content: AlertEvent[];
  number: number;
  totalPages: number;
  totalElements: number;
};

export type AlertRule = Required<
  Pick<
    components['schemas']['AlertRule'],
    'id' | 'ruleName' | 'module' | 'measureType' | 'operator' | 'threshold' | 'severity' | 'active' | 'cooldownMinutes'
  >
> &
  Omit<
    components['schemas']['AlertRule'],
    'id' | 'ruleName' | 'module' | 'measureType' | 'operator' | 'threshold' | 'severity' | 'active' | 'cooldownMinutes'
  >;

export type AlertRuleRequest = Required<
  Pick<
    components['schemas']['AlertRuleRequest'],
    'ruleName' | 'module' | 'measureType' | 'operator' | 'threshold' | 'severity' | 'cooldownMinutes'
  >
>;

export const getAlerts = (params?: {
  status?: string;
  severity?: string;
  module?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  tenantId?: string;
}) => apiClient.get<AlertEventsPage>('/alerts', { params }).then((r) => r.data);

export const acknowledgeAlert = (id: string, note?: string, tenantId?: string) =>
  apiClient.put<AlertEvent>(`/alerts/${id}/acknowledge`, { note, tenantId }).then((r) => r.data);

export const escalateAlert = (id: string, note?: string, tenantId?: string) =>
  apiClient.put<AlertEvent>(`/alerts/${id}/escalate`, { note, tenantId }).then((r) => r.data);

export const resolveAlert = (id: string, note?: string) =>
  apiClient.put<AlertEvent>(`/alerts/${id}/resolve`, { note }).then((r) => r.data);

export const getCitizenNotifications = (params?: { page?: number; size?: number }) =>
  apiClient.get<AlertEventsPage>('/alerts/notifications', { params }).then((r) => r.data);

export const getAlertRules = () =>
  apiClient.get<AlertRule[]>('/admin/alert-rules').then((r) => r.data);

export const createAlertRule = (data: AlertRuleRequest) =>
  apiClient.post<AlertRule>('/admin/alert-rules', data).then((r) => r.data);

export const deleteAlertRule = (id: string) =>
  apiClient.delete(`/admin/alert-rules/${id}`);
