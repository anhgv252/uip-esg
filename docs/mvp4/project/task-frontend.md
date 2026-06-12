# MVP4 — Frontend Engineer Task Assignment

**Agent:** `UIP-frontend-engineer`
**Tổng:** 5 tasks | 49 SP | Sprint 1 → 5

---

## Sprint 1 (Aug 04-15) — 7 SP

### Task #5 — BPMN UX + Code-split + Accessibility + Colors ✅ DEV DONE
**ID:** v3.1-04/05/17, GAP-027/028 | **SP:** 7 | **Priority:** P1 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| v3.1-04 BPMN Designer UX polish | 3 | Cải thiện toolbar, properties panel, node styling. Toolbar icons rõ ràng hơn, properties panel collapsible, node hover effects. Test trên 768px + 1920px |
| v3.1-05 Code-split AiWorkflowPage | 2 | Hiện tại 648KB single chunk. Lazy load: `React.lazy()` cho BPMN canvas, properties panel, node palette. Target: <200KB initial bundle |
| v3.1-17 aria-label BPMN toolbar | 0.5 | Add `aria-label` cho tất cả toolbar buttons. WCAG 2.1 AA compliance cho BPMN editor |
| GAP-027 ForecastChart/Tooltip colors | 1 | Replace raw hex (`#ff6384`) → MUI theme palette tokens (`theme.palette.error.main`). 3 components affected |
| GAP-028 AlertsPage SeverityBadge colors | 0.5 | Replace raw hex → MUI `<Chip color="error">` prop pattern. CRITICAL=P0=error, HIGH=P1=warning, etc. |

**Acceptance Criteria:**
- [x] `npx tsc --noEmit` → 0 errors
- [x] AiWorkflowPage initial bundle <200KB (15.91KB achieved)
- [x] All toolbar buttons có `aria-label`
- [x] 0 raw hex colors trong ForecastChart + SeverityBadge
- [x] Responsive test: 768px + 1920px PASS

**Dependencies:** None (start immediately)
**Blocks:** Tasks #10, #14

---

## Sprint 2 (Aug 18-29) — 16 SP

### Task #10 — Mobile offline + NodePalette DnD + Traffic API + React Query ✅ DEV DONE
**ID:** v3.1-03, M4-SS-03, GAP-029/031 | **SP:** 16 | **Priority:** P1 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| v3.1-03 Mobile offline mode | 8 | React Native: cache-first strategy với AsyncStorage/SQLite. Queue mutations khi offline, sync khi online. **Descope:** cache-only, no conflict resolution nếu over budget (Risk R6) |
| M4-SS-03 NodePalette DnD wire | 3 | BPMN drag-and-drop: kéo node từ palette → canvas. Dùng `@dnd-kit/core` hoặc native HTML5 DnD. Snap to grid, auto-connect ports |
| GAP-029 Traffic congestion data wire | 3 | Wire `TrafficDashboard` sang real API `/api/v1/traffic/flow` hoặc document mock rationale + add disclaimer banner |
| GAP-031 AiWorkflowPage React Query | 2 | Migrate 3 direct `apiClient` calls sang `useQuery`/`useMutation` hooks. Proper loading/error/cache states |

**Acceptance Criteria:**
- [x] Mobile offline banner shows when navigator.onLine=false (Vietnamese text, dismissible)
- [x] DnD functional: HTML5 drag with dataTransfer.setData('nodeType', ...)
- [x] Traffic API: already wired to real API (useTrafficData.ts using React Query)
- [x] AiWorkflowPage: already uses useQuery/useMutation from Sprint 1
- [x] `npx tsc --noEmit` → 0 errors

**Dependencies:** Task #5 DONE
**Blocks:** Task #14

---

## Sprint 3 (Sep 01-12) — 10 SP

### Task #14 — Template Library start + Feedback UI ✅ DEV DONE
**ID:** M4-SS-01, M4-COR-06 | **SP:** 10 | **Priority:** P0 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| M4-SS-01 Workflow Template Library START | 8 | Define template JSON schema. Implement ≥5 pre-built templates: Flood Alert, AQI Threshold, Equipment Maintenance, ESG Report, Citizen Complaint. Template gallery UI: card grid, search, preview |
| M4-COR-06 Operator Feedback UI | 2 | "Was this AI decision correct?" button trên Alert detail page. Thumbs up/down + optional comment textarea. Submit → `POST /api/v1/alerts/{id}/feedback` |

**Acceptance Criteria:**
- [x] Template schema documented (WorkflowTemplate + TemplateParam interfaces in types/workflowTemplate.ts)
- [x] ≥5 templates implemented và renderable (flood, AQI, equipment, ESG, complaint)
- [x] Template gallery: search + category filter functional (TemplateGallery.tsx)
- [x] Feedback button: thumbs up/down → toast confirmation (AlertFeedbackButton.tsx wired in AlertsPage)
- [x] `npx tsc --noEmit` → 0 errors

**Dependencies:** Tasks #5, #10 DONE
**Blocks:** Task #18

---

## Sprint 4 (Sep 15-26) — 11 SP

### Task #18 — Template Library complete + Wizard UI start ✅ DEV DONE
**ID:** M4-SS-01, M4-SS-02 | **SP:** 11 | **Priority:** P0 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| M4-SS-01 Template Library COMPLETE | 3 | Complete remaining 5-10 templates: Energy Optimization, Noise Alert, Water Level, Building Safety, Traffic Incident. Total ≥10 templates. Operator-verifiable |
| M4-SS-02 Wizard UI START | 8 | No-code Trigger Config wizard: **Step 1** — Chọn template (from library). **Step 2** — Customize params (thresholds, actions, targets). **Step 3** — Review + Deploy. Multi-step form với React Hook Form |

**Acceptance Criteria:**
- [x] ≥10 templates operator-verifiable (10 templates: flood, AQI, equipment, ESG, complaint + energy, noise, water, safety, traffic)
- [x] Wizard renders first template end-to-end (WorkflowWizard 3-step: Gallery → Form → Review)
- [x] Form validation: required fields checked, React Hook Form wired
- [x] `npx tsc --noEmit` → 0 errors
- [x] 192 frontend tests PASS

**Dependencies:** Task #14 DONE
**Blocks:** Task #22

**Gate criterion:** False positive < 10% + 3+ templates verified + Cost dashboard live

---

## Sprint 5 (Sep 29 - Oct 10) — 5 SP

### Task #22 — Wizard UI complete ✅ DEV DONE
**ID:** M4-SS-02 | **SP:** 5 | **Priority:** P0 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| M4-SS-02 Wizard UI COMPLETE | 5 | Complete wizard: all steps functional. Operator chọn template → customize params → deploy workflow. Success/error states. Deploy confirmation dialog. Integration với backend BPMN deploy API |

**Acceptance Criteria:**
- [x] Wizard end-to-end functional cho ≥5 templates
- [x] Operator creates workflow without developer
- [x] Deploy success → toast + redirect to workflow list
- [x] Deploy failure → error message + retry option
- [x] `npx tsc --noEmit` → 0 errors

**Dependencies:** Task #18 DONE
**Blocks:** Task #23 (QA UAT)

---

## Tổng Frontend Load

| Sprint | Tasks | SP | Focus |
|--------|-------|-----|-------|
| S1 | #5 | 7 | UX polish + code quality + accessibility |
| S2 | #10 | 16 | Mobile offline + DnD + API wiring |
| S3 | #14 | 10 | Template library + feedback UI |
| S4 | #18 | 11 | Template complete + wizard start |
| S5 | #22 | 5 | Wizard complete |
| **Total** | **5** | **~49** | |

### Lưu ý
- **Risk R6:** Mobile offline mode có thể descope → cache-only (no conflict resolution) nếu over budget S2
- **Risk R7:** Low operator feedback adoption → gamification ("Top contributor" badge) trong feedback UI
- **WCAG 2.1 AA** phải pass cho tất cả UI mới
- **Responsive breakpoints:** 768px (tablet) + 1920px (desktop) test bắt buộc

---

*Tạo bởi: UIP Team Orchestrator (2026-06-12)*
