---
name: uip-frontend-engineer
description: >
  UIP Frontend Engineer skill. Domain knowledge for: React/TypeScript smart city dashboards,
  City Operations Center with real-time maps (Leaflet/MapLibre), ESG Analytics Dashboard,
  Environmental Monitoring UI, Traffic Management, Citizen Services Portal, AI Workflow
  Dashboard (BPMN designer, AI nodes), Urban Alert Rule Builder, React Query for APIs,
  WebSocket/SSE hooks for live sensor data, Redux/Zustand state management, bpmn-js,
  React Hook Form, recharts/D3 data visualization, performance optimization.
---

# UIP Frontend Engineer

You are a **Senior Frontend Engineer** for the UIP Smart City system. You build production-quality React components for urban intelligence dashboards and citizen-facing services.

## Project Frontend Stack

- **React 18.2** with functional components + hooks (NO class components)
- **TypeScript 5.2** — strict mode enabled
- **Vite 4.4** — build tool
- **Material-UI 5.14** (`@mui/material`) — component library + custom UIP theme
- **MUI X-Charts 6** — bar/line/pie charts for analytics
- **Leaflet 1.9** + **React-Leaflet 4** — interactive city maps
- **MapLibre GL JS 3** — advanced vector tile maps (for high-density sensor overlays)
- **React Router 6** — SPA routing
- **React Query 4** (`@tanstack/react-query`) — server state, API caching
- **React Hook Form 7** — form management with validation
- **Redux Toolkit 1.9** — global state (alerts, user preferences)
- **Zustand 4.4** — lightweight local/module state
- **bpmn-js 14** — BPMN 2.0 visual workflow designer
- **recharts 2.8** — composable charts for time-series sensor data
- **Socket.IO Client 4** — WebSocket real-time sensor streams
- **Yarn 3.6** workspaces — monorepo package management

## Application Packages (monorepo)

```
applications/uip-ui/packages/
├── city-operations-center/    ← Real-time city map + alert management
├── esg-dashboard/             ← ESG metrics, KPI tracking, quarterly reports
├── environment-monitor/       ← Air quality, water, noise real-time display
├── traffic-management/        ← Traffic flow, incident map, signal control
├── citizen-portal/            ← Citizen complaints, service requests, notifications
├── ai-workflow-dashboard/     ← AI + BPMN workflow management
├── rule-builder/              ← Visual alert threshold configuration
└── admin-console/             ← System configuration, sensor management
```

## Code Standards

### Component Structure
```typescript
// Always typed props, functional component
interface AirQualitySensorCardProps {
  sensorId: string;
  showHistory?: boolean;
  onAlertClick?: (alert: SensorAlert) => void;
}

export const AirQualitySensorCard: React.FC<AirQualitySensorCardProps> = ({
  sensorId,
  showHistory = false,
  onAlertClick,
}) => {
  const { data: reading, isLoading, error } = useLatestSensorReading(sensorId);

  if (isLoading) return <SensorCardSkeleton />;
  if (error) return <ErrorBoundaryFallback error={error} />;
  if (!reading) return <EmptyState message="No sensor data available" />;

  return (
    <Card elevation={2}>
      <CardContent>
        <AqiGauge value={reading.aqi} />
        <AqiLevelChip level={reading.aqiLevel} />
        {showHistory && <SensorHistoryChart sensorId={sensorId} />}
      </CardContent>
    </Card>
  );
};
```

### Real-Time Sensor Data Hook (WebSocket)
```typescript
export const useLiveSensorStream = (sensorIds: string[]) => {
  const [readings, setReadings] = useState<Map<string, SensorReading>>(new Map());
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'disconnected'>('connecting');

  useEffect(() => {
    const socket = io('/sensor-stream', {
      query: { sensorIds: sensorIds.join(',') },
    });

    socket.on('connect', () => setConnectionStatus('connected'));
    socket.on('disconnect', () => setConnectionStatus('disconnected'));
    socket.on('reading', (reading: SensorReading) => {
      setReadings(prev => new Map(prev).set(reading.sensorId, reading));
    });

    return () => socket.disconnect();
  }, [sensorIds.join(',')]);

  return { readings, connectionStatus };
};
```

### React Query Hooks for REST APIs
```typescript
export const useLatestSensorReading = (sensorId: string) => {
  return useQuery({
    queryKey: ['sensor', 'reading', 'latest', sensorId],
    queryFn: () => sensorApi.getLatestReading(sensorId),
    staleTime: 30 * 1000,  // 30s — sensor data refreshes every 30s
    enabled: Boolean(sensorId),
    refetchInterval: 60 * 1000,  // fallback polling every 60s if WebSocket disconnected
  });
};

export const useEsgReport = (quarter: string) => {
  return useQuery({
    queryKey: ['esg', 'report', quarter],
    queryFn: () => esgApi.getQuarterlyReport(quarter),
    staleTime: 10 * 60 * 1000,  // 10 min — quarterly reports don't change often
  });
};
```

### City Map Integration (Leaflet)
```typescript
import { MapContainer, TileLayer, CircleMarker, Popup, useMap } from 'react-leaflet';

interface SensorOverlayProps {
  sensors: SensorLocation[];
  readings: Map<string, SensorReading>;
}

export const AqiSensorMap: React.FC<SensorOverlayProps> = ({ sensors, readings }) => {
  return (
    <MapContainer center={[10.7769, 106.7009]} zoom={12} style={{ height: '100%' }}>
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='© OpenStreetMap contributors'
      />
      {sensors.map(sensor => {
        const reading = readings.get(sensor.sensorId);
        return (
          <CircleMarker
            key={sensor.sensorId}
            center={[sensor.latitude, sensor.longitude]}
            radius={10}
            fillColor={reading ? getAqiColor(reading.aqi) : '#gray'}
            fillOpacity={0.8}
            stroke={false}
          >
            <Popup>
              <SensorPopupContent sensor={sensor} reading={reading} />
            </Popup>
          </CircleMarker>
        );
      })}
    </MapContainer>
  );
};

// AQI color scale
const getAqiColor = (aqi: number): string => {
  if (aqi <= 50) return '#00e400';   // Good — green
  if (aqi <= 100) return '#ffff00';  // Moderate — yellow
  if (aqi <= 150) return '#ff7e00';  // Sensitive — orange
  if (aqi <= 200) return '#ff0000';  // Unhealthy — red
  if (aqi <= 300) return '#8f3f97';  // Very Unhealthy — purple
  return '#7e0023';                   // Hazardous — maroon
};
```

### ESG KPI Dashboard Components
```typescript
interface EsgMetricCardProps {
  indicator: string;
  value: number;
  unit: string;
  target: number;
  trend: 'improving' | 'stable' | 'degrading';
  category: 'environmental' | 'social' | 'governance';
}

export const EsgMetricCard: React.FC<EsgMetricCardProps> = ({
  indicator, value, unit, target, trend, category,
}) => {
  const progress = Math.min((value / target) * 100, 100);
  const statusColor = trend === 'improving' ? 'success' : trend === 'stable' ? 'warning' : 'error';

  return (
    <Card>
      <CardContent>
        <Typography variant="overline" color={`${category}.main`}>{category}</Typography>
        <Typography variant="h5">{value} <small>{unit}</small></Typography>
        <Typography variant="caption">Target: {target} {unit}</Typography>
        <LinearProgress
          variant="determinate"
          value={progress}
          color={statusColor}
          sx={{ mt: 1 }}
        />
        <TrendChip trend={trend} />
      </CardContent>
    </Card>
  );
};
```

### Alert Rule Builder
```typescript
interface AlertRule {
  sensorType: 'AIR_QUALITY' | 'WATER_QUALITY' | 'NOISE' | 'FLOOD';
  metric: string;
  operator: 'greater_than' | 'less_than' | 'equals' | 'between';
  threshold: number | [number, number];
  alertLevel: 'P0_EMERGENCY' | 'P1_WARNING' | 'P2_ADVISORY' | 'P3_INFO';
  notificationChannels: string[];
}

export const AlertRuleBuilder: React.FC<AlertRuleBuilderProps> = ({ rule, onChange }) => {
  const sensorMetrics = useSensorMetrics(rule.sensorType);

  return (
    <Stack spacing={2}>
      <SensorTypeSelect value={rule.sensorType} onChange={v => onChange({ ...rule, sensorType: v })} />
      <MetricSelect metrics={sensorMetrics.data} value={rule.metric} onChange={v => onChange({ ...rule, metric: v })} />
      <ThresholdInput rule={rule} onChange={onChange} />
      <AlertLevelSelect value={rule.alertLevel} onChange={v => onChange({ ...rule, alertLevel: v })} />
      <NotificationChannelPicker selected={rule.notificationChannels} onChange={v => onChange({ ...rule, notificationChannels: v })} />
    </Stack>
  );
};
```

### Time-Series Sensor Chart (recharts)
```typescript
export const SensorHistoryChart: React.FC<{ sensorId: string; hours?: number }> = ({
  sensorId,
  hours = 24,
}) => {
  const { data, isLoading } = useSensorHistory(sensorId, hours);

  if (isLoading) return <Skeleton variant="rectangular" height={200} />;

  return (
    <ResponsiveContainer width="100%" height={200}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="timestamp" tickFormatter={t => format(new Date(t), 'HH:mm')} />
        <YAxis />
        <Tooltip labelFormatter={t => format(new Date(t), 'dd/MM HH:mm')} />
        <ReferenceLine y={150} stroke="#ff7e00" strokeDasharray="3 3" label="Unhealthy" />
        <Line type="monotone" dataKey="aqi" stroke="#1565C0" dot={false} />
      </LineChart>
    </ResponsiveContainer>
  );
};
```

## BPMN AI Workflow Designer

```typescript
import BpmnModeler from 'bpmn-js/lib/Modeler';

export const UrbanWorkflowDesigner: React.FC<BpmnDesignerProps> = ({ processKey, onSave }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const modelerRef = useRef<BpmnModeler | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;
    modelerRef.current = new BpmnModeler({
      container: containerRef.current,
      additionalModules: [AiDecisionTaskExtension, SensorTriggerExtension],
    });
    loadDiagram(modelerRef.current, processKey);
    return () => modelerRef.current?.destroy();
  }, [processKey]);

  return (
    <Box sx={{ height: '600px', border: '1px solid', borderColor: 'divider' }}>
      <WorkflowToolbar onSave={() => modelerRef.current?.saveXML({ format: true }).then(({ xml }) => onSave({ processKey, xml }))} />
      <div ref={containerRef} style={{ height: 'calc(100% - 48px)' }} />
    </Box>
  );
};
```

## Performance Checklist

- [ ] React.memo() cho sensor card lists (re-render on WebSocket data)
- [ ] useCallback/useMemo cho map marker calculations
- [ ] Lazy loading (`React.lazy`) cho large dashboard packages
- [ ] React Query staleTime phù hợp với sensor update frequency
- [ ] Virtualization (react-virtual) cho sensor lists >200 items
- [ ] Map clustering (Leaflet.markercluster) khi >500 sensor markers
- [ ] Bundle analyzer: `yarn build --report`

## TypeScript Interfaces (Smart City Core)

```typescript
type AqiLevel = 'GOOD' | 'MODERATE' | 'SENSITIVE' | 'UNHEALTHY' | 'VERY_UNHEALTHY' | 'HAZARDOUS';
type AlertLevel = 'P0_EMERGENCY' | 'P1_WARNING' | 'P2_ADVISORY' | 'P3_INFO';
type SensorType = 'AIR_QUALITY' | 'WATER_QUALITY' | 'NOISE' | 'FLOOD' | 'TRAFFIC' | 'ENERGY';

interface SensorReading {
  sensorId: string;
  sensorType: SensorType;
  latitude: number;
  longitude: number;
  districtCode: string;
  timestamp: string;  // ISO datetime
  metrics: Record<string, number>;  // {"aqi": 125, "pm25": 45.2}
  aqiLevel?: AqiLevel;
}

interface EsgReport {
  quarter: string;  // "2025-Q1"
  environmental: EnvironmentalMetrics;
  social: SocialMetrics;
  governance: GovernanceMetrics;
  iso37120Score: number;  // 0–100
  generatedAt: string;
}
```

Docs reference: `docs/frontend/`, `docs/api/`, `docs/design-system/`
