import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getProcessDefinitions,
  getProcessInstances,
  getInstanceVariables,
  startProcess,
  getProcessDefinitionXml,
} from '@/api/workflow';

export function useProcessDefinitions() {
  return useQuery({
    queryKey: ['workflow', 'definitions'],
    queryFn: getProcessDefinitions,
    staleTime: 5 * 60 * 1000,
  });
}

export function useProcessInstances(status?: string, page = 0, size = 20) {
  return useQuery({
    queryKey: ['workflow', 'instances', status, page, size],
    queryFn: () => getProcessInstances({ status: status || undefined, page, size }),
    staleTime: 15 * 1000,
    refetchInterval: 30 * 1000,
  });
}

export function useInstanceVariables(instanceId: string | null) {
  return useQuery({
    queryKey: ['workflow', 'instance-variables', instanceId],
    queryFn: () => getInstanceVariables(instanceId!),
    enabled: Boolean(instanceId),
    staleTime: 10 * 1000,
  });
}

export function useProcessDefinitionXml(definitionId: string | null) {
  return useQuery({
    queryKey: ['workflow', 'definition-xml', definitionId],
    queryFn: () => getProcessDefinitionXml(definitionId!),
    enabled: Boolean(definitionId),
    staleTime: 10 * 60 * 1000,
  });
}

export function useStartProcess() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      processKey,
      variables,
    }: {
      processKey: string;
      variables: Record<string, unknown>;
    }) => startProcess(processKey, variables),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflow', 'instances'] });
    },
  });
}
