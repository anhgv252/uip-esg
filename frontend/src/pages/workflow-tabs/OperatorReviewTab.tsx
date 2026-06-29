import { useState } from 'react';
import { Box, Typography, Grid, CircularProgress, Alert, Tabs, Tab } from '@mui/material';
import { useWorkflowDrafts, useApproveDraft, useRejectDraft, useSimulateDraft } from '@/hooks/useOperatorReview';
import type { WorkflowDraft, SimulationResult } from '@/types/nlWorkflow';
import WorkflowReviewCard from './components/WorkflowReviewCard';
import BpmnPreviewPane from './components/BpmnPreviewPane';

type StatusFilter = 'ALL' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'SIMULATED';

export default function OperatorReviewTab() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('PENDING_REVIEW');
  const [selectedDraft, setSelectedDraft] = useState<WorkflowDraft | null>(null);
  const [simulationResult, setSimulationResult] = useState<SimulationResult | null>(null);

  const { data: drafts = [], isLoading, error } = useWorkflowDrafts(
    statusFilter === 'ALL' ? undefined : statusFilter
  );
  const approveMutation = useApproveDraft();
  const rejectMutation = useRejectDraft();
  const simulateMutation = useSimulateDraft();

  const handleApprove = (id: string) => {
    approveMutation.mutate(id, {
      onSuccess: () => {
        setSelectedDraft(null);
        setSimulationResult(null);
      },
    });
  };

  const handleReject = (id: string, reason: string) => {
    rejectMutation.mutate(
      { id, reason },
      {
        onSuccess: () => {
          setSelectedDraft(null);
          setSimulationResult(null);
        },
      }
    );
  };

  const handleSimulate = (id: string) => {
    simulateMutation.mutate(id, {
      onSuccess: (result) => {
        setSimulationResult(result);
      },
    });
  };

  const pendingCount = drafts.filter((d: WorkflowDraft) => d.status === 'PENDING_REVIEW').length;

  if (error) {
    return (
      <Alert severity="error" sx={{ mt: 2 }}>
        Failed to load workflow drafts
      </Alert>
    );
  }

  return (
    <Box sx={{ mt: 2 }}>
      {/* Status Filter Tabs */}
      <Tabs
        value={statusFilter}
        onChange={(_, newValue) => setStatusFilter(newValue)}
        sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}
      >
        <Tab label={`All (${drafts.length})`} value="ALL" />
        <Tab label={`Pending (${pendingCount})`} value="PENDING_REVIEW" />
        <Tab label="Approved" value="APPROVED" />
        <Tab label="Rejected" value="REJECTED" />
        <Tab label="Simulated" value="SIMULATED" />
      </Tabs>

      {isLoading && (
        <Box display="flex" justifyContent="center" py={6}>
          <CircularProgress />
        </Box>
      )}

      {!isLoading && (
        <>
          <Typography variant="body2" color="text.secondary" mb={2}>
            {drafts.length} workflow{drafts.length !== 1 ? 's' : ''}
          </Typography>

          {/* Two-column layout */}
          <Grid container spacing={2}>
            {/* Left: Review List (30%) */}
            <Grid item xs={12} md={4}>
              <Box sx={{ maxHeight: 'calc(100vh - 300px)', overflow: 'auto', pr: 1 }}>
                {drafts.length === 0 ? (
                  <Box
                    sx={{
                      p: 3,
                      textAlign: 'center',
                      border: '1px dashed',
                      borderColor: 'divider',
                      borderRadius: 1,
                    }}
                  >
                    <Typography color="text.secondary" variant="body2">
                      No workflows {statusFilter !== 'ALL' && statusFilter.toLowerCase().replace('_', ' ')}
                    </Typography>
                  </Box>
                ) : (
                  drafts.map((draft: WorkflowDraft) => (
                    <WorkflowReviewCard
                      key={draft.id}
                      review={draft as any} // compatibility adapter
                      isSelected={selectedDraft?.id === draft.id}
                      onSelect={() => {
                        setSelectedDraft(draft);
                        setSimulationResult(null);
                      }}
                      onApprove={() => handleApprove(draft.id)}
                      onReject={() => handleReject(draft.id, 'Quick reject from list')}
                    />
                  ))
                )}
              </Box>
            </Grid>

            {/* Right: BPMN Preview (70%) */}
            <Grid item xs={12} md={8}>
              <BpmnPreviewPane
                review={selectedDraft as any} // compatibility adapter
                simulationResult={simulationResult}
                onApprove={() => selectedDraft && handleApprove(selectedDraft.id)}
                onReject={(reason) => selectedDraft && handleReject(selectedDraft.id, reason)}
                onSimulate={() => selectedDraft && handleSimulate(selectedDraft.id)}
                isPending={selectedDraft?.status === 'PENDING_REVIEW'}
                isSimulating={simulateMutation.isPending}
              />
            </Grid>
          </Grid>
        </>
      )}
    </Box>
  );
}
