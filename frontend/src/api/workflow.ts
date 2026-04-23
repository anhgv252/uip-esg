import { apiClient } from './client';

export interface ProcessDefinition {
  id: string;
  key: string;
  name: string;
  tenantId: string | null;
  version: number;
  deploymentId: string;
  suspended: boolean;
}

export interface ProcessInstance {
  id: string;
  processDefinitionId: string;
  processDefinitionKey: string;
  businessKey: string | null;
  state: 'ACTIVE' | 'COMPLETED' | 'EXTERNALLY_TERMINATED' | string;
  startTime: string;
  variables: Record<string, unknown>;
}

export interface ProcessInstancesPage {
  content: ProcessInstance[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export const getProcessDefinitions = () =>
  apiClient.get<ProcessDefinition[]>('/workflow/definitions').then((r) => r.data);

export const getProcessInstances = (params?: {
  status?: string;
  page?: number;
  size?: number;
}) =>
  apiClient
    .get<ProcessInstancesPage>('/workflow/instances', { params })
    .then((r) => r.data);

export const getInstanceVariables = (instanceId: string) =>
  apiClient
    .get<Record<string, unknown>>(`/workflow/instances/${instanceId}/variables`)
    .then((r) => r.data);

export const startProcess = (processKey: string, variables: Record<string, unknown>) =>
  apiClient
    .post<ProcessInstance>(`/workflow/start/${processKey}`, variables)
    .then((r) => r.data);

export const getProcessDefinitionXml = (definitionId: string) =>
  apiClient
    .get<string>(`/workflow/definitions/${definitionId}/xml`, {
      responseType: 'text',
    })
    .then((r) => r.data);
