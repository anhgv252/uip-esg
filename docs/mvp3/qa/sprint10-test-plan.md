# Sprint 10 — Test Plan

**Version:** 1.0
**Sprint:** MVP3-10
**QA Lead:** [QA Engineer]
**Date:** 2026-06-05
**Target:** ≥1,300 regression tests, 100% PASS

---

## 1. Test Scope

### In Scope
- API Contract verification (110 endpoints)
- Error response code validation (15 critical endpoints)
- Security testing (production profile, @PreAuthorize, tenant isolation)
- Full regression on HA staging environment

### Out of Scope
- Performance/load testing (covered in MVP2)
- Mobile app testing (separate test plan)
- Chaos engineering (deferred to v3.1)

---

## 2. API Contract Tests (NEW — Sprint 10)

### 2.1 Endpoint Discovery Verification

Verify all 110 endpoints are documented in `openapi.json`:

```bash
# Count documented paths
cat docs/api/openapi.json | python3 -c "
import json,sys
d=json.load(sys.stdin)
paths=d.get('paths',{})
count=sum(len([m for m in methods if m in ('get','post','put','delete','patch')])
          for methods in paths.values())
print(f'Total operations: {count}')
"
# Target: ≥ 110 operations
```

### 2.2 Error Response Code Verification

| # | Endpoint | Expected Error Codes | Test Method |
|---|----------|---------------------|-------------|
| 1 | `POST /api/v1/auth/login` | 401 (bad creds), 429 (rate limit) | REST Assured |
| 2 | `POST /api/v1/auth/invite/accept` | 400 (bad token), 401, 404 | REST Assured |
| 3 | `PUT /api/v1/alerts/{id}/acknowledge` | 401, 403, 404 | REST Assured |
| 4 | `PUT /api/v1/alerts/{id}/resolve` | 401, 403, 404 | REST Assured |
| 5 | `POST /api/v1/esg/reports/generate` | 400, 403 | REST Assured |
| 6 | `GET /api/v1/environment/sensors/{id}/readings` | 404 | REST Assured |
| 7 | `POST /api/v1/admin/tenants` | 400, 401, 403 | REST Assured |
| 8 | `POST /api/v1/admin/tenants/{id}/users/invite` | 400, 401, 403 | REST Assured |
| 9 | `PUT /api/v1/admin/tenants/{id}/users/{uid}/role` | 400, 401, 403, 404 | REST Assured |
| 10 | `POST /api/v1/bms/devices` | 400, 401, 403 | REST Assured |
| 11 | `POST /api/v1/bms/devices/{id}/commands` | 400, 401, 403, 404 | REST Assured |
| 12 | `POST /api/v1/buildings` | 400, 401, 403 | REST Assured |
| 13 | `POST /api/v1/push/subscribe` | 400, 401 | REST Assured |
| 14 | `GET /api/v1/forecast/energy` | 401, 404 | REST Assured |
| 15 | `POST /api/v1/workflows` | 400, 401, 403 | REST Assured |

### 2.3 Test Case Template (Error Response)

```java
@Test
void acknowledgeAlert_withoutAuth_returns401() throws Exception {
    mockMvc.perform(put("/api/v1/alerts/{id}/acknowledge", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
}

@Test
@WithMockUser(roles = "CITIZEN")
void acknowledgeAlert_asCitizen_returns403() throws Exception {
    mockMvc.perform(put("/api/v1/alerts/{id}/acknowledge", UUID.randomUUID()))
        .andExpect(status().isForbidden());
}

@Test
@WithMockUser(roles = "OPERATOR")
void acknowledgeAlert_nonExistentAlert_returns404() throws Exception {
    mockMvc.perform(put("/api/v1/alerts/{id}/acknowledge", UUID.randomUUID()))
        .andExpect(status().isNotFound());
}
```

---

## 3. Security Tests

### 3.1 Production Profile — Debug Endpoints (S10-SEC-03)

| Test Case | Request | Expected | Profile |
|-----------|---------|----------|---------|
| SEC-01 | `POST /api/v1/test/inject-reading` | 404 | production |
| SEC-02 | `POST /api/v1/test/inject-flood-alert` | 404 | production |
| SEC-03 | `GET /api/v1/internal/fake-traffic` | 404 | production |
| SEC-04 | `POST /api/v1/simulate/iot-sensor` | Should be documented but gated | production |

### 3.2 @PreAuthorize Enforcement

| Module | Endpoint | Required Role | Test: No Auth | Test: Wrong Role |
|--------|----------|---------------|---------------|------------------|
| Admin | `POST /admin/sensors` | ADMIN | 401 | 403 (as OPERATOR) |
| Tenant Admin | `POST /admin/tenants` | ADMIN | 401 | 403 (as OPERATOR) |
| Alerts | `PUT /alerts/{id}/resolve` | OPERATOR+ | 401 | 403 (as CITIZEN) |
| ESG | `POST /esg/reports/generate` | OPERATOR+ esg:write | 401 | 403 (as CITIZEN) |
| BMS | `POST /bms/devices/{id}/commands` | Authenticated | 401 | N/A |

### 3.3 Multi-Tenant Isolation

| Test Case | Description |
|-----------|-------------|
| TENANT-01 | Tenant A user cannot access Tenant B buildings |
| TENANT-02 | Tenant A user cannot read Tenant B sensor data |
| TENANT-03 | Tenant A admin cannot manage Tenant B users |
| TENANT-04 | Tenant A alerts are not visible to Tenant B |

---

## 4. Regression Coverage Summary

| Category | Sprint 1-9 Baseline | Sprint 10 New | Total Target |
|----------|---------------------|---------------|--------------|
| Unit Tests | ~650 | +40 (error codes) | ~690 |
| Integration Tests | ~180 | +30 (security) | ~210 |
| API Contract Tests | ~80 | +100 (110 endpoints) | ~180 |
| E2E Tests | ~40 | +10 | ~50 |
| Arch Tests | ~20 | +0 | ~20 |
| **Total** | **~970** | **~180** | **≥1,150** |
| Stretch (additional integration) | | +150 | **≥1,300** |

---

## 5. Quality Gates (Sprint 10 — 14 Hard Gates)

| Gate | Criterion | Test Verification |
|------|-----------|-------------------|
| G1 | 110/110 endpoints documented | `openapi.json` path count ≥ 110 |
| G2 | CI contract drift check PASS | `npm run gen-api-types && git diff --exit-code` |
| G3 | Frontend tsc 0 errors | `npx tsc --noEmit` |
| G4 | Debug endpoints → 404 in production | ProductionProfileSecurityTest (3 tests) |
| G5 | Keycloak rotation verified | Manual test + rotation procedure doc |
| G6 | ≥15 endpoints with error codes | OpenAPI spec `responses` section audit |
| G7 | iOS cert submitted | Apple Developer portal check |
| G8 | Pilot Runbook 6 scenarios | PM sign-off |
| G9 | Regression ≥1,300 PASS | Test report |
| G10 | OWASP 0 high+ CVEs | `./gradlew dependencyCheckAnalyze` |
| G11 | SA Code Review APPROVED | `sprint10-code-review.md` |
| G12 | Demo dry-run approved | PO sign-off |
| G13 | Total tests ≥1,300 | CI test report |
| G14 | Debug endpoints 0 reachable in prod | Integration test PASS |

---

## 6. Execution Schedule

| Day | Activity | Owner |
|-----|----------|-------|
| Day 1-3 | Prepare test cases, add contract tests | QA |
| Day 5-6 | OWASP scan (pair DevOps) | QA + DevOps |
| Day 8 | Run regression on HA staging (first pass) | QA + Tester |
| Day 9 | Fix any failures, second pass | QA + Backend |
| Day 10 | Final regression report, sign-off | QA |

---

*Document: Sprint 10 Test Plan v1.0 | Created 2026-06-05*
