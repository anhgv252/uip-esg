import { lazy, Suspense, useState, useRef, useCallback } from 'react';
import {
  Box,
  Typography,
  Tabs,
  Tab,
  TextField,
  MenuItem,
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
  Divider,
  LinearProgress,
  Stack,
  Slider,
} from '@mui/material';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import BoltIcon from '@mui/icons-material/Bolt';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import {
  useProcessDefinitions,
  useProcessInstances,
  useProcessDefinitionXml,
  useStartProcess,
} from '@/hooks/useWorkflowData';
import ProcessInstanceTable from '@/components/workflow/ProcessInstanceTable';
import InstanceDetailDrawer from '@/components/workflow/InstanceDetailDrawer';
import type { ProcessInstance, ProcessDefinition } from '@/api/workflow';
import { useAuth } from '@/hooks/useAuth';
import { fireWorkflowTrigger } from '@/api/workflowConfig';
import { apiClient } from '@/api/client';

const BpmnViewer = lazy(() => import('@/components/workflow/BpmnViewer'));

// ── Definitions tab ─────────────────────────────────────────────────────────

interface StartProcessDialogProps {
  definition: ProcessDefinition;
  onClose: () => void;
}

function StartProcessDialog({ definition, onClose }: StartProcessDialogProps) {
  const { mutate: start, isPending } = useStartProcess();
  const [variablesJson, setVariablesJson] = useState('{}');
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
          Process key: <strong>{definition.key}</strong> · Version {definition.version}
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
        {definition.name ?? definition.key} · v{definition.version}
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

function DefinitionsTab() {
  const { data: definitions, isLoading, error } = useProcessDefinitions();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ROLE_ADMIN';

  const [selectedDef, setSelectedDef] = useState<ProcessDefinition | null>(null);
  const [startingDef, setStartingDef] = useState<ProcessDefinition | null>(null);

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
                        onClick={() => setStartingDef(def)}
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

      {startingDef && (
        <StartProcessDialog
          definition={startingDef}
          onClose={() => setStartingDef(null)}
        />
      )}
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

// ── Live Demo tab ────────────────────────────────────────────────────────────

const DEMO_SCENARIOS = [
  {
    key: 'noise',
    label: 'Khiếu nại tiếng ồn công trình',
    description: 'Tiếng ồn lớn ban đêm từ công trình xây dựng trái phép, ảnh hưởng nghiêm trọng đến khu dân cư',
    district: 'D1',
    requestType: 'NOISE_COMPLAINT',
  },
  {
    key: 'trash',
    label: 'Rác thải bị đổ trái phép',
    description: 'Rác thải công nghiệp đổ trái phép bên đường Nguyễn Văn Cừ, mùi hôi lan rộng',
    district: 'D5',
    requestType: 'ENVIRONMENTAL_VIOLATION',
  },
  {
    key: 'water',
    label: 'Vỡ ống nước cấp',
    description: 'Ống nước cấp bị vỡ tại ngã tư Lý Thường Kiệt, nước tràn ra mặt đường gây ngập cục bộ',
    district: 'D10',
    requestType: 'UTILITY_INCIDENT',
  },
];

type LogEntry = {
  id: number;
  ts: string;
  icon: 'pending' | 'running' | 'done' | 'ai';
  label: string;
  detail?: string;
};

function ts() {
  const d = new Date();
  return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) + '.' + String(d.getMilliseconds()).padStart(3, '0');
}

function aqiColor(v: number) {
  if (v > 300) return '#c62828';
  if (v > 200) return '#e53935';
  if (v > 150) return '#fb8c00';
  if (v > 100) return '#fdd835';
  if (v > 50)  return '#66bb6a';
  return '#43a047';
}

function aqiLabel(v: number) {
  if (v > 300) return 'Hazardous';
  if (v > 200) return 'Very Unhealthy';
  if (v > 150) return 'Unhealthy';
  if (v > 100) return 'Sensitive';
  if (v > 50)  return 'Moderate';
  return 'Good';
}

// ── IoT Sensor Demo section ──────────────────────────────────────────────────

function IoTSensorSection() {
  const [sensorId, setSensorId]     = useState('ENV-HCM-D1-001');
  const [aqiValue, setAqiValue]     = useState(175);
  const [district, setDistrict]     = useState('D1');
  const [logs, setLogs]             = useState<LogEntry[]>([]);
  const [running, setRunning]       = useState(false);
  const [result, setResult]         = useState<Record<string, unknown> | null>(null);
  const [showPayload, setShowPayload] = useState(false);
  const logIdRef  = useRef(0);
  const pollRef   = useRef<ReturnType<typeof setInterval> | null>(null);
  const logEndRef = useRef<HTMLDivElement>(null);

  const mqttPayload = {
    '@context': ['https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld'],
    id: `urn:ngsi-ld:AirQualitySensor:${sensorId}`,
    type: 'AirQualitySensor',
    aqi: { type: 'Property', value: aqiValue, unitCode: 'P1' },
    districtCode: { type: 'Property', value: district },
    location: { type: 'GeoProperty', value: { type: 'Point', coordinates: [106.6297, 10.8231] } },
    observedAt: new Date().toISOString(),
  };

  function addLog(icon: LogEntry['icon'], label: string, detail?: string) {
    logIdRef.current += 1;
    const entry = { id: logIdRef.current, ts: ts(), icon, label, detail };
    setLogs(prev => [...prev, entry]);
    setTimeout(() => logEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 50);
  }

  function updateLastLog(label: string, icon: LogEntry['icon'], detail?: string) {
    setLogs(prev => {
      const next = [...prev];
      const last = next[next.length - 1];
      if (last) next[next.length - 1] = { ...last, label, icon, detail: detail ?? last.detail };
      return next;
    });
  }

  const handleReset = useCallback(() => {
    if (pollRef.current) clearInterval(pollRef.current);
    setLogs([]); setResult(null); setRunning(false);
  }, []);

  const handleFire = useCallback(async () => {
    if (running) return;
    setLogs([]); setResult(null); setRunning(true);
    if (pollRef.current) clearInterval(pollRef.current);

    // Step 1: MQTT publish
    addLog('done', `📡  MQTT Publish → EMQX broker`, `topic: uip/sensors/${sensorId}/telemetry | QoS: 1 | payload: NGSI-LD`);
    await new Promise(r => setTimeout(r, 450));

    // Step 2: EMQX receive
    addLog('done', `🔌  EMQX broker nhận message`, `clientId: ${sensorId} | retained: false | ts: ${ts()}`);
    await new Promise(r => setTimeout(r, 400));

    // Step 3: Redpanda Connect bridge
    addLog('running', '🔁  Redpanda Connect: MQTT → Kafka bridge...');
    await new Promise(r => setTimeout(r, 650));
    updateLastLog('🔁  Redpanda Connect: transform NGSI-LD + publish Kafka', 'done',
      'input: mqtt_broker | output: kafka_franz | schema: ngsi-ld-v1');

    // Step 4: Kafka
    await new Promise(r => setTimeout(r, 350));
    const partition = Math.floor(Math.random() * 6);
    const offset    = 10000 + Math.floor(Math.random() * 90000);
    addLog('done', '📨  Kafka topic: ngsi_ld_environment',
      `partition: ${partition} | offset: ${offset} | key: ${sensorId}`);

    // Step 5: Flink consume
    await new Promise(r => setTimeout(r, 450));
    addLog('running', '⚡  Flink EnvironmentFlinkJob: consuming event...', 'group.id: flink-environment-job');
    await new Promise(r => setTimeout(r, 500));
    updateLastLog('⚡  Flink: event decoded + watermark assigned', 'done',
      `event_time: ${new Date().toISOString()} | watermark: T−30s`);

    // Step 6: Flink 30s tumbling window
    await new Promise(r => setTimeout(r, 600));
    addLog('running', `📊  Flink 30s tumbling window aggregating...`, `keyBy: districtCode="${district}"`);
    await new Promise(r => setTimeout(r, 750));
    updateLastLog(`📊  Flink window result: AQI=${aqiValue} (district ${district})`, 'done',
      `window: [now−30s, now] | readings: 1 | avg: ${aqiValue} | max: ${aqiValue}`);

    // Step 7: Threshold check
    await new Promise(r => setTimeout(r, 350));
    const exceeded = aqiValue > 150;

    if (exceeded) {
      addLog('done', `🚨  Threshold breach! AQI ${aqiValue} > 150 → AlertEvent generated`,
        `severity: ${aqiValue > 200 ? 'HIGH' : 'MEDIUM'} | module: ENVIRONMENT`);
      await new Promise(r => setTimeout(r, 400));

      // Step 8: Publish to alert topic
      addLog('done', '📤  Flink publish → Kafka: UIP.flink.alert.detected.v1',
        `key: ${district} | alertType: AQI_THRESHOLD | sensorId: ${sensorId}`);
      await new Promise(r => setTimeout(r, 500));

      // Step 9: Backend API call (REAL — triggers actual Camunda process)
      addLog('running', '🔍  GenericKafkaTriggerService: evaluating filter conditions...');
      let simResult: { processInstanceId: string; alertTriggered: boolean };
      try {
        const resp = await apiClient.post<{ processInstanceId: string; alertTriggered: boolean }>(
          '/simulate/iot-sensor',
          { sensorType: 'AQI', sensorId, value: aqiValue, district },
        );
        simResult = resp.data;
        updateLastLog('🔍  Filter match: module=ENVIRONMENT, AQI>150 → startProcess()', 'done',
          'processKey: aiC01_aqiCitizenAlert | triggerType: KAFKA');
      } catch {
        updateLastLog('❌  Lỗi khi gọi backend simulate API', 'done');
        setRunning(false);
        return;
      }

      // Step 10: Camunda process started
      await new Promise(r => setTimeout(r, 300));
      addLog('done', '🏃  Camunda BPMN aiC01_aqiCitizenAlert started',
        `processInstanceId: ${simResult.processInstanceId}`);

      // Step 11: AI analysis
      await new Promise(r => setTimeout(r, 400));
      addLog('running', '🤖  AIAnalysisDelegate: scenarioKey=aiC01_aqiCitizenAlert...',
        'Calling Claude AI / RuleBasedFallback service');

      // Poll for variables
      let attempts = 0;
      pollRef.current = setInterval(async () => {
        attempts++;
        try {
          const vars = await apiClient
            .get<Record<string, unknown>>(`/workflow/instances/${simResult.processInstanceId}/variables`)
            .then(r => r.data);

          if (vars?.aiDecision) {
            clearInterval(pollRef.current!);
            const conf = Math.round(Number(vars.aiConfidence ?? 0) * 100);
            updateLastLog(
              `🤖  AI quyết định: ${vars.aiDecision} (confidence: ${conf}%)`,
              'ai',
              String(vars.aiReasoning ?? ''),
            );

            await new Promise(r => setTimeout(r, 400));
            const notified = Number(vars.citizensNotified ?? 1500);
            addLog('done', `📢  AqiCitizenAlertDelegate: SSE notification gửi thành công`,
              `${notified.toLocaleString()} cư dân quận ${district} được cảnh báo | channel: uip:alerts`);

            await new Promise(r => setTimeout(r, 300));
            addLog('done', '✅  Pipeline hoàn tất — BPMN EndEvent reached', `notificationSent: true`);

            setResult(vars);
            setRunning(false);
          } else if (attempts > 25) {
            clearInterval(pollRef.current!);
            updateLastLog('⚠️  Timeout chờ AI result', 'done');
            setRunning(false);
          }
        } catch { /* retry next tick */ }
      }, 600);

    } else {
      // Below threshold — no alert
      addLog('done', `✅  AQI ${aqiValue} ≤ 150 — below threshold, no alert generated`,
        'Flink: data written to TimescaleDB only. No AlertEvent published.');
      setRunning(false);
    }
  }, [running, sensorId, aqiValue, district]);

  const iconEl = (icon: LogEntry['icon']) => {
    if (icon === 'done')    return <CheckCircleIcon sx={{ fontSize: 16, color: 'success.main', mt: '2px' }} />;
    if (icon === 'ai')      return <SmartToyIcon sx={{ fontSize: 16, color: 'primary.main', mt: '2px' }} />;
    if (icon === 'running') return <CircularProgress size={14} sx={{ mt: '3px' }} />;
    return <RadioButtonUncheckedIcon sx={{ fontSize: 16, color: 'text.disabled', mt: '2px' }} />;
  };

  const color = aqiColor(aqiValue);
  const label = aqiLabel(aqiValue);

  return (
    <Box sx={{ maxWidth: 860 }}>
      {/* Sensor config form */}
      <Paper variant="outlined" sx={{ p: 2.5, mb: 2 }}>
        <Typography variant="subtitle2" fontWeight={700} gutterBottom>
          📡 Cấu hình cảm biến IoT
        </Typography>
        <Stack spacing={2} mt={1.5}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="center" flexWrap="wrap">
            <TextField
              size="small"
              label="Sensor ID"
              value={sensorId}
              onChange={e => setSensorId(e.target.value)}
              disabled={running}
              sx={{ minWidth: 200 }}
            />
            <TextField
              select size="small" label="Quận/Huyện"
              value={district} onChange={e => setDistrict(e.target.value)}
              disabled={running} sx={{ minWidth: 140 }}
            >
              {['D1', 'D2', 'D3', 'D4', 'D5', 'D7', 'D10', 'D12', 'BinhThanh', 'GoVap'].map(d => (
                <MenuItem key={d} value={d}>{d}</MenuItem>
              ))}
            </TextField>
            <Chip label="MQTT" size="small" variant="outlined" />
            <Chip label="NGSI-LD v1" size="small" variant="outlined" color="primary" />
            <Chip label="AQI Sensor" size="small" color="default" />
          </Stack>

          {/* AQI Slider */}
          <Box>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={0.5}>
              <Typography variant="caption" color="text.secondary">Giá trị AQI (0 – 400)</Typography>
              <Box display="flex" alignItems="center" gap={1}>
                <Typography variant="h6" fontWeight={700} sx={{ color }}>
                  {aqiValue}
                </Typography>
                <Chip label={label} size="small" sx={{ bgcolor: color, color: '#fff', fontWeight: 600 }} />
              </Box>
            </Box>
            <Slider
              value={aqiValue}
              onChange={(_, v) => setAqiValue(v as number)}
              min={0} max={400} step={5}
              disabled={running}
              marks={[
                { value: 50,  label: '50' },
                { value: 100, label: '100' },
                { value: 150, label: '⚠️ 150' },
                { value: 200, label: '200' },
                { value: 300, label: '300' },
              ]}
              sx={{ color }}
            />
            {aqiValue > 150 ? (
              <MuiAlert severity="warning" sx={{ mt: 1, py: 0.5 }}>
                AQI {aqiValue} vượt ngưỡng (150) → Flink sẽ phát alert → Camunda BPMN <strong>aiC01_aqiCitizenAlert</strong> được kích hoạt
              </MuiAlert>
            ) : (
              <MuiAlert severity="success" sx={{ mt: 1, py: 0.5 }}>
                AQI {aqiValue} dưới ngưỡng (150) → Không có alert, Flink chỉ ghi vào TimescaleDB
              </MuiAlert>
            )}
          </Box>

          {/* Quick presets */}
          <Stack direction="row" spacing={1} flexWrap="wrap">
            <Typography variant="caption" color="text.secondary" alignSelf="center">Preset:</Typography>
            {[
              { label: 'Dưới ngưỡng (120)', val: 120 },
              { label: 'Ô nhiễm (175)', val: 175 },
              { label: 'Nguy hiểm (235)', val: 235 },
            ].map(p => (
              <Chip key={p.val} label={p.label} size="small" clickable
                onClick={() => setAqiValue(p.val)} disabled={running}
                sx={{ bgcolor: aqiColor(p.val), color: '#fff' }} />
            ))}
          </Stack>

          {/* MQTT Payload Preview */}
          <Box>
            <Button size="small" variant="text" onClick={() => setShowPayload(v => !v)}>
              {showPayload ? '▾' : '▸'} Xem MQTT Payload (NGSI-LD)
            </Button>
            {showPayload && (
              <Paper variant="outlined" sx={{ p: 1.5, mt: 1, bgcolor: '#0d1117' }}>
                <Typography variant="caption" color="grey.500" sx={{ fontFamily: 'monospace', display: 'block', mb: 0.5 }}>
                  {'// MQTT topic: uip/sensors/' + sensorId + '/telemetry  |  broker: emqx:1883'}
                </Typography>
                <Box
                  component="pre"
                  sx={{ fontFamily: 'monospace', color: '#a5d6a7', fontSize: '0.73rem',
                        overflowX: 'auto', m: 0, whiteSpace: 'pre-wrap' }}
                >
                  {JSON.stringify(mqttPayload, null, 2)}
                </Box>
              </Paper>
            )}
          </Box>

          <Stack direction="row" spacing={1.5}>
            <Button
              variant="contained"
              color={aqiValue > 150 ? 'error' : 'success'}
              startIcon={running ? <CircularProgress size={16} color="inherit" /> : <BoltIcon />}
              onClick={handleFire}
              disabled={running}
            >
              {running ? 'Đang xử lý pipeline...' : 'Bắn sự kiện IoT'}
            </Button>
            {logs.length > 0 && !running && (
              <Button variant="outlined" size="small" onClick={handleReset}>Reset</Button>
            )}
          </Stack>
        </Stack>
      </Paper>

      {/* Pipeline Console */}
      {logs.length > 0 && (
        <Paper variant="outlined" sx={{ p: 0, mb: 2, overflow: 'hidden', border: '1px solid', borderColor: 'divider' }}>
          <Box sx={{ px: 2, py: 1, bgcolor: 'grey.900', display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ff5f57' }} />
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ffbd2e' }} />
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#28c840' }} />
            <Typography variant="caption" color="grey.400" sx={{ ml: 1, fontFamily: 'monospace' }}>
              UIP IoT Pipeline — MQTT → Redpanda → Kafka → Flink → Alert → Camunda
            </Typography>
          </Box>
          {running && <LinearProgress sx={{ height: 2 }} />}
          <Box sx={{ px: 2, py: 1.5, bgcolor: '#0d1117', minHeight: 80 }}>
            {logs.map(log => (
              <Box key={log.id} display="flex" alignItems="flex-start" gap={1.5} mb={1}>
                {iconEl(log.icon)}
                <Box flex={1}>
                  <Box display="flex" gap={1.5} alignItems="baseline" flexWrap="wrap">
                    <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'grey.500', flexShrink: 0 }}>
                      {log.ts}
                    </Typography>
                    <Typography variant="body2" sx={{
                      fontFamily: 'monospace',
                      color: log.icon === 'ai' ? '#64b5f6' : log.icon === 'done' ? '#a5d6a7' : '#fff9c4',
                      fontWeight: log.icon === 'ai' ? 600 : 400,
                    }}>
                      {log.label}
                    </Typography>
                  </Box>
                  {log.detail && (
                    <Typography variant="caption" sx={{
                      fontFamily: 'monospace', color: 'grey.500', display: 'block',
                      mt: 0.25, maxWidth: 700, wordBreak: 'break-all', whiteSpace: 'pre-wrap',
                    }}>
                      {log.detail}
                    </Typography>
                  )}
                </Box>
              </Box>
            ))}
            <div ref={logEndRef} />
          </Box>
        </Paper>
      )}

      {/* Alert Result Card */}
      {result && (
        <Paper variant="outlined" sx={{ p: 2.5 }}>
          <Box display="flex" alignItems="center" gap={1} mb={2}>
            <SmartToyIcon color="error" />
            <Typography variant="subtitle2" fontWeight={700}>Kết quả xử lý AQI Alert</Typography>
            <Chip label={`${Math.round(Number(result.aiConfidence ?? 0) * 100)}% confidence`} size="small" color="primary" />
            <Chip label={String(result.aiSeverity ?? 'MEDIUM')} size="small"
              color={result.aiSeverity === 'HIGH' ? 'error' : 'warning'} />
          </Box>
          <Divider sx={{ mb: 2 }} />
          <Stack spacing={1.5}>
            <Box display="flex" gap={2} flexWrap="wrap">
              <Box flex={1} minWidth={160}>
                <Typography variant="caption" color="text.secondary" display="block">AI Decision</Typography>
                <Typography variant="body2" fontWeight={600} color="error.main">{String(result.aiDecision ?? '—')}</Typography>
              </Box>
              <Box flex={1} minWidth={160}>
                <Typography variant="caption" color="text.secondary" display="block">Công dân được cảnh báo</Typography>
                <Typography variant="body2" fontWeight={600} color="warning.main">
                  {Number(result.citizensNotified ?? 1500).toLocaleString()} cư dân
                </Typography>
              </Box>
              <Box flex={1} minWidth={120}>
                <Typography variant="caption" color="text.secondary" display="block">Notification Sent</Typography>
                <Typography variant="body2" fontWeight={600}>
                  {result.notificationSent ? '✅ SSE đã gửi' : '—'}
                </Typography>
              </Box>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">AI Reasoning</Typography>
              <Typography variant="body2" sx={{ mt: 0.5, lineHeight: 1.6 }}>{String(result.aiReasoning ?? '—')}</Typography>
            </Box>
            {Array.isArray(result.aiRecommendedActions) && (
              <Box>
                <Typography variant="caption" color="text.secondary" display="block" mb={0.5}>Khuyến nghị</Typography>
                <Stack spacing={0.5}>
                  {(result.aiRecommendedActions as string[]).map((a, i) => (
                    <Box key={i} display="flex" gap={1} alignItems="flex-start">
                      <CheckCircleIcon sx={{ fontSize: 14, color: 'success.main', mt: '3px', flexShrink: 0 }} />
                      <Typography variant="body2">{a}</Typography>
                    </Box>
                  ))}
                </Stack>
              </Box>
            )}
          </Stack>
        </Paper>
      )}
    </Box>
  );
}

function LiveDemoTab() {
  const [demoMode, setDemoMode] = useState<'citizen' | 'iot'>('citizen');
  const [scenarioIdx, setScenarioIdx] = useState(0);
  const [customDesc, setCustomDesc] = useState('');
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<Record<string, unknown> | null>(null);
  const [_processId, setProcessId] = useState<string | null>(null);
  const logIdRef = useRef(0);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const scenario = DEMO_SCENARIOS[scenarioIdx];
  const description = customDesc || scenario.description;

  function addLog(icon: LogEntry['icon'], label: string, detail?: string) {
    logIdRef.current += 1;
    setLogs(prev => [...prev, { id: logIdRef.current, ts: ts(), icon, label, detail }]);
  }

  function updateLastLog(label: string, icon: LogEntry['icon'], detail?: string) {
    setLogs(prev => {
      const next = [...prev];
      const last = next[next.length - 1];
      if (last) { next[next.length - 1] = { ...last, label, icon, detail: detail ?? last.detail }; }
      return next;
    });
  }

  const handleReset = useCallback(() => {
    if (pollRef.current) clearInterval(pollRef.current);
    setLogs([]);
    setResult(null);
    setProcessId(null);
    setRunning(false);
    setCustomDesc('');
  }, []);

  const handleFire = useCallback(async () => {
    if (running) return;
    setLogs([]);
    setResult(null);
    setProcessId(null);
    setRunning(true);

    // Step 1: received
    addLog('done', '📡  Sự kiện nhận được', `requestType: ${scenario.requestType} | district: ${scenario.district}`);

    // Step 2: fire to backend
    await new Promise(r => setTimeout(r, 400));
    addLog('running', '⚙️  Đang khởi tạo BPMN process...');
    let pid: string;
    try {
      const resp = await fireWorkflowTrigger('aiC02_citizenServiceRequest', {
        description,
        district: scenario.district,
        requestType: scenario.requestType,
      });
      pid = resp.processInstanceId;
      setProcessId(pid);
      updateLastLog('⚙️  Camunda process khởi tạo thành công', 'done', `processInstanceId: ${pid}`);
    } catch {
      updateLastLog('❌  Lỗi khi gọi workflow trigger', 'done');
      setRunning(false);
      return;
    }

    // Step 3: AI analysis
    await new Promise(r => setTimeout(r, 500));
    addLog('running', '🤖  AI đang phân tích yêu cầu...', 'Gọi Claude AI / Rule Engine');

    // Poll for variables
    let attempts = 0;
    pollRef.current = setInterval(async () => {
      attempts++;
      try {
        const vars = await apiClient
          .get<Record<string, unknown>>(`/workflow/instances/${pid}/variables`)
          .then(r => r.data);

        if (vars?.aiDecision) {
          clearInterval(pollRef.current!);

          const conf = typeof vars.aiConfidence === 'number' ? Math.round(vars.aiConfidence * 100) : 50;
          updateLastLog(
            `🤖  AI phân loại: ${vars.aiDecision} (confidence: ${conf}%)`,
            'ai',
            String(vars.aiReasoning ?? ''),
          );

          await new Promise(r => setTimeout(r, 300));
          addLog('done', `🏢  Chuyển xử lý → Phòng ${vars.department ?? 'GENERAL'}`, `Priority: ${vars.priority ?? 'MEDIUM'}`);

          await new Promise(r => setTimeout(r, 300));
          addLog('done', '✅  Yêu cầu đã xử lý xong', `requestId: ${vars.requestId ?? 'N/A'}`);

          setResult(vars);
          setRunning(false);
        } else if (attempts > 20) {
          clearInterval(pollRef.current!);
          updateLastLog('⚠️  Timeout chờ kết quả AI', 'done');
          setRunning(false);
        }
      } catch { /* retry */ }
    }, 600);
  }, [running, scenario, description]);

  const iconEl = (icon: LogEntry['icon']) => {
    if (icon === 'done') return <CheckCircleIcon sx={{ fontSize: 16, color: 'success.main', mt: '2px' }} />;
    if (icon === 'ai') return <SmartToyIcon sx={{ fontSize: 16, color: 'primary.main', mt: '2px' }} />;
    if (icon === 'running') return <CircularProgress size={14} sx={{ mt: '3px' }} />;
    return <RadioButtonUncheckedIcon sx={{ fontSize: 16, color: 'text.disabled', mt: '2px' }} />;
  };

  return (
    <Box sx={{ mt: 2 }}>
      {/* Mode selector */}
      <Stack direction="row" spacing={1} mb={2.5}>
        <Button
          variant={demoMode === 'citizen' ? 'contained' : 'outlined'}
          startIcon={<AccountTreeIcon />}
          onClick={() => setDemoMode('citizen')}
          size="small"
        >
          🏙️ Yêu cầu công dân
        </Button>
        <Button
          variant={demoMode === 'iot' ? 'contained' : 'outlined'}
          color={demoMode === 'iot' ? 'error' : 'inherit'}
          startIcon={<BoltIcon />}
          onClick={() => setDemoMode('iot')}
          size="small"
        >
          📡 Cảm biến IoT
        </Button>
      </Stack>

      {demoMode === 'iot' ? (
        <IoTSensorSection />
      ) : (
      <Box sx={{ maxWidth: 860 }}>
      {/* Event Form */}
      <Paper variant="outlined" sx={{ p: 2.5, mb: 2 }}>
        <Typography variant="subtitle2" gutterBottom fontWeight={700}>
          🎬 Mô phỏng sự kiện công dân thực tế
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} mt={1.5} alignItems="flex-start">
          <TextField
            select
            size="small"
            label="Loại sự kiện"
            value={scenarioIdx}
            onChange={e => { setScenarioIdx(Number(e.target.value)); setCustomDesc(''); }}
            sx={{ minWidth: 240 }}
            disabled={running}
          >
            {DEMO_SCENARIOS.map((s, i) => (
              <MenuItem key={s.key} value={i}>{s.label}</MenuItem>
            ))}
          </TextField>
          <TextField
            size="small"
            fullWidth
            label="Mô tả (có thể chỉnh sửa)"
            value={customDesc || scenario.description}
            onChange={e => setCustomDesc(e.target.value)}
            disabled={running}
            multiline
            rows={2}
          />
        </Stack>
        <Stack direction="row" spacing={1.5} mt={2}>
          <Button
            variant="contained"
            color="error"
            startIcon={running ? <CircularProgress size={16} color="inherit" /> : <BoltIcon />}
            onClick={handleFire}
            disabled={running}
          >
            {running ? 'Đang xử lý...' : 'Fire Event'}
          </Button>
          {logs.length > 0 && !running && (
            <Button variant="outlined" size="small" onClick={handleReset}>
              Reset
            </Button>
          )}
        </Stack>
      </Paper>

      {/* Live Console */}
      {logs.length > 0 && (
        <Paper
          variant="outlined"
          sx={{
            p: 0,
            mb: 2,
            overflow: 'hidden',
            border: '1px solid',
            borderColor: 'divider',
          }}
        >
          <Box sx={{ px: 2, py: 1, bgcolor: 'grey.900', display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ff5f57' }} />
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ffbd2e' }} />
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#28c840' }} />
            <Typography variant="caption" color="grey.400" sx={{ ml: 1, fontFamily: 'monospace' }}>
              UIP AI Workflow Engine — Live Event Console
            </Typography>
          </Box>
          {running && <LinearProgress sx={{ height: 2 }} />}
          <Box sx={{ px: 2, py: 1.5, bgcolor: '#1a1a2e', minHeight: 80 }}>
            {logs.map(log => (
              <Box key={log.id} display="flex" alignItems="flex-start" gap={1.5} mb={1}>
                {iconEl(log.icon)}
                <Box flex={1}>
                  <Box display="flex" gap={1.5} alignItems="baseline" flexWrap="wrap">
                    <Typography
                      variant="caption"
                      sx={{ fontFamily: 'monospace', color: 'grey.500', flexShrink: 0 }}
                    >
                      {log.ts}
                    </Typography>
                    <Typography
                      variant="body2"
                      sx={{
                        fontFamily: 'monospace',
                        color: log.icon === 'ai' ? '#64b5f6' : log.icon === 'done' ? '#a5d6a7' : '#fff9c4',
                        fontWeight: log.icon === 'ai' ? 600 : 400,
                      }}
                    >
                      {log.label}
                    </Typography>
                  </Box>
                  {log.detail && (
                    <Typography
                      variant="caption"
                      sx={{
                        fontFamily: 'monospace',
                        color: 'grey.500',
                        display: 'block',
                        mt: 0.25,
                        ml: 0,
                        maxWidth: 680,
                        wordBreak: 'break-all',
                        whiteSpace: 'pre-wrap',
                      }}
                    >
                      {log.detail}
                    </Typography>
                  )}
                </Box>
              </Box>
            ))}
          </Box>
        </Paper>
      )}

      {/* AI Decision Card */}
      {result && (
        <Paper variant="outlined" sx={{ p: 2.5 }}>
          <Box display="flex" alignItems="center" gap={1} mb={2}>
            <SmartToyIcon color="primary" />
            <Typography variant="subtitle2" fontWeight={700}>Kết quả phân tích AI</Typography>
            <Chip label={`${Math.round(Number(result.aiConfidence ?? 0) * 100)}% confidence`} size="small" color="primary" />
            <Chip label={String(result.aiSeverity ?? 'MEDIUM')} size="small"
              color={result.aiSeverity === 'HIGH' || result.aiSeverity === 'CRITICAL' ? 'error' : 'warning'} />
          </Box>
          <Divider sx={{ mb: 2 }} />
          <Stack spacing={1.5}>
            <Box display="flex" gap={2} flexWrap="wrap">
              <Box flex={1} minWidth={160}>
                <Typography variant="caption" color="text.secondary" display="block">Quyết định</Typography>
                <Typography variant="body2" fontWeight={600}>{String(result.aiDecision ?? '—')}</Typography>
              </Box>
              <Box flex={1} minWidth={160}>
                <Typography variant="caption" color="text.secondary" display="block">Phòng ban tiếp nhận</Typography>
                <Typography variant="body2" fontWeight={600} color="primary.main">{String(result.department ?? '—')}</Typography>
              </Box>
              <Box flex={1} minWidth={120}>
                <Typography variant="caption" color="text.secondary" display="block">Ưu tiên</Typography>
                <Typography variant="body2" fontWeight={600}>{String(result.priority ?? '—')}</Typography>
              </Box>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">Lý giải của AI</Typography>
              <Typography variant="body2" sx={{ mt: 0.5, lineHeight: 1.6 }}>{String(result.aiReasoning ?? '—')}</Typography>
            </Box>
            {Array.isArray(result.aiRecommendedActions) && (
              <Box>
                <Typography variant="caption" color="text.secondary" display="block" mb={0.5}>Hành động được đề xuất</Typography>
                <Stack spacing={0.5}>
                  {(result.aiRecommendedActions as string[]).map((a, i) => (
                    <Box key={i} display="flex" gap={1} alignItems="flex-start">
                      <CheckCircleIcon sx={{ fontSize: 14, color: 'success.main', mt: '3px', flexShrink: 0 }} />
                      <Typography variant="body2">{a}</Typography>
                    </Box>
                  ))}
                </Stack>
              </Box>
            )}
            <Box>
              <Typography variant="caption" color="text.secondary">Auto-response gửi công dân: </Typography>
              <Typography variant="caption" fontStyle="italic">"{String(result.autoResponseText ?? '')}"</Typography>
            </Box>
          </Stack>
        </Paper>
      )}
    </Box>
      )}
    </Box>
  );
}

// ── Main page ────────────────────────────────────────────────────────────────

export default function AiWorkflowPage() {
  const [tab, setTab] = useState(0);

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
        />
        <Tab
          label="Process Definitions"
          icon={<SmartToyIcon />}
          iconPosition="start"
          sx={{ minHeight: 48 }}
        />
        <Tab
          label="Live Demo"
          icon={<BoltIcon />}
          iconPosition="start"
          sx={{ minHeight: 48, color: tab === 2 ? 'error.main' : undefined }}
        />
      </Tabs>

      {tab === 0 && <InstancesTab />}
      {tab === 1 && <DefinitionsTab />}
      {tab === 2 && <LiveDemoTab />}
    </Box>
  );
}
