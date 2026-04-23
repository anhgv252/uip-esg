import { apiClient } from './client';

export interface TriggerConfig {
  id: number;
  scenarioKey: string;
  processKey: string;
  displayName: string;
  description: string | null;
  triggerType: 'KAFKA' | 'SCHEDULED' | 'REST';
  kafkaTopic: string | null;
  kafkaConsumerGroup: string | null;
  filterConditions: string | null;
  variableMapping: string;
  scheduleCron: string | null;
  scheduleQueryBean: string | null;
  promptTemplatePath: string | null;
  aiConfidenceThreshold: number | null;
  deduplicationKey: string | null;
  enabled: boolean;
  createdAt: string | null;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface TestTriggerResult {
  filterMatch: boolean;
  mappedVariables: Record<string, unknown>;
  processKey: string;
  scenarioKey: string;
}

export const getWorkflowConfigs = () =>
  apiClient.get<TriggerConfig[]>('/admin/workflow-configs').then((r) => r.data);

export const getWorkflowConfig = (id: number) =>
  apiClient.get<TriggerConfig>(`/admin/workflow-configs/${id}`).then((r) => r.data);

export const createWorkflowConfig = (config: Partial<TriggerConfig>) =>
  apiClient.post<TriggerConfig>('/admin/workflow-configs', config).then((r) => r.data);

export const updateWorkflowConfig = (id: number, config: Partial<TriggerConfig>) =>
  apiClient.put<TriggerConfig>(`/admin/workflow-configs/${id}`, config).then((r) => r.data);

export const disableWorkflowConfig = (id: number) =>
  apiClient.delete(`/admin/workflow-configs/${id}`);

export const testWorkflowConfig = (id: number, samplePayload: Record<string, unknown>) =>
  apiClient.post<TestTriggerResult>(`/admin/workflow-configs/${id}/test`, samplePayload).then((r) => r.data);

export interface FireTriggerResult {
  processInstanceId: string;
  scenarioKey: string;
  status: string;
}

export const fireWorkflowTrigger = (scenarioKey: string, payload: Record<string, unknown>) =>
  apiClient.post<FireTriggerResult>(`/workflow/trigger/${scenarioKey}`, payload).then((r) => r.data);
