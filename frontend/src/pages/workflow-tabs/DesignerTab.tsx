/**
 * DesignerTab — extracted from AiWorkflowPage for code-splitting (v3.1-05).
 * Lazy-loaded so bpmn-js (~648KB) is only fetched when the Designer tab is active.
 *
 * Migrated from direct apiClient calls to React Query hooks (GAP-031).
 */
import { lazy, Suspense, useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  Stack,
  Chip,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert as MuiAlert,
  Skeleton,
  CircularProgress,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SaveIcon from '@mui/icons-material/Save';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import {
  useWorkflowDefinitions,
  useCreateWorkflowDefinition,
  useUpdateWorkflowDefinition,
  useDeployWorkflowDefinition,
  useDeleteWorkflowDefinition,
  type WorkflowDefinition,
} from '@/hooks/useWorkflowDesigner';

// Lazy-loaded heavy BPMN components
const WorkflowModeler = lazy(() => import('@/components/workflow/WorkflowModeler'));
const NodePalette = lazy(() => import('@/components/workflow/NodePalette'));
const AiNodeConfigPanel = lazy(() => import('@/components/workflow/AiNodeConfigPanel'));

const EMPTY_BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_1" bpmnElement="StartEvent_1">
        <dc:Bounds x="173" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

function DesignerSkeleton() {
  return (
    <Box display="flex" gap={2} flexDirection={{ xs: 'column', md: 'row' }}>
      <Box sx={{ width: { xs: '100%', md: 200 } }}>
        <Skeleton variant="rectangular" height={520} />
      </Box>
      <Box flex={1}>
        <Skeleton variant="rectangular" height={520} />
      </Box>
      <Box sx={{ width: { xs: '100%', md: 200 } }}>
        <Skeleton variant="rectangular" height={250} />
        <Skeleton variant="rectangular" height={250} sx={{ mt: 2 }} />
      </Box>
    </Box>
  );
}

export default function DesignerTab() {
  // React Query hooks (GAP-031)
  const { data: definitionsPage, isLoading: definitionsLoading, error: definitionsError } = useWorkflowDefinitions();
  const createMutation = useCreateWorkflowDefinition();
  const updateMutation = useUpdateWorkflowDefinition();
  const deployMutation = useDeployWorkflowDefinition();
  const deleteMutation = useDeleteWorkflowDefinition();

  const definitions = definitionsPage?.content ?? [];

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedXml, setSelectedXml] = useState<string | null>(null);
  const [selectedName, setSelectedName] = useState('');
  const [selectedDesc, setSelectedDesc] = useState('');
  const [selectedNodeId] = useState<string | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [snackbar, setSnackbar] = useState<{ msg: string; severity: 'success' | 'error' | 'info' } | null>(null);

  const selectWorkflow = (wf: WorkflowDefinition) => {
    setSelectedId(wf.id);
    setSelectedXml(wf.bpmnXml);
    setSelectedName(wf.name);
    setSelectedDesc(wf.description ?? '');
  };

  const handleNew = async () => {
    const name = `Workflow ${definitions.length + 1}`;
    const defaultXml = EMPTY_BPMN_XML.replace(
      'Process_1',
      name.replace(/\s/g, '_'),
    ).replace(
      'Process_1',
      name.replace(/\s/g, '_'),
    );

    createMutation.mutate(
      { name, bpmnXml: defaultXml },
      {
        onSuccess: (created) => {
          selectWorkflow(created);
          setSnackbar({ msg: 'New workflow created', severity: 'success' });
        },
        onError: () => {
          setSnackbar({ msg: 'Failed to create workflow', severity: 'error' });
        },
      },
    );
  };

  const handleSave = async (xml: string) => {
    if (!selectedId) return;
    updateMutation.mutate(
      { id: selectedId, data: { name: selectedName, description: selectedDesc, bpmnXml: xml } },
      {
        onSuccess: (updated) => {
          setSnackbar({ msg: `Saved (v${updated.version})`, severity: 'success' });
        },
        onError: () => {
          setSnackbar({ msg: 'Save failed', severity: 'error' });
        },
      },
    );
  };

  const handleDeploy = async () => {
    if (!selectedId) return;
    deployMutation.mutate(selectedId, {
      onSuccess: () => {
        setSnackbar({ msg: 'Deployed to Camunda!', severity: 'success' });
      },
      onError: () => {
        setSnackbar({ msg: 'Deploy failed', severity: 'error' });
      },
    });
  };

  const handleDelete = () => {
    if (!selectedId) return;
    setDeleteConfirmOpen(true);
  };

  const handleDeleteConfirmed = async () => {
    setDeleteConfirmOpen(false);
    if (!selectedId) return;
    deleteMutation.mutate(selectedId, {
      onSuccess: () => {
        setSelectedId(null);
        setSelectedXml(null);
        setSnackbar({ msg: 'Workflow deleted', severity: 'info' });
      },
      onError: () => {
        setSnackbar({ msg: 'Delete failed', severity: 'error' });
      },
    });
  };

  const isSaving = updateMutation.isPending;
  const isDeploying = deployMutation.isPending;

  return (
    <Box sx={{ mt: 2 }}>
      {/* Loading state (GAP-031) */}
      {definitionsLoading && (
        <Box display="flex" justifyContent="center" py={4}>
          <CircularProgress />
        </Box>
      )}

      {/* Error state (GAP-031) */}
      {definitionsError && (
        <MuiAlert severity="error" sx={{ mb: 2 }}>
          Failed to load workflow definitions. Please try again.
        </MuiAlert>
      )}

      {/* Toolbar — all buttons have aria-label (v3.1-17) */}
      <Stack direction="row" spacing={1.5} mb={2} flexWrap="wrap" alignItems="center">
        <Button
          variant="contained"
          size="small"
          startIcon={<AddIcon />}
          onClick={handleNew}
          disabled={createMutation.isPending}
          aria-label="Create new workflow"
        >
          {createMutation.isPending ? 'Creating...' : 'New Workflow'}
        </Button>
        <Button
          variant="outlined"
          size="small"
          startIcon={<SaveIcon />}
          disabled={!selectedId || isSaving}
          onClick={() => window.dispatchEvent(new Event('bpmn-save'))}
          aria-label="Save current workflow"
        >
          {isSaving ? 'Saving...' : 'Save'}
        </Button>
        <Button
          variant="outlined"
          size="small"
          color="success"
          startIcon={<CloudUploadIcon />}
          disabled={!selectedId || isDeploying}
          onClick={handleDeploy}
          aria-label="Deploy workflow to Camunda"
        >
          {isDeploying ? 'Deploying...' : 'Deploy'}
        </Button>
        <Button
          variant="outlined"
          size="small"
          color="error"
          startIcon={<DeleteOutlineIcon />}
          disabled={!selectedId || deleteMutation.isPending}
          onClick={handleDelete}
          aria-label="Delete current workflow"
        >
          {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
        </Button>
        {selectedId && (
          <Chip label={selectedName} size="small" color="primary" variant="outlined" />
        )}
      </Stack>

      <Suspense fallback={<DesignerSkeleton />}>
        <Box display="flex" gap={2} flexDirection={{ xs: 'column', md: 'row' }}>
          {/* Workflow list */}
          <Box sx={{ width: { xs: '100%', md: 200 }, flexShrink: 0 }}>
            <Paper variant="outlined" sx={{ p: 1, maxHeight: 520, overflow: 'auto' }}>
              <Typography variant="caption" fontWeight={700} color="text.secondary" gutterBottom display="block" px={0.5}>
                Workflows
              </Typography>
              {!definitionsLoading && definitions.length === 0 && (
                <Typography variant="body2" color="text.secondary" px={0.5} py={1}>
                  No workflows yet
                </Typography>
              )}
              <Stack spacing={0.5}>
                {definitions.map((wf) => (
                  <Box
                    key={wf.id}
                    onClick={() => selectWorkflow(wf)}
                    role="button"
                    tabIndex={0}
                    aria-label={`Select workflow: ${wf.name} version ${wf.version}`}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        selectWorkflow(wf);
                      }
                    }}
                    sx={{
                      p: 0.75,
                      borderRadius: 1,
                      cursor: 'pointer',
                      bgcolor: selectedId === wf.id ? 'action.selected' : 'transparent',
                      '&:hover': {
                        bgcolor: 'action.hover',
                        transform: 'translateX(2px)',
                        transition: 'transform 0.15s ease',
                      },
                    }}
                  >
                    <Typography variant="body2" fontSize="0.8rem" noWrap>{wf.name}</Typography>
                    <Typography variant="caption" color="text.secondary">v{wf.version}</Typography>
                  </Box>
                ))}
              </Stack>
            </Paper>
          </Box>

          {/* BPMN Modeler */}
          <Box flex={1}>
            <WorkflowModeler
              initialXml={selectedXml}
              onSave={handleSave}
              height={520}
            />
          </Box>

          {/* Right panel: Palette + AI Config */}
          <Box sx={{ width: { xs: '100%', md: 220 }, flexShrink: 0 }}>
            <Stack spacing={2}>
              <NodePalette />
              <AiNodeConfigPanel selectedNodeId={selectedNodeId} />
            </Stack>
          </Box>
        </Box>
      </Suspense>

      {/* Snackbar */}
      {snackbar && (
        <MuiAlert
          severity={snackbar.severity}
          sx={{ mt: 2 }}
          onClose={() => setSnackbar(null)}
        >
          {snackbar.msg}
        </MuiAlert>
      )}

      <Dialog open={deleteConfirmOpen} onClose={() => setDeleteConfirmOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete Workflow?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            Are you sure you want to delete <strong>{selectedName || 'this workflow'}</strong>?
            This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirmOpen(false)}>Cancel</Button>
          <Button variant="contained" color="error" startIcon={<DeleteOutlineIcon />} onClick={handleDeleteConfirmed}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
