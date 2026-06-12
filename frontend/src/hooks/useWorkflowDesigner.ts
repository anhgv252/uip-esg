/**
 * useWorkflowDesigner — React Query hooks for the BPMN workflow designer.
 * Migrated from direct apiClient calls in DesignerTab (GAP-031).
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getWorkflowDefinitions,
  createWorkflowDefinition,
  updateWorkflowDefinition,
  deployWorkflowDefinition,
  deleteWorkflowDefinition,
  type WorkflowDefinition,
} from '@/api/workflow';

/** Fetch paginated workflow definitions */
export function useWorkflowDefinitions(page = 0, size = 50) {
  return useQuery({
    queryKey: ['workflow-designer', 'definitions', page, size],
    queryFn: async () => {
      const result = await getWorkflowDefinitions({ page, size });
      return result;
    },
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

/** Create a new workflow definition */
export function useCreateWorkflowDefinition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string; description?: string; bpmnXml: string }) =>
      createWorkflowDefinition(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['workflow-designer', 'definitions'] });
    },
  });
}

/** Update an existing workflow definition */
export function useUpdateWorkflowDefinition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: { name: string; description?: string; bpmnXml: string } }) =>
      updateWorkflowDefinition(id, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['workflow-designer', 'definitions'] });
    },
  });
}

/** Deploy a workflow definition to Camunda */
export function useDeployWorkflowDefinition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deployWorkflowDefinition(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['workflow-designer', 'definitions'] });
    },
  });
}

/** Delete a workflow definition */
export function useDeleteWorkflowDefinition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteWorkflowDefinition(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['workflow-designer', 'definitions'] });
    },
  });
}

export type { WorkflowDefinition };
