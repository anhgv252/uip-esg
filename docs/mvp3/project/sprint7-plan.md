# Sprint MVP3-7 — Master Plan

**Status:** APPROVED — PO Planning 2026-06-02
**Document Date:** 2026-06-02 (updated with gap analysis)
**Sprint Start:** 2026-06-16 (Mon)
**Sprint End:** 2026-06-27 (Fri EOD)
**Gate Review:** 2026-06-27 15:00 SGT
**Sprint trước:** MVP3-6 — GATE PASS (Tier 1 + Tier 2 ALL DONE) | 1,107 tests | 86% coverage
**PO:** anhgv

---

## Context

Sprint 6 hoàn thành AI Innovation + Mobile Foundation (Tier 1 + Tier 2 ALL DONE, 60.5 SP delivered, 1,107 tests, 86% coverage). Có 2 P1 bugs open trên deployed environment cần fix ngay Sprint 7 Day 1-2.

**PO quyết định (planning 2026-06-02):**
- **Sprint 7 focus:** Building Safety + Avro Schema Registry + Pilot Readiness — sprint cuối trước City Authority pilot
- **Building Safety Backend:** Flink CEP với Welford algorithm cho structural vibration analysis (TCVN 9386:2012 + ISO 4866 thresholds)
- **Building Safety UI:** Real-time gauge + trend chart + alert integration
- **Avro Schema Registry:** Apicurio + 4 topic migration (dual-publish Phase 1)
- **ESG PDF Export:** GRI-formatted report generation (P1)
- **Mobile Enhancement:** Dashboard + Alerts screens native (P1)
- **Pilot Readiness:** 100+ regression test cases + SLA gate + Executive demo script
- **Target:** City Authority pilot demo đầu tháng 8

**⚠️ CRITICAL Safety Constraint (BR-010):** Structural P0 alert = **operator review ONLY** — KHÔNG auto-evacuate, KHÔNG auto-execute. Mọi P0 structural alert phải được operator xác nhận trước khi hành động.

**Carry-over từ Sprint 6:**
- **BUG-2026-06-01-002:** ESG scope-gated permission bypass — user không có `esg:write` vẫn generate được report (P1, 2 SP)
- **Analytics service recovery:** 10 regression tests failing do analytics offline (P1, 2 SP)
- **Forecast Redis cache eviction:** CacheEvict khi trigger forecast, tránh stale NONE result (P2, 1 SP)
- **Native device test iOS/Android:** PKCE deep-link + push token test trên native device (P2, 3 SP)
- **8 MINOR SA findings:** B-MIN-1..3, F-MIN-1..2, FE-MIN-1..3 (P2, 5 SP consolidated)
- **E2E flakiness:** 4 tests (17% flakiness rate) cần stabilize (P2, 2 SP)
- **BMS ITs supplement:** 5 edge case ITs deferred (P2, 2 SP)

---

## 1. Sprint Overview

| Dimension | Value |
|---|---|
| **Sprint Name** | MVP3-7: Building Safety + Avro + Pilot Readiness |
| **Duration** | 2026-06-16 (Mon) → 2026-06-27 (Fri) — 10 calendar days |
| **Team** | 5 FTE (Backend 2, Frontend 1, QA 1, DevOps 1) + SA spike |
| **Net Capacity** | ~47 SP (59 SP - 20% buffer) |
| **Committed** | ~76 SP (Tier 1 + Tier 2) |
| **Over-commit** | +29 SP (62%) — aggressive, cắt theo Tier 1/2/3 triage nghiêm ngặt |

> **76 SP vs 47 SP capacity (+29 SP).** Aggressive commit. Tier 1 (51 SP) = PHẢI DONE cho pilot. Tier 2 (25 SP) = best-effort. Tier 3 (12 SP) = descope. Cut order đã định.

---

## 2. Sprint Goal (SMART)

Team sẽ đạt **HARD PASS** by 2026-06-27 15:00 SGT bằng cách:

1. **Carry-over P0 fixes** — ESG permission bypass + analytics recovery DONE by Day 2
2. **Building Safety Backend** — Flink CEP + Welford algorithm (TCVN 9386:2012 thresholds), P0 = operator review only (BR-010)
3. **Building Safety UI** — Safety gauge + vibration trend chart + sensor status grid + alert banner
4. **Avro Schema Registry** — Apicurio deployed, 4 topics dual-publish Phase 1 (producer side)
5. **Pilot Regression** — 100+ test cases PASS, SLA gate verified
6. **Executive Demo Script** — 15 phút City Authority dry-run video
7. **ESG PDF Export** — GRI 302-1/305-4 formatted PDF generation (Tier 2)
8. **BMS Command ACK + SSE** — Real-time device command acknowledgment (Tier 2)
9. **Mobile Dashboard + Alerts** — Native screens với live data (Tier 2)
10. **Regression maintain** — 1,200+ tests PASS, 0 failures

---

## 3. Backlog Committed

### Tier 1 — PHẢI DONE (51 SP)

#### Epic 0: Carry-over P0 Fixes [4 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S7-C01 | Fix ESG permission bypass — check `esg:write` scope trước generate report | 2 | Backend-1 | P0 | User KHÔNG có `esg:write` → 403 Forbidden; regression test PASS |
| S7-C02 | Recover analytics service — investigate offline root cause, fix + verify 10 regression tests | 2 | DevOps | P0 | `curl analytics:8082/actuator/health` → UP; 82/82 regression PASS |

**Deadline:** 2026-06-17 (Day 2) — BLOCK demo PO nếu không xong

#### Epic 1: Building Safety Backend [13 SP]

**Threshold values (TCVN 9386:2012 + ISO 4866):**

| Sensor | Warning | Critical | Unit |
|--------|---------|----------|------|
| STRUCTURAL_VIBRATION | 10 | 50 | mm/s |
| STRUCTURAL_TILT | 3 | 10 | mrad |
| STRUCTURAL_CRACK | 0.3 | 2.0 | mm |

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S7-B01 | SA Spike: Welford algorithm + Flink CEP design cho structural vibration | 2 | SA | P0 | **ADR-034** Welford CEP approved; prototype verified. Welford: skip alerts khi n<1000 (cold start), pre-seed từ historical data trên restart |
| S7-B02 | VibrationAnomalyJob (Flink CEP) — 3 consecutive spikes > baseline+4σ within 10s → alert | 5 | Backend-2 | P0 | Unit tests (boundary values) + integration test PASS. Flink CEP pattern verified |
| S7-B03 | BuildingSafetyService — safety score 0-100, alert correlation, Redis cache TTL 5min | 3 | Backend-2 | P0 | Safety score 0-100, cache TTL 5min, IT with Testcontainers |
| S7-B04 | REST API — GET /buildings/{id}/safety, GET /buildings/{id}/vibration/readings | 2 | Backend-2 | P0 | API contract match frontend, RLS + tenant filter |
| S7-B05 | Kafka integration — topic `UIP.structural.alert.critical.v1`, P0 escalation <15s | 1 | Backend-2 | P0 | P0 alert → FCM/APNs push + Email city authority (cooldown 1 phút). **BR-010: operator review only, KHÔNG auto-evacuate** |

**Migration:** V31__structural_sensor_types.sql — seed 6 structural sensor types (vibration/tilt/crack × warning/critical)

#### Epic 2: Building Safety UI [8 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S7-FE01 | SafetyScoreGauge — 0-100 score, color zones (green/amber/red/offline) + react-window virtualization nếu >50 sensors | 2 | Frontend | P0 | Gauge renders with real API data, responsive |
| S7-FE02 | SafetyTrendChart — recharts 24h sparkline, threshold markers at 10/50 mm/s, zoom | 2 | Frontend | P0 | Chart with confidence intervals, 24h default range |
| S7-FE03 | Building Detail Page — safety tab + SafetySensorStatusGrid + SafetyAlertBanner (sticky P0/P1) | 3 | Frontend | P0 | Tab navigation, data loading states, error handling |
| S7-FE04 | Safety Alert Integration — structural alerts in alert list + map overlay, severity badge | 1 | Frontend | P0 | Safety alerts appear with correct severity |

#### Epic 3: Avro Schema Registry [8 SP] — NEW from gap analysis

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S7-B08 | Deploy Apicurio Schema Registry + configure Kafka serializers/deserializers | 3 | Backend-1 + DevOps | P0 | Apicurio healthy, schema validation active |
| S7-B09 | Producer dual-publish — 4 topics publish Avro v2 song song JSON v1 | 3 | Backend-1 | P0 | Topics: `UIP.iot.sensor.reading.v1`, `UIP.iot.bms.reading.v1`, `UIP.flink.alert.detected.v1`, `UIP.flink.analytics.hourly-rollup.v1`. BACKWARD compat CI green |
| S7-B10 | Kafka topic registry update + Avro schema versioning docs | 1 | Backend-1 | P0 | `kafka-topic-registry.xlsx` updated, Avro schemas registered |
| S7-B11 | Consumer migration (Phase 2) — migrate consumers sang Avro v2 | 1 | Backend-2 | P0 | All 4 consumers reading Avro v2, JSON v1 deprecated |

**Migration strategy (không breaking):**
```
Phase 1 (Week 1): Producer dual-publish JSON v1 + Avro v2
Phase 2 (Week 2): Consumer migrate sang Avro v2
Phase 3 (end Sprint 7): Deprecate v1 (retention = 1 day)
```

#### Epic 4: Pilot Readiness [18 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S7-QA01 | Pilot regression suite — 100+ test cases covering all MVP3 features | 5 | QA + Tester | P0 | 100+ cases documented, automated where possible |
| S7-QA02 | SLA gate verification — structural <15s, cross-building <2s, ESG <30s, mobile <3s, Kong p99 <100ms | 2 | QA | P0 | JMeter/k6 scenarios PASS, report attached |
| S7-PM01 | Pilot readiness gate checklist — 25 items + OWASP 0 Critical findings | 2 | PM + SA | P0 | Checklist 25/25 PASS, OWASP scan clean |
| S7-QA03 | E2E flakiness fix — stabilize 4 failing E2E tests | 2 | QA | P0 | 34/34 Playwright PASS, 0 flakiness |
| S7-OPS01 | Deployment runbook — production deploy + rollback + 6 incident scenarios + pilot deployment guide | 3 | DevOps | P0 | Runbook reviewed, dry-run PASS, 6 incident scenarios documented |
| S7-OPS02 | Monitoring verification — Prometheus alerts + Grafana dashboards for pilot | 2 | DevOps | P0 | All panels render real data, alert rules fire correctly |
| S7-PM02 | Executive demo script v2 — 15 phút City Authority dry-run video | 3 | PM + SA | P0 | Video recorded, approved by PM, City Authority sign-off ready |

**k6 Performance Scenarios (must pass before pilot):**
```
bms_ingestion:     1,667 events/sec sustained 5 phút
analytics_users:   500 VU cross-building dashboard (ramp 2m→10m→5m→3m)
mobile_operators:  200 VU constant 20 phút

Thresholds:
  ESG summary p95:        <150ms
  Cross-building p95:     <500ms
  ClickHouse query p95:   <1,000ms
  Mobile alerts p95:      <100ms
  Structural alert <15s:  P0 path
  Error rate:             <0.01%
```

### Tier 2 — BEST EFFORT (25 SP)

#### Epic 5: ESG PDF Export [5 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S7-B06 | PDF generation service — GRI 302-1 (energy) + 305-4 (carbon), chart embedding | 3 | Backend-1 | P1 | PDF generates with charts, tables, GRI-compliant |
| S7-FE05 | PDF download UI — button on ESG page, download progress, preview | 2 | Frontend | P1 | Click → download PDF, loading state |

#### Epic 6: BMS Command ACK + SSE [3 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S7-B07 | BMS Command ACK — Kafka consumer for `bms.command.ack`, update device status | 2 | Backend-1 | P1 | Command sent → ACK received → status updated |
| S7-FE06 | SSE integration for BMS — real-time device status updates in UI | 1 | Frontend | P1 | Device status auto-refresh via SSE |

#### Epic 7: Mobile Enhancement [13 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S7-FE07 | Mobile Dashboard — KPI cards + mini charts, shared hooks | 4 | Frontend | P1 | Dashboard renders with real API, responsive |
| S7-FE08 | Mobile Alerts — alert list + detail + filter by severity | 3 | Frontend | P1 | Alerts list with pull-to-refresh, filter chips |
| S7-FE09 | Mobile Push — foreground notification handling + deep-link | 1 | Frontend | P1 | Push received → navigate to alert detail |
| S7-QA04 | Native device test — iOS + Android PKCE + push token | 3 | Tester | P1 | Tests PASS on physical device |
| S7-QA05 | Mobile regression — 20 test cases for mobile screens | 2 | QA | P1 | 20/20 PASS |

### Tier 3 — DESCOPE IF NEEDED

| ID | Story | SP | Priority |
|---|---|---|---|
| S7-FE10 | Mobile Control Panel — device control from mobile | 5 | P2 |
| S7-C03 | 8 MINOR SA findings consolidated | 5 | P2 |
| S7-C04 | BMS ITs supplement (5 edge cases) | 2 | P2 |

---

## 4. Cut Order (nếu chậm tiến độ)

```
1. S7-B07/S7-FE06  BMS Command ACK + SSE        (3 SP)
2. S7-FE04         Safety Gauge decorative        (1 SP)
3. S7-FE10         Mobile Control Panel           (5 SP)
4. S7-B06/S7-FE05  ESG PDF Export                 (5 SP)
5. S7-FE07-FE09    Mobile Enhancement             (8 SP)
```

**Mục tiêu tối thiểu:** Tier 1 (51 SP) = pilot demo viable.

---

## 5. Milestones

| Date | Milestone | Gate |
|------|-----------|------|
| **2026-06-16 (Mon)** | Sprint 7 Kickoff — carry-over P0 fixes bắt đầu | |
| **2026-06-17 (Tue)** | Carry-over P0 DONE — ESG bypass + analytics recovered | GATE-0 |
| **2026-06-18 (Wed)** | SA Spike Welford complete — ADR-034 approved | GATE-1 |
| **2026-06-20 (Fri)** | Building Safety Backend first draft + Avro Apicurio deployed | |
| **2026-06-22 (Sun)** | Building Safety UI + Backend integration + Avro Phase 1 | GATE-2 |
| **2026-06-24 (Tue)** | Avro Phase 2 consumer migration + ESG PDF + BMS ACK | |
| **2026-06-25 (Wed)** | Pilot regression run (100+) + SLA gate | GATE-3 |
| **2026-06-26 (Thu)** | SA Code Review + OWASP scan + Pilot Readiness Gate + Executive demo video | GATE-4 |
| **2026-06-27 (Fri)** | Sprint 7 Close — Gate Review 15:00 SGT | **FINAL GATE** |

---

## 6. Risk Register

| ID | Risk | Probability | Impact | Owner | Mitigation |
|----|------|------------|--------|-------|------------|
| R-01 | Welford cold start → false negative structural alert (n<1000) | MEDIUM (40%) | HIGH | SA | SA spike Day 1-2, pre-seed from historical data, document limitation |
| R-02 | Frontend bottleneck — Safety UI + Mobile + ESG PDF = 22 SP cho 1 dev | HIGH (50%) | HIGH | PM | Prioritize Safety UI > ESG PDF > Mobile. Cut Mobile nếu cần |
| R-03 | Over-commit 1.62x — 76 SP vs 47 SP capacity | HIGH (65%) | HIGH | PM | Tier 1/2/3 triage nghiêm ngặt. Cut order đã định |
| R-04 | Pilot readiness gate miss — regression flakiness 17% | MEDIUM (35%) | HIGH | QA | Fix flakiness Day 1-3 trước pilot run |
| R-05 | Analytics service root cause chưa xác định | MEDIUM (30%) | MEDIUM | DevOps | Investigate Day 1, fallback: redeploy nếu config issue |
| R-06 | City Authority pilot deadline — non-negotiable | LOW (15%) | CRITICAL | PM | Tier 1 PHẢI DONE trước 2026-06-26 |
| R-07 | Avro migration breaking existing consumers | MEDIUM (40%) | HIGH | Backend-1 | Dual-publish Phase 1, BACKWARD compat CI, rollback <5 phút |
| R-08 | Building Safety sensor data availability — cần vibration sensor seed data | LOW (20%) | MEDIUM | Backend-2 | Prepare seed script V31, mock data cho demo |
| R-09 | Redis cache stale forecast (NONE result) vẫn cần manual DEL | LOW (15%) | LOW | Backend-1 | CacheEvict implementation trong carry-over |
| R-10 | Keycloak realm config cho pilot — cần realm export/import | LOW (10%) | MEDIUM | DevOps | Test realm import trên staging trước |
| R-11 | OWASP scan finds Critical findings → BLOCK pilot | LOW (15%) | CRITICAL | SA | Scan early Day 8, remediation buffer Day 9 |
| R-12 | Executive demo video quality — cần City Authority approval | LOW (20%) | MEDIUM | PM | Draft Day 9, review + record Day 10 |

---

## 7. Team Assignments

### Backend-1 (2 SP Tier 1 carry-over + 8 SP Tier 1 Avro + 5 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-2 | S7-C01: Fix ESG permission bypass | 2 |
| Day 3-5 | S7-B08: Apicurio Schema Registry deploy + serializers | 3 |
| Day 5-7 | S7-B09: Producer dual-publish 4 topics | 3 |
| Day 7-8 | S7-B10: Registry docs + S7-B11: Consumer migration support | 2 |
| Day 8-9 | S7-B06: ESG PDF Export backend | 3 |
| Day 9-10 | S7-B07: BMS Command ACK + cache eviction | 2 |

### Backend-2 (13 SP Tier 1 Building Safety)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-2 | S7-B01: SA Spike Welford (pair với SA) | 2 |
| Day 3-5 | S7-B02: VibrationAnomalyJob (Flink CEP) | 5 |
| Day 5-7 | S7-B03: BuildingSafetyService + cache + V31 migration | 3 |
| Day 7-8 | S7-B04: REST API + S7-B05: Kafka + P0 escalation | 3 |
| Day 8-9 | S7-B11: Consumer Avro migration (pair Backend-1) | 1 |
| Day 9-10 | Buffer / integration testing | - |

### Frontend (8 SP Tier 1 + 13 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 3-4 | S7-FE01: SafetyScoreGauge + react-window | 2 |
| Day 4-5 | S7-FE02: SafetyTrendChart + threshold markers | 2 |
| Day 5-7 | S7-FE03: Building Detail Page + SensorStatusGrid + AlertBanner | 3 |
| Day 7-8 | S7-FE04: Safety Alert Integration | 1 |
| Day 8-9 | S7-FE05: PDF download UI + S7-FE06: BMS SSE | 3 |
| (best-effort) | S7-FE07-FE09: Mobile Enhancement | 8 |

### DevOps (2 SP Tier 1 carry-over + 5 SP Tier 1 infra + 3 SP Avro support)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-2 | S7-C02: Analytics service recovery | 2 |
| Day 3-4 | S7-B08 support: Apicurio Schema Registry Docker deploy | (included in B08) |
| Day 7-9 | S7-OPS01: Deployment runbook + 6 incident scenarios + pilot guide | 3 |
| Day 9-10 | S7-OPS02: Monitoring verification | 2 |

### QA + Tester (11 SP Tier 1 + 5 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-3 | S7-QA03: E2E flakiness fix | 2 |
| Day 5-8 | S7-QA01: Pilot regression suite (100+) | 5 |
| Day 8-9 | S7-QA02: SLA gate verification + k6 scenarios | 2 |
| Day 9-10 | S7-QA04: Native device test | 3 |
| (best-effort) | S7-QA05: Mobile regression | 2 |

### PM + SA (7 SP Tier 1)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-2 | SA Spike: Welford + **ADR-034** | 2 |
| Day 9-10 | S7-PM01: Pilot readiness gate + OWASP scan + S7-OPS01 review | 2 |
| Day 9-10 | S7-PM02: Executive demo script v2 + City Authority dry-run video | 3 |

---

## 8. Quality Gates

### Hard Gates (14) — ALL MUST PASS

| Gate | Criterion | Verifier | Status |
|------|-----------|----------|--------|
| G1 | Building Safety Backend: Flink CEP job + API + Kafka, P0 <15s latency | Unit + IT tests | ⬜ |
| G2 | Building Safety UI: Gauge + Chart + SensorStatusGrid + AlertBanner | Manual QA | ⬜ |
| G3 | Carry-over P0: ESG bypass fixed + analytics UP | Regression tests | ⬜ |
| G4 | Avro: Apicurio deployed, 4 topics dual-publish, BACKWARD compat CI green | CI | ⬜ |
| G5 | Pilot Regression: 100+ cases PASS | QA report | ⬜ |
| G6 | SLA Gate: all latency targets met (k6 scenarios) | JMeter/k6 report | ⬜ |
| G7 | E2E: 34/34 Playwright PASS | CI | ⬜ |
| G8 | Tests: 1,200+ total, 0 failures | CI | ⬜ |
| G9 | Coverage: LINE ≥86%, BRANCH ≥70% | JaCoCo | ⬜ |
| G10 | TypeScript: 0 errors (web + mobile) | tsc --noEmit | ⬜ |
| G11 | SA Code Review: APPROVED | SA | ⬜ |
| G12 | OWASP scan: 0 Critical findings | SA | ⬜ |
| G13 | Deployment runbook: reviewed (6 incident scenarios) | DevOps + PM | ⬜ |
| G14 | Pilot readiness gate: 25/25 items | PM + SA | ⬜ |

### Soft Gates (4) — Best Effort

| Gate | Criterion | Status |
|------|-----------|--------|
| GS1 | ESG PDF Export generates valid GRI report | ⬜ |
| GS2 | Mobile Dashboard + Alerts render on native device | ⬜ |
| GS3 | BMS Command ACK <5s end-to-end | ⬜ |
| GS4 | Avro Phase 2: consumers fully migrated to Avro v2 | ⬜ |

---

## 9. Dependencies

```
S7-C01/C02 (carry-over) ──→ unblocks ALL (Day 2)
S7-B01 (SA spike) ────────→ S7-B02 (Flink job) ──→ S7-B03 (Service) ──→ S7-B04 (API) ──→ S7-FE01..04 (UI)
S7-B08 (Apicurio deploy) ─→ S7-B09 (dual-publish) ──→ S7-B11 (consumer migration)
S7-B05 (Kafka) ───────────→ S7-QA01 (pilot regression)
S7-B07 (BMS ACK) ────────→ S7-FE06 (SSE UI)
S7-QA03 (flakiness) ─────→ S7-QA01 (regression)
S7-OPS01 (runbook) ──────→ S7-PM01 (pilot gate)
S7-PM01 (pilot gate) ────→ S7-PM02 (exec demo video)
```

---

## 10. Migration Version Map (Sprint 7)

> Sprint 6 đã deploy: V28 (AI workflow), V29 (alert location), V30 (FORCE RLS).
> Sprint 7 bắt đầu từ **V31**.

| Version | File | Story |
|---------|------|-------|
| V31 | `V31__structural_sensor_types.sql` | S7-B01 — seed 6 structural sensor types + alert rules |
| V32 | `V32__structural_safety_score.sql` | S7-B03 — building_safety_scores table + RLS |

---

## 11. Kafka Topic Registry Update

| Topic | Producer | Consumer | Migration |
|-------|----------|----------|-----------|
| `UIP.structural.alert.critical.v1` | Flink VibrationAnomalyJob | monolith (AlertService) | **NEW** Sprint 7 |
| `UIP.iot.sensor.reading.v1` → v2 (Avro) | iot-ingestion-service | monolith, Flink, analytics | Avro dual-publish |
| `UIP.iot.bms.reading.v1` → v2 (Avro) | iot-ingestion-service | Flink, analytics | Avro dual-publish |
| `UIP.flink.alert.detected.v1` → v2 (Avro) | Flink | monolith | Avro dual-publish |
| `UIP.flink.analytics.hourly-rollup.v1` → v2 (Avro) | Flink | analytics-service | Avro dual-publish |

---

## 12. Tenant Isolation Tests — Structural Module

| Scenario | Test ID | What to verify |
|----------|---------|----------------|
| Structural alert isolation | ISO-008 | Tenant A không thấy structural alert Tenant B |
| Safety score isolation | ISO-009 | Safety score chỉ cho tenant's buildings |
| Vibration reading RLS | ISO-010 | Sensor readings RLS-filtered by tenant_id |

---

*Document prepared by UIP Project Manager — 2026-06-02*
*Updated: Gap analysis — added Avro Schema Registry, Executive demo script, BR-010 constraint, OWASP gate, V31+ migration, ADR-034, threshold values, k6 scenarios*
*Previous: [Sprint 6 Plan](sprint6-plan.md) | Detail Plan ref: [detail-plan.md](detail-plan.md) Section 8*
