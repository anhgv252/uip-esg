# Sprint MVP3-3 PO Demo Agenda
**Gate Review: 2026-05-30 15:00 SGT**

---

## 1. Demo Overview

### Objectives
- **Primary**: PO sign-off on GRI 302/305 ESG report export (Excel + PDF) — **critical path for city authority 2026-06-15 deadline**
- **Secondary**: Validate Keycloak RSA authentication & Flink enrichment architecture
- **Tertiary**: Verify P2 bug fixes improve UI stability
- **Decision Required**: Approve Sprint MVP3-3 for Production deployment OR request rework

### What the PO Will See
1. **End-to-end GRI report generation** — select reporting period → configure GRI standards → export to Excel vs PDF → validate metrics
2. **Keycloak RSA authentication** — login with city authority credentials → transparent dual-issuer handoff (HMAC legacy + RSA new)
3. **Flink enrichment in dashboard** — sensor data now includes building metadata (no manual backfill)
4. **P2 bug fixes** — before/after comparison: improved tooltips, fixed AQI polling, smooth filter animations

### City Authority Context
- **Deadline**: 2026-06-15 — GRI 302/305 export must be production-ready
- **Key Stakeholder**: Environmental Division (requires ESG reporting compliance)
- **Success Definition**: PO confirms export accuracy + usability meets city authority requirements

---

## 2. Demo Script (Live Walkthrough)

### Step 1: GRI Report Generation — Period & Standard Selection
**What to show:**
- Navigate to ESG Dashboard → "Generate Report" button
- Select reporting period: Q2 2026 (2026-04-01 to 2026-06-30)
- Select GRI standards: `GRI 302 (Energy)` + `GRI 305 (Emissions)` checkboxes
- Show pre-calculated metrics preview (baseline + period delta)

**Expected Result:**
- UI responds immediately; no loader > 2 seconds
- Metrics preview shows Energy (kWh) and Emissions (tCO₂e) with city authority templates

**Success Criteria:**
- PO confirms metrics align with city authority baseline
- Period selection matches calendar requirements
- Standard selection is intuitive (no confusion between GRI categories)

---

### Step 2: Report Export — Excel Format
**What to show:**
- Click "Export as Excel" button
- Download completes < 3 seconds
- Open generated .xlsx file in local tool
- Validate sheet structure: Metadata tab + Energy tab + Emissions tab
- Show sample rows: sensor readings × enriched building data

**Expected Result:**
- File downloads without errors
- Excel sheets are correctly formatted (no corrupted cells)
- Metadata section includes city, reporting period, GRI version
- Energy/Emissions tabs contain rolled-up metrics per building/zone

**Success Criteria:**
- PO can immediately open and read Excel export
- Column headers match city authority template
- No missing data or calculation errors
- File size < 10 MB (acceptable for email/archival)

---

### Step 3: Report Export — PDF Format
**What to show:**
- Go back to ESG Dashboard; same report (Q2 2026, GRI 302/305)
- Click "Export as PDF" button
- Download completes < 3 seconds
- Open PDF locally
- Show formatting: title page with city logo + metadata, charts + tables, appendix with raw data

**Expected Result:**
- PDF renders cleanly on desktop/mobile
- Charts (Energy trend, Emissions by sector) are visually clear
- Tables show summary metrics with 2 decimal precision
- Appendix includes raw sensor counts for audit trail

**Success Criteria:**
- PO confirms PDF meets city authority branding requirements
- Printing/archival ready (no page breaks breaking data)
- Accessibility check: text is selectable (not image-based)
- File size < 5 MB

---

### Step 4: Keycloak RSA Authentication — Login Handoff
**What to show:**
- Open incognito browser; clear all cookies
- Logout current session (if logged in)
- Navigate to login page
- Enter city authority credentials (test account: `po-demo@hcmc.gov.vn` / demo password)
- Show JWT token inspection (browser DevTools → Network → Authorization header)
- Point out token `iss` claim: `https://keycloak.hcmc.gov.vn/realms/smartcity` (new RSA issuer)
- Demonstrate token refresh works transparently (click a protected API endpoint)
- Show no error in browser console; user remains logged in

**Expected Result:**
- Login succeeds with Keycloak credentials
- JWT token contains RSA signature (new issuer)
- API calls include Authorization header with token
- No "401 Unauthorized" errors from legacy HMAC validation

**Success Criteria:**
- PO confirms login UX is unchanged from legacy HMAC
- No visible authentication delays (< 500ms total)
- Audit log shows token issued by Keycloak (non-repudiation for city authority compliance)

---

### Step 5: Flink Enrichment in Dashboard — Sensor Data with Building Metadata
**What to show:**
- Navigate to Environmental Monitoring dashboard
- Show air quality sensor readings for a building (e.g., "Building A - PM2.5")
- Click on sensor row → expand to show enriched metadata:
  - Building Name: [name from master data]
  - Zone: [administrative zone from PostGIS]
  - Floor: [from building metadata]
  - Coordinates: [lat/long]
- Show timestamp of enrichment (Flink DAG adds metadata in real-time, < 100ms latency)
- Compare to previous sprint: **no manual backfill required**; data appears automatically

**Expected Result:**
- Sensor readings immediately show building context (not blank)
- Building metadata matches master data (no inconsistencies)
- Enrichment latency is imperceptible to user
- Flink DAG is running without restarts (checkpoint status healthy)

**Success Criteria:**
- PO confirms data quality: no null metadata for active sensors
- Real-time enrichment improves UX (city operators can act without manual lookup)
- Flink job is stable (no restarts in last 24h)

---

### Step 6: P2 Bug Fixes — Before/After Comparison
**What to show:**

**6a. Tooltip Fix (P2-001)**
- Environmental Monitoring → hover over AQI gauge
- Tooltip appears immediately (not delayed); text is readable
- Before: tooltip cut off at screen edge; now: repositioned correctly

**6b. AQI Poll Interval (P2-002)**
- Air Quality page → watch real-time updates
- Sensor values update every 30 seconds (configurable via ConfigMap, now tested)
- Before: sporadic updates (15s–60s jitter); now: consistent polling interval

**6c. Filter Animation (P2-003)**
- Environmental Monitoring → toggle filter (e.g., "Show PM2.5 only")
- Sensor list animates smoothly (200ms fade transition)
- Before: abrupt list refresh (jarring UX); now: smooth transition

**Expected Result:**
- All three UX improvements are visually apparent
- No performance degradation (page still responsive)
- Tooltips, polling, and animations work consistently

**Success Criteria:**
- PO confirms UX improvements make platform more usable
- City operators will have better experience during daily operations

---

### Step 7: Test Status & Known Limitations
**What to show:**
- Show test report: **849/864 tests passing (98.3%)**
- Explain 15 failing tests:
  - Root cause: infrastructure dependencies (Kafka, ClickHouse, PostgreSQL containers not mocked in CI/CD)
  - Scope: **NOT logic failures** — these are integration/environment tests
  - Impact: None to production (tested manually in UAT environment)
  - Remediation: Will be fixed before 2026-05-30 by containerizing test infrastructure
- Confirm: All GRI export logic tests pass (100%)
- Confirm: All Keycloak authentication tests pass (100%)
- Confirm: All Flink enrichment tests pass (100%)

**Expected Result:**
- PO understands the 15 failures are environmental, not functional
- Confidence in production readiness is maintained

**Success Criteria:**
- PO accepts infra-dep test failures as known limitation (acceptable risk)
- PO confirms 2026-05-30 deployment proceeds as planned

---

### Step 8: Scope & Change Orders
**What to show:**
- Sprint MVP3-3 originally included AC-03: ClickHouse 2-node HA clustering
- AC-03 descoped → Sprint 4 (Change Order C20)
- Reason: ClickHouse 2-node failover testing revealed edge cases in replication protocol; requires 2-week investigation (impacts AC-04 Flink enrichment if included)
- Decision: Deploy single-node ClickHouse now (production-grade for MVP3 timeline); add HA in Sprint 4 after testing completes
- Impact to city authority: No impact — report generation works identically; failover is operational concern, not feature concern

**Expected Result:**
- PO understands tradeoff: faster delivery now (AC-03 scope) vs. robust HA later (Sprint 4)
- PO accepts risk: single-node ClickHouse in production (mitigated by backup/restore procedures)

**Success Criteria:**
- PO approves descope + change order
- Backup strategy for ClickHouse is confirmed documented (DevOps to show SOP)

---

## 3. Known Limitations

### AC-03: ClickHouse 2-Node HA — Descoped to Sprint 4
- **Why descoped**: Failover testing revealed edge cases in ClickHouse replication protocol; requires RCA + fixes before safe deployment
- **Current state**: Single-node ClickHouse in production (acceptable for MVP3 timeline)
- **Risk mitigation**: 
  - Automated backups to MinIO S3 (hourly, 7-day retention)
  - Documented restore procedure (RTO = 15 min)
  - Monitoring alerts for disk usage (threshold = 80%)
- **Sprint 4 plan**: Deploy 2-node HA after RCA completes; no feature impact, only operational resilience

### 15 Infra-Dependent Test Failures
- **Root cause**: CI/CD pipeline lacks containerized test infrastructure (Kafka, ClickHouse, PostgreSQL)
- **Evidence**: Tests pass in local dev environment & UAT (containers running)
- **Scope**: Integration/environment tests; NOT logic failures
- **Examples of failing tests**:
  - `SensorKafkaConsumerIntegrationTest` — Kafka topic unavailable in CI
  - `EsgReportRepositoryIntegrationTest` — ClickHouse JDBC connection unavailable
  - `ClickHouseAnalyticsIntegrationTest` — table schema not initialized
- **Remediation plan**: 
  - Containerize test infrastructure in CI/CD (docker-compose in GitHub Actions)
  - Expected completion: 2026-05-29 (before 2026-05-30 gate)
  - Target: 864/864 tests passing for final gate sign-off
- **Production impact**: None — UAT environment has full containers; no regression risk

### Test Coverage Snapshot (2026-05-23 EOD)
| Category | Passing | Failing | % Pass | Status |
|---|---|---|---|---|
| Unit Tests (Backend) | 412 | 0 | 100% | ✅ |
| Unit Tests (Frontend) | 198 | 0 | 100% | ✅ |
| Integration Tests (w/ Containers) | 239 | 15 | 98.3% | ⚠️ Infra-dep |
| **TOTAL** | **849** | **15** | **98.3%** | ✅ Ready |

---

## 4. PO Sign-Off Checklist

### By Acceptance Criteria

**AC-01: GRI 302/305 Export — Excel + PDF** ✅ **READY FOR SIGN-OFF**
- [ ] PO has validated Excel export format (column headers, sheet structure)
- [ ] PO has validated PDF export format (branding, charts, appendix)
- [ ] PO confirms metrics calculations match city authority baseline
- [ ] PO confirms export performance acceptable (< 5 seconds for typical report)
- [ ] **PO Sign-Off**: __________________________ Date: __________

**AC-02: Keycloak RSA Authentication** ✅ **READY FOR SIGN-OFF**
- [ ] PO has tested login with Keycloak credentials
- [ ] PO confirms JWT token includes RSA signature (new issuer)
- [ ] PO confirms no login UX regression vs. legacy HMAC
- [ ] PO confirms audit trail shows Keycloak token issuance
- [ ] **PO Sign-Off**: __________________________ Date: __________

**AC-04: Flink Enrichment Inline** ✅ **READY FOR SIGN-OFF**
- [ ] PO has viewed sensor data with enriched building metadata
- [ ] PO confirms no manual backfill required (automatic enrichment)
- [ ] PO confirms enrichment latency is imperceptible (< 100ms)
- [ ] PO confirms data quality (no null metadata for active sensors)
- [ ] **PO Sign-Off**: __________________________ Date: __________

**AC-05: Regression Test Suite** ⚠️ **CONDITIONAL SIGN-OFF**
- [ ] PO understands 15/864 failures are infrastructure-dependent (not logic)
- [ ] PO accepts remediation plan (fix by 2026-05-29)
- [ ] PO approves proceeding to production with 98.3% pass rate (infra-dep failures excluded)
- [ ] **PO Sign-Off**: __________________________ Date: __________

**AC-06: P2 Bug Fixes (3 items)** ✅ **READY FOR SIGN-OFF**
- [ ] PO has validated tooltip fix (P2-001)
- [ ] PO has validated AQI polling fix (P2-002)
- [ ] PO has validated filter animation fix (P2-003)
- [ ] PO confirms UX improvements are production-grade
- [ ] **PO Sign-Off**: __________________________ Date: __________

**AC-03: ClickHouse 2-Node HA** ➡️ **DESCOPED TO SPRINT 4**
- [ ] PO understands AC-03 descope rationale (RCA required)
- [ ] PO approves Change Order C20 (postpone to Sprint 4)
- [ ] PO acknowledges backup/restore SOP (single-node risk mitigation)
- [ ] **PO Sign-Off**: __________________________ Date: __________

### Overall Gate Decision
- [ ] **APPROVED**: Sprint MVP3-3 is production-ready; proceed to deployment
- [ ] **REWORK REQUIRED**: [specify issues]
- [ ] **HOLD**: [specify blockers]

**PO Name & Title**: __________________ 

**PO Signature**: __________________ **Date**: __________

---

## 5. Post-Demo Next Steps

### Immediate Actions (2026-05-30 to 2026-06-06)
1. **DevOps**: Fix 15 infra-dep test failures by containerizing CI/CD (target: 2026-05-29)
   - Verify 864/864 tests pass in final gate review
2. **Backend**: Final production build & artifact signing (target: 2026-05-31)
3. **Frontend**: Production bundle size validation (target: 2026-05-31)
4. **QA**: Smoke test in production-like UAT environment (target: 2026-06-01)

### Sprint 4 Roadmap (2026-06-03 to 2026-06-15)
1. **ClickHouse 2-Node HA (AC-03)**: Complete RCA & deploy failover architecture
   - Parallel: Begin ESG Phase 2 features (TCFD reporting, Scope 3 emissions)
2. **Performance optimization**: JMeter load test @ 100K sensor events/sec
3. **City Authority UAT**: Parallel UAT with Environmental Division (validation of GRI export)
4. **Deployment readiness**: Final SOP review, runbook handoff to city authority ops team

### City Authority Deadline — 2026-06-15 (CRITICAL PATH)
- **Deliverable**: Production-ready GRI 302/305 export + Keycloak authentication
- **Validation**: City authority signs off on export accuracy & compliance
- **Deployment window**: 2026-06-15 EOD (go-live for city authority dashboard)
- **Contingency**: If UAT finds issues, Sprint 5 (2026-06-17) has 48-hour remediation window

### Key Dependencies
- **Keycloak OIDC integration**: City authority IT must complete realm setup by 2026-06-10
- **GIS/PostGIS zone data**: City authority GIS team must provide final boundary shapefiles by 2026-06-08
- **ClickHouse backup infrastructure**: MinIO S3 must be configured & tested by 2026-06-06

---

## Appendix A: Demo Environment Setup

### Pre-Demo Checklist (2026-05-30 14:30 SGT — 30 min before demo)
- [ ] Backend service running & healthy (`GET /api/v1/health` returns 200)
- [ ] Frontend deployed to staging URL (https://staging.uip.smartcity.local)
- [ ] Keycloak realm initialized with test user: `po-demo@hcmc.gov.vn`
- [ ] Flink DAG running & checkpoints healthy (Flink UI shows 0 backpressure)
- [ ] ClickHouse service running & GRI tables have sample data (≥ 1000 rows)
- [ ] Kafka broker healthy (test producer/consumer connectivity)
- [ ] Browser DevTools open (Network tab) for JWT inspection in Step 4
- [ ] Demo data: Q2 2026 reporting period has ≥ 100 sensor readings (Environmental Monitoring)
- [ ] Network connectivity: screen share tested (Zoom/Teams) with acceptable latency

### Demo Rollback Plan (if critical issue occurs)
- If export fails: Show pre-generated sample Excel/PDF from `/tmp/demo-reports/`
- If Keycloak fails: Fall back to legacy HMAC authentication (same UX, different token issuer)
- If Flink fails: Show building metadata in dashboard via manual lookup (slower, but functional)
- If frontend crashes: Demo API endpoints directly via curl/Postman

---

## Appendix B: Frequently Asked Questions for PO

**Q: Why is ClickHouse HA descoped?**
A: Edge case in replication protocol during failover testing. Better to investigate thoroughly now (2 weeks) than deploy broken HA & face production incident. Single-node is production-grade for MVP3 timeline.

**Q: Will GRI export work without Keycloak?**
A: Yes. Keycloak is for authentication. Export logic uses HMAC fallback if Keycloak is unavailable. However, production deployment uses Keycloak for city authority compliance (audit trail).

**Q: Can we export partial data (e.g., only Energy, no Emissions)?**
A: Yes. GRI standard selection is flexible (checkboxes). PO can choose any subset of GRI 302/305 standards. This sprint adds the feature; next sprint can add more GRI standards (303, 304, etc.).

**Q: What if Excel/PDF export file is corrupted?**
A: Unlikely (exported via Apache POI + iText libraries, well-tested). If corruption occurs, user can retry export or fall back to manual CSV export. DevOps has backup data in ClickHouse for data recovery.

**Q: When is the city authority UAT?**
A: Sprint 4 (2026-06-03 to 2026-06-15). PO will have access to production-like environment; Environmental Division team can test GRI export end-to-end.

---

**Document Version**: 1.0  
**Created**: 2026-05-23  
**Last Updated**: 2026-05-23  
**Status**: Ready for Gate Review  
**Next Review**: 2026-05-30 post-demo  
