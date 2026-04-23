import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Switch,
  IconButton,
  Tooltip,
  CircularProgress,
  Alert,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Stack,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import ScienceIcon from '@mui/icons-material/Science';
import SendIcon from '@mui/icons-material/Send';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import SettingsSuggestIcon from '@mui/icons-material/SettingsSuggest';
import {
  useWorkflowConfigs,
  useCreateWorkflowConfig,
  useUpdateWorkflowConfig,
  useTestWorkflowConfig,
  useFireWorkflowTrigger,
  type TriggerConfig,
  type FireTriggerResult,
} from '@/hooks/useWorkflowConfig';

const TRIGGER_TYPE_CHIP: Record<string, { label: string; color: 'primary' | 'secondary' | 'success' }> = {
  KAFKA: { label: 'Kafka', color: 'primary' },
  SCHEDULED: { label: 'Scheduled', color: 'secondary' },
  REST: { label: 'REST', color: 'success' },
};

interface ConfigFormData {
  scenarioKey: string;
  processKey: string;
  displayName: string;
  description: string;
  triggerType: TriggerConfig['triggerType'];
  kafkaTopic: string;
  kafkaConsumerGroup: string;
  filterConditions: string;
  variableMapping: string;
  scheduleCron: string;
  scheduleQueryBean: string;
  promptTemplatePath: string;
  aiConfidenceThreshold: string;
  deduplicationKey: string;
}

const EMPTY_FORM: ConfigFormData = {
  scenarioKey: '',
  processKey: '',
  displayName: '',
  description: '',
  triggerType: 'KAFKA',
  kafkaTopic: '',
  kafkaConsumerGroup: '',
  filterConditions: '[]',
  variableMapping: '{}',
  scheduleCron: '',
  scheduleQueryBean: '',
  promptTemplatePath: '',
  aiConfidenceThreshold: '0.85',
  deduplicationKey: '',
};

const DEMO_PRESET: ConfigFormData = {
  scenarioKey: 'demo_noise_complaint_alert',
  processKey: 'aiC02_citizenServiceRequest',
  displayName: 'Cảnh báo tiếng ồn khu dân cư (Demo)',
  description: 'Demo: Phân loại và xử lý khiếu nại tiếng ồn từ cư dân',
  triggerType: 'REST',
  kafkaTopic: '',
  kafkaConsumerGroup: '',
  filterConditions: '[]',
  variableMapping: JSON.stringify({
    scenarioKey: { static: 'aiC02_citizenServiceRequest' },
    citizenId: { source: 'citizenId', default: 'citizen-demo' },
    requestType: { static: 'NOISE_COMPLAINT' },
    description: { source: 'description', default: 'Tiếng ồn lớn ban đêm' },
    district: { source: 'district', default: 'D1' },
  }, null, 2),
  scheduleCron: '',
  scheduleQueryBean: '',
  promptTemplatePath: 'prompts/aiC02_citizenServiceRequest.txt',
  aiConfidenceThreshold: '0.85',
  deduplicationKey: '',
};

const SAMPLE_PAYLOADS: Record<string, string> = {
  KAFKA: JSON.stringify({
    module: 'ENVIRONMENT',
    measureType: 'AQI',
    value: 185,
    sensorId: 'AQI-D1-DEMO',
    districtCode: 'D1',
    detectedAt: new Date().toISOString(),
  }, null, 2),
  SCHEDULED: JSON.stringify({
    anomalyType: 'HIGH_CONSUMPTION',
    buildingId: 'BLDG-DEMO-001',
    metricValue: 320,
  }, null, 2),
  REST: JSON.stringify({
    citizenId: 'citizen-demo',
    requestType: 'NOISE_COMPLAINT',
    description: 'Tiếng ồn lớn ban đêm từ công trình xây dựng',
    district: 'D1',
  }, null, 2),
};

function formToConfig(form: ConfigFormData): Partial<TriggerConfig> {
  return {
    scenarioKey: form.scenarioKey,
    processKey: form.processKey,
    displayName: form.displayName,
    description: form.description || null,
    triggerType: form.triggerType,
    kafkaTopic: form.triggerType === 'KAFKA' ? form.kafkaTopic : null,
    kafkaConsumerGroup: form.triggerType === 'KAFKA' ? form.kafkaConsumerGroup : null,
    filterConditions: form.filterConditions || null,
    variableMapping: form.variableMapping,
    scheduleCron: form.triggerType === 'SCHEDULED' ? form.scheduleCron : null,
    scheduleQueryBean: form.triggerType === 'SCHEDULED' ? form.scheduleQueryBean : null,
    promptTemplatePath: form.promptTemplatePath || null,
    aiConfidenceThreshold: form.aiConfidenceThreshold ? parseFloat(form.aiConfidenceThreshold) : null,
    deduplicationKey: form.deduplicationKey || null,
    enabled: true,
  };
}

function configToForm(c: TriggerConfig): ConfigFormData {
  return {
    scenarioKey: c.scenarioKey,
    processKey: c.processKey,
    displayName: c.displayName,
    description: c.description ?? '',
    triggerType: c.triggerType,
    kafkaTopic: c.kafkaTopic ?? '',
    kafkaConsumerGroup: c.kafkaConsumerGroup ?? '',
    filterConditions: c.filterConditions ?? '[]',
    variableMapping: c.variableMapping,
    scheduleCron: c.scheduleCron ?? '',
    scheduleQueryBean: c.scheduleQueryBean ?? '',
    promptTemplatePath: c.promptTemplatePath ?? '',
    aiConfidenceThreshold: c.aiConfidenceThreshold?.toString() ?? '0.85',
    deduplicationKey: c.deduplicationKey ?? '',
  };
}

interface ConfigFormDialogProps {
  open: boolean;
  editing: TriggerConfig | null;
  onClose: () => void;
}

function ConfigFormDialog({ open, editing, onClose }: ConfigFormDialogProps) {
  const [form, setForm] = useState<ConfigFormData>(EMPTY_FORM);
  const [jsonError, setJsonError] = useState<string | null>(null);
  const createMut = useCreateWorkflowConfig();
  const updateMut = useUpdateWorkflowConfig();

  const isEdit = editing !== null;
  const isPending = createMut.isPending || updateMut.isPending;

  useEffect(() => {
    if (open) {
      setForm(editing ? configToForm(editing) : EMPTY_FORM);
      setJsonError(null);
    }
  }, [open, editing]);

  const applyDemoPreset = () => {
    setForm({ ...DEMO_PRESET, scenarioKey: `demo_noise_${Date.now()}` });
    setJsonError(null);
  };

  const setField = (field: keyof ConfigFormData, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const validateJson = (value: string): boolean => {
    try {
      JSON.parse(value);
      return true;
    } catch {
      return false;
    }
  };

  const handleSubmit = () => {
    if (!validateJson(form.filterConditions)) {
      setJsonError('Filter Conditions: Invalid JSON');
      return;
    }
    if (!validateJson(form.variableMapping)) {
      setJsonError('Variable Mapping: Invalid JSON');
      return;
    }
    setJsonError(null);
    const payload = formToConfig(form);

    if (isEdit) {
      updateMut.mutate(
        { id: editing.id, config: payload },
        { onSuccess: onClose },
      );
    } else {
      createMut.mutate(payload, { onSuccess: onClose });
    }
  };

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
      fullWidth
    >
      <DialogTitle>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <span>{isEdit ? `Edit: ${editing?.displayName}` : 'New Trigger Configuration'}</span>
          {!isEdit && (
            <Button
              size="small"
              variant="outlined"
              color="secondary"
              startIcon={<AutoFixHighIcon />}
              onClick={applyDemoPreset}
            >
              Demo Preset
            </Button>
          )}
        </Box>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Display Name"
            value={form.displayName}
            onChange={(e) => setField('displayName', e.target.value)}
            fullWidth
            required
          />
          <TextField
            label="Scenario Key"
            value={form.scenarioKey}
            onChange={(e) => setField('scenarioKey', e.target.value)}
            fullWidth
            required
            disabled={isEdit}
            helperText={isEdit ? 'Cannot change scenario key' : 'Unique identifier, e.g. aiC01_aqiCitizenAlert'}
          />
          <TextField
            label="Process Key"
            value={form.processKey}
            onChange={(e) => setField('processKey', e.target.value)}
            fullWidth
            required
          />
          <TextField
            label="Description"
            value={form.description}
            onChange={(e) => setField('description', e.target.value)}
            fullWidth
            multiline
            rows={2}
          />
          <TextField
            select
            label="Trigger Type"
            value={form.triggerType}
            onChange={(e) => setField('triggerType', e.target.value)}
            fullWidth
            required
          >
            <MenuItem value="KAFKA">Kafka</MenuItem>
            <MenuItem value="SCHEDULED">Scheduled (Cron)</MenuItem>
            <MenuItem value="REST">REST Endpoint</MenuItem>
          </TextField>

          {form.triggerType === 'KAFKA' && (
            <>
              <TextField
                label="Kafka Topic"
                value={form.kafkaTopic}
                onChange={(e) => setField('kafkaTopic', e.target.value)}
                fullWidth
                required
              />
              <TextField
                label="Kafka Consumer Group"
                value={form.kafkaConsumerGroup}
                onChange={(e) => setField('kafkaConsumerGroup', e.target.value)}
                fullWidth
              />
            </>
          )}

          {form.triggerType === 'SCHEDULED' && (
            <>
              <TextField
                label="Cron Expression"
                value={form.scheduleCron}
                onChange={(e) => setField('scheduleCron', e.target.value)}
                fullWidth
                required
                placeholder="0 */2 * * *"
                helperText="e.g. '0 */2 * * *' for every 2 minutes"
              />
              <TextField
                label="Query Bean"
                value={form.scheduleQueryBean}
                onChange={(e) => setField('scheduleQueryBean', e.target.value)}
                fullWidth
                required
                placeholder="esgService.detectUtilityAnomalies"
                helperText="Spring bean.method for querying anomalies"
              />
            </>
          )}

          {form.triggerType !== 'REST' && (
            <TextField
              label="Filter Conditions (JSON)"
              value={form.filterConditions}
              onChange={(e) => setField('filterConditions', e.target.value)}
              fullWidth
              multiline
              rows={4}
              required
              inputProps={{ style: { fontFamily: 'monospace', fontSize: 13 } }}
              placeholder='[{"field":"module","op":"EQ","value":"ENVIRONMENT"}]'
            />
          )}

          <TextField
            label="Variable Mapping (JSON)"
            value={form.variableMapping}
            onChange={(e) => setField('variableMapping', e.target.value)}
            fullWidth
            multiline
            rows={4}
            required
            inputProps={{ style: { fontFamily: 'monospace', fontSize: 13 } }}
            placeholder='{"scenarioKey":{"static":"myScenario"},"sensorId":{"source":"payload.sensorId","default":"UNKNOWN"}}'
          />

          <Accordion elevation={0} variant="outlined">
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Typography variant="body2" color="text.secondary">Advanced Options</Typography>
            </AccordionSummary>
            <AccordionDetails>
              <Stack spacing={2}>
                <TextField
                  label="Prompt Template Path"
                  value={form.promptTemplatePath}
                  onChange={(e) => setField('promptTemplatePath', e.target.value)}
                  fullWidth
                  placeholder="prompts/aiC01_aqiCitizenAlert.txt"
                />
                <TextField
                  label="AI Confidence Threshold"
                  value={form.aiConfidenceThreshold}
                  onChange={(e) => setField('aiConfidenceThreshold', e.target.value)}
                  fullWidth
                  type="number"
                  inputProps={{ step: 0.05, min: 0, max: 1 }}
                />
                <TextField
                  label="Deduplication Key"
                  value={form.deduplicationKey}
                  onChange={(e) => setField('deduplicationKey', e.target.value)}
                  fullWidth
                  placeholder="Variable name to check for duplicate processes"
                />
              </Stack>
            </AccordionDetails>
          </Accordion>

          {jsonError && <Alert severity="error">{jsonError}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={isPending || !form.scenarioKey || !form.processKey || !form.displayName}
          startIcon={isPending ? <CircularProgress size={16} /> : undefined}
        >
          {isEdit ? 'Update' : 'Create'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

interface TestDialogProps {
  open: boolean;
  config: TriggerConfig | null;
  onClose: () => void;
}

function TestDialog({ open, config, onClose }: TestDialogProps) {
  const [payload, setPayload] = useState('{}');
  const [result, setResult] = useState<{ filterMatch: boolean; mappedVariables: Record<string, unknown>; processKey: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const testMut = useTestWorkflowConfig();

  const handleTest = () => {
    if (!config) return;
    try {
      const parsed = JSON.parse(payload) as Record<string, unknown>;
      setError(null);
      testMut.mutate(
        { id: config.id, payload: parsed },
        {
          onSuccess: (data) => setResult(data),
          onError: (err) => setError(err instanceof Error ? err.message : 'Test failed'),
        },
      );
    } catch {
      setError('Invalid JSON payload');
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Test Trigger: {config?.displayName}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="caption" color="text.secondary">
            Scenario: {config?.scenarioKey} | Process: {config?.processKey}
          </Typography>
          <TextField
            label="Sample Payload (JSON)"
            value={payload}
            onChange={(e) => setPayload(e.target.value)}
            fullWidth
            multiline
            rows={6}
            inputProps={{ style: { fontFamily: 'monospace', fontSize: 13 } }}
            placeholder='{"module":"ENVIRONMENT","measureType":"AQI","value":180,"sensorId":"SENSOR-001"}'
          />
          {error && <Alert severity="error">{error}</Alert>}
          {result && (
            <Alert severity={result.filterMatch ? 'success' : 'warning'}>
              <Typography variant="subtitle2">Filter Match: {result.filterMatch ? 'YES' : 'NO'}</Typography>
              <Typography variant="caption" component="pre" sx={{ mt: 1, whiteSpace: 'pre-wrap' }}>
                Mapped Variables:{'\n'}{JSON.stringify(result.mappedVariables, null, 2)}
              </Typography>
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => { setResult(null); setError(null); setPayload('{}'); }}>Reset</Button>
        <Button onClick={onClose}>Close</Button>
        <Button variant="contained" onClick={handleTest} disabled={testMut.isPending} startIcon={<ScienceIcon />}>
          Run Test
        </Button>
      </DialogActions>
    </Dialog>
  );
}

interface FireEventDialogProps {
  open: boolean;
  config: TriggerConfig | null;
  onClose: () => void;
}

function FireEventDialog({ open, config, onClose }: FireEventDialogProps) {
  const navigate = useNavigate();
  const [payload, setPayload] = useState('{}');
  const [result, setResult] = useState<FireTriggerResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fireMut = useFireWorkflowTrigger();

  useEffect(() => {
    if (open && config) {
      setPayload(SAMPLE_PAYLOADS[config.triggerType] ?? '{}');
      setResult(null);
      setError(null);
    }
  }, [open, config]);

  const handleFire = () => {
    if (!config) return;
    try {
      const parsed = JSON.parse(payload) as Record<string, unknown>;
      setError(null);
      fireMut.mutate(
        { scenarioKey: config.scenarioKey, payload: parsed },
        {
          onSuccess: (data) => setResult(data),
          onError: (err) => {
            const msg = err instanceof Error ? err.message : 'Fire failed';
            setError(msg);
          },
        },
      );
    } catch {
      setError('Invalid JSON payload');
    }
  };

  const canFire = config?.triggerType === 'REST';

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        <Box display="flex" alignItems="center" gap={1}>
          <SendIcon color="error" />
          <span>Simulate Event: {config?.displayName}</span>
        </Box>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {!canFire ? (
            <Alert severity="warning">
              Config này dùng trigger <strong>{config?.triggerType}</strong>. Chỉ REST configs có thể bắn trực tiếp qua UI.
              <br />Để simulate Kafka/Scheduled, dùng nút <strong>&quot;Start Workflow&quot;</strong> trên trang AI Workflows.
            </Alert>
          ) : (
            <Alert severity="warning" icon={<SendIcon />}>
              Sẽ <strong>thực sự start</strong> một workflow process mới (không phải dry-run).
            </Alert>
          )}
          <Typography variant="caption" color="text.secondary">
            Scenario: <code>{config?.scenarioKey}</code> | Process: <code>{config?.processKey}</code> | Type: {config?.triggerType}
          </Typography>
          {canFire && (
            <TextField
              label="Event Payload (JSON)"
              value={payload}
              onChange={(e) => setPayload(e.target.value)}
              fullWidth
              multiline
              rows={7}
              inputProps={{ style: { fontFamily: 'monospace', fontSize: 13 } }}
            />
          )}
          {error && <Alert severity="error">{error}</Alert>}
          {result && (
            <Alert severity="success">
              <Typography variant="subtitle2">✅ Workflow đã được khởi tạo!</Typography>
              <Typography variant="body2" sx={{ mt: 0.5 }}>
                Process Instance ID: <code>{result.processInstanceId}</code>
              </Typography>
              <Typography variant="body2">Status: {result.status}</Typography>
              <Button
                size="small"
                variant="outlined"
                sx={{ mt: 1 }}
                onClick={() => { onClose(); navigate('/ai-workflow'); }}
              >
                Xem trên AI Workflows Dashboard →
              </Button>
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Đóng</Button>
        {canFire && (
          <Button
            variant="contained"
            color="error"
            onClick={handleFire}
            disabled={fireMut.isPending || !config?.enabled}
            startIcon={fireMut.isPending ? <CircularProgress size={16} /> : <SendIcon />}
          >
            {fireMut.isPending ? 'Đang gửi...' : 'Fire Event'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}

export default function WorkflowConfigPage() {
  const { data: configs, isLoading, error } = useWorkflowConfigs();
  const updateMut = useUpdateWorkflowConfig();

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<TriggerConfig | null>(null);
  const [testOpen, setTestOpen] = useState(false);
  const [testing, setTesting] = useState<TriggerConfig | null>(null);
  const [fireOpen, setFireOpen] = useState(false);
  const [firing, setFiring] = useState<TriggerConfig | null>(null);

  const handleToggle = (config: TriggerConfig) => {
    updateMut.mutate({ id: config.id, config: { enabled: !config.enabled } });
  };

  const handleEdit = (config: TriggerConfig) => {
    setEditing(config);
    setFormOpen(true);
  };

  const handleFire = (config: TriggerConfig) => {
    setFiring(config);
    setFireOpen(true);
  };

  return (
    <Box>
      <Box display="flex" alignItems="center" justifyContent="space-between" mb={3}>
        <Box display="flex" alignItems="center" gap={1}>
          <SettingsSuggestIcon color="primary" />
          <Typography variant="h5">Workflow Trigger Config</Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => { setEditing(null); setFormOpen(true); }}
        >
          New Config
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>Failed to load configurations</Alert>}

      {isLoading ? (
        <Box display="flex" justifyContent="center" py={4}><CircularProgress /></Box>
      ) : (
        <TableContainer component={Paper} variant="outlined" sx={{ overflowX: 'auto' }}>
          <Table size="small" sx={{ minWidth: 900 }}>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Scenario Key</TableCell>
                <TableCell align="center">Type</TableCell>
                <TableCell align="center">Enabled</TableCell>
                <TableCell>Dedup Key</TableCell>
                <TableCell align="center">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(!configs || configs.length === 0) && (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary" variant="body2">No configurations found</Typography>
                  </TableCell>
                </TableRow>
              )}
              {configs?.map((config) => {
                const typeChip = TRIGGER_TYPE_CHIP[config.triggerType] ?? { label: config.triggerType, color: 'default' as const };
                return (
                  <TableRow key={config.id} hover>
                    <TableCell>
                      <Typography variant="body2" fontWeight={600}>{config.displayName}</Typography>
                      <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', maxWidth: 200 }}>
                        {config.description}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" fontFamily="monospace">{config.scenarioKey}</Typography>
                    </TableCell>
                    <TableCell align="center">
                      <Chip label={typeChip.label} color={typeChip.color} size="small" variant="outlined" />
                    </TableCell>
                    <TableCell align="center">
                      <Switch
                        checked={config.enabled}
                        onChange={() => handleToggle(config)}
                        size="small"
                        color={config.enabled ? 'success' : 'default'}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        {config.deduplicationKey ?? '—'}
                      </Typography>
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => handleEdit(config)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Dry-run test (no process started)">
                        <IconButton size="small" onClick={() => { setTesting(config); setTestOpen(true); }}>
                          <ScienceIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title={config.triggerType === 'REST' ? 'Simulate Event (fire real process)' : 'Simulate Event (REST only)'}
>
                        <span>
                          <IconButton
                            size="small"
                            color="error"
                            onClick={() => handleFire(config)}
                            disabled={!config.enabled}
                          >
                            <SendIcon fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <ConfigFormDialog open={formOpen} editing={editing} onClose={() => { setFormOpen(false); setEditing(null); }} />
      <TestDialog open={testOpen} config={testing} onClose={() => { setTestOpen(false); setTesting(null); }} />
      <FireEventDialog open={fireOpen} config={firing} onClose={() => { setFireOpen(false); setFiring(null); }} />
    </Box>
  );
}
