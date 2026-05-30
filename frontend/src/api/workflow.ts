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

// --- AI Workflow Designer (Sprint 6: S6-AI02) ---

export interface WorkflowDefinition {
  id: string;
  tenantId: string;
  name: string;
  description: string | null;
  bpmnXml: string;
  version: number;
  isActive: boolean;
  camundaDeploymentId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowDefinitionsPage {
  content: WorkflowDefinition[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export const getWorkflowDefinitions = (params?: { page?: number; size?: number }) =>
  apiClient
    .get<WorkflowDefinitionsPage>('/workflows', { params })
    .then((r) => r.data);

export const getWorkflowDefinition = (id: string) =>
  apiClient
    .get<WorkflowDefinition>(`/workflows/${id}`)
    .then((r) => r.data);

export const createWorkflowDefinition = (data: { name: string; description?: string; bpmnXml: string }) =>
  apiClient
    .post<WorkflowDefinition>('/workflows', data)
    .then((r) => r.data);

export const updateWorkflowDefinition = (id: string, data: { name: string; description?: string; bpmnXml: string }) =>
  apiClient
    .put<WorkflowDefinition>(`/workflows/${id}`, data)
    .then((r) => r.data);

export const deleteWorkflowDefinition = (id: string) =>
  apiClient.delete(`/workflows/${id}`);

export const deployWorkflowDefinition = (id: string) =>
  apiClient
    .post<WorkflowDefinition>(`/workflows/${id}/deploy`)
    .then((r) => r.data);

export const executeWorkflowDefinition = (id: string) =>
  apiClient
    .post<Record<string, unknown>>(`/workflows/${id}/execute`)
    .then((r) => r.data);
