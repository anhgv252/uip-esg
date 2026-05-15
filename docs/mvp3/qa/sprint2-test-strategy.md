# Sprint MVP3-2 — Test Strategy

**Sprint:** MVP3-2 (2026-05-19 → 2026-05-30)
**QA Owner:** QA Lead
**Status:** ACTIVE

---

## Sprint Goal

Analytics Foundation & ClickHouse Go-Live: Ship analytics-service cutover with live ClickHouse queries, deliver Analytics Dashboard MVP with Flink enrichment pipeline operational.

---

## Test Scope

### In Scope

| Area | Stories | Test Level |
|------|---------|-----------|
| ClickHouse ReplacingMergeTree dedup | TD-01 | Unit + IT + Manual |
| OffsetsInitializer fix (no data loss) | TD-01 | IT |
| CrossBuildingAggregation IT coverage | TD-02 | IT (Testcontainers) |
| Flink kill/restart checkpoint recovery | TD-03 | E2E automated |
| Kong health check + restart | TD-05 | Manual + Script |
| analytics-service cutover (feature flag) | v3-EXT-04 | Manual + Runbook |
| ClickHouse Client + Analytics Queries | v3-BE-03 | Unit + IT + Performance |
| Analytics Dashboard (charts) | v3-FE-03 | Manual + E2E |
| Aggregation Filters | v3-FE-04 | Manual + E2E |
| Flink ClickHouse Enrichment | v3-BE-04 | IT + Performance |

### Out of Scope (Sprint 3+)

- ARIMA forecast validation (Sprint 3)
- BMS integration testing (Sprint 4)
- Mobile app testing (Sprint 5)
- Building safety structural monitoring (Sprint 6)

---

## Quality Gates (10/10 Required for HARD PASS)

| Gate | Criteria | Verification | Owner |
|------|----------|--------------|-------|
| G1 | Analytics Dashboard Live | PO UAT sign-off + 4h smoke test | Frontend |
| G2 | ClickHouse Deduplication | ReplacingMergeTree verified, no dups | Backend |
| G3 | Flink Checkpoint Recovery | E2E kill/restart, 0 event loss | QA |
| G4 | CrossBuilding Coverage ≥85% | SonarQube report | QA |
| G5 | Integration Test Coverage ≥25% | Coverage report | QA |
| G6 | Zero P0/P1 bugs | JIRA query | All |
| G7 | Shadow 72h Delta <0.01% | Analytics query comparison | Backend + QA |
| G8 | Tier-1 Regression 103/103 | CI/CD regression suite | QA |
| G9 | Load Test ≥10k events/sec | JMeter/load test report | QA |
| G10 | Code Review all stories approved | GitHub review logs | Tech Lead |

### Conditional Pass Thresholds

| Gate | Borderline | Action |
|------|-----------|--------|
| G4 | 82-84% | Extend coverage Wed-Thu; <82% escalate CTO |
| G7 | 0.01-0.05% | Root cause analysis + CTO sign-off |
| G9 | 8k-10k events/sec | Investigate Flink bottleneck; <8k escalate |

---

## Test Levels

### Automated Tests

#### Unit Tests
- EsgDualSinkJob: extractBuildingId, TenantIdValidator integration
- ClickHouseEnergyRepository: query building, SQL correctness
- EnergyAggregateService: aggregation logic
- Analytics filters: GroupBy auto-restrict logic

#### Integration Tests (Testcontainers)
- CrossBuildingAggregationServiceIT: 17 test cases (AGG-IT-01 to AGG-IT-17)
- ClickHouseEnergyRepositoryIT: ClickHouse Testcontainers
- TenantIsolationIT: RLS verification

#### E2E Tests
- Flink kill/restart checkpoint recovery (TD-03)
- Kong restart test script
- Frontend E2E: Analytics Dashboard navigation + data load

### Manual Test Cases (15 TC)

| TC | Title | Area | Priority |
|----|-------|------|----------|
| TC-S2-01 | Analytics Dashboard loads with live data | v3-FE-03 | CRITICAL |
| TC-S2-02 | Dashboard responsive — Desktop 1920px | v3-FE-03 | HIGH |
| TC-S2-03 | Dashboard responsive — Tablet 768px | v3-FE-03 | HIGH |
| TC-S2-04 | Dashboard responsive — Mobile 375px | v3-FE-03 | HIGH |
| TC-S2-05 | Filter: Date range pre-sets (7d, 30d, 90d) | v3-FE-04 | HIGH |
| TC-S2-06 | Filter: Building multi-select (max 10) | v3-FE-04 | HIGH |
| TC-S2-07 | Filter: GroupBy auto-restrict by date range | v3-FE-04 | HIGH |
| TC-S2-08 | Filter: URL state persistence (shareable link) | v3-FE-04 | MEDIUM |
| TC-S2-09 | ClickHouse query vs TimescaleDB consistency | v3-BE-03 | CRITICAL |
| TC-S2-10 | Cutover: analytics-service receives traffic | v3-EXT-04 | CRITICAL |
| TC-S2-11 | Cutover rollback: monolith resumes analytics | v3-EXT-04 | CRITICAL |
| TC-S2-12 | Flink enrichment: enriched events in CH | v3-BE-04 | HIGH |
| TC-S2-13 | Lighthouse performance ≥90 | v3-FE-03 | HIGH |
| TC-S2-14 | Accessibility: keyboard nav + screen reader | v3-FE-03 | MEDIUM |
| TC-S2-15 | Rapid filter clicks: no state corruption | v3-FE-04 | MEDIUM |

---

## Performance Test Plan

### Load Test Scenario (G9)

- **Target:** ≥10,000 events/sec sustained
- **Duration:** 5 minutes sustained
- **Tool:** JMeter or custom script
- **Metrics:** throughput, p50/p95/p99 latency, error rate, CPU/memory

### Query Performance (v3-BE-03)

| Query | Dataset | Target |
|-------|---------|--------|
| getEnergyByBuilding | 1M rows | <1s p99 |
| getEmissionsByTenant | 1M rows | <1s p99 |
| getAQITrend | 1M rows | <1s p99 |
| Cross-building aggregate | 10M rows | <2s p95 |

### Frontend Performance (v3-FE-03)

| Metric | Target |
|--------|--------|
| Lighthouse Performance | ≥90 |
| First Contentful Paint | <2s |
| 25 filter combos load | <3s (cached <500ms) |

---

## Shadow Validation Plan

### 72h Shadow Delta Check (G7)

| Metric | Threshold | Frequency |
|--------|-----------|-----------|
| Row count diff | <0.01% | Daily |
| Value sum diff (kWh) | <0.01% relative | Daily |
| Error rate analytics-service | <0.1% | Continuous |
| Latency ratio (svc/mono) | <1.5x | Continuous |

If delta >0.01% detected: escalate to CTO immediately.

---

## Defect Classification

| Severity | Response | Sprint Impact |
|----------|----------|---------------|
| P0 (data leak, security, data loss) | Block sprint, fix same day | Gate blocker |
| P1 (functional regression) | Fix within sprint | Gate blocker |
| P2 (minor functional) | Backlog, Sprint 3 | Non-blocking |
| P3 (cosmetic, UX) | Nice-to-have | Non-blocking |

---

## Coverage Targets

| Module | Current | Target |
|--------|---------|--------|
| CrossBuildingAggregationService | 39% | ≥85% |
| ClickHouseEnergyRepository | ~60% | ≥90% |
| EnergyAggregateService | ~70% | ≥85% |
| EsgDualSinkJob | ~50% | ≥80% |
| Project-wide | ~78% | ≥82% |
| Integration test coverage | ~15% | ≥25% |
