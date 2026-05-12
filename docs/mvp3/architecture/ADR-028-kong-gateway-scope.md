# ADR-028: Kong Gateway Scope — Extracted Services Only (Not Monolith)

**Date:** 2026-05-12  
**Status:** Accepted  
**Deciders:** SA, DevOps, Backend Lead  
**Sprint:** MVP3-1 (non-prod), MVP3-4 (production)

---

## Status

Accepted

## Context

As UIP extracts services (analytics-service Sprint 1, iot-ingestion-service Sprint 4), we need an API gateway to handle:
- JWT validation for extracted services (Keycloak tokens, ADR-027)
- Tenant header injection (`X-Tenant-ID` from JWT)
- Rate limiting for analytics-heavy endpoints
- Metrics/tracing correlation

The question is: should Kong sit in front of the monolith as well?

The monolith already has:
- Spring Security JWT validation (tested through OWASP Sprint 4 — 0 Critical findings)
- Nginx Ingress for TLS termination
- `TenantContextFilter` for `X-Tenant-ID` extraction from `Authorization` header

Adding Kong in front of the monolith would require re-testing the full security matrix and introduces a single point of failure for the entire platform.

---

## Decision

**Kong scope = extracted services ONLY** (`analytics-service`, `iot-ingestion-service`)  
**Monolith continues through nginx Ingress** with existing Spring Security + TenantContextFilter

### Plugin execution order (LOCKED — CI enforced, never reorder)

| Priority | Plugin | Purpose |
|----------|--------|---------|
| 1 | `cors` | Browser preflight — must be first |
| 2 | `jwt` | Validate Keycloak token; reject `alg=none`, expired tokens |
| 3 | `request-transformer` | Inject `X-Tenant-ID`, `X-Building-IDs` from JWT claims; strip client-supplied headers |
| 4 | `rate-limiting` | 1,000 req/min per consumer (analytics-service) |
| 5 | `prometheus` | Metrics scraping |
| 6 | `correlation-id` | Request tracing ID |

**Plugin order is a security invariant**: if `request-transformer` runs before `jwt`, tenant injection happens before authentication = spoofing possible. This order must never change without security review.

### Routing topology

```
                    ┌─────────────────────────────────┐
Internet            │           UIP Platform           │
                    │                                  │
Browser/API ──┬──→  │  nginx Ingress ──→ Monolith      │  (existing)
              │     │                                  │
              └──→  │  Kong Gateway ──→ analytics-svc  │  (NEW Sprint 1)
                    │               ──→ iot-svc        │  (Sprint 4)
                    └─────────────────────────────────┘
```

### Kong DB-less mode

Kong deployed in DB-less mode (declarative config via `kong.yml`). No Kong Postgres dependency. Configuration changes via CI/CD pipeline, not Kong Admin API.

---

## Alternatives Considered

### Alternative 1: Kong in front of all services (monolith + extracted)
**Rejected.** Requires full security re-test of monolith auth flows (3+ weeks). Kong becomes single point of failure for all traffic. Nginx Ingress is already battle-tested for monolith.

### Alternative 2: Istio service mesh instead of Kong
**Deferred.** Istio provides mTLS between all services (value for Sprint 7+). For Sprint 1 we need JWT validation + rate limiting, not full service mesh. Istio sidecar injection can be added later; Kong covers API gateway requirements now. See ADR-012 for gRPC/Istio roadmap.

### Alternative 3: Spring Cloud Gateway embedded in analytics-service
**Rejected.** Mixes routing concerns with business logic. Not reusable across multiple extracted services. Cannot enforce plugin order across services.

---

## Consequences

### Positive
- Monolith security posture unchanged (zero re-testing required)
- analytics-service protected by JWT validation without code changes (Kong handles it)
- Rate limiting isolated: analytics query spike doesn't affect Kong handling monolith traffic (separate paths)
- DB-less Kong = no additional Postgres dependency, simpler ops

### Negative / Risks
- **R7 (20%)**: Kong plugin order error = auth bypass → mitigation: CI test `alg=none → 401`, tenant-spoof → 403 per PR with `gateway` label
- Two separate auth code paths (Spring Security for monolith, Kong for extracted services) → medium-term complexity; resolved when monolith migrates to Kong (v4.0 or never if monolith EOL)
- Kong non-prod setup Sprint 1 → production setup Sprint 4 = 2-sprint window where extracted services have no production gateway

---

## Implementation

**Sprint 1:** `v3-DevOps-02` — Kong non-prod + Keycloak realm, smoke tests  
**Sprint 4:** `v3-DevOps-04` — Kong production TLS + rate-limiting + audit log  
**Config:** `infra/kong/kong.yml` (declarative, DB-less)  
**CI test:** `kong-security-scan` job — triggers on PRs with label `gateway`
