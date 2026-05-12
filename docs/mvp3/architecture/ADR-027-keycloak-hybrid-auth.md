# ADR-027: Keycloak Hybrid Auth — Dual-Issuer Migration Strategy

**Date:** 2026-05-12  
**Status:** Accepted  
**Deciders:** Backend Lead, DevOps, SA  
**Sprint:** MVP3-1 (non-prod setup), MVP3-4 (production TLS)

---

## Status

Accepted

## Context

MVP2 uses a custom Spring Boot JWT issuer (`UipJwtService`) with tenant claims embedded directly. This works for Tier 1 single-tenant deployments but does not support:

- Multi-realm Keycloak for Building Cluster tenants
- PKCE OAuth2 flow for mobile operators (Sprint 5)
- External IdP federation (LDAP, enterprise SSO) required by city authority pilot
- `building_ids` claim in JWT for fine-grained Kong plugin authorization

Replacing the MVP2 JWT issuer in one cut-over risks breaking existing Tier 1 customers. A dual-issuer migration allows gradual cutover while maintaining backward compatibility.

---

## Decision

Adopt **Keycloak as the canonical JWT issuer** with a **dual-issuer transition period** (Sprint 4–5):

### JWT claims contract (Keycloak format)

```json
{
  "iss": "https://keycloak.uip.local/realms/uip",
  "tenant_id": "tenant-a",
  "parent_tenant_id": "cluster-mgmt-corp",
  "building_ids": ["bld-001", "bld-002"],
  "is_aggregator": false,
  "roles": ["OPERATOR"]
}
```

### Migration timeline

| Sprint | Action |
|--------|--------|
| MVP3-1 | Deploy Keycloak non-prod, realm setup, `UipJwtConverter` dual-decoder |
| MVP3-4 | Kong production TLS, rate-limiting, negative auth CI tests |
| MVP3-5 | Tier 2 customers migrated to Keycloak tokens |
| MVP3-7 | Legacy issuer sunset (after pilot) |

### Dual-issuer implementation

```java
@Bean
public JwtDecoder jwtDecoder() {
    // RoutingJwtDecoder: inspects 'iss' claim, routes to correct JWK set
    // Issuer 1: legacy Spring Boot issuer (MVP2 Tier 1 tokens)
    // Issuer 2: Keycloak (MVP3 Tier 2 tokens)
    return new RoutingJwtDecoder(
        Map.of(
            legacyIssuerUri, NimbusJwtDecoder.withJwkSetUri(legacyJwkSetUri).build(),
            keycloakIssuerUri, NimbusJwtDecoder.withJwkSetUri(keycloakJwkSetUri).build()
        )
    );
}
```

### Security requirements

| Requirement | Implementation |
|-------------|----------------|
| `alg=none` → 401 | Explicit algorithm allowlist in JWK decoder |
| X-Tenant-ID not spoofable | Kong `request-transformer` plugin injects from JWT claim; client-supplied header stripped |
| JWK cache TTL | 60 seconds (prevent stale keys; balance with performance) |
| Token grant latency | <200ms p95 (Sprint 1 gate) |

---

## Alternatives Considered

### Alternative 1: Replace MVP2 JWT immediately (hard cut-over)
**Rejected.** Breaks all existing Tier 1 clients on deployment. No rollback path. Unacceptable for customers in production.

### Alternative 2: Continue custom Spring Boot JWT forever
**Rejected.** Custom JWT has no PKCE support, no LDAP federation, no enterprise SSO. City authority pilot requires LDAP (OQ requirement). Building mobile PKCE (Sprint 5) requires standards-compliant OAuth2.

### Alternative 3: Auth0 or AWS Cognito (managed)
**Rejected.** Cost and data sovereignty concerns for Vietnamese city authority pilot. On-prem Keycloak preferred. Can add managed IdP as federation source later.

---

## Consequences

### Positive
- PKCE support enables React Native mobile app (Sprint 5) with `expo-auth-session`
- LDAP federation enables city authority enterprise SSO (pilot requirement)
- `building_ids` claim allows Kong to inject building scope without extra DB lookup
- JWT standard compliance (RFC 7519) improves auditability

### Negative / Risks
- **R9 (30%)**: JWK cache thrash → 401 spike during key rotation → mitigation: `RoutingJwtDecoder` per-issuer cache, 60s TTL, `refreshCacheOnMiss: true`
- Dual-issuer period (Sprint 4–5) requires maintaining two auth code paths
- Keycloak adds infrastructure dependency (Postgres backend, HA setup Sprint 4)

---

## Implementation

**Sprint 1:** Keycloak non-prod Docker deploy, realm + claims design  
**Sprint 4:** `v3-DevOps-04/05` — Kong prod TLS, Keycloak production HA  
**Sprint 5:** `v3-EXT-08` — Tier 2 token migration  
**Code:** `com.uip.backend.auth.config.UipJwtConverter`, `RoutingJwtDecoder` bean
