# MVP3 Frontend Source Code Review -- 2026-06-11

**Reviewer**: FE Engineer (automated SA review)
**Scope**: Web frontend (`frontend/src/`) + Mobile app (`applications/operator-mobile/`)
**Method**: `tsc --noEmit`, file-by-file source code review, grep analysis

---

## 1. TypeScript Status

| Project | Result | Details |
|---|---|---|
| **Web Frontend** | PASS (0 errors) | `npx tsc --noEmit` -- clean |
| **Mobile App** | PASS (production code) | 11 errors all in `__mocks__/` (jest namespace not typed) -- zero errors in production code |

**Verdict**: Both codebases compile cleanly. The mock-only jest errors are cosmetic (test infrastructure).

---

## 2. Component Inventory

### Web Frontend (`frontend/src/`)

| Metric | Count |
|---|---|
| Total TypeScript files | 168 |
| `.tsx` components/pages | 97 |
| `.ts` hooks/api/types | 71 |
| API layer files | 17 |
| Custom hooks | 22 |
| Pages | 15 |

**Module Breakdown**:

| Module | Components | Pages |
|---|---|---|
| City Operations Center | SensorMap, AlertFeedPanel, SensorMarker, DistrictFilter | CityOpsPage |
| ESG Dashboard | EsgKpiCard, EsgBarChart, EsgPdfDownloadButton, ReportGenerationPanel, EsgReportPreview | EsgPage |
| Environment Monitoring | AqiGauge, AqiTrendChart, SensorStatusTable | EnvironmentPage |
| Alert System | SafetyAlertBanner, SafetyAlertHistory, SafetyScoreGauge, SafetySensorStatusGrid, SafetyTrendChart, BuildingSafetyMapOverlay | AlertsPage |
| BPMN/AI Workflow | WorkflowModeler, BpmnViewer, NodePalette, AiNodeConfigPanel, ProcessInstanceTable, InstanceDetailDrawer, FloodAlertCard, WaterLevelGauge, FloodRiskMapOverlay | AiWorkflowPage, WorkflowConfigPage |
| Buildings/BMS | BuildingDashboardSkeleton, MultiBuildingSelector, BuildingContextBar, CrossBuildingShell | BmsDevicesPage, CrossBuildingDashboardPage |
| Traffic | TrafficBarChart, IncidentTable | TrafficPage |
| Citizen | InvoicePage, CitizenNotificationsPage, CitizenProfilePage, CitizenRegisterPage | CitizenPage |
| Admin | ErrorRecordTable | AdminPage, TenantAdminPage |
| Forecast | ForecastChart, ForecastTooltip | (embedded in EsgPage) |
| Mobile (web responsive) | MobileLayout, MobileNav, MobileNotificationBanner, AqiGauge | -- |

### Mobile App (`applications/operator-mobile/`)

| Metric | Count |
|---|---|
| Total TypeScript files | 26 |
| Screens | 6 (Dashboard, Alerts, Controls, Profile, Login, TenantSelection) |
| Components | 4 (KpiCard, OfflineBanner, ControlConfirmModal, HighDangerConfirmModal) |
| Hooks | 8 |
| Services | 2 (SyncQueue, OfflineCache) |
| Contexts | 1 (AuthContext) |

---

## 3. Module-by-Module Review

### 3.1 City Operations Center

| Criterion | Status | Notes |
|---|---|---|
| Loading state | PASS | CircularProgress overlay during sensor data load |
| Error state | PASS | `Alert severity="warning"` when sensors fail to load |
| Empty state | N/A | Map always renders (sensors can be empty) |
| Map library | PASS | Leaflet via `react-leaflet` + `react-leaflet-cluster` -- correct per spec |
| Real-time SSE | PASS | `useMapSSE` hook with exponential backoff reconnect |
| Responsive | PASS | `useMediaQuery(breakpoints.down('md'))` -- switches to column layout on mobile |
| Accessibility | PARTIAL | No `aria-label` on map container; Leaflet tiles not accessible by nature |
| MUI theme tokens | PASS | All styling uses `sx` with theme tokens |
| TypeScript strict | PASS | No `any` in component logic (1 `as any` for Leaflet icon workaround -- acceptable) |

**Real-time Integration**: `useMapSSE` connects to `/api/v1/notifications/stream`, handles SENSOR_UPDATE and ALERT events, merges live data with REST-fetched data, implements exponential backoff (1s to 30s cap). Well-designed.

**Traffic Layer**: Congestion segments rendered as Polylines with color-coded severity. Currently uses mock data (`CONGESTION_MOCK`) -- needs real API when traffic module is live.

### 3.2 ESG Dashboard

| Criterion | Status | Notes |
|---|---|---|
| Loading state | PASS | Skeleton cards for KPI, CircularProgress for chart |
| Error state | PASS | `Alert severity="error"` for forecast failures |
| Empty state | PASS | "No data" fallback in EsgBarChart when data array is empty |
| GRI 302-1/305-4 | PASS | Referenced in workflow templates and api-types; ESG report generation uses these standards |
| PDF export | PASS | `EsgPdfDownloadButton` via POST `/esg/reports/pdf` -- blob download with `URL.revokeObjectURL()` |
| Excel export | PASS | `ReportGenerationPanel` supports XLSX download |
| Report generation | PASS | Async workflow: trigger -> poll status (3s interval) -> download when DONE |
| Responsive | PASS | `useMediaQuery(breakpoints.down('sm'))` -- toggle buttons go fullWidth on mobile |
| Accessibility | PASS | `aria-label` on download buttons; form labels on Year/Quarter selects |
| MUI theme tokens | PASS | All cards, papers, typography use theme tokens |
| Permission check | PASS | `useScope('esg:write')` gates report generation and PDF export |
| Energy Forecast | PASS | `ForecastChart` with anomaly detection markers, MAPE display, fallback indicator |

**Issues Found**:
- **[MEDIUM]** `EsgBarChart` uses hardcoded recharts colors (`COLORS` array) instead of theme palette. This is acceptable for chart libraries but noted for consistency.
- **[LOW]** `ForecastTooltip` and `ForecastChart` use raw hex colors extensively (`#3b82f6`, `#ef4444`, etc.) -- not using theme tokens.

### 3.3 Environment Monitoring

| Criterion | Status | Notes |
|---|---|---|
| Loading state | PASS | Skeleton rectangles for AQI gauges, loading table rows |
| Error state | PASS | Stale data indicator when `calculatedAt` > 30s old |
| Empty state | PASS | "No historical data" in AqiTrendChart, "No sensors found" in table |
| AQI visualization | PASS | EPA-standard gauge with 6 color zones, SVG needle overlay, recharts PieChart |
| Real-time alerts | PASS | `useNotificationSSE` for live alert snackbar |
| Responsive | PASS | Grid switches xs=6/sm=4/md=3 for gauge cards |
| Accessibility | PARTIAL | `aria-label="Data may be stale"` on stale indicator; no aria on gauge SVG |
| MUI theme tokens | PASS | All Box/Card/Paper styling via `sx` theme tokens |

**Issues Found**:
- **[LOW]** AQI gauge colors are hardcoded hex values matching EPA standard -- acceptable as they must match regulatory color codes.

### 3.4 Alert System (Safety)

| Criterion | Status | Notes |
|---|---|---|
| Loading state | PASS | Skeleton rows in SafetyAlertHistory, CircularProgress in alerts page |
| Error state | PASS | `Alert severity="error"` in page; "Failed to load alerts" message |
| Empty state | PASS | "No alerts found" in both table and card views |
| SSE integration | PASS | `useAlertStream` with exponential backoff (1s-30s), status indicator (Live/Connecting/Offline) |
| Severity colors | PASS | 4-level: CRITICAL(#b71c1c), HIGH(#f44336), MEDIUM(#ff9800), LOW(#4caf50) + text labels |
| BR-010 compliance | PASS | P0/CRITICAL alerts non-dismissible in SafetyAlertBanner (`onClose={undefined}`) |
| Acknowledge/Escalate/Resolve | PASS | Full workflow with permission checks via `useScope` |
| Responsive | PASS | Desktop: table; Mobile: card list (`useMediaQuery`) |
| Accessibility | PASS | `role="alert"`, `aria-live="assertive"` on critical banners; `aria-label` on severity/status chips |
| Module badges | PASS | Color-coded: STRUCTURAL(purple), FLOOD(blue), ENVIRONMENT(green), TRAFFIC(orange) |
| Bulk actions | PASS | Checkbox selection + bulk acknowledge with permission gate |

**Issues Found**:
- **[MEDIUM]** Severity colors in `AlertsPage` use raw hex (#4caf50, #ff9800, etc.) instead of MUI theme palette. However, the `AlertFeedPanel` in City Ops correctly uses MUI `color` prop ("default", "warning", "error").
- **[LOW]** `SafetyScoreGauge` uses hardcoded hex colors (#EF4444, #F59E0B, #22C55E) for score zones -- these are spec-mandated safety colors, so acceptable.

### 3.5 BPMN / AI Workflow Designer

| Criterion | Status | Notes |
|---|---|---|
| bpmn-js integration | PASS | `BpmnModeler` from `bpmn-js/lib/Modeler` for editing; `BpmnJS` for viewing |
| AI Decision nodes | PASS | `AiNodeConfigPanel` with prompt editor, confidence threshold slider, model selector |
| Node Palette | PASS | Draggable palette: Start, Service Task, AI Decision, Notification, End |
| Loading state | PASS | CircularProgress overlay during XML import |
| Error state | PASS | `Alert severity="error"` for XML parse failures |
| Empty state | PASS | "Select a process definition to view" in BpmnViewer |
| Templates | PASS | Built-in templates (ESG Report, Flood Alert, Building Safety, Air Quality Citizen Alert) |
| Properties panel | PASS | Element ID, Name, Type fields; name updates propagate to modeler |
| Export | PASS | BPMN XML export with `URL.revokeObjectURL()` cleanup |
| Instance management | PASS | ProcessInstanceTable with pagination + InstanceDetailDrawer with AI variable extraction |
| Accessibility | PARTIAL | `aria-label` on close buttons and drawer actions; node palette has `role="button"` + keyboard support; toolbar buttons lack `aria-label` |
| Memory cleanup | PASS | `modeler.destroy()` in useEffect cleanup; `URL.revokeObjectURL()` after export |

**Issues Found**:
- **[MEDIUM]** Toolbar IconButtons in WorkflowModeler lack `aria-label` attributes (Save, Undo, Redo, Delete, Zoom In/Out, Fit, Export). Only `data-testid` is present.
- **[LOW]** bpmn-js event listener cleanup uses comment "// bpmn-js doesn't provide off" -- the listeners may leak on re-render, though the modeler is singleton so practical impact is low.
- **[LOW]** Inline `<style>` block in WorkflowModeler for BPMN node colors -- works but not ideal for theme switching.

### 3.6 Buildings / BMS

| Criterion | Status | Notes |
|---|---|---|
| Loading state | PASS | `BuildingDashboardSkeleton` component |
| Multi-building | PASS | `MultiBuildingSelector` + `CrossBuildingShell` |
| BMS device commands | PASS | `useBmsDevices` + `useBmsCommandAck` with SSE for acknowledgment |
| Responsive | PASS | Grid xs=12/md=6 for dashboard panels |

### 3.7 Citizen Portal

| Criterion | Status | Notes |
|---|---|---|
| Notifications | PASS | `CitizenNotificationsPage` with SSE via `useNotificationSSE` |
| Invoice display | PASS | `InvoicePage` |
| Registration | PASS | `CitizenRegisterPage` with form validation |
| Responsive | PASS | Grid xs=12/sm=6 for form fields |

---

## 4. Mobile App Status

### Architecture

| Feature | Implementation | Quality |
|---|---|---|
| **Auth (Keycloak PKCE)** | `useAuthMobile` with 4-step flow: fetch config -> OIDC discovery -> PKCE prompt -> token exchange | PASS -- proper PKCE via `expo-auth-session` |
| **Offline mode** | Two-tier cache (`OfflineCache`): Tier-1 in-memory LRU (64 entries, 5min TTL), Tier-2 AsyncStorage (30min TTL) | PASS -- well-designed |
| **Sync Queue** | `SyncQueue` with AsyncStorage persistence, exponential backoff, conflict handling (409), max 3 retries, tenant header injection | PASS -- production-quality |
| **Push Notifications** | `usePushToken` via `expo-notifications`, registers with backend `/api/v1/push/subscribe` | PASS |
| **React Query** | QueryClient with retry=1, staleTime=30s; `useOfflineQuery` wrapper | PASS |
| **Navigation** | Bottom tabs: Dashboard, Alerts, Controls, Profile | PASS |
| **Tenant Selection** | `TenantSelectionScreen` before auth | PASS |

### Screen Quality

| Screen | Loading | Error | Empty | Notes |
|---|---|---|---|
| Dashboard | PASS (pull-to-refresh) | PASS (error banner + retry) | PASS (empty alerts state) | 4 KPI cards, sensor bar, recent alerts |
| Alerts | PASS (loading text) | PASS (error + retry) | PASS (checkmark empty state) | Severity/module filters, safety score |
| Login | PASS (ActivityIndicator) | PASS (error text) | N/A | Keycloak PKCE button |
| Controls | -- | -- | -- | Building controls with confirmation modals |
| Profile | -- | -- | -- | Settings/logout |

### Mobile Issues Found

- **[MEDIUM]** Raw hex colors throughout (React Native StyleSheet): `#1565C0`, `#F44336`, `#4CAF50`, etc. -- this is standard for React Native (no MUI theme system), so acceptable.
- **[LOW]** `DashboardScreen` uses emoji icons for KPI cards instead of proper icon library -- works but not professional.
- **[LOW]** No `accessibilityLabel` on mobile TouchableOpacities (filter chips, alert cards) -- RN accessibility gap.

---

## 5. API Integration Assessment

### Web Frontend API Layer

| API Module | Endpoints | Patterns |
|---|---|---|
| Auth (`auth.ts`) | Login, refresh, logout | `apiClient` with interceptors |
| ESG (`esg.ts`) | GET summary/energy/carbon, POST generate/pdf, GET status/download | useQuery for GET, useMutation for POST |
| Alerts (`alerts.ts`) | GET alerts, PUT acknowledge/escalate/resolve, GET rules, POST/DELETE rules | Proper REST patterns |
| CityOps (`cityops.ts`) | Composite: getSensorsForMap (parallel fetch), getRecentAlerts, getCongestionSegments (mock) | Good composition |
| Environment (`environment.ts`) | GET sensors, current AQI, AQI history | Standard useQuery |
| Workflow (`workflow.ts`) | GET definitions, instances, variables; POST start | useQuery + useMutation |
| BMS (`bms.ts`) | GET devices, POST commands | useQuery + useMutation |
| Analytics (`analytics.ts`) | GET emissions, energy, AQI trends, building breakdown | useQuery |
| Admin (`adminMgmt.ts`) | GET/POST/PUT error records | useQuery + useMutation |
| Citizen (`citizen.ts`) | GET invoices, notifications; POST complaints | useQuery + useMutation |
| Traffic (`traffic.ts`) | GET traffic data | useQuery |
| Push (`pushSubscription.ts`) | POST subscribe/unsubscribe | useMutation |

### API Client Quality

- **Auth interceptors**: Bearer token injection, silent refresh on 401 with request queuing, redirect to `/login` on refresh failure. Production-quality.
- **Tenant header**: `X-Tenant-ID` injected via interceptor from `tenantStore`.
- **Error handling**: `createApiError()` extracts `traceId`, `timestamp`, `path` from backend error responses.
- **Token storage**: In-memory + localStorage with proper cleanup on logout.

### Generated Types

- `api-types.ts` (8550+ lines) from OpenAPI spec -- properly used in alerts, API layer.
- Type narrowing pattern: `Required<Pick<...>>` + `Omit<...>` for AlertEvent -- ensures required fields are typed correctly.

---

## 6. React Query Patterns Audit

| Pattern | Count | Compliance |
|---|---|---|
| `useQuery` for GET | 44 calls | PASS -- all read operations use useQuery |
| `useMutation` for POST/PUT/DELETE | 21 calls | PASS -- all write operations use useMutation |
| `useQueryClient` for invalidation | Used in all mutations | PASS -- proper cache invalidation after mutations |
| `refetchInterval` for polling | Sensors (60s), AQI (30s), Alerts (30s) | PASS -- appropriate intervals |
| `enabled` flag for conditional queries | ESG energy/carbon toggle, report status poll | PASS |
| `staleTime` configuration | ESG (5min), others default | PASS |
| SSE for real-time | `useAlertStream`, `useMapSSE`, `useNotificationSSE`, `useBmsCommandAck` | PASS -- 4 separate SSE hooks |

---

## 7. UX / Accessibility Issues

### Summary

| Check | Result |
|---|---|
| `aria-label` usage (web components) | 37 instances -- good coverage on buttons, chips, gauges |
| `role` attributes | 118 instances -- tables, grids, alerts, status |
| `aria-live` regions | Used on SafetyAlertBanner (`assertive`) -- correct for critical alerts |
| Form labels | All `FormControl` + `InputLabel` pairs properly linked |
| WCAG 2.1 AA | Partial -- some toolbar buttons missing aria-label; map components inherently inaccessible |
| Color-only indicators | Severity uses color + text label + icon -- PASS |
| Keyboard navigation | NodePalette supports Enter/Space; tab navigation works in forms |

### Specific Issues

| Severity | Component | Issue |
|---|---|---|
| MEDIUM | WorkflowModeler toolbar | 8 IconButtons missing `aria-label` (Save, Undo, Redo, Delete, Zoom In/Out, Fit, Export) |
| MEDIUM | ForecastChart/ForecastTooltip | All styling via raw hex (#3b82f6, #ef4444, etc.) -- not theme-aware |
| LOW | SensorMap | Map container has no `aria-label` or `role` |
| LOW | Mobile filter chips | No `accessibilityLabel` on TouchableOpacity elements |
| LOW | AqiGauge/BpmnViewer | SVG needle/viewer not accessible to screen readers |

---

## 8. Raw Hex Color Audit

| Area | Raw Hex Count | Severity |
|---|---|---|
| AQI color constants (EPA standard) | ~15 | Acceptable -- regulatory colors |
| Severity colors (safety/alert) | ~12 | MEDIUM -- should use theme palette |
| Forecast components | ~10 | MEDIUM -- should use theme tokens |
| Chart colors (recharts) | ~8 | LOW -- chart library limitation |
| Citizen notification severity | ~5 | LOW -- similar to alert severity |
| Bpmn-js node styling (inline CSS) | ~8 | LOW -- third-party override |
| **Total** | **~58** | |

---

## 9. Match vs Nghiệp Vu (Business Requirements)

| Module | Business Need | Match % | Notes |
|---|---|---|---|
| **City Operations Center** | Real-time map of HCMC sensors, district filter, alert feed | **95%** | Full Leaflet map, SSE live updates, traffic overlay (mock data for traffic). Viewport persistence in sessionStorage. |
| **ESG Dashboard** | Energy/Water/Carbon KPIs, GRI 302-1/305-4, PDF/XLSX export | **90%** | KPI trends, chart by building, report generation workflow, PDF/XLSX download. GRI references in workflow templates. Missing: GRI labels directly on KPI cards. |
| **Environmental Monitoring** | AQI monitoring, sensor status, 24h trends | **95%** | EPA-standard AQI gauge, stale data detection, trend chart with threshold lines, sensor table. |
| **Alert System** | Multi-severity alerts, ack/escalate/resolve workflow, SSE real-time | **95%** | Full lifecycle, BR-010 P0 non-dismissible, bulk actions, SSE with status indicator, permission-gated actions. |
| **BPMN/AI Workflow** | Visual designer, AI decision nodes, process instances | **85%** | bpmn-js modeler + viewer, AI node config (prompt/threshold/model), templates, instance management. AI execution log shown in AiWorkflowPage. Gap: drag-from-palette-to-canvas not wired (palette `onNodeSelect` not connected to modeler canvas). |
| **Building Safety** | Safety score, structural alerts, sensor grid | **90%** | SafetyScoreGauge, SafetyAlertBanner (BR-010), SafetySensorStatusGrid, BuildingSafetyMapOverlay. |
| **Mobile App** | Operator dashboard, alerts, offline, push, Keycloak PKCE | **80%** | Solid foundation: PKCE auth, offline cache, sync queue, push registration, 4 KPI cards, alert list with filters. Gaps: no map view, no building detail screen, controls screen is basic. |
| **Citizen Portal** | Invoices, notifications, complaints, registration | **85%** | Invoice display, live notifications via SSE, registration form. Complaint submission implemented. |
| **Admin** | Error records, alert rules, tenant management | **90%** | ErrorRecordTable, alert rule CRUD, tenant settings page. |

---

## 10. Issues Found

### Critical (0)

None found.

### High (0)

None found.

### Medium (5)

| # | Module | Issue | Recommendation |
|---|---|---|---|
| M1 | Workflow | Toolbar IconButtons (8x) missing `aria-label` -- WCAG failure | Add `aria-label` to all toolbar buttons |
| M2 | Forecast | ForecastChart + ForecastTooltip use only raw hex colors -- not theme-aware, breaks in dark mode | Replace with theme tokens or CSS variables |
| M3 | Alerts | AlertsPage SeverityBadge uses raw hex colors instead of MUI `color` prop | Use `color="error/warning/success"` like AlertFeedPanel does |
| M4 | CityOps | Traffic congestion data is hardcoded mock (`CONGESTION_MOCK`) | Wire to real traffic API when available; document as known limitation |
| M5 | Workflow | NodePalette `onNodeSelect` not connected to WorkflowModeler -- palette drag does not add nodes to canvas | Wire DnD events to bpmn-js modeling API |

### Low (8)

| # | Module | Issue |
|---|---|---|
| L1 | BPMN | Inline `<style>` for node colors -- does not respect theme mode |
| L2 | BPMN | eventBus listener cleanup skipped ("bpmn-js doesn't provide off") -- minor memory leak |
| L3 | SensorMap | Leaflet icon workaround uses `as any` cast (1 instance) -- unavoidable |
| L4 | Dashboard hook | `useDashboard.ts` catch block uses `error: any` -- should use `unknown` |
| L5 | Mobile | Emoji icons in KpiCard instead of icon library (react-native-vector-icons) |
| L6 | Mobile | No `accessibilityLabel` on TouchableOpacity filter chips and alert cards |
| L7 | AqiGauge | SVG needle/gauge not accessible to screen readers |
| L8 | Citizen | Severity colors in CitizenNotificationsPage use raw hex instead of theme |

---

## 11. Recommendations

### Priority 1 (Pre-demo)
1. Add `aria-label` to WorkflowModeler toolbar IconButtons (8 items, 15 min fix)
2. Replace raw hex severity colors in AlertsPage with MUI `color` prop
3. Verify ESG PDF export works end-to-end with real backend

### Priority 2 (Sprint 12)
4. Wire NodePalette drag events to bpmn-js canvas for true DnD workflow design
5. Replace ForecastChart/ForecastTooltip raw hex with theme-aware tokens
6. Add `accessibilityLabel` to mobile TouchableOpacity elements
7. Add real traffic API integration (replace CONGESTION_MOCK)

### Priority 3 (Backlog)
8. Refactor BPMN inline styles to CSS module or styled component
9. Add bpmn-js eventBus listener cleanup pattern
10. Mobile: replace emoji icons with react-native-vector-icons
11. Mobile: add map screen for building/sensor visualization

---

## 12. Summary Statistics

| Metric | Value |
|---|---|
| Total components reviewed | 97 (web) + 10 (mobile) |
| TypeScript errors (production) | 0 |
| useQuery calls | 44 |
| useMutation calls | 21 |
| SSE hooks | 4 |
| aria-label instances | 37 |
| Raw hex color violations | ~58 |
| Critical issues | 0 |
| High issues | 0 |
| Medium issues | 5 |
| Low issues | 8 |
| Overall code quality | **Strong** -- well-structured, consistent patterns, good error/loading/empty coverage |

---

*Report generated: 2026-06-11 | Next action: Address M1-M5 before MVP3 demo*
