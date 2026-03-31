import { apiClient } from './client';

export interface ErrorRecord {
  id: number;
  sourceModule: string;
  kafkaTopic: string;
  kafkaOffset: number;
  errorType: string;
  errorMessage: string;
  rawPayload: Record<string, unknown> | null;
  status: 'UNRESOLVED' | 'RESOLVED' | 'REINGESTED';
  occurredAt: string;
  resolvedAt: string | null;
  resolvedBy: string | null;
}

export interface ErrorRecordsPage {
  content: ErrorRecord[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export const getErrors = (params?: {
  module?: string;
  status?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}) => apiClient.get<ErrorRecordsPage>('/admin/errors', { params }).then((r) => r.data);

export const resolveError = (id: number) =>
  apiClient.post<ErrorRecord>(`/admin/errors/${id}/resolve`).then((r) => r.data);

export const reingestError = (id: number) =>
  apiClient.post<ErrorRecord>(`/admin/errors/${id}/reingest`).then((r) => r.data);
