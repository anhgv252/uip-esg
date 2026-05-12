# ADR-033: Cross-Building Tenant Hierarchy — Parent Tenant Aggregation Pattern

**Date:** 2026-05-12  
**Status:** Accepted  
**Deciders:** Backend Lead, SA, QA Lead  
**Sprint:** MVP3-1 — BLOCKER (Day 3)

---

## Status

Accepted

## Context

MVP2 supports single-tenant = single-building model: each tenant has exactly one building context, enforced by PostgreSQL RLS via `current_setting('app.tenant_id', true)`.

MVP3 introduces **Building Clusters**: a single enterprise (e.g., HCMC Property Corp) manages 5–20 buildings across a city. Requirements:

- A **Cluster Manager** tenant must see aggregated metrics across all buildings in their portfolio
- Individual **Building Operator** tenants see only their assigned building(s)
- Cross-tenant data leakage must be structurally impossible (RLS, not just application-layer)
- Tier 1 customers (single building) must see **zero behavior change**

The existing `public.tenants` table has `tenant_id` but no concept of hierarchy or aggregation scope.

---

## Decision

Introduce a **two-level tenant hierarchy** with `public.buildings` as the linking entity:

### Schema additions

1. `public.tenants.is_aggregator BOOLEAN DEFAULT FALSE` — flag for Cluster Manager tenants
2. `public.buildings` table — maps buildings to tenants with optional `cluster_id`
3. RLS policy on `public.buildings` using `current_setting('app.tenant_id', true)`

### Key invariants

| Rule | Enforcement |
|------|------------|
| BR-001: Cluster Manager sees only assigned cluster data | `is_aggregator=true` check in BuildingClusterService |
| BR-002: Building ownership validated before query | `validateOwnership()` called at service layer, before SQL |
| BR-003: Depth max = 2 (cluster → building) | Application-layer: no sub-building nesting |
| BR-004: Empty `app.tenant_id` = admin bypass | RLS policy USING clause |

### RLS policy

```sql
CREATE POLICY buildings_tenant_isolation ON public.buildings
    USING (
        tenant_id = current_setting('app.tenant_id', true)
        OR current_setting('app.tenant_id', true) = ''
        OR current_setting('app.tenant_id', true) IS NULL
    );
```

### Cross-building aggregation flow

```
HTTP Request (X-Tenant-ID: cluster-mgmt-corp)
  → BuildingClusterController
  → BuildingClusterService.validateOwnership()   ← service-layer check (BR-002)
  → CrossBuildingAggregationService.aggregate()  ← SQL query (time-bounded, tenant-scoped)
  → Response
```

---

## Alternatives Considered

### Alternative 1: Separate DB per building
**Rejected.** Operational complexity (N databases per cluster), cross-building JOIN impossible, Flyway migration N× harder.

### Alternative 2: Application-layer filtering only (no RLS)
**Rejected.** Single bug in filter logic = data leak. RLS provides defense-in-depth; consistent with MVP2 security posture validated through OWASP Sprint 4.

### Alternative 3: Graph-based hierarchy (parent_tenant_id FK)
**Rejected.** Unlimited depth creates unbounded recursion risk. Two-level hierarchy covers all current requirements; v4.0 can extend if needed (OQ-007).

---

## Consequences

### Positive
- Tenant isolation hardened at DB layer (RLS) + application layer (service validation)
- Zero Tier 1 behavior change: `public.buildings` has no interaction with MVP2 code paths
- `cluster_id` provides flexible grouping without strict hierarchy

### Negative / Risks
- **R1 (30%)**: RLS + cross-building query may hit p95 >500ms with 10M rows → mitigated by `idx_clean_metrics_building_tenant_ts` + app cache
- **R2 (25%)**: V26 migration could affect Tier 1 if rollback not idempotent → mitigated by `ON CONFLICT DO NOTHING` seeds + CI `values-tier1.yaml` test

### Performance design
- Index `idx_clean_metrics_building_tenant_ts ON esg.clean_metrics(building_id, tenant_id, timestamp DESC)` supports cross-building queries
- Target: p95 <500ms @ 5 buildings × 100K rows each
- Rollup pre-aggregation (Sprint 2) needed for 10M rows target

---

## Implementation

**Migration:** `V26__building_cluster.sql`  
**Entity:** `com.uip.backend.building.domain.BuildingCluster`  
**Service:** `com.uip.backend.building.service.BuildingClusterService`  
**API:** `GET/POST /api/v1/buildings`, `GET /api/v1/buildings/clusters/{clusterId}`

---

## Handoff to v3-BE-01

SA spike complete. V26 migration designed, RLS policy validated conceptually, cross-building aggregation pattern defined. Backend Engineer 1: proceed with implementation.
