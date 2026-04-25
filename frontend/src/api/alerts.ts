import { apiClient } from './client';

export interface AlertEvent {
  id: string;
  ruleId: string | null;
  ruleName: string | null;
  sensorId: string | null;
  module: string;
  measureType: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  value: number;
  threshold: number;
  note: string | null;
  status: 'OPEN' | 'ACKNOWLEDGED' | 'ESCALATED';
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  detectedAt: string;
}

export interface AlertEventsPage {
  content: AlertEvent[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface AlertRule {
  id: string;
  ruleName: string;
  module: string;
  measureType: string;
  operator: string;
  threshold: number;
  severity: string;
  active: boolean;
  cooldownMinutes: number;
}

export interface AlertRuleRequest {
  ruleName: string;
  module: string;
  measureType: string;
  operator: string;
  threshold: number;
  severity: string;
  cooldownMinutes: number;
}

export const getAlerts = (params?: {
  status?: string;
  severity?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}) => apiClient.get<AlertEventsPage>('/alerts', { params }).then((r) => r.data);

export const acknowledgeAlert = (id: string, note?: string) =>
  apiClient.put<AlertEvent>(`/alerts/${id}/acknowledge`, { note }).then((r) => r.data);

export const escalateAlert = (id: string, note?: string) =>
  apiClient.put<AlertEvent>(`/alerts/${id}/escalate`, { note }).then((r) => r.data);

export const getCitizenNotifications = (params?: { page?: number; size?: number }) =>
  apiClient.get<AlertEventsPage>('/alerts/notifications', { params }).then((r) => r.data);

export const getAlertRules = () =>
  apiClient.get<AlertRule[]>('/admin/alert-rules').then((r) => r.data);

export const createAlertRule = (data: AlertRuleRequest) =>
  apiClient.post<AlertRule>('/admin/alert-rules', data).then((r) => r.data);

export const deleteAlertRule = (id: string) =>
  apiClient.delete(`/admin/alert-rules/${id}`);
