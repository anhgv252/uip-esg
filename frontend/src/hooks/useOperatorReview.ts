import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { WorkflowDraft, SimulationResult } from '@/types/nlWorkflow';

const DRAFTS_URL = '/api/v1/nl/drafts';

export function useWorkflowDrafts(statusFilter?: string) {
  return useQuery({
    queryKey: ['workflow-drafts', statusFilter],
    queryFn: async () => {
      const url = `${DRAFTS_URL}${statusFilter ? `?status=${statusFilter}` : ''}`;
      const response = await apiClient.get<WorkflowDraft[]>(url);
      return response;
    },
    refetchInterval: 30_000, // poll every 30s for new pending reviews
  });
}

export function useApproveDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      return await apiClient.put<WorkflowDraft>(`${DRAFTS_URL}/${id}/approve`, {});
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-drafts'] });
    },
  });
}

export function useRejectDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, reason }: { id: string; reason: string }) => {
      return await apiClient.put<WorkflowDraft>(`${DRAFTS_URL}/${id}/reject`, {
        rejectionReason: reason,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-drafts'] });
    },
  });
}

export function useSimulateDraft() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      return await apiClient.post<SimulationResult>(`${DRAFTS_URL}/${id}/simulate`, {});
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow-drafts'] });
    },
  });
}

// Legacy export for backward compatibility (to be removed in M5-4)
export type WorkflowReview = WorkflowDraft;
export function useOperatorReview() {
  const { data: reviews = [] } = useWorkflowDrafts();
  const approve = useApproveDraft();
  const reject = useRejectDraft();
  
  return { 
    reviews, 
    approve: { mutate: approve.mutate }, 
    reject: { mutate: reject.mutate } 
  };
}
