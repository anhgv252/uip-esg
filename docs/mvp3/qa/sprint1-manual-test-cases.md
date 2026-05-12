# Sprint MVP3-1 — Manual Test Cases

**Sprint:** MVP3-1 | **Total:** 10 TC | **Priority P0:** 6 | **P1:** 4

---

## TC-001: Building List — Tenant Isolation (P0)

**Story:** v3-BE-01 | **Type:** API + Isolation  
**Pre-condition:** V26 migration deployed; seed buildings exist for 'hcm' and 'default' tenants

**Steps:**
1. Login as `hcm` tenant user, obtain JWT
2. `GET /api/v1/buildings` with `X-Tenant-ID: hcm`
3. Inspect response

**Expected:** Response contains only buildings with `tenantId = "hcm"`. No `default` tenant buildings visible.  
**Pass Criteria:** ≥ 2 buildings for hcm; 0 buildings from default tenant

---

## TC-002: Building Create — Valid Request (P0)

**Story:** v3-BE-01 | **Type:** API functional

**Steps:**
1. `POST /api/v1/buildings` with `X-Tenant-ID: hcm`
   ```json
   {"buildingCode": "BLD-TEST-001", "buildingName": "Test Tower", "floorCount": 5, "totalAreaM2": 5000}
   ```
2. Inspect response

**Expected:** HTTP 201; response body contains `id`, `buildingCode: "BLD-TEST-001"`, `isActive: true`  
**Cleanup:** Note building ID for cleanup

---

## TC-003: Building Create — Duplicate Code (P1)

**Story:** v3-BE-01 | **Type:** Negative

**Steps:**
1. `POST /api/v1/buildings` with `X-Tenant-ID: hcm` and `buildingCode: "BLD-HCM-001"` (already seeded)
2. Inspect response

**Expected:** HTTP 400 Bad Request; error message mentions duplicate building code

---

## TC-004: Cross-Building Aggregation — Happy Path (P0)

**Story:** v3-BE-02 | **Type:** API functional + Performance

**Steps:**
1. `POST /api/v1/analytics/cross-building/aggregate` with `X-Tenant-ID: hcm`
   ```json
   {
     "buildingCodes": ["BLD-HCM-001", "BLD-HCM-002"],
     "metricType": "energy",
     "from": "2026-04-01T00:00:00Z",
     "to": "2026-04-30T23:59:59Z"
   }
   ```
2. Measure response time
3. Inspect response

**Expected:** HTTP 200; 2 result items, each with `buildingCode`, `totalValue`, `avgValue`, `dataPoints`  
**Performance:** Response time < 2s (Sprint 1 baseline; Sprint 2 gate = <500ms)

---

## TC-005: Cross-Building Aggregation — Foreign Building (P0)

**Story:** v3-BE-02 | **Type:** Security/Isolation

**Steps:**
1. `POST /api/v1/analytics/cross-building/aggregate` with `X-Tenant-ID: hcm`
   ```json
   {
     "buildingCodes": ["BLD-HCM-001", "BLD-DEFAULT-001"],
     "metricType": "energy",
     "from": "2026-04-01T00:00:00Z",
     "to": "2026-04-30T23:59:59Z"
   }
   ```

**Expected:** HTTP 403 Forbidden; `BLD-DEFAULT-001` belongs to 'default' tenant, not 'hcm'  
**Critical:** This test MUST PASS before any PR merge (ISO-002 hard block)

---

## TC-006: Cross-Building Aggregation — Exceeds Max 5 Buildings (P1)

**Story:** v3-BE-02 | **Type:** Negative/Validation

**Steps:**
1. `POST /api/v1/analytics/cross-building/aggregate` with 6 building codes in request

**Expected:** HTTP 400 Bad Request; error mentions "Max 5 buildings per request"

---

## TC-007: RLS Direct SQL Isolation (P0)

**Story:** v3-BE-01 | **Type:** Database isolation  
**Pre-condition:** psql access to database (or test DB endpoint)

**Steps:**
```sql
-- Step 1: Set tenant context
SELECT set_config('app.tenant_id', 'hcm', false);

-- Step 2: Query buildings
SELECT building_code, tenant_id FROM public.buildings;
```

**Expected:** Only rows with `tenant_id = 'hcm'` returned. `default` tenant rows NOT visible.  
**Note:** RLS enforced at DB level, independent of application code

---

## TC-008: Capability Flag — Tier 1 (No Property Set) (P0)

**Story:** v3-EXT-02 | **Type:** Configuration

**Steps:**
1. Start backend with no `uip.capabilities.analytics-external` property set (default)
2. Check startup logs

**Expected:** Log line: `[Capability] analytics-external=false → TimescaleDbAnalyticsAdapter loaded`  
**NOT expected:** `ClickHouseRestAnalyticsAdapter` should NOT appear in logs  
**Gate:** Verifies `matchIfMissing=true` behavior — Tier 1 zero-regression

---

## TC-009: ClickHouse POC Health Check (P1)

**Story:** v3-DevOps-01 | **Type:** Infrastructure smoke

**Steps:**
```bash
cd infra/clickhouse
docker-compose -f docker-compose.poc.yml up -d
sleep 30
curl -s http://localhost:8123/ping
```

**Expected:** Response: `Ok.`  
**Also check:** `docker-compose ps` shows `uip-clickhouse-poc` in `Up (healthy)` state

---

## TC-010: Building Selector UI — Select and Persist (P1)

**Story:** v3-FE-01/02 | **Type:** Frontend functional

**Steps:**
1. Navigate to `/buildings` in browser
2. Click "Select Buildings" button
3. Search for a building
4. Select 2 buildings
5. Click "Done"
6. Check URL
7. Reload page

**Expected:**
- BuildingContextBar shows 2 selected building chips
- URL contains `?ids=<id1>,<id2>`
- After reload: same 2 buildings still selected (localStorage persist)  
**Note:** Backend may not have data yet; selector should show loading state gracefully

---

## Test Execution Tracking

| TC | Tester | Date | Status | Bug ID |
|----|--------|------|--------|--------|
| TC-001 | | | PENDING | |
| TC-002 | | | PENDING | |
| TC-003 | | | PENDING | |
| TC-004 | | | PENDING | |
| TC-005 | | | PENDING | |
| TC-006 | | | PENDING | |
| TC-007 | | | PENDING | |
| TC-008 | | | PENDING | |
| TC-009 | | | PENDING | |
| TC-010 | | | PENDING | |
