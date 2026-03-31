# SPRINT 2 MANUAL QA — COMPRESSED SUMMARY

**Date:** 31/03/2026 | **Tester:** UIP Manual Tester | **Duration:** ~20 min

---

## TEST-DONE:

```
executed:     18 test cases across 4 stories (S2-05, S2-06, S2-07, S2-08)
pass:         7
fail:         8  
blocked:      3

bugs:         
  - BUG-S2-05-01: AQI latest endpoint → 500 CRITICAL
  - BUG-S2-05-02: SSE notification → 500 CRITICAL  
  - BUG-S2-05-03: AQI history empty CRITICAL
  - BUG-S2-05-04: Sensors all OFFLINE HIGH
  - BUG-S2-06-01: All ESG APIs → 500 CRITICAL (4 endpoints)
  - BUG-S2-07-01: Error records → 500 CRITICAL
  - BUG-S2-08-01: Traffic filtering → 500 HIGH

acceptance_criteria: FAIL (1/17 AC passing = 6%)

smoke_test: 
  ✅ Backend health: 200 UP
  ✅ Frontend health: 200 UP  
  ✅ Auth/login: 200, JWT token issued
  ✅ S2-08 basic skeleton: 200, mock data working
  ❌ AQI endpoints: 500 errors
  ❌ ESG endpoints: 500 errors
  ❌ Error API: 500 errors
  ❌ S2-08 filtering: 500 errors

open:
  BLOCKER - Shared backend issue: 7+ API endpoints returning generic 500 "An unexpected error occurred"
  ROOT CAUSE - Likely database initialization, Flyway migration failure, or Spring DI issue
  IMPACT - S2-05, S2-06, S2-07 completely blocked; S2-08 partial block
  
  ACTION REQUIRED:
    1. Check full stack trace in backend logs (java.lang.Exception root cause)
    2. Verify Flyway schema migrations completed: SELECT * FROM flyway_schema_history
    3. Test database connectivity & TimescaleDB schema integrity
    4. Check Spring component initialization order
    
  STATUS: DO NOT MERGE ⛔
```

---

## EVIDENCE TABLE (Critical Failures)

| Test Case | Action | Expected | Actual | Status |
|-----------|--------|----------|--------|--------|
| S2-05: AQI Latest Data | GET /api/v1/environment/aqi/latest + Bearer token | HTTP 200, {aqi, pm25, timestamp} | **HTTP 500** "An unexpected error occurred" | ❌ FAIL |
| S2-05: Real-time Updates | GET /api/v1/notifications/subscribe (SSE) | HTTP 200, SSE stream | **HTTP 500** generic error | ❌ FAIL |
| S2-05: Trend Data | GET /api/v1/environment/aqi/history?days=7 | HTTP 200, array of readings | **HTTP 200, but `[]` empty** | ⚠️ PARTIAL |
| S2-05: Sensor Table | GET /api/v1/environment/sensors | HTTP 200, 8+ sensors online | **HTTP 200, 8 sensors ALL OFFLINE, null lastSeenAt** | ⚠️ PARTIAL |
| S2-06: KPI Cards | GET /api/v1/esg/kpi | HTTP 200, {energy, water, carbon} | **HTTP 500** | ❌ FAIL |
| S2-06: Monthly Chart | GET /api/v1/esg/summary/monthly | HTTP 200, monthly buckets | **HTTP 500** | ❌ FAIL |
| S2-06: Report Gen | POST /api/v1/esg/report/generate | HTTP 200/202, job ID | **HTTP 500** | ❌ FAIL |
| S2-06: Report Status | GET /api/v1/esg/report/latest-status | HTTP 200, status enum | **HTTP 500** | ❌ FAIL |
| S2-07: Error List | GET /api/v1/admin/errors | HTTP 200, error array | **HTTP 500** | ❌ FAIL |
| S2-07: Filter Errors | GET /api/v1/admin/errors?status=UNRESOLVED | HTTP 200, filtered | **HTTP 500** | ❌ FAIL |
| S2-08: Basic Skeleton | GET /api/v1/traffic/counts | HTTP 200, mock [{INT-001: 120}, {INT-002: 45}] | **HTTP 200, data correct** ✅ | ✅ PASS |
| S2-08: Filtering | GET /api/v1/traffic/counts?intersection=INT-001&from=...&to=... | HTTP 200/400 | **HTTP 500** | ❌ FAIL |
| Frontend Routes | GET /dashboard/environment, /esg, /data-quality, /traffic | HTTP 200 | **HTTP 200 for all** | ✅ PASS (4/4) |

---

## ACCEPTANCE CRITERIA BREAKDOWN

| Story | AC 1 | AC 2 | AC 3 | AC 4 | AC 5 | AC 6 | Pass % |
|-------|------|------|------|------|------|------|--------|
| **S2-05** (AQI Map+) | ❌ | ⚠️ | ⚠️ | ❌ | 🔒 | 🔒 | **0%** |
| **S2-06** (ESG KPI+) | ❌ | ❌ | ❌ | 🔒 | — | — | **0%** |
| **S2-07** (Error Review) | ❌ | ❌ | 🔒 | 🔒 | — | — | **0%** |
| **S2-08** (Traffic API) | 🔒 | ✅ | ❌ | — | — | — | **33%** |
| **TOTAL** | | | | | | | **1/17 = 6%** |

---

## ROOT CAUSE ANALYSIS

**Symptom:** 7+ independent API endpoints (AQI, ESG KPI, ESG Report, Error list, Traffic status) all return identical HTTP 500 generic error with no detail

**Pattern Indicators:**
- NOT endpoint-specific (would expect different errors per endpoint)
- NOT database connection (MySQL/PG would error differently per query)
- Likely: Shared initialization path, Spring component wiring, or Flyway migration

**Probable Root Causes (in order):**
1. **Flyway migration schema missing** → Services try to query non-existent tables → NullPointerException → 500
2. **Spring DI failure** → @Autowired repository/service = null → @Bean constructor fails → 500
3. **Database connection pool exhausted** → All requests timeout → 500
4. **HikariCP misconfiguration** → Connection leak → pool full → 500
5. **Kafka consumer thread death** → Affects alert/notification SSE → 500

**How to Debug:**
```bash
# Full backend logs with errors
docker logs uip-backend 2>&1 | grep -A10 "ERROR\|Exception\|stack trace"

# Check if migrations ran
docker exec uip-timescaledb psql -U postgres -d uip_db \
  -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_on DESC LIMIT 5;"

# Check pool status
docker logs uip-backend 2>&1 | grep -i "hikari\|pool\|connection"
```

---

## DEFECT LOG (Summary)

| Bug ID | Story | Severity | Title | Reproducibility | Fix Est |
|--------|-------|----------|-------|-----------------|---------|
| BUG-S2-05-01 | S2-05 | **🔴 CRITICAL** | AQI latest → 500 | 100% (deterministic) | 2-4h |
| BUG-S2-05-02 | S2-05 | **🔴 CRITICAL** | SSE subscribe → 500 | 100% | 2-4h |
| BUG-S2-05-03 | S2-05 | **🔴 CRITICAL** | AQI history empty | 100% | 2-4h |
| BUG-S2-05-04 | S2-05 | **🟠 HIGH** | Sensors all OFFLINE | 100% | 1-2h (data seed) |
| BUG-S2-06-01 | S2-06 | **🔴 CRITICAL** | All 4 ESG APIs → 500 | 100% | 2-4h |
| BUG-S2-07-01 | S2-07 | **🔴 CRITICAL** | Error list → 500 | 100% | 2-4h |
| BUG-S2-08-01 | S2-08 | **🟠 HIGH** | Traffic filter → 500 | 100% (w/ params) | 1-2h |

---

## NEXT STEPS

1. **[URGENT]** Backend teams investigate 500 errors:
   - [ ] Pull full error stack trace from `docker logs uip-backend` 
   - [ ] Check Flyway migration status
   - [ ] Verify Spring components initialized (grep for "Started Application")
   - [ ] ETA: 2-4 hours to root cause

2. **[Parallel]** Seed test data (1-2h):
   - [ ] Insert sensor readings into `environment.sensor_readings`
   - [ ] Insert AQI history into `environment.aqi_history`
   - [ ] Insert error records into `error_mgmt.errors`

3. **[Post-Fix]** Re-run full manual QA: 15-20 min per iteration

4. **[Decision]** Sprint 2 sign-off only after all 7 CRITICAL bugs resolved

---

**Report:** `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/.claude/workdir/test-report-2026-03-31.md` (full)

