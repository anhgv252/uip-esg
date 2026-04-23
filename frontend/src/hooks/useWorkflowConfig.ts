import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getWorkflowConfigs,
  getWorkflowConfig,
  createWorkflowConfig,
  updateWorkflowConfig,
  disableWorkflowConfig,
  testWorkflowConfig,
  fireWorkflowTrigger,
} from '@/api/workflowConfig';
import type { TriggerConfig, TestTriggerResult, FireTriggerResult } from '@/api/workflowConfig';

export function useWorkflowConfigs() {
  return useQuery({
    queryKey: ['workflow', 'configs'],
    queryFn: getWorkflowConfigs,
    staleTime: 2 * 60 * 1000,
  });
}

export function useWorkflowConfig(id: number | null) {
  return useQuery({
    queryKey: ['workflow', 'configs', id],
    queryFn: () => getWorkflowConfig(id!),
    enabled: Boolean(id),
    staleTime: 2 * 60 * 1000,
  });
}

export function useCreateWorkflowConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (config: Partial<TriggerConfig>) => createWorkflowConfig(config),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflow', 'configs'] });
    },
  });
}

export function useUpdateWorkflowConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, config }: { id: number; config: Partial<TriggerConfig> }) =>
      updateWorkflowConfig(id, config),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflow', 'configs'] });
    },
  });
}

export function useDisableWorkflowConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => disableWorkflowConfig(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflow', 'configs'] });
    },
  });
}

export function useTestWorkflowConfig() {
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: Record<string, unknown> }) =>
      testWorkflowConfig(id, payload),
  });
}

export function useFireWorkflowTrigger() {
  return useMutation({
    mutationFn: ({ scenarioKey, payload }: { scenarioKey: string; payload: Record<string, unknown> }) =>
      fireWorkflowTrigger(scenarioKey, payload),
  });
}

export type { TriggerConfig, TestTriggerResult, FireTriggerResult };
