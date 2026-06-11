# Auth Gap Analysis — HMAC vs Keycloak JWT Architecture

**Date:** 2026-06-11
**Status:** Analysis Complete — Recommendations for v3.1
**Author:** Solution Architect

---

## 1. Current State

### Authentication Architecture (Dual System)

The UIP platform currently operates **two independent JWT authentication systems** that do not interoperate:

| Component | Token Type | Issuer | Acceptance | Used By |
|-----------|-----------|--------|------------|---------|
| **Backend monolith** | HMAC HS256 | `uip-backend` (configured via `jwt.issuer`) | `JwtAuthenticationFilter` → `JwtTokenProvider` | Web frontend, mobile app (direct login) |
| **Kong Gateway** | RSA RS256 | `http://keycloak:8085/realms/uip` | Kong JWT plugin → JWK verify | Analytics-service via Kong proxy |
| **RoutingJwtDecoder** | Both | Routes by `iss` claim | **EXISTS but NOT WIRED** in SecurityConfig | N/A |

### Data Flow

```
┌─────────────┐     HMAC JWT (HS256)      ┌──────────────────┐
│  Web/Mobile  │ ──── POST /auth/login ──→ │ Backend Monolith  │
│  Frontend    │ ←─── JWT (iss=uip-backend)│ (JwtAuthFilter)   │
└─────────────┘                            └──────────────────┘

┌─────────────┐     RSA JWT (RS256)       ┌──────────────────┐
│  Kong JWT    │ ──── Verify token ──────→ │ Keycloak          │
│  Plugin      │                            │ (realm: uip)      │
└──────┬───────┘                            └──────────────────┘
       │ proxy_pass
       ↓
┌──────────────────┐
│ Analytics Service │ ← accepts BOTH HMAC + RSA (inline filter)
│ (dual JWT filter) │
└──────────────────┘
```

### Key Observations

1. **Backend `SecurityConfig`** (`backend/src/main/java/com/uip/backend/auth/config/SecurityConfig.java`):
   - Uses `JwtAuthenticationFilter` which calls `JwtTokenProvider`
   - `JwtTokenProvider` generates and validates HMAC HS256 tokens
   - `RoutingJwtDecoder` exists (dual HMAC+RSA decoder) but is **NOT registered as a bean or filter**
   - Login endpoint: `POST /api/v1/auth/login` → returns HMAC JWT

2. **Analytics-service `SecurityConfig`** (`applications/analytics-service/.../SecurityConfig.java`):
   - Has inline `OncePerRequestFilter` that tries **both** HMAC and RSA verification
   - `tryHmacAuth()`: validates with HMAC secret
   - `tryRsaAuth()`: validates with Keycloak public key (PEM)
   - This is the **only service that accepts both token types**

3. **Kong staging** (`infra/kong/kong.staging.yml`):
   - Routes `/api/v1/analytics` to analytics-service
   - JWT plugin validates **only** Keycloak RS256 tokens
   - If frontend sends HMAC JWT to Kong route → **401 Unauthorized**

4. **Frontend/Mobile**:
   - Login via `POST /api/v1/auth/login` → receives HMAC JWT
   - Uses HMAC JWT for all backend API calls
   - Cannot access analytics endpoints via Kong (requires RSA JWT)

---

## 2. Problems Identified

### P1 — Analytics endpoints unreachable from frontend via Kong

- Frontend holds HMAC JWT only
- Kong requires RSA JWT
- Frontend **cannot** call `/api/v1/analytics/*` through Kong gateway
- **Current workaround**: Analytics endpoints are called directly on backend (port 8080), which delegates to analytics-service internally via REST or gRPC

### P2 — Keycloak is deployed but unused for end-user auth

- Keycloak realm `uip` is deployed and configured
- RSA public key is configured in Kong and analytics-service
- But no user login flow uses Keycloak as IdP
- PKCE flow exists in mobile app (`useAuthMobile`) but points to HMAC login

### P3 — RoutingJwtDecoder is dead code

- `RoutingJwtDecoder.java` implements correct dual-issuer routing
- Has unit tests (`RoutingJwtDecoderTest`) that pass
- But the class is never instantiated or used in any SecurityConfig
- Would require Spring Security OAuth2 Resource Server setup to activate

### P4 — Inconsistent auth between services

- Backend: HMAC-only (via filter)
- Analytics-service: HMAC + RSA (via inline filter)
- Kong: RSA-only (via plugin)
- No unified auth story across the platform

---

## 3. Root Cause

The project followed a **gradual migration path** (ADR-027: Keycloak Hybrid Auth):
1. **Phase 1 (MVP2)**: HMAC JWT — simple, no external IdP dependency
2. **Phase 2 (MVP3)**: Add Keycloak + Kong — for extracted services
3. **Phase 3 (v3.1)**: Migrate all auth to Keycloak

The migration stopped at Phase 2 because:
- Sprint time constraints
- Keycloak requires full user federation (user store migration)
- HMAC works well for current pilot scope (2 tenants, ~50 users)
- Kong only needs to protect analytics-service (ADR-028)

---

## 4. Recommendations for v3.1

### Option A: Full Keycloak Migration (Recommended)

Migrate all end-user authentication to Keycloak RS256:

1. **Backend SecurityConfig** → switch to Spring Security OAuth2 Resource Server with `RoutingJwtDecoder`
2. **Frontend login** → redirect to Keycloak login page (or use Keycloak REST API for SPA)
3. **Mobile login** → switch PKCE to Keycloak authorization endpoint
4. **Remove** `JwtTokenProvider` HMAC token generation (keep for service-to-service internal)
5. **Kong** → remains as-is (already validates RS256)

**Effort**: ~13 SP (SA spike + backend + frontend + mobile + testing)

### Option B: Bridge Token (Quick Fix)

Issue a short-lived Keycloak token when HMAC login succeeds:

1. Backend `/auth/login` → HMAC JWT + calls Keycloak token exchange → returns both tokens
2. Frontend uses HMAC JWT for backend, RSA JWT for Kong routes
3. No migration needed

**Effort**: ~5 SP  
**Risk**: Token sync issues, two tokens to manage, Keycloak must trust backend as client

### Option C: Accept Current State (Pilot)

For the pilot (5 buildings, ~50 users):
- HMAC auth is sufficient for all backend APIs
- Analytics-service is called internally (not through Kong from frontend)
- Kong protects analytics-service from external access only
- Document as known limitation

**Effort**: 0 SP  
**Risk**: Not production-ready for multi-tenant city authority deployment

---

## 5. Decision Required

| Option | Effort | Risk | Production-ready | Recommended for |
|--------|--------|------|-------------------|-----------------|
| A — Full Keycloak | 13 SP | Low | ✅ Yes | v3.1 post-pilot |
| B — Bridge Token | 5 SP | Medium | ⚠️ Partial | Emergency pilot fix |
| C — Accept Current | 0 SP | High for scale | ❌ No | Pilot only |

**SA Recommendation**: Option C for pilot (2026-08-04), Option A for v3.1 (post-pilot). The dual-auth system is acceptable for 2-tenant pilot with ~50 users. Analytics endpoints work correctly via internal backend delegation.

---

## 6. Action Items

| Priority | Item | Sprint | Owner |
|----------|------|--------|-------|
| P2 | Wire `RoutingJwtDecoder` into backend SecurityConfig | v3.1 Sprint 1 | Backend Lead |
| P2 | Migrate frontend login to Keycloak | v3.1 Sprint 1 | Frontend |
| P2 | Update mobile PKCE to use Keycloak auth endpoint | v3.1 Sprint 1 | Mobile |
| P3 | Remove HMAC token generation for end-user login | v3.1 Sprint 2 | Backend |
| P3 | ADR-027 status update → "Phase 3 pending" | v3.1 Sprint 1 | SA |

---

*Document: auth-gap-analysis.md | Generated 2026-06-11 | Version 1.0*
