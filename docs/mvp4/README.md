# MVP4 — AI Scale + Correlation Engine + Operator Self-Service

**Trạng thái:** ✅ DEMO COMPLETED
**Ngày lập kế hoạch:** 2026-06-11
**Ngày demo hoàn thành:** 2026-06-18
**Sprint start dự kiến:** 2026-08-04 (sau pilot go-live)
**Target:** Tier 2 pilot stabilized + AI scale to 10,000 sensors + Operator self-service
**Goal:** UIP sẵn sàng phục vụ 50+ buildings với AI cost < $1/ngày và false positive < 5%

---

## 1. Executive Summary

| Mục tiêu | Target | Confidence |
|-----------|--------|------------|
| AI cost cho 10K sensors | < $1/ngày (hiện ~$50) | 90% |
| False positive rate | < 5% (hiện ~20%) | 85% |
| Operator self-service workflows | 80% không cần developer | 80% |
| iOS App Store submission | App live | 75% |
| K8s readiness (staging) | Helm charts validated | 70% |
| Production uptime (pilot) | ≥99.5% | 95% |

**3 Critical Success Factors:**
1. District-level Flink batching phải pass load test 10K sensors trước khi bật AI cho production
2. Pilot Phase feedback phải thu thập đủ 30 ngày dữ liệu trước khi tuning correlation engine
3. iOS cert submission phải hoàn thành Sprint 1 — mobile là primary channel cho operator

---

## 2. MVP4 Scope — 3 Trụ chính

### Trụ 1: AI Scale & Cost Optimization (~26 SP)

**Vấn đề:** 10,000 sensors × 1 AI call/event = 600,000 calls/phút = $50/ngày — không khả thi.

| ID | Feature | SP | Sprint | Mô tả |
|----|---------|-----|--------|-------|
| M4-AI-01 | District-level Flink batching | 5 | S2 | Group events by districtCode + 60s window trước AI call. Giảm 600K → 50 calls/phút |
| M4-AI-02 | Model routing (Haiku/Sonnet) | 3 | S2 | `aiModelTier` field trong TriggerConfig. Tier 1 → Haiku (nhanh/rẻ), Tier 2 → Sonnet (chính xác) |
| M4-AI-03 | Smart pre-filter | 5 | S3 | Rule-based filter xử lý 80% cases, chỉ escalate uncertain (confidence < 0.7) đến AI |
| M4-AI-04 | AI response caching (Redis) | 3 | S3 | Cache AI response cho same district/AQI range trong 5 phút. Deduplicate calls |
| M4-AI-05 | Token budgeting per scenario | 2 | S2 | `maxTokens` config trong TriggerConfig. Prompt optimization |
| M4-AI-06 | Cost dashboard (Grafana) | 3 | S4 | Panel tracking AI token usage + cost per tenant per day. Chargeback model |
| M4-AI-07 | Welford Universal anomaly | 5 | S3-4 | Extend Welford từ structural → ALL sensor types. Anomaly-first, no hardcoded thresholds |

**Kết quả mục tiêu:** $0.60/ngày cho 10K sensors (83x giảm)

### Trụ 2: Multi-Device Correlation Engine (~26 SP)

**Vấn đề:** Mỗi sensor trigger riêng lẻ → false positive 20%, operator bị alert fatigue.

| ID | Feature | SP | Sprint | Mô tả |
|----|---------|-----|--------|-------|
| M4-COR-01 | IncidentCorrelationFlinkJob | 8 | S3-4 | Flink CEP window 30s per building, detect 3+ sensor types triggering cùng lúc → 1 incident |
| M4-COR-02 | Correlated payload builder | 5 | S4 | Merge N sensor events thành unified AI payload. Full context cho AI decisions |
| M4-COR-03 | BMS auto-command (POC) | 5 | S5 | AI decides EVACUATE → auto-send BMS command (HVAC_OFF, SPRINKLER_ON). **SAFETY: require operator confirm** |
| M4-COR-04 | BMS bidirectional feedback loop | 8 | S5-6 | AI decision → BMS command → BMS feedback → AI confirmation. Closed-loop automation |
| M4-COR-05 | Baseline drift detection | 3 | S4 | AQI baseline rising → auto-adjust anomaly thresholds. Adaptive over time |
| M4-COR-06 | Operator feedback capture | 2 | S3 | "Was this AI decision correct?" button trên Alert page. Training data accumulation |
| M4-COR-07 | Incident Feedback Loop | 5 | S5-6 | 30-day data → AI analyzes patterns → generates improved trigger suggestions |

**Kết quả mục tiêu:** False positive < 5%, "smart intervention" thay vì chỉ monitoring

### Trụ 3: Operator Self-Service (~16 SP)

**Vấn đề:** Mỗi workflow cần developer viết BPMN XML → bottleneck khi scale.

| ID | Feature | SP | Sprint | Mô tả |
|----|---------|-----|--------|-------|
| M4-SS-01 | Workflow Template Library | 8 | S3-4 | 10-15 pre-built templates: Flood Alert, AQI Threshold, Equipment Maintenance, ESG Report, etc. |
| M4-SS-02 | No-code Trigger Config wizard UI | 8 | S4-5 | Form-based: "When trigger? → What action?" UI. Operator chọn template → customize params → deploy |
| M4-SS-03 | NodePalette DnD wire (carry-over) | 3 | S2 | BPMN drag-and-drop vào canvas. Hoàn thiện từ MVP3 deferred item |

**Kết quả mục tiêu:** 80% workflows operator tự tạo, không cần developer

---

## 3. v3.1 Carry-Over — Gộp Sprint 1-2 (~72 SP)

### Pre-Pilot Fixes (P0) — Trước 2026-08-04 (~8 SP)

| ID | Task | SP | Owner | Mô tả |
|----|------|-----|-------|-------|
| P0-1 | FCM/APNs credentials config | 2 | DevOps | Configure real push notification credentials cho pilot |
| P0-2 | Override CHANGE_ME passwords | 1 | DevOps | Set strong passwords trong .env.staging |
| P0-3 | Resource limits (mem_limit) | 1 | DevOps | Add limits cho all services trong docker-compose.ha.yml |
| P0-4 | Rebuild Flink JAR | 0.5 | DevOps | Rebuild stale JAR (06-06) |
| P0-5 | Performance benchmark staging | 2 | QA | Run perf_benchmark.py on staging |
| P0-6 | Chaos engineering suite | 1 | DevOps | Run run-all-chaos.sh validation |
| P0-7 | Correct coverage claims | 0.5 | PM | Fix 86%→76.3% trong materials |

### v3.1 Tech Debt — Sprint 1-2 (~64 SP)

| ID | Task | SP | Owner | Sprint |
|----|------|-----|-------|--------|
| v3.1-01 | iOS certificate submission | 1 | DevOps | S1 |
| v3.1-02 | Android APK store submission | 2 | DevOps | S1 |
| v3.1-03 | Mobile offline mode | 8 | Frontend | S1-2 |
| v3.1-04 | BPMN Designer UX polish | 3 | Frontend | S1 |
| v3.1-05 | Code-split AiWorkflowPage (648KB) | 2 | Frontend | S1 |
| v3.1-06 | REST Assured API contract tests | 8 | QA | S1-2 |
| v3.1-07 | Pact contracts cho inter-service | 5 | QA | S2 |
| v3.1-08 | 1000 VU JMeter performance scenario | 8 | QA | S2 |
| v3.1-09 | JWT validation IT (expired/tampered) | 3 | Backend | S1 |
| v3.1-10 | Rate limiter integration test | 2 | Backend | S1 |
| v3.1-11 | SQL injection test | 2 | Backend | S1 |
| v3.1-12 | Analytics-service coverage ≥50% | 5 | Backend | S1-2 |
| v3.1-13 | Audit Kafka consumers DLQ coverage | 3 | Backend | S1 |
| v3.1-14 | Global error response OpenAPI spec | 5 | Backend | S2 |
| v3.1-15 | Externalize Kong JWT config (JWKS) | 3 | DevOps | S2 |
| v3.1-16 | Mask email PII in logs | 1 | Backend | S1 |
| v3.1-17 | Add aria-label BPMN toolbar | 0.5 | Frontend | S1 |

### P1 Backend Fixes — Từ SA/Backend/Frontend Review (~35 SP)

> **Nguồn:** Gap analysis từ 12 tài liệu review (SA Tech Debt, Backend Review, Frontend Review, QA Assessment, DevOps Assessment, EA Assessment)

| ID | Task | SP | Owner | Sprint | Nguồn |
|----|------|-----|-------|--------|-------|
| GAP-005 | **BacnetIpAdapter.sendCommand() — implement real execution** (hiện là stub log-only) | 5 | Backend | S1 | SA ARCH-01, Backend P2-4 |
| GAP-006 | **calculateWaterIntensity() — implement ISO 37120** (hiện trả null) | 5 | Backend | S2 | Backend M1 |
| GAP-007 | **CO2 emission factor configurable** (hardcoded 0.5 kg/kWh) | 1 | Backend | S1 | Backend M3 |
| GAP-009 | **MqttPublisher.publishCommand() — fix race condition** | 2 | Backend | S1 | Backend P2-6 |
| GAP-010 | **gRPC integration test vs real analytics-service** | 3 | Backend | S2 | SA CQ-05 |
| GAP-011 | **Clear 4 TODO markers trong production code** | 1 | Backend | S1 | SA CQ-02 |
| GAP-013 | **@Deprecated NotificationController — remove or document migration** | 1 | Backend | S2 | SA DEPREC-01 |
| GAP-020 | **EnvironmentController tests** (0 tests hiện tại) | 3 | Backend | S1 | QA Gap-7 |
| GAP-021 | **TrafficController tests** (0 tests hiện tại) | 3 | Backend | S1 | QA Gap-8 |
| GAP-022 | **bms.mqtt coverage** (21% → target 60%) | 3 | Backend | S2 | QA Critical |
| GAP-023 | **kafka.producer error paths** (22% → target 60%) | 2 | Backend | S2 | QA High |
| GAP-031 | **AiWorkflowPage — migrate 3 direct apiClient calls sang React Query** | 2 | Frontend | S2 | Code Review M10 |
| GAP-033 | **TenantContextFilter — refactor extractJsonField() sang ObjectMapper** | 1 | Backend | S1 | Backend P2-7 |
| GAP-034 | **SecurityConfig — convert sang @RequiredArgsConstructor** | 0.5 | Backend | S1 | Backend P3-8 |
| GAP-036 | **Flink deployment automation** (Makefile target + CI) | 2 | DevOps | S2 | EA R7 |
| GAP-037 | **Avro schema registration automation** (bootstrap script) | 1 | DevOps | S2 | EA R8 |

### P1 Frontend Fixes (~10 SP)

| ID | Task | SP | Owner | Sprint | Nguồn |
|----|------|-----|-------|--------|-------|
| GAP-027 | ForecastChart/Tooltip — raw hex → MUI theme colors | 1 | Frontend | S1 | Frontend M2 |
| GAP-028 | AlertsPage SeverityBadge — raw hex → MUI color prop | 0.5 | Frontend | S1 | Frontend M3 |
| GAP-029 | Traffic congestion data — wire sang real API hoặc document mock | 3 | Frontend | S2 | Frontend M4 |
| GAP-026 | Sensor-to-alert E2E latency test (<30s SLA) | 2 | QA | S2 | QA Gap |
| GAP-016 | Parameterized tests cho AQI/flood/noise thresholds | 1 | QA | S1 | QA W1 |
| GAP-017 | Replace 12 Thread.sleep() → Awaitility | 2 | QA | S1 | QA W2 |

### P2 Deferred — Post-MVP4 hoặc theo nhu cầu

| ID | Task | Nguồn | Note |
|----|------|-------|------|
| GAP-039 | ClickHouse Keeper dedicated dashboard | SA INFRA-01 | Low priority, health checks có |
| GAP-040 | Proto-breaking-change CI (buf breaking) | SA PROTO-01 | gRPC contract safety |
| GAP-046 | SSL/TLS termination trước Kong | DevOps Rec-10 | Public HTTPS |
| GAP-053 | Mobile emoji → react-native-vector-icons | Frontend P2-5 | UX polish |
| GAP-055 | Mobile map screen | Frontend P2-11 | Nice-to-have |
| GAP-062 | Energy Forecast empty points fallback | Bug report | Edge case |

---

## 4. Sprint Plan (6 Sprints — Aug → Nov 2026)

### Sprint 1 (2026-08-04 → 08-15): Pilot Stabilize + v3.1 Start — ~50 SP

**Sprint Goal:** Pilot go-live ổn định + iOS/Android store + security hardening + BMS stub fix + testing foundation

| Team | Tasks | SP |
|------|-------|-----|
| Backend | v3.1-09 JWT IT + v3.1-10 Rate limiter IT + v3.1-11 SQL injection test + v3.1-13 DLQ audit + v3.1-16 PII mask + GAP-005 BMS sendCommand real + GAP-007 CO2 configurable + GAP-009 MQTT race fix + GAP-011 TODO markers + GAP-033 ObjectMapper refactor + GAP-034 SecurityConfig + GAP-020 EnvController tests + GAP-021 TrafficController tests | 22 |
| Frontend | v3.1-04 BPMN UX + v3.1-05 Code-split + v3.1-17 aria-label + GAP-027 ForecastChart colors + GAP-028 AlertsPage colors | 7 |
| DevOps | P0-1 FCM credentials + P0-2 Passwords + P0-3 Resource limits + P0-4 Flink rebuild + v3.1-01 iOS cert + v3.1-02 Android APK | 7.5 |
| QA | P0-5 Perf benchmark + P0-6 Chaos suite + P0-7 Coverage fix + v3.1-06 REST Assured (start) + GAP-016 Parameterized tests + GAP-017 Awaitility | 13 |
| PM | Pilot monitoring + stakeholder communication | 1 |

**Gate:** Pilot running 7 days without P0 incidents + iOS submitted + Android APK live

---

### Sprint 2 (2026-08-18 → 08-29): v3.1 Complete + AI Cost Foundation — ~55 SP

**Sprint Goal:** Hoàn thành v3.1 tech debt + AI batching foundation + backend code quality

| Team | Tasks | SP |
|------|-------|-----|
| Backend | v3.1-12 Analytics coverage + v3.1-14 OpenAPI errors + GAP-006 Water intensity + GAP-010 gRPC IT + GAP-013 NotificationController + GAP-022 BMS MQTT coverage + GAP-023 Kafka producer coverage + M4-AI-01 Flink batching + M4-AI-02 Model routing + M4-AI-05 Token budgeting | 25 |
| Frontend | v3.1-03 Mobile offline mode + M4-SS-03 NodePalette DnD + GAP-029 Traffic real API + GAP-031 AiWorkflow React Query | 16 |
| QA | v3.1-06 REST Assured (complete) + v3.1-07 Pact contracts + v3.1-08 JMeter 1000 VU + GAP-026 Sensor-to-alert latency test | 18 |
| DevOps | v3.1-15 Kong JWKS + GAP-036 Flink automation + GAP-037 Avro automation | 6 |

**Gate:** All v3.1 items DONE + AI batching verified with 10K simulated sensors + JMeter 1000 VU PASS

---

### Sprint 3 (2026-09-01 → 09-12): AI Optimization + Correlation Start — ~30 SP

**Sprint Goal:** AI cost đạt < $1/ngày + correlation engine foundation

| Team | Tasks | SP |
|------|-------|-----|
| Backend | M4-AI-03 Smart pre-filter + M4-COR-01 Flink CEP correlation job + M4-COR-06 Operator feedback capture | 12 |
| Frontend | M4-SS-01 Template library (start) + M4-COR-06 Feedback UI | 10 |
| Backend-2 | M4-AI-07 Welford Universal (start) | 3 |
| DevOps | M4-AI-04 Redis AI caching | 3 |

**Gate:** AI cost < $5/ngày @ 10K simulated sensors + Correlation job RUNNING

---

### Sprint 4 (2026-09-15 → 09-26): Correlation Engine + Self-Service — ~35 SP

**Sprint Goal:** Correlation engine production-ready + operator self-service MVP

| Team | Tasks | SP |
|------|-------|-----|
| Backend | M4-COR-01 Correlation job (complete) + M4-COR-02 Payload builder + M4-COR-05 Drift detection | 16 |
| Frontend | M4-SS-01 Template library (complete) + M4-SS-02 Wizard UI (start) | 11 |
| DevOps | M4-AI-06 Cost dashboard | 3 |
| QA | Correlation engine E2E test + Template library UAT | 5 |

**Gate:** False positive < 10% + 3+ templates verified + Cost dashboard live

---

### Sprint 5 (2026-09-29 → 10-10): BMS Automation + Self-Service Complete — ~30 SP

**Sprint Goal:** BMS closed-loop POC + operator wizard hoàn chỉnh

| Team | Tasks | SP |
|------|-------|-----|
| Backend | M4-COR-03 BMS auto-command + M4-COR-04 Feedback loop (start) | 13 |
| Frontend | M4-SS-02 Wizard UI (complete) | 5 |
| QA | BMS simulator integration + Wizard UAT | 5 |
| DevOps | Monitoring for BMS commands | 2 |

**Gate:** BMS auto-command with operator confirmation working + Wizard end-to-end

---

### Sprint 6 (2026-10-13 → 10-24): Feedback Loop + MVP4 Gate — ~25 SP

**Sprint Goal:** Incident Feedback Loop + declare MVP4 DONE

| Team | Tasks | SP |
|------|-------|-----|
| Backend | M4-COR-04 Feedback loop (complete) + M4-COR-07 Incident feedback + M4-AI-07 Welford (complete) | 16 |
| QA | Regression ≥1,500 tests + Performance gate 1000 VU + MVP4 gate review | 5 |
| PM | MVP4 Summary + Roadmap MVP5 + Stakeholder demo | 4 |

**Gate:** False positive < 5% + AI cost < $1/ngày + Operator self-service verified + **DECLARE MVP4 DONE**

---

## 5. Total Effort

| Phase | SP | Timeline |
|-------|-----|----------|
| Pre-Pilot Fixes (P0) | 8 | Jul 2026 |
| Sprint 1: Pilot Stabilize + v3.1 Start | 50 | Aug 04-15 |
| Sprint 2: v3.1 Complete + AI Foundation | 55 | Aug 18-29 |
| Sprint 3: AI Optimization + Correlation Start | 30 | Sep 01-12 |
| Sprint 4: Correlation + Self-Service | 35 | Sep 15-26 |
| Sprint 5: BMS Automation + Self-Service Complete | 30 | Sep 29 - Oct 10 |
| Sprint 6: Feedback Loop + Gate | 25 | Oct 13-24 |
| **Total** | **~255 SP** | **Aug → Oct 2026** |

---

## 6. Infrastructure Strategy

**Decision: Docker Compose + HA (no K8s migration trong MVP4)**

| Component | MVP3 (hiện tại) | MVP4 (giữ nguyên) | Khi nào chuyển K8s |
|-----------|----------------|-------------------|-------------------|
| Deployment | docker-compose.ha.yml | docker-compose.ha.yml (optimized) | >20 buildings hoặc Tier 3 customer |
| Resource Limits | ❌ Chưa có | ✅ mem_limit cho all services | K8s migration |
| Monitoring | Prometheus + Grafana | + AI Cost dashboard + Correlation metrics | Giữ Compose |
| Auto-scaling | ❌ Manual | ❌ Manual (pilot đủ) | K8s HPA khi cần |
| Secret Management | .env files | .env + externalized Kong JWKS | HashiCorp Vault khi K8s |

**Lý do:** Pilot 5 buildings không cần K8s overhead. Docker Compose + HA stack đã proven. K8s migration là MVP5 item khi scale >20 buildings.

---

## 7. KPIs & Targets

### Technical SLAs (MVP4)

| SLA | Target | Hiện tại (MVP3) |
|-----|--------|-----------------|
| AI cost per day (10K sensors) | < $1.00 | ~$50 (unoptimized) |
| False positive rate | < 5% | ~20% |
| Operator self-service adoption | ≥ 80% workflows | 0% (all need developer) |
| Correlated incident detection | < 60s | N/A (per-sensor only) |
| Pilot uptime | ≥ 99.5% | — |
| BMS command latency (auto) | < 5s | N/A (manual only) |

### Quality Gates (MVP4)

| Gate | Criterion |
|------|-----------|
| G1 | AI cost < $1/ngày @ 10K simulated sensors |
| G2 | False positive < 5% on 30-day pilot data |
| G3 | ≥ 10 workflow templates operator-verifiable |
| G4 | Regression ≥ 1,500 tests, 0 failures |
| G5 | 1000 VU JMeter performance PASS |
| G6 | iOS + Android apps live in stores |
| G7 | BMS auto-command with safety confirmation |
| G8 | SA Code Review APPROVED |
| G9 | OWASP 0 Critical, 0 High CVEs |
| G10 | Pilot uptime ≥ 99.5% for 30 consecutive days |

---

## 8. Risk Register

| ID | Risk | Severity | Prob | Mitigation | Owner |
|----|------|---------|------|-----------|-------|
| R1 | AI batching reduces accuracy — missed critical alerts | HIGH | 20% | Critical events (flood, fire) bypass batching, route trực tiếp đến AI | Backend |
| R2 | BMS auto-command safety — wrong command sent | CRITICAL | 10% | 2-step confirm: AI đề xuất → Operator approve → Execute. BR-010 safety constraint vẫn enforce | Backend |
| R3 | iOS Apple review rejects app | MEDIUM | 25% | Start Sprint 1 Day 1; Android APK as fallback; expedited review request | DevOps |
| R4 | Correlation engine complexity > estimate | MEDIUM | 30% | Reuse VibrationAnomalyJob CEP pattern; POC Sprint 3, full Sprint 4 | Backend |
| R5 | Pilot data insufficient for correlation tuning | LOW | 20% | Use synthetic data for initial tuning; real data refinement post-pilot | QA |
| R6 | Mobile offline mode complex, delays Sprint 2 | MEDIUM | 25% | Descope to cache-only (no conflict resolution) if over budget | Frontend |
| R7 | Operator feedback capture low adoption | LOW | 40% | Gamification: "Top contributor" badge; default prompt in alert detail | Frontend |

---

## 9. ADRs cần viết cho MVP4

| ADR | Title | Sprint | Mô tả |
|-----|-------|--------|-------|
| ADR-041 | AI Cost Optimization Strategy — District Batching + Model Routing + Caching | Sprint 2 | Luồng AI optimization, cost model, fallback khi batching miss |
| ADR-042 | Incident Correlation Engine — Flink CEP Multi-Device Pattern | Sprint 3 | CEP window strategy, correlation scoring, false positive reduction |
| ADR-043 | BMS Auto-Command Safety Protocol | Sprint 5 | 2-step confirm, safety constraints, rollback mechanism |
| ADR-044 | Operator Self-Service Architecture — Template + Wizard | Sprint 3-4 | Template schema, wizard flow, permission model |
| ADR-045 | Welford Universal — Adaptive Anomaly Detection | Sprint 3-4 | Extend từ structural sang all sensor types, cold-start strategy |
| ADR-046 | Incident Feedback Loop — Self-Improving AI | Sprint 5-6 | Feedback capture → analysis → prompt suggestion → A/B test |

---

## 10. Resource Plan

| Role | Sprint 1-2 (v3.1 + GAP fixes) | Sprint 3-4 (AI+Correlation) | Sprint 5-6 (Automation+Gate) |
|------|-------------------------------|------------------------------|------------------------------|
| Backend Eng 1 | JWT/Rate/SQL ITs + DLQ audit + BMS sendCommand + MQTT race + EnvCtrl/TrafficCtrl tests | AI batching + Model routing + Welford | BMS feedback loop + Incident feedback |
| Backend Eng 2 | Water intensity + CO2 config + Analytics coverage + gRPC IT + NotificationController + BMS/Kafka coverage + OpenAPI errors + TODOs + ObjectMapper + SecurityConfig | Correlation Flink job + Payload builder | BMS auto-command + Drift detection |
| Frontend Eng | BPMN polish + Code-split + Mobile offline + aria-label + Colors fix + Traffic API wire + AiWorkflow React Query | Template library + Feedback UI + NodePalette DnD | Wizard UI + Template verification |
| DevOps | FCM/APNs + Passwords + Limits + Flink rebuild + iOS/Android + Kong JWKS + Flink/Avro automation | Redis AI caching | Cost dashboard + Monitoring |
| QA | REST Assured + Pact + JMeter 1000 VU + Perf benchmark + Parameterized tests + Awaitility + Latency test | Correlation E2E + Template UAT | Regression + MVP4 gate |

**Total effort:** ~12 person-weeks | **Team:** 5 FTE + 0.5 PM = 5.5 FTE
**Delivered:** ~255 SP | **Timeline:** 12 weeks (Aug → Oct 2026)

---

## 11. Descope / Contingency Plan

**Nếu Sprint 2 v3.1 over budget:**
- Mobile offline mode → Sprint 3 (cache-only, no conflict resolution)
- JMeter 1000 VU → Sprint 3

**Nếu AI batching không đạt accuracy target:**
- Keep per-sensor AI cho critical events (flood, fire)
- District batching chỉ cho non-critical (AQI advisory, energy recommendations)

**Nếu Correlation engine complexity quá cao:**
- Descope M4-COR-03 BMS auto-command → v4.1
- Focus chỉ M4-COR-01 correlation detection + M4-COR-02 payload builder

**Nếu iOS Apple review reject:**
- Continue with Android APK + PWA
- iOS resubmit Sprint 3

---

## 12. Competitive Moat (Investor Value)

| Feature | Thời gian replicate | Giá trị |
|---------|---------------------|---------|
| Welford Universal anomaly | 3-6 months | Anomaly-first, không hardcoded thresholds |
| Incident Correlation Engine | 6-9 months | Multi-device → 1 incident, false positive < 5% |
| Incident Feedback Loop | 6-12 months | Self-improving AI decisions |
| NL→BPMN (MVP5) | 6-9 months | Vietnamese natural language → workflow |
| Template Library | 1-2 months | Operator self-service, quick time-to-value |

**Revenue projection:** $110K MRR by Q2 2027 with MVP4 features → **Series A trigger at $100K MRR**

---

## 13. Cấu trúc Tài liệu MVP4

| Thư mục | Nội dung |
|---------|---------|
| `project/` | Sprint plans chi tiết (S1-S6), demo scripts, roadmap |
| `docs/adr/` | **ADR-041 đến ADR-046** (nằm ở `docs/adr/` chuẩn repo, không phải `docs/mvp4/architecture/`). Xem ADR-041 AI Cost, ADR-042 Correlation, ADR-043 BMS Safety, ADR-044 Self-Service, ADR-045 Welford, ADR-046 Feedback Loop |
| `qa/` | Test strategies, performance plans *(chưa có nội dung — TODO: test-strategy.md)* |
| `reports/` | Sprint reviews, code reviews, gate review |
| `uat/` | UAT sign-off: sprint4 correlation/template, sprint5 BMS-safety/wizard |

---

*Tổng hợp bởi: SA + PM + BA (3 agents, 2026-06-11)*
*Inputs: Investor Q&A, SA Tech Debt Review, EA Assessment, Closeout Assessment, MVP2 Roadmap*
*Next: PO review + Sprint 1 planning after pilot go-live (2026-08-04)*
