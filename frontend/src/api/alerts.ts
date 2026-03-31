import { apiClient } from './client';

export interface AlertEvent {
  id: number;
  ruleName: string;
  module: string;
  measureType: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  sensorId: number | null;
  sensorName: string | null;
  value: number;
  threshold: number;
  message: string;
  status: 'OPEN' | 'ACKNOWLEDGED';
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
  id: number;
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

export const acknowledgeAlert = (id: number, note?: string) =>
  apiClient.put<AlertEvent>(`/alerts/${id}/acknowledge`, { note }).then((r) => r.data);

export const getAlertRules = () =>
  apiClient.get<AlertRule[]>('/admin/alert-rules').then((r) => r.data);

export const createAlertRule = (data: AlertRuleRequest) =>
  apiClient.post<AlertRule>('/admin/alert-rules', data).then((r) => r.data);

export const deleteAlertRule = (id: number) =>
  apiClient.delete(`/admin/alert-rules/${id}`);
