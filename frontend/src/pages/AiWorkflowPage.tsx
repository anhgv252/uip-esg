import { lazy, Suspense, useState } from 'react';
import {
  Box,
  Typography,
  Tabs,
  Tab,
  Chip,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  CircularProgress,
  Alert as MuiAlert,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Skeleton,
  TextField,
  MenuItem,
} from '@mui/material';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import BoltIcon from '@mui/icons-material/Bolt';
import DesignServicesIcon from '@mui/icons-material/DesignServices';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import {
  useProcessDefinitions,
  useProcessInstances,
  useProcessDefinitionXml,
  useStartProcess,
} from '@/hooks/useWorkflowData';
import ProcessInstanceTable from '@/components/workflow/ProcessInstanceTable';
import InstanceDetailDrawer from '@/components/workflow/InstanceDetailDrawer';
import TemplateGallery from '@/components/workflow/TemplateGallery';
import WorkflowWizard from '@/components/workflow/WorkflowWizard';
import type { ProcessInstance, ProcessDefinition } from '@/api/workflow';
import type { WorkflowTemplate } from '@/types/workflowTemplate';
import { useAuth } from '@/hooks/useAuth';

// ── Code-split heavy components (v3.1-05) ──────────────────────────────────
const BpmnViewer = lazy(() => import('@/components/workflow/BpmnViewer'));

// Lazy load heavy tab content to reduce initial bundle
const LazyDesignerTab = lazy(() => import('@/pages/workflow-tabs/DesignerTab'));
const LazyLiveDemoTab = lazy(() => import('@/pages/workflow-tabs/LiveDemoTab'));

// ── Definitions tab ─────────────────────────────────────────────────────────

interface StartProcessDialogProps {
  definition: ProcessDefinition;
  onClose: () => void;
  initialVariablesJson?: string;
}

function StartProcessDialog({ definition, onClose, initialVariablesJson }: StartProcessDialogProps) {
  const { mutate: start, isPending } = useStartProcess();
  const [variablesJson, setVariablesJson] = useState(initialVariablesJson ?? '{}');
  const [jsonError, setJsonError] = useState<string | null>(null);

  const handleSubmit = () => {
    try {
      const variables = JSON.parse(variablesJson) as Record<string, unknown>;
      setJsonError(null);
      start(
        { processKey: definition.key, variables },
        { onSuccess: onClose },
      );
    } catch {
      setJsonError('Invalid JSON');
    }
  };

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Start Process: {definition.name ?? definition.key}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" gutterBottom sx={{ mt: 1 }}>
          Process key: <strong>{definition.key}</strong> &middot; Version {definition.version}
        </Typography>
        <TextField
          fullWidth
          multiline
          rows={6}
          label="Variables (JSON)"
          value={variablesJson}
          onChange={(e) => setVariablesJson(e.target.value)}
          error={!!jsonError}
          helperText={jsonError ?? 'Enter process variables as JSON object'}
          sx={{ mt: 2 }}
          inputProps={{ style: { fontFamily: 'monospace' } }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={isPending}
          startIcon={isPending ? <CircularProgress size={16} /> : <PlayArrowIcon />}
        >
          Start
        </Button>
      </DialogActions>
    </Dialog>
  );
}

interface DefinitionDetailProps {
  definition: ProcessDefinition | null;
}

function DefinitionDetail({ definition }: DefinitionDetailProps) {
  const { data: xml, isLoading } = useProcessDefinitionXml(definition?.id ?? null);

  if (!definition) {
    return (
      <Box display="flex" alignItems="center" justifyContent="center" height={300}>
        <Typography color="text.secondary" variant="body2">
          Select a process definition to view its diagram
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="subtitle2" gutterBottom>
        {definition.name ?? definition.key} &middot; v{definition.version}
      </Typography>
      {isLoading ? (
        <Skeleton variant="rectangular" height={400} />
      ) : (
        <Suspense fallback={<Skeleton variant="rectangular" height={400} />}>
          <BpmnViewer xml={xml} height={400} />
        </Suspense>
      )}
    </Box>
  );
}

function DefinitionsTab({ onStartProcess }: { onStartProcess: (def: ProcessDefinition) => void }) {
  const { data: definitions, isLoading, error } = useProcessDefinitions();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ROLE_ADMIN';

  const [selectedDef, setSelectedDef] = useState<ProcessDefinition | null>(null);

  return (
    <Box sx={{ display: 'flex', gap: 2, mt: 2, flexDirection: { xs: 'column', md: 'row' } }}>
      {/* Definition list */}
      <Box sx={{ width: { xs: '100%', md: 320 }, flexShrink: 0 }}>
        {error && <MuiAlert severity="error" sx={{ mb: 2 }}>Failed to load definitions</MuiAlert>}
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell align="center">Ver</TableCell>
                <TableCell align="center">Status</TableCell>
                {isAdmin && <TableCell align="center">Action</TableCell>}
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={4} align="center" sx={{ py: 3 }}>
                    <CircularProgress size={20} />
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && (!definitions || definitions.length === 0) && (
                <TableRow>
                  <TableCell colSpan={4} align="center" sx={{ py: 3 }}>
                    <Typography color="text.secondary" variant="body2">No definitions deployed</Typography>
                  </TableCell>
                </TableRow>
              )}
              {definitions?.map((def) => (
                <TableRow
                  key={def.id}
                  hover
                  selected={selectedDef?.id === def.id}
                  sx={{ cursor: 'pointer' }}
                  onClick={() => setSelectedDef(def)}
                >
                  <TableCell>
                    <Typography variant="body2" noWrap sx={{ maxWidth: 160 }}>
                      {def.name ?? def.key}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                      {def.key}
                    </Typography>
                  </TableCell>
                  <TableCell align="center">
                    <Chip label={`v${def.version}`} size="small" variant="outlined" />
                  </TableCell>
                  <TableCell align="center">
                    <Chip
                      label={def.suspended ? 'Suspended' : 'Active'}
                      size="small"
                      color={def.suspended ? 'default' : 'success'}
                    />
                  </TableCell>
                  {isAdmin && (
                    <TableCell align="center" onClick={(e) => e.stopPropagation()}>
                      <Button
                        size="small"
                        variant="outlined"
                        disabled={def.suspended}
                        startIcon={<PlayArrowIcon />}
                        onClick={() => onStartProcess(def)}
                        aria-label={`Start process ${def.name ?? def.key}`}
                      >
                        Start
                      </Button>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>

      {/* BPMN diagram */}
      <Box flex={1}>
        <DefinitionDetail definition={selectedDef} />
      </Box>
    </Box>
  );
}

// ── Instances tab ────────────────────────────────────────────────────────────

function InstancesTab() {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedInstance, setSelectedInstance] = useState<ProcessInstance | null>(null);

  const { data, isLoading, error } = useProcessInstances(
    statusFilter === 'ALL' ? undefined : statusFilter,
    page,
    20,
  );

  const instances = data?.content ?? [];

  return (
    <Box sx={{ mt: 2 }}>
      {/* Filters */}
      <Box display="flex" gap={2} mb={2} flexWrap="wrap" alignItems="center">
        <TextField
          select
          size="small"
          label="State"
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="ALL">All States</MenuItem>
          <MenuItem value="ACTIVE">Active</MenuItem>
          <MenuItem value="COMPLETED">Completed</MenuItem>
          <MenuItem value="EXTERNALLY_TERMINATED">Terminated</MenuItem>
        </TextField>
        {data && (
          <Chip
            label={`${data.totalElements} instance${data.totalElements !== 1 ? 's' : ''}`}
            size="small"
          />
        )}
      </Box>

      {error && <MuiAlert severity="error" sx={{ mb: 2 }}>Failed to load instances</MuiAlert>}

      <ProcessInstanceTable
        instances={instances}
        totalElements={data?.totalElements ?? 0}
        page={page}
        onPageChange={setPage}
        isLoading={isLoading}
        onRowClick={setSelectedInstance}
      />

      <InstanceDetailDrawer
        instance={selectedInstance}
        onClose={() => setSelectedInstance(null)}
      />
    </Box>
  );
}

// ── Main page ────────────────────────────────────────────────────────────────

export default function AiWorkflowPage() {
  const [tab, setTab] = useState(0);
  const [startingDef, setStartingDef] = useState<ProcessDefinition | null>(null);
  const [startingDefInitialVars, setStartingDefInitialVars] = useState('{}');
  const [wizardOpen, setWizardOpen] = useState(false);
  const { mutateAsync: startProcessAsync, isPending: isStartPending } = useStartProcess();

  const handleStartProcess = (def: ProcessDefinition) => {
    setStartingDefInitialVars('{}');
    setStartingDef(def);
  };

  const handleSelectTemplate = (template: WorkflowTemplate) => {
    const defaultVars: Record<string, unknown> = {};
    for (const param of template.params) {
      if (param.defaultValue !== undefined) {
        defaultVars[param.key] = param.defaultValue;
      }
    }
    const syntheticDef: ProcessDefinition = {
      id: template.bpmnKey,
      key: template.bpmnKey,
      name: template.name,
      tenantId: null,
      version: 1,
      deploymentId: '',
      suspended: false,
    };
    setStartingDefInitialVars(JSON.stringify(defaultVars, null, 2));
    setStartingDef(syntheticDef);
  };

  // Returns a Promise so the wizard can show success/error state internally.
  // The wizard is responsible for closing itself via the success screen's Close button.
  const handleWizardDeploy = async (
    template: WorkflowTemplate,
    variables: Record<string, unknown>,
  ): Promise<void> => {
    await startProcessAsync({ processKey: template.bpmnKey, variables });
  };

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={3}>
        <SmartToyIcon color="primary" />
        <Typography variant="h5">AI Workflow Dashboard</Typography>
      </Box>

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tab
          label="Process Instances"
          icon={<AccountTreeIcon />}
          iconPosition="start"
          sx={{ minHeight: 48 }}
          aria-label="View process instances"
        />
        <Tab
          label="Process Definitions"
          icon={<SmartToyIcon />}
          iconPosition="start"
          sx={{ minHeight: 48 }}
          aria-label="View process definitions"
        />
        <Tab
          label="Templates"
          icon={<ViewModuleIcon />}
          iconPosition="start"
          sx={{ minHeight: 48 }}
          aria-label="Browse workflow templates"
        />
        <Tab
          label="Designer"
          icon={<DesignServicesIcon />}
          iconPosition="start"
          sx={{ minHeight: 48, color: tab === 3 ? 'secondary.main' : undefined }}
          aria-label="Open BPMN workflow designer"
        />
        <Tab
          label="Live Demo"
          icon={<BoltIcon />}
          iconPosition="start"
          sx={{ minHeight: 48, color: tab === 4 ? 'error.main' : undefined }}
          aria-label="Open live demo simulations"
        />
      </Tabs>

      {tab === 0 && <InstancesTab />}
      {tab === 1 && <DefinitionsTab onStartProcess={handleStartProcess} />}
      {tab === 2 && (
        <Box>
          <Box display="flex" justifyContent="flex-end" mt={1} mb={0}>
            <Button
              variant="contained"
              startIcon={<BoltIcon />}
              onClick={() => setWizardOpen(true)}
              aria-label="Open new workflow wizard"
            >
              New Workflow
            </Button>
          </Box>
          <TemplateGallery onSelectTemplate={handleSelectTemplate} />
        </Box>
      )}
      {tab === 3 && (
        <Suspense fallback={<Skeleton variant="rectangular" height={520} sx={{ mt: 2 }} />}>
          <LazyDesignerTab />
        </Suspense>
      )}
      {tab === 4 && (
        <Suspense fallback={<CircularProgress sx={{ display: 'block', mx: 'auto', mt: 4 }} />}>
          <LazyLiveDemoTab />
        </Suspense>
      )}

      {startingDef && (
        <StartProcessDialog
          definition={startingDef}
          initialVariablesJson={startingDefInitialVars}
          onClose={() => setStartingDef(null)}
        />
      )}

      <WorkflowWizard
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
        onDeploy={handleWizardDeploy}
        isPending={isStartPending}
      />
    </Box>
  );
}
