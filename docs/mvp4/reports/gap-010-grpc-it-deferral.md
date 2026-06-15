# GAP-010 — gRPC IT vs real analytics-service: Deferral Rationale

| Field | Value |
|---|---|
| **Task** | GAP-010 (Sprint 2, Task #9) |
| **Original ask** | Test gRPC communication với actual analytics-service instance (Testcontainers) |
| **Decision** | DEFERRED — accepted with rationale below |
| **Decision date** | 2026-06-15 |
| **Decided by** | Solution Architect + Backend Eng |
| **Replaces** | Testcontainers-based gRPC integration test |

---

## 1. Why deferred

GAP-010 originally assumed a **pure gRPC** contract between backend and analytics-service. On inspection, the analytics integration is **dual-port** and the production path is **REST-first**:

| Aspect | Reality found in code |
|---|---|
| Transport | REST (primary) + gRPC (optional Tier-2 path) |
| Primary adapter | `TimescaleDbAnalyticsAdapter`, `ClickHouseRestAnalyticsAdapter` — both `RestTemplate`-based |
| gRPC adapter | `ClickHouseGrpcAnalyticsAdapter` — gated behind capability flag, not default |
| Mutual exclusivity | Enforced by `AnalyticsPortMutualExclusivityIT` |

A Testcontainers setup spinning up a real analytics-service gRPC server would:
1. Require a gRPC server image / stub — analytics-service does not ship a standalone gRPC container today.
2. Test only the **non-default** Tier-2 gRPC path, not the REST path operators actually use in pilot.
3. Add significant CI weight (proto stub generation + container build) for marginal coverage gain.

---

## 2. Existing coverage that mitigates GAP-010

The integration boundary is already protected by **four** complementary test classes:

| Test | Covers | Type |
|---|---|---|
| `AnalyticsServiceConsumerPactTest` | REST contract backend↔analytics-service (energy-aggregate) | Pact consumer-driven contract |
| `AnalyticsAdapterCoverageTest` | Adapter unit coverage ≥50% — error paths, timeouts, RestClientException | Unit (Mockito) |
| `ClickHouseGrpcAnalyticsAdapterTest` | gRPC adapter behavior when enabled | Unit (Mockito) |
| `AnalyticsPortMutualExclusivityIT` | REST vs gRPC cannot both be active | Integration |

**Net effect:** the contract boundary is verified; only a live-container gRPC smoke is missing, and that path is not the pilot default.

---

## 3. When to revisit (trigger)

Implement the Testcontainers gRPC IT when **any** of:
- analytics-service ships a standalone gRPC container image, OR
- a Tier-2 customer activates the gRPC analytics port in production, OR
- proto-breaking-change CI (GAP-040, deferred) is implemented — at that point a live gRPC IT becomes cheap to add alongside.

Until then, the Pact contract + adapter unit tests are the agreed gate.

---

## 4. Task #9 checklist reconciliation

Task #9 originally marked:
```
- [ ] gRPC IT PASS với real analytics-service *(deferred — Testcontainers gRPC setup phức tạp)*
```

This document is the formal deferral record. Task #9 is otherwise **DONE**; GAP-010 moves to the P2 deferred backlog (README §3) alongside GAP-039/040/046.

---

*Authored: 2026-06-15 | Reviewed against: backend/src/test/java/com/uip/backend/esg/config/analytics/*
