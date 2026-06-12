/**
 * LiveDemoTab — extracted from AiWorkflowPage for code-splitting (v3.1-05).
 * Contains the IoT sensor demo + citizen request demo pipelines.
 * Lazy-loaded to reduce initial bundle size.
 */
import { useState, useRef, useCallback } from 'react';
import {
  Box,
  Typography,
  TextField,
  MenuItem,
  Chip,
  Paper,
  CircularProgress,
  Alert as MuiAlert,
  Button,
  Divider,
  LinearProgress,
  Stack,
  Slider,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  useTheme,
} from '@mui/material';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import BoltIcon from '@mui/icons-material/Bolt';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { useFireWorkflowTrigger } from '@/hooks/useWorkflowConfig';
import { apiClient } from '@/api/client';

// ── Types ─────────────────────────────────────────────────────────────────

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
] as const;

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

// ── AQI helpers using MUI theme tokens ──────────────────────────────────

function useAqiHelpers() {
  const theme = useTheme();
  const aqiColor = (v: number): string => {
    if (v > 300) return theme.palette.error.dark;
    if (v > 200) return theme.palette.error.main;
    if (v > 150) return theme.palette.warning.main;
    if (v > 100) return '#fdd835';
    if (v > 50) return theme.palette.success.main;
    return theme.palette.success.dark;
  };
  const aqiLabel = (v: number): string => {
    if (v > 300) return 'Hazardous';
    if (v > 200) return 'Very Unhealthy';
    if (v > 150) return 'Unhealthy';
    if (v > 100) return 'Sensitive';
    if (v > 50) return 'Moderate';
    return 'Good';
  };
  return { aqiColor, aqiLabel };
}

// ── IoT Sensor Demo section ─────────────────────────────────────────────

function IoTSensorSection() {
  const theme = useTheme();
  const { aqiColor, aqiLabel } = useAqiHelpers();
  const [sensorId, setSensorId] = useState('ENV-HCM-D1-001');
  const [aqiValue, setAqiValue] = useState(175);
  const [district, setDistrict] = useState('D1');
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<Record<string, unknown> | null>(null);
  const [showPayload, setShowPayload] = useState(false);
  const [iotPid, setIotPid] = useState<string | null>(null);
  const logIdRef = useRef(0);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
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
    setLogs([]); setResult(null); setRunning(false); setIotPid(null);
  }, []);

  const handleFire = useCallback(async () => {
    if (running) return;
    setLogs([]); setResult(null); setRunning(true);
    if (pollRef.current) clearInterval(pollRef.current);

    addLog('done', `MQTT Publish -> EMQX broker`, `topic: uip/sensors/${sensorId}/telemetry | QoS: 1 | payload: NGSI-LD`);
    await new Promise(r => setTimeout(r, 450));

    addLog('done', `EMQX broker received message`, `clientId: ${sensorId} | retained: false | ts: ${ts()}`);
    await new Promise(r => setTimeout(r, 400));

    addLog('running', 'Redpanda Connect: MQTT -> Kafka bridge...');
    await new Promise(r => setTimeout(r, 650));
    updateLastLog('Redpanda Connect: transform NGSI-LD + publish Kafka', 'done',
      'input: mqtt_broker | output: kafka_franz | schema: ngsi-ld-v1');

    await new Promise(r => setTimeout(r, 350));
    const partition = Math.floor(Math.random() * 6);
    const offset = 10000 + Math.floor(Math.random() * 90000);
    addLog('done', 'Kafka topic: ngsi_ld_environment',
      `partition: ${partition} | offset: ${offset} | key: ${sensorId}`);

    await new Promise(r => setTimeout(r, 450));
    addLog('running', 'Flink EnvironmentFlinkJob: consuming event...', 'group.id: flink-environment-job');
    await new Promise(r => setTimeout(r, 500));
    updateLastLog('Flink: event decoded + watermark assigned', 'done',
      `event_time: ${new Date().toISOString()} | watermark: T-30s`);

    await new Promise(r => setTimeout(r, 600));
    addLog('running', `Flink 30s tumbling window aggregating...`, `keyBy: districtCode="${district}"`);
    await new Promise(r => setTimeout(r, 750));
    updateLastLog(`Flink window result: AQI=${aqiValue} (district ${district})`, 'done',
      `window: [now-30s, now] | readings: 1 | avg: ${aqiValue} | max: ${aqiValue}`);

    await new Promise(r => setTimeout(r, 350));
    const exceeded = aqiValue > 150;

    if (exceeded) {
      addLog('done', `Threshold breach! AQI ${aqiValue} > 150 -> AlertEvent generated`,
        `severity: ${aqiValue > 200 ? 'HIGH' : 'MEDIUM'} | module: ENVIRONMENT`);
      await new Promise(r => setTimeout(r, 400));

      addLog('done', 'Flink publish -> Kafka: UIP.flink.alert.detected.v1',
        `key: ${district} | alertType: AQI_THRESHOLD | sensorId: ${sensorId}`);
      await new Promise(r => setTimeout(r, 500));

      addLog('running', 'GenericKafkaTriggerService: evaluating filter conditions...');
      let simResult: { processInstanceId: string; alertTriggered: boolean };
      try {
        const resp = await apiClient.post<{ processInstanceId: string; alertTriggered: boolean }>(
          '/simulate/iot-sensor',
          { sensorType: 'AQI', sensorId, value: aqiValue, district },
        );
        simResult = resp.data;
        setIotPid(simResult.processInstanceId);
        updateLastLog('Filter match: module=ENVIRONMENT, AQI>150 -> startProcess()', 'done',
          'processKey: aiC01_aqiCitizenAlert | triggerType: KAFKA');
      } catch {
        updateLastLog('Error calling backend simulate API', 'done');
        setRunning(false);
        return;
      }

      await new Promise(r => setTimeout(r, 300));
      addLog('done', 'Camunda BPMN aiC01_aqiCitizenAlert started',
        `processInstanceId: ${simResult.processInstanceId}`);

      await new Promise(r => setTimeout(r, 400));
      addLog('running', 'AIAnalysisDelegate: scenarioKey=aiC01_aqiCitizenAlert...',
        'Calling Claude AI / RuleBasedFallback service');

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
              `AI decision: ${vars.aiDecision} (confidence: ${conf}%)`,
              'ai',
              String(vars.aiReasoning ?? ''),
            );

            await new Promise(r => setTimeout(r, 400));
            const notified = Number(vars.citizensNotified ?? 1500);
            addLog('done', `AqiCitizenAlertDelegate: SSE notification sent`,
              `${notified.toLocaleString()} residents in district ${district} alerted | channel: uip:alerts`);

            await new Promise(r => setTimeout(r, 300));
            addLog('done', 'Pipeline complete -- BPMN EndEvent reached', `notificationSent: true`);

            setResult(vars);
            setRunning(false);
          } else if (attempts > 25) {
            clearInterval(pollRef.current!);
            updateLastLog('Timeout waiting for AI result', 'done');
            setRunning(false);
          }
        } catch { /* retry next tick */ }
      }, 600);

    } else {
      addLog('done', `AQI ${aqiValue} <= 150 -- below threshold, no alert generated`,
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
          IoT Sensor Configuration
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
              select size="small" label="District"
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
              <Typography variant="caption" color="text.secondary">AQI Value (0 - 400)</Typography>
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
                { value: 150, label: '150' },
                { value: 200, label: '200' },
                { value: 300, label: '300' },
              ]}
              sx={{ color }}
              aria-label="AQI value slider"
            />
            {aqiValue > 150 ? (
              <MuiAlert severity="warning" sx={{ mt: 1, py: 0.5 }}>
                AQI {aqiValue} exceeds threshold (150){' -> '}Flink alert{' -> '}Camunda BPMN <strong>aiC01_aqiCitizenAlert</strong> triggered
              </MuiAlert>
            ) : (
              <MuiAlert severity="success" sx={{ mt: 1, py: 0.5 }}>
                AQI {aqiValue} below threshold (150){' -- '}No alert, Flink writes to TimescaleDB only
              </MuiAlert>
            )}
          </Box>

          {/* Quick presets */}
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <Typography variant="caption" color="text.secondary" alignSelf="center">Preset:</Typography>
            {[
              { label: 'Below threshold (120)', val: 120 },
              { label: 'Polluted (175)', val: 175 },
              { label: 'Dangerous (235)', val: 235 },
            ].map(p => (
              <Chip key={p.val} label={p.label} size="small" clickable
                onClick={() => setAqiValue(p.val)} disabled={running}
                sx={{ bgcolor: aqiColor(p.val), color: '#fff' }} />
            ))}
          </Stack>

          {/* MQTT Payload Preview */}
          <Box>
            <Button size="small" variant="text" onClick={() => setShowPayload(v => !v)}>
              {showPayload ? 'Hide' : 'Show'} MQTT Payload (NGSI-LD)
            </Button>
            {showPayload && (
              <Paper variant="outlined" sx={{ p: 1.5, mt: 1, bgcolor: theme.palette.mode === 'dark' ? 'grey.900' : '#0d1117' }}>
                <Typography variant="caption" sx={{ fontFamily: 'monospace', display: 'block', mb: 0.5, color: 'grey.500' }}>
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
              aria-label={running ? 'Pipeline running' : 'Fire IoT sensor event'}
            >
              {running ? 'Processing pipeline...' : 'Fire IoT Event'}
            </Button>
            {logs.length > 0 && !running && (
              <Button variant="outlined" size="small" onClick={handleReset} aria-label="Reset pipeline">
                Reset
              </Button>
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
            <Typography variant="caption" sx={{ ml: 1, fontFamily: 'monospace', color: 'grey.400' }}>
              UIP IoT Pipeline{' -- '}MQTT{' > '}Redpanda{' > '}Kafka{' > '}Flink{' > '}Alert{' > '}Camunda
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
          <Box display="flex" alignItems="center" gap={1} mb={2} flexWrap="wrap">
            <SmartToyIcon color="error" />
            <Typography variant="subtitle2" fontWeight={700}>AQI Alert Result</Typography>
            {(() => {
              const conf = Number(result.aiConfidence ?? 0);
              const ro = conf > 0.85 ? 'AUTO_EXECUTE' : conf >= 0.6 ? 'OPERATOR_QUEUE' : 'ESCALATE';
              const roColor: 'success' | 'warning' | 'error' = conf > 0.85 ? 'success' : conf >= 0.6 ? 'warning' : 'error';
              return (
                <>
                  <Chip label={`${Math.round(conf * 100)}% confidence`} size="small" color="primary" />
                  <Chip label={String(result.aiSeverity ?? 'MEDIUM')} size="small"
                    color={result.aiSeverity === 'HIGH' ? 'error' : 'warning'} />
                  <Chip label={ro} size="small" color={roColor} variant="outlined" />
                </>
              );
            })()}
          </Box>
          <Divider sx={{ mb: 2 }} />
          <Stack spacing={2}>
            {/* DecisionRouter Panel */}
            {(() => {
              const conf = Number(result.aiConfidence ?? 0);
              const confPct = Math.min(Math.round(conf * 100), 100);
              const ro = conf > 0.85 ? 'AUTO_EXECUTE' : conf >= 0.6 ? 'OPERATOR_QUEUE' : 'ESCALATE';
              const roColor: 'success' | 'warning' | 'error' = conf > 0.85 ? 'success' : conf >= 0.6 ? 'warning' : 'error';
              const thresholdLabel = conf > 0.85 ? 'conf > 0.85 -> AUTO_EXECUTE' : conf >= 0.6 ? '0.60 <= conf <= 0.85 -> OPERATOR_QUEUE' : 'conf < 0.60 -> ESCALATE';
              return (
                <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'action.hover' }}>
                  <Typography variant="caption" fontWeight={700} color="text.secondary" display="block" mb={1.5}>
                    DecisionRouter (confidence-based routing)
                  </Typography>
                  <Box mb={1.5}>
                    <Box display="flex" justifyContent="space-between" mb={0.5}>
                      <Typography variant="caption" color="text.secondary">AI Confidence Score</Typography>
                      <Typography variant="caption" fontWeight={700} color={`${roColor}.main`}>{confPct}%</Typography>
                    </Box>
                    <Box position="relative" sx={{ height: 10, borderRadius: 5, bgcolor: 'grey.300', overflow: 'visible' }}>
                      <Box sx={{
                        position: 'absolute', left: 0, top: 0, height: '100%', borderRadius: 5,
                        width: `${confPct}%`,
                        bgcolor: conf > 0.85 ? 'success.main' : conf >= 0.6 ? 'warning.main' : 'error.main',
                        transition: 'width 0.6s ease',
                      }} />
                      <Box sx={{ position: 'absolute', left: '60%', top: -3, bottom: -3, width: 2, bgcolor: 'warning.dark', borderRadius: 1 }} />
                      <Box sx={{ position: 'absolute', left: '85%', top: -3, bottom: -3, width: 2, bgcolor: 'success.dark', borderRadius: 1 }} />
                    </Box>
                    <Box display="flex" sx={{ mt: 0.5, position: 'relative', height: 18 }}>
                      <Typography variant="caption" color="error.main" sx={{ position: 'absolute', left: 0 }}>ESCALATE</Typography>
                      <Typography variant="caption" color="warning.dark" sx={{ position: 'absolute', left: '60%', transform: 'translateX(-50%)' }}>60%</Typography>
                      <Typography variant="caption" color="success.dark" sx={{ position: 'absolute', left: '85%', transform: 'translateX(-50%)' }}>85%</Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ position: 'absolute', right: 0 }}>AUTO</Typography>
                    </Box>
                  </Box>
                  <Stack direction="row" spacing={2} flexWrap="wrap" rowGap={1}>
                    <Box>
                      <Typography variant="caption" color="text.secondary" display="block">Routing Outcome</Typography>
                      <Chip label={ro} size="small" color={roColor} />
                    </Box>
                    <Box>
                      <Typography variant="caption" color="text.secondary" display="block">Threshold Applied</Typography>
                      <Typography variant="caption" fontWeight={600} sx={{ fontFamily: 'monospace', fontSize: '0.7rem' }}>{thresholdLabel}</Typography>
                    </Box>
                    <Box>
                      <Typography variant="caption" color="text.secondary" display="block">Cache</Typography>
                      <Chip label="Redis TTL 15m" size="small" variant="outlined" />
                    </Box>
                    {iotPid && (
                      <Box>
                        <Typography variant="caption" color="text.secondary" display="block">Process ID</Typography>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace', fontSize: '0.7rem', color: 'primary.main' }}>{iotPid}</Typography>
                      </Box>
                    )}
                  </Stack>
                </Paper>
              );
            })()}

            {/* Main fields */}
            <Box display="flex" gap={2} flexWrap="wrap">
              <Box flex={1} minWidth={160}>
                <Typography variant="caption" color="text.secondary" display="block">AI Decision</Typography>
                <Typography variant="body2" fontWeight={600} color="error.main">{String(result.aiDecision ?? '--')}</Typography>
              </Box>
              <Box flex={1} minWidth={160}>
                <Typography variant="caption" color="text.secondary" display="block">Citizens Alerted</Typography>
                <Typography variant="body2" fontWeight={600} color="warning.main">
                  {Number(result.citizensNotified ?? 1500).toLocaleString()} residents
                </Typography>
              </Box>
              <Box flex={1} minWidth={120}>
                <Typography variant="caption" color="text.secondary" display="block">Notification Sent</Typography>
                <Typography variant="body2" fontWeight={600}>
                  {result.notificationSent ? 'SSE sent' : '--'}
                </Typography>
              </Box>
            </Box>

            {/* AI Reasoning */}
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">AI Reasoning</Typography>
              <Typography variant="body2" sx={{ mt: 0.5, lineHeight: 1.6 }}>{String(result.aiReasoning ?? '--')}</Typography>
            </Box>

            {/* Recommended actions */}
            {Array.isArray(result.aiRecommendedActions) && (
              <Box>
                <Typography variant="caption" color="text.secondary" display="block" mb={0.5}>Recommendations</Typography>
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

            {/* Raw process variables */}
            <Accordion disableGutters elevation={0} sx={{ border: '1px solid', borderColor: 'divider', '&:before': { display: 'none' }, borderRadius: 1 }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon sx={{ fontSize: 18 }} />} sx={{ minHeight: 36, '& .MuiAccordionSummary-content': { margin: '6px 0' } }}>
                <Typography variant="caption" fontWeight={600}>Raw Process Variables ({Object.keys(result).length} fields)</Typography>
              </AccordionSummary>
              <AccordionDetails sx={{ p: 0 }}>
                <Box component="pre" sx={{ fontFamily: 'monospace', fontSize: '0.7rem', color: '#a5d6a7', bgcolor: '#0d1117', p: 2, m: 0, overflowX: 'auto', maxHeight: 240, overflowY: 'auto' }}>
                  {JSON.stringify(result, null, 2)}
                </Box>
              </AccordionDetails>
            </Accordion>
          </Stack>
        </Paper>
      )}
    </Box>
  );
}

// ── Citizen Demo Tab ──────────────────────────────────────────────────────

function CitizenDemoSection() {
  const [scenarioIdx, setScenarioIdx] = useState(0);
  const [customDesc, setCustomDesc] = useState('');
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<Record<string, unknown> | null>(null);
  const [processId, setProcessId] = useState<string | null>(null);
  const logIdRef = useRef(0);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // React Query mutation (GAP-031)
  const fireTriggerMutation = useFireWorkflowTrigger();

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

    addLog('done', 'Event received', `requestType: ${scenario.requestType} | district: ${scenario.district}`);

    await new Promise(r => setTimeout(r, 400));
    addLog('running', 'Initializing BPMN process...');
    let pid: string;
    try {
      const resp = await fireTriggerMutation.mutateAsync({
        scenarioKey: 'aiC02_citizenServiceRequest',
        payload: {
          description,
          district: scenario.district,
          requestType: scenario.requestType,
        },
      });
      pid = resp.processInstanceId;
      setProcessId(pid);
      updateLastLog('Camunda process initialized', 'done', `processInstanceId: ${pid}`);
    } catch {
      updateLastLog('Error calling workflow trigger', 'done');
      setRunning(false);
      return;
    }

    await new Promise(r => setTimeout(r, 500));
    addLog('running', 'AI analyzing request...', 'Calling Claude AI / Rule Engine');

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
            `AI classification: ${vars.aiDecision} (confidence: ${conf}%)`,
            'ai',
            String(vars.aiReasoning ?? ''),
          );

          await new Promise(r => setTimeout(r, 300));
          addLog('done', `Routed to Department ${vars.department ?? 'GENERAL'}`, `Priority: ${vars.priority ?? 'MEDIUM'}`);

          await new Promise(r => setTimeout(r, 300));
          addLog('done', 'Request processed', `requestId: ${vars.requestId ?? 'N/A'}`);

          setResult(vars);
          setRunning(false);
        } else if (attempts > 20) {
          clearInterval(pollRef.current!);
          updateLastLog('Timeout waiting for AI result', 'done');
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
    <Box sx={{ maxWidth: 860 }}>
      {/* Event Form */}
      <Paper variant="outlined" sx={{ p: 2.5, mb: 2 }}>
        <Typography variant="subtitle2" gutterBottom fontWeight={700}>
          Simulate Citizen Request
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} mt={1.5} alignItems="flex-start">
          <TextField
            select
            size="small"
            label="Event Type"
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
            label="Description (editable)"
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
            aria-label={running ? 'Processing request' : 'Fire citizen event'}
          >
            {running ? 'Processing...' : 'Fire Event'}
          </Button>
          {logs.length > 0 && !running && (
            <Button variant="outlined" size="small" onClick={handleReset} aria-label="Reset demo">
              Reset
            </Button>
          )}
        </Stack>
      </Paper>

      {/* Live Console */}
      {logs.length > 0 && (
        <Paper variant="outlined" sx={{ p: 0, mb: 2, overflow: 'hidden', border: '1px solid', borderColor: 'divider' }}>
          <Box sx={{ px: 2, py: 1, bgcolor: 'grey.900', display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ff5f57' }} />
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#ffbd2e' }} />
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: '#28c840' }} />
            <Typography variant="caption" sx={{ ml: 1, fontFamily: 'monospace', color: 'grey.400' }}>
              UIP AI Workflow Engine -- Live Event Console
            </Typography>
          </Box>
          {running && <LinearProgress sx={{ height: 2 }} />}
          <Box sx={{ px: 2, py: 1.5, bgcolor: '#1a1a2e', minHeight: 80 }}>
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
                      fontFamily: 'monospace', color: 'grey.500',
                      display: 'block', mt: 0.25, ml: 0,
                      maxWidth: 680, wordBreak: 'break-all', whiteSpace: 'pre-wrap',
                    }}>
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
          <Box display="flex" alignItems="center" gap={1} mb={2} flexWrap="wrap">
            <SmartToyIcon color="primary" />
            <Typography variant="subtitle2" fontWeight={700}>AI Analysis Result</Typography>
            {(() => {
              const conf = Number(result.aiConfidence ?? 0);
              const ro = conf > 0.85 ? 'AUTO_EXECUTE' : conf >= 0.6 ? 'OPERATOR_QUEUE' : 'ESCALATE';
              const roColor: 'success' | 'warning' | 'error' = conf > 0.85 ? 'success' : conf >= 0.6 ? 'warning' : 'error';
              return (
                <>
                  <Chip label={`${Math.round(conf * 100)}% confidence`} size="small" color="primary" />
                  <Chip label={String(result.aiSeverity ?? 'MEDIUM')} size="small"
                    color={result.aiSeverity === 'HIGH' || result.aiSeverity === 'CRITICAL' ? 'error' : 'warning'} />
                  <Chip label={ro} size="small" color={roColor} variant="outlined" />
                </>
              );
            })()}
          </Box>
          <Divider sx={{ mb: 2 }} />
          <Stack spacing={2}>
            {/* DecisionRouter Panel */}
            {(() => {
              const conf = Number(result.aiConfidence ?? 0);
              const confPct = Math.min(Math.round(conf * 100), 100);
              const ro = conf > 0.85 ? 'AUTO_EXECUTE' : conf >= 0.6 ? 'OPERATOR_QUEUE' : 'ESCALATE';
              const roColor: 'success' | 'warning' | 'error' = conf > 0.85 ? 'success' : conf >= 0.6 ? 'warning' : 'error';
              const thresholdLabel = conf > 0.85 ? 'conf > 0.85 -> AUTO_EXECUTE' : conf >= 0.6 ? '0.60 <= conf <= 0.85 -> OPERATOR_QUEUE' : 'conf < 0.60 -> ESCALATE';
              return (
                <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'action.hover' }}>
                  <Typography variant="caption" fontWeight={700} color="text.secondary" display="block" mb={1.5}>
                    DecisionRouter (confidence-based routing)
                  </Typography>
                  <Box mb={1.5}>
                    <Box display="flex" justifyContent="space-between" mb={0.5}>
                      <Typography variant="caption" color="text.secondary">AI Confidence Score</Typography>
                      <Typography variant="caption" fontWeight={700} color={`${roColor}.main`}>{confPct}%</Typography>
                    </Box>
                    <Box position="relative" sx={{ height: 10, borderRadius: 5, bgcolor: 'grey.300', overflow: 'visible' }}>
                      <Box sx={{
                        position: 'absolute', left: 0, top: 0, height: '100%', borderRadius: 5,
                        width: `${confPct}%`,
                        bgcolor: conf > 0.85 ? 'success.main' : conf >= 0.6 ? 'warning.main' : 'error.main',
                        transition: 'width 0.6s ease',
                      }} />
                      <Box sx={{ position: 'absolute', left: '60%', top: -3, bottom: -3, width: 2, bgcolor: 'warning.dark', borderRadius: 1 }} />
                      <Box sx={{ position: 'absolute', left: '85%', top: -3, bottom: -3, width: 2, bgcolor: 'success.dark', borderRadius: 1 }} />
                    </Box>
                    <Box display="flex" sx={{ mt: 0.5, position: 'relative', height: 18 }}>
                      <Typography variant="caption" color="error.main" sx={{ position: 'absolute', left: 0 }}>ESCALATE</Typography>
                      <Typography variant="caption" color="warning.dark" sx={{ position: 'absolute', left: '60%', transform: 'translateX(-50%)' }}>60%</Typography>
                      <Typography variant="caption" color="success.dark" sx={{ position: 'absolute', left: '85%', transform: 'translateX(-50%)' }}>85%</Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ position: 'absolute', right: 0 }}>AUTO</Typography>
                    </Box>
                  </Box>
                  <Stack direction="row" spacing={2} flexWrap="wrap" rowGap={1}>
                    <Box>
                      <Typography variant="caption" color="text.secondary" display="block">Routing Outcome</Typography>
                      <Chip label={ro} size="small" color={roColor} />
                    </Box>
                    <Box>
                      <Typography variant="caption" color="text.secondary" display="block">Threshold Applied</Typography>
                      <Typography variant="caption" fontWeight={600} sx={{ fontFamily: 'monospace', fontSize: '0.7rem' }}>{thresholdLabel}</Typography>
                    </Box>
                    <Box>
                      <Typography variant="caption" color="text.secondary" display="block">Cache</Typography>
                      <Chip label="Redis TTL 15m" size="small" variant="outlined" />
                    </Box>
                    {processId && (
                      <Box>
                        <Typography variant="caption" color="text.secondary" display="block">Process ID</Typography>
                        <Typography variant="caption" sx={{ fontFamily: 'monospace', fontSize: '0.7rem', color: 'primary.main' }}>{processId}</Typography>
                      </Box>
                    )}
                  </Stack>
                </Paper>
              );
            })()}

            {/* Main decision fields */}
            <Box display="flex" gap={2} flexWrap="wrap">
              <Box flex={1} minWidth={160}>
                <Typography variant="caption" color="text.secondary" display="block">Decision</Typography>
                <Typography variant="body2" fontWeight={600}>{String(result.aiDecision ?? '--')}</Typography>
              </Box>
              <Box flex={1} minWidth={160}>
                <Typography variant="caption" color="text.secondary" display="block">Assigned Department</Typography>
                <Typography variant="body2" fontWeight={600} color="primary.main">{String(result.department ?? '--')}</Typography>
              </Box>
              <Box flex={1} minWidth={120}>
                <Typography variant="caption" color="text.secondary" display="block">Priority</Typography>
                <Typography variant="body2" fontWeight={600}>{String(result.priority ?? '--')}</Typography>
              </Box>
            </Box>

            {/* AI reasoning */}
            <Box>
              <Typography variant="caption" color="text.secondary" display="block">AI Reasoning</Typography>
              <Typography variant="body2" sx={{ mt: 0.5, lineHeight: 1.6 }}>{String(result.aiReasoning ?? '--')}</Typography>
            </Box>

            {/* Recommended actions */}
            {Array.isArray(result.aiRecommendedActions) && (
              <Box>
                <Typography variant="caption" color="text.secondary" display="block" mb={0.5}>Recommended Actions</Typography>
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

            {/* Auto-response text */}
            <Box>
              <Typography variant="caption" color="text.secondary">Auto-response to citizen: </Typography>
              <Typography variant="caption" fontStyle="italic">"{String(result.autoResponseText ?? '')}"</Typography>
            </Box>

            {/* Raw process variables */}
            <Accordion disableGutters elevation={0} sx={{ border: '1px solid', borderColor: 'divider', '&:before': { display: 'none' }, borderRadius: 1 }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon sx={{ fontSize: 18 }} />} sx={{ minHeight: 36, '& .MuiAccordionSummary-content': { margin: '6px 0' } }}>
                <Typography variant="caption" fontWeight={600}>Raw Process Variables ({Object.keys(result).length} fields)</Typography>
              </AccordionSummary>
              <AccordionDetails sx={{ p: 0 }}>
                <Box component="pre" sx={{ fontFamily: 'monospace', fontSize: '0.7rem', color: '#a5d6a7', bgcolor: '#0d1117', p: 2, m: 0, overflowX: 'auto', maxHeight: 240, overflowY: 'auto' }}>
                  {JSON.stringify(result, null, 2)}
                </Box>
              </AccordionDetails>
            </Accordion>
          </Stack>
        </Paper>
      )}
    </Box>
  );
}

// ── Main LiveDemoTab export ────────────────────────────────────────────────

export default function LiveDemoTab() {
  const [demoMode, setDemoMode] = useState<'citizen' | 'iot'>('citizen');

  return (
    <Box sx={{ mt: 2 }}>
      {/* Mode selector */}
      <Stack direction="row" spacing={1} mb={2.5}>
        <Button
          variant={demoMode === 'citizen' ? 'contained' : 'outlined'}
          startIcon={<AccountTreeIcon />}
          onClick={() => setDemoMode('citizen')}
          size="small"
          aria-label="Switch to citizen request demo"
        >
          Citizen Request
        </Button>
        <Button
          variant={demoMode === 'iot' ? 'contained' : 'outlined'}
          color={demoMode === 'iot' ? 'error' : 'inherit'}
          startIcon={<BoltIcon />}
          onClick={() => setDemoMode('iot')}
          size="small"
          aria-label="Switch to IoT sensor demo"
        >
          IoT Sensor
        </Button>
      </Stack>

      {demoMode === 'iot' ? (
        <IoTSensorSection />
      ) : (
        <CitizenDemoSection />
      )}
    </Box>
  );
}
