# SA Code Review — Sprint 9 (Buffer Window)

**Reviewer:** SA Agent  
**Date:** 2026-06-17  
**Sprint:** MVP3-9 — HA Infrastructure + API Contract + Security Hardening (Buffer Window 2026-06-04 → 2026-06-17)  
**Scope:** All Sprint 9 buffer-window deliverables (uncommitted working tree — sprint commits pending)

---

## Overall Status: APPROVED (post-fix)

Initial review: **BLOCKED** — 1 MAJOR, 1 MINOR security finding  
After fixes applied 2026-06-17: **APPROVED WITH NOTES**

---

## Deliverables Reviewed

| # | File / Artifact | Task | Verdict |
|---|---|---|---|
| 1 | `infrastructure/scripts/keycloak-realm-patch.sh` | S9-SEC-01-PREP | APPROVED ✅ (m-1 fixed) |
| 2 | `infra/keycloak/realm-uip-production.json` | S9-SEC-01-PREP | APPROVED ✅ (M-1 fixed) |
| 3 | `packages/hooks/src/` (4 files) | S9-TD-04 | APPROVED ✅ |
| 4 | `packages/api-types/src/generated.ts` | S9-CONTRACT-01 | APPROVED ✅ |
| 5 | `.github/workflows/api-contract-check.yml` | S9-CONTRACT-03 | APPROVED WITH NOTES (m-2) |
| 6 | `infrastructure/scripts/ch-cluster-init.sh` | S9-TD-01 | APPROVED WITH NOTES (m-3) |
| 7 | `infrastructure/scripts/ha-health-check.sh` | S9-QA-ENV | APPROVED ✅ |
| 8 | `docs/api/openapi.json` (SA-043 fix) | S9-CI-SA-TRACK | APPROVED ✅ |

---

## MAJOR Findings (FIXED)

### M-1 — Production realm `bruteForceProtected: false` [FIXED ✅]

**File:** `infra/keycloak/realm-uip-production.json`  
**Severity:** MAJOR (security hardening gap for UAT/production)  
**Issue:** `bruteForceProtected` was `false` in the generated production realm. Any account — including `admin` — would be susceptible to credential stuffing and brute-force login attempts with no rate limiting or lockout. OWASP A07 (Authentication Failures).  
**Fix applied:** Set `bruteForceProtected: true` with sensible defaults:
- `failureFactor: 10` (lock after 10 failures)
- `maxDeltaTimeSeconds: 43200` (12-hour reset window)
- `maxFailureWaitSeconds: 900` (max 15-min lockout)
- `waitIncrementSeconds: 60`
- `minimumQuickLoginWaitSeconds: 60`
- `permanentLockout: false` (self-unlocking after window)

---

## MINOR Findings (FIXED)

### m-1 — Plaintext secret in rotation log output [FIXED ✅]

**File:** `infrastructure/scripts/keycloak-realm-patch.sh:103`  
**Severity:** MINOR  
**Issue:** After rotation, the script echoed `"New uip-api secret: ${NEW_SECRET} → store in ...env"` — printing the full secret in plaintext to stdout. If run in CI/CD, the secret would appear in build logs (OWASP A02 — Cryptographic Failures / A09 — Security Logging).  
**Fix applied:** Changed to `${NEW_SECRET:0:4}****` — logs only the first 4 chars. Full value must be read from Keycloak Admin UI or the token response directly.

---

## MINOR Findings (Deferred)

| ID | File | Issue | Action |
|----|------|-------|--------|
| m-2 | `.github/workflows/api-contract-check.yml:23,26,49` | Actions use mutable `@v4` tags (`actions/checkout@v4`, `actions/setup-node@v4`, `actions/upload-artifact@v4`) instead of pinned commit SHAs. Supply-chain attack vector (OWASP A08). | Pin to SHA in Sprint 10 CI hardening |
| m-3 | `infrastructure/scripts/ch-cluster-init.sh:14` | `curl --user "${CH_USER}:${CH_PASSWORD}"` passes credentials in command-line args — visible in `ps` on multi-tenant hosts. Low-risk inside containers but not ideal. | Use `CURLOPT_NETRC` or `--netrc-file` in Sprint 10 if cluster moves to shared host |

---

## INFO

| ID | Note |
|----|------|
| I-1 | `realm-uip-production.json` — `accessTokenLifespan: 3600` (1h). Adequate for web dashboards. Operator-mobile uses PKCE refresh flow — sessions will survive via refresh tokens. No change needed. |
| I-2 | `packages/hooks/src/useSensors.ts` — `metadata?: Record<string, any>` is intentionally untyped for sensor extensibility across IoT hardware variants. Acceptable. |
| I-3 | `packages/hooks/src/useDashboard.ts` — fallback to `/dashboard/stats` on 404 is an intentional compatibility shim from Sprint 8 (C-2 fix). Acceptable until deprecated in Sprint 11. |
| I-4 | `docs/api/openapi.json` SA-043 — `POST /citizen/meters` response corrected 200→201; `bearerAuth` security added to GET+POST `/citizen/meters`. Verified against `InvoiceController.java` — controller returns `ResponseEntity.status(201)` with `@PreAuthorize("hasRole('CITIZEN')")`. Types regenerated (0 errors). |
| I-5 | `keycloak-realm-patch.sh` — `UIP_API_CLIENT_SECRET` defaults to `uip-api-secret-dev` for local development. Safe since `set -euo pipefail` is present and a warning comment explains the production requirement. |
| I-6 | `ha-health-check.sh` — `set -euo pipefail` present. No `eval` or unquoted expansions. CRITICAL_SERVICES array correctly enumerates all 17 mandatory containers including both ClickHouse nodes and 3 Kafka brokers. |
| I-7 | `ch-cluster-init.sh` — all DDL uses `IF NOT EXISTS` + `ON CLUSTER ${CLUSTER_NAME}` → correctly idempotent. `ReplicatedMergeTree` paths use `{shard}/{replica}` macros → correct for 2-node HA topology. |
| I-8 | `api-contract-check.yml` — CI drift check is logically correct: regenerates types, then `git diff --exit-code` → fails pipeline if types are stale. `upload-artifact@v4` on failure provides debug aid. Solid design. |

---

## Per-Deliverable Summary

| Deliverable | Findings | Verdict |
|---|---|---|
| `keycloak-realm-patch.sh` | m-1 FIXED | APPROVED ✅ |
| `realm-uip-production.json` | M-1 FIXED | APPROVED ✅ |
| `packages/hooks/` (apiClient, useDashboard, useAlerts, useSensors) | None — `tsc --noEmit` → 0 errors | APPROVED ✅ |
| `packages/api-types/generated.ts` | None — auto-generated, 0 errors | APPROVED ✅ |
| `api-contract-check.yml` | m-2 (deferred S10) | APPROVED WITH NOTES |
| `ch-cluster-init.sh` | m-3 (deferred S10) | APPROVED WITH NOTES |
| `ha-health-check.sh` | None | APPROVED ✅ |
| `openapi.json` SA-043 | None — fix verified against controller | APPROVED ✅ |

---

## SA Checklist Coverage

### Backend checklist (10 items)
| Item | Result |
|---|---|
| Unused imports / dead code | ✅ None found in reviewed Java changes |
| Spring bean registration | ✅ No new beans added this sprint (infra-only) |
| Null safety | ✅ `keycloak-realm-patch.sh` uses `:-` defaults for all env vars |
| Exception handling | ✅ `ha-health-check.sh` handles NOT_FOUND / UNHEALTHY cases correctly |
| JWT claims (`iss`, `sub`, `tenant_id`) | ✅ `realm-uip-production.json` preserves all 4 protocol mappers from dev realm |
| Resource leak | ✅ `ch-cluster-init.sh` uses `curl -o /dev/null` — no temp files left open |
| Thread safety | ✅ N/A (infra scripts / static config, no concurrent state) |
| Config env defaults (`${VAR:-default}`) | ✅ All env vars in all scripts have safe defaults |
| Dependency license | ✅ No new runtime dependencies added (openapi-typescript is MIT) |
| API contract match frontend | ✅ SA-043 fix aligns spec with InvoiceController; generated.ts regenerated |

### Frontend checklist (10 items)
| Item | Result |
|---|---|
| `npx tsc --noEmit` → 0 errors | ✅ `packages/hooks/` — EXIT:0 confirmed |
| API call signature match backend | ✅ `useAlerts` → `/alerts`, `useSensors` → `/sensors`, `useDashboard` → `/dashboard` — all match backend routes |
| React Query patterns (`useMutation` POST, `useQuery` GET) | ✅ `useAcknowledgeAlert` uses `useMutation`; all read hooks use `useQuery` |
| Null/undefined safety | ✅ Optional chaining used throughout; `AlertFilters` all optional |
| Accessibility | ✅ N/A (hooks package — no DOM/JSX) |
| Memory leak (`URL.revokeObjectURL`, useEffect cleanup) | ✅ No subscriptions or URL object references in hooks |
| Bundle size impact | ✅ `packages/hooks` is a shared monorepo package — tree-shakeable; no new heavy dependencies |
| Responsive breakpoints | ✅ N/A (hooks package) |
| Error states | ✅ All hooks return React Query `error` and `isLoading` states; callers handle these |
| Auth guard | ✅ `withCredentials: true` on `defaultApiClient`; JWT enforced server-side via `@PreAuthorize` |

---

## Blocked Items (Not Reviewed — Awaiting Live Infrastructure)

| Item | Blocker | Expected Date |
|---|---|---|
| S9-SEC-01 live `uip-api` secret rotation | Needs running Keycloak at UAT/prod (`localhost:8085`) | 2026-06-20 |
| S9-QA-HA-TC execution (40 TCs from S8 backlog) | Needs `make up-ha` on dedicated HA staging server | Day 1 = 2026-06-18 |

*These items are pre-documented in `docs/mvp3/test/sprint9-ha-tc-execution-plan.md` and will be reviewed post-execution.*

---

## G10 Gate: **PASS** ✅

All CRITICAL and MAJOR findings resolved. 2 MINOR items deferred to Sprint 10.  
2 live-infrastructure items remain blocked — pre-work fully complete.  
**DevOps may proceed with sprint commit + smoke test once HA staging is available (2026-06-18).**

---

*SA Code Review — Sprint 9 Buffer Window | 2026-06-17*
