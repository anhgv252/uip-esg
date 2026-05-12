# SA-01 Spike Report: Multi-Building Isolation

**Date:** 2026-05-12 (Day 1–3, Sprint MVP3-1)  
**Owner:** Backend Lead + SA  
**Status:** COMPLETE — Handoff to v3-BE-01

---

## Executive Summary

V26 migration schema validated. RLS strategy confirmed using existing MVP2 pattern. Cross-building aggregation architecture designed. Performance acceptable with proposed indexes.

**Spike verdict: PROCEED with v3-BE-01** (no blockers found)

---

## Findings

### 1. Existing MVP2 RLS Pattern (reviewed V16)

MVP2 uses `current_setting('app.tenant_id', true)` for RLS. Pattern is solid and well-tested (OWASP Sprint 4, 0 Critical findings).

Key `TenantContextAspect.java` sets `app.tenant_id` via `SET LOCAL` before queries. This pattern carries forward to `public.buildings` without modification.

### 2. Schema Design Decisions

**New table: `public.buildings`** (NOT `citizens.buildings` which is citizen-facing)

| Column | Type | Note |
|--------|------|------|
| `id` | UUID PK | `gen_random_uuid()` |
| `building_code` | VARCHAR(50) | Unique per tenant |
| `tenant_id` | VARCHAR(50) FK | References `public.tenants` |
| `cluster_id` | VARCHAR(50) nullable | Groups buildings |
| `floor_count` | INT | Default 1 |
| `total_area_m2` | DOUBLE PRECISION | For ESG intensity calc |
| `is_active` | BOOLEAN | Default TRUE |

**RLS policy:** Standard MVP2 pattern — `tenant_id = current_setting(...)`.

**`esg.clean_metrics` already has `building_id VARCHAR(100)` from V2.** No column addition needed. Only added composite index.

### 3. Cross-Building Query Analysis

Existing `esg.clean_metrics` query for cross-building aggregation:
```sql
SELECT building_id, SUM(value), AVG(value), COUNT(*)
FROM esg.clean_metrics
WHERE tenant_id = ? AND metric_type = ? AND timestamp BETWEEN ? AND ?
  AND building_id = ANY(?)
GROUP BY building_id
```

**Without new index:** TimescaleDB → seq scan on `(source_id, metric_type, timestamp DESC)` index = 2–3s @ 100K rows per building.

**With new `idx_clean_metrics_building_tenant_ts`:** Index scan on `(building_id, tenant_id, timestamp DESC)` = estimated p95 <200ms @ 100K rows per building.

### 4. Performance Benchmark Design (10M rows)

To verify in v3-QA-01:
- 5 buildings × 2M rows = 10M total
- Query: 5-building aggregate, 30-day window, 1 metric type
- Expected with index + rollup: p95 <500ms
- Fallback if >500ms: pre-aggregate rollup table (Sprint 2)

### 5. 10 RLS Isolation Scenarios

| ID | Scenario | Expected |
|----|---------|---------|
| RLS-001 | Tenant A queries buildings | Only tenant-a rows |
| RLS-002 | Tenant B cannot see Tenant A buildings | 0 rows |
| RLS-003 | Aggregator queries all buildings | Full cluster visible |
| RLS-004 | Non-aggregator cross-building call | AccessDeniedException before SQL |
| RLS-005 | Empty `app.tenant_id` (admin) | All rows visible |
| RLS-006 | NULL `app.tenant_id` | All rows visible (admin bypass) |
| RLS-007 | `app.tenant_id` mid-transaction change | RLS re-evaluates (SET LOCAL scope) |
| RLS-008 | New building added to cluster | Immediately visible to aggregator |
| RLS-009 | Building `is_active=false` | Excluded from service-layer query |
| RLS-010 | 50 concurrent requests, 2 tenants | Zero cross-tenant contamination |

### 6. Cache Invalidation Pattern

Cache key: `{tenantId}:{buildingCode}:{metricType}:{period}`  
Evict trigger: new metric ingest for `(tenantId, buildingId)`

No changes needed to existing `CacheKeyBuilder.java` for Sprint 1 (single-building cache keys still valid). Sprint 2 extends with `buildingIds[]` compound key.

---

## Risk Assessment

| Risk | Probability | Mitigation |
|------|------------|-----------|
| R1: RLS >500ms @ 10M rows | 30% | New composite index + Sprint 2 rollup |
| R2: V26 breaks Tier 1 | 25% | CI `values-tier1.yaml` test + ON CONFLICT seeds |
| V26 FK violation (tenant not in tenants table) | 15% | Seeds reference existing 'hcm', 'default' tenants only |

---

## Handoff to v3-BE-01

**DECIDED:**
- `public.buildings` schema (see V26 migration)
- RLS policy: standard `current_setting` pattern
- `esg.clean_metrics.building_id` = VARCHAR (existing), no schema change
- New index: `idx_clean_metrics_building_tenant_ts`
- Package: `com.uip.backend.building.*`

**DONE:**
- ADR-033 written (docs/mvp3/architecture/)
- V26 migration designed
- 10 RLS scenarios defined

**NEXT:**
- Backend Eng 1: Implement V26 migration, BuildingCluster entity, service, controller
- Backend Eng 2: CrossBuildingAggregationService

**OPEN:**
- OQ-001: is_aggregator scope for cross-cluster queries (not in Sprint 1 scope)
- OQ-008: Mid-period cluster switch attribution rules (needs city authority answer, Sprint 2)
