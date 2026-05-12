# Sprint MVP3-1 — Test Strategy

**Sprint:** MVP3-1 (2026-05-12 → 2026-05-25)  
**QA Owner:** QA Engineer  
**Status:** ACTIVE

---

## Sprint Goal

Multi-building RLS isolation + ClickHouse POC foundation + analytics-service shadow deploy + Cross-building dashboard skeleton

---

## Test Scope

### In Scope

| Area | Stories | Test Level |
|------|---------|-----------|
| Building entity + V26 migration | v3-BE-01 | Unit + IT |
| Cross-building aggregation API | v3-BE-02 | Unit + IT + Manual |
| RLS isolation scenarios | v3-BE-01 | Manual (SQL + API) |
| Capability flag (Tier 1/2) | v3-EXT-02 | Unit |
| ClickHouse POC health | v3-DevOps-01 | Manual smoke |
| Frontend building selector | v3-FE-01/02 | Manual |

### Out of Scope (Sprint 2+)

- Full 10M row performance benchmark (Sprint 2 gate)
- analytics-service shadow validation 72h (Sprint 1 end gate)
- Kong rate-limit testing (Sprint 4)

---

## Quality Gates

### Developer Gate (before PR merge)

- [ ] Unit test coverage ≥ 85% for `building` package
- [ ] No SonarQube CRITICAL/BLOCKER (maintain baseline)
- [ ] TypeScript strict — zero errors in new files
- [ ] Anti-pattern checklist PASS (see CLAUDE.md)

### Sprint 1 Gate (2026-05-25 — HARD BLOCK)

- [ ] ADR-026, ADR-027, ADR-028, ADR-033 merged (≥ 2 reviewers)
- [ ] V26 migration: deploy clean + rollback idempotent
- [ ] RLS-001..RLS-010: all 10 scenarios PASS
- [ ] Cross-building query: TC-004 PASS (p95 <500ms @ 100K rows)
- [ ] Capability flag: TC-008 PASS (Tier 1 → TimescaleDbAnalyticsAdapter)
- [ ] ClickHouse POC: TC-009 PASS (docker-compose up, /ping 200)
- [ ] CI `values-tier1.yaml` PASS — no Tier 1 regression
- [ ] Zero P0/P1 bugs open

---

## Isolation Test Matrix (Hard Block)

No merge if ANY of these fail:

| Test ID | Scenario | Expected | Hard Block |
|---------|---------|---------|-----------|
| ISO-001 | Tenant A queries `/api/v1/buildings` | Only tenant-a buildings | YES |
| ISO-002 | POST cross-building with foreign buildingCode | HTTP 403 | YES |
| ISO-003 | SQL `SET app.tenant_id='tenant-a'` → query `public.buildings` | Only tenant-a rows | YES |
| ISO-004 | Aggregator tenant (`is_aggregator=true`) | All cluster buildings visible | YES |
| ISO-005 | Non-aggregator tenant calls cross-building agg | Only own buildings in result | YES |
| ISO-006 | 50 concurrent requests from 2 tenants | Zero cross-tenant contamination | YES |
| ISO-007 | Empty `app.tenant_id` (admin service account) | All buildings visible | YES |

---

## Test Levels

### Unit Tests (`BuildingClusterServiceTest`)

8 test cases covering:
- `findByTenant` returns only active buildings
- `findByCode` throws for missing/inactive building
- `validateOwnership` throws for foreign buildings
- `create` throws for duplicate code
- `create` success with defaults

**Target coverage: ≥ 85%**

### Integration Tests (Sprint 2 — Testcontainers)

Deferred to Sprint 2 due to sprint capacity. Sprint 1 relies on unit tests + manual TC.

### Manual Test Cases

10 test cases (TC-001..TC-010) — see `sprint1-manual-test-cases.md`

---

## Performance Baseline

Sprint 1 establishes baseline (not gate):

| Query | Dataset | Measured Baseline |
|-------|---------|------------------|
| Single building aggregate | 100K rows | < 200ms |
| 3-building cross-aggregate | 300K rows | TBD Sprint 1 |
| 5-building cross-aggregate | 500K rows | TBD Sprint 1 |

Sprint 2 gate: 5-building p95 <500ms @ 10M total rows (with pre-seeded data from QA-01)

---

## Shadow Validation Plan

analytics-service shadow mode (v3-EXT-03, Sprint 1 Week 2):

1. Deploy analytics-service alongside monolith on Tier 2 staging
2. Mirror read traffic to both
3. Compare responses: diff <0.01% row count + value sum
4. Duration: 72h sustained before cutover gate

See `shadow-validation-criteria.md` for full criteria.

---

## Defect Classification

| Severity | Response |
|---------|---------|
| P0 (data leak, security) | Block sprint, fix same day |
| P1 (functional regression) | Fix within sprint |
| P2 (minor functional) | Backlog, Sprint 2 |
| P3 (cosmetic) | Nice-to-have |
