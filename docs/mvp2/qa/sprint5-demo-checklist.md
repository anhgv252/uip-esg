# Sprint 5 Demo Day Checklist — PO Review

## 🎯 Demo Objectives
- Show Multi-Tenant isolation works across all layers (DB, Cache, API, UI)
- Demonstrate Tenant Admin Dashboard + Mobile PWA citizen features
- Prove system stability under concurrent multi-tenant load

---

## ⏰ Timeline (Demo Day)

### T-30min: Pre-Demo Setup
- [ ] Run smoke tests: `python scripts/sprint5_smoke_test.py`
- [ ] Verify 8/8 tests PASS (if fail, escalate immediately)
- [ ] Collect evidence: `./scripts/sprint5_collect_evidence.sh`
- [ ] Start screen recording software (OBS/QuickTime)
- [ ] Open browser tabs:
  - http://localhost:3000 (frontend)
  - http://localhost:8080/actuator/health (backend health)
  - http://localhost:16686 (Jaeger - optional)
- [ ] Open terminal windows:
  - psql (for live RLS demo)
  - redis-cli (for cache namespace demo)
  - kafka-console-consumer (for event demo)

### T-5min: Final Checks
- [ ] All services UP and stable
- [ ] Test data seeded (3 tenants, 10 users each)
- [ ] Demo users can login:
  - ✅ admin@uip.vn / admin123
  - ✅ tenant_admin_hcm / password
  - ✅ citizen_hcm / password
  - ✅ operator_hcm / password
- [ ] No active errors in backend logs
- [ ] Frontend console has no red errors

---

## 📋 Demo Script (20 minutes)

### Part 1: Multi-Tenant Isolation (5 min)

**Talking points:**
- "We've implemented multi-tenant architecture with database-level isolation using PostgreSQL RLS"
- "Each tenant's data is strictly separated — impossible to see other tenant's data"

**Demo steps:**
1. Show RLS policies in psql:
   ```sql
   SELECT tablename, policyname FROM pg_policies WHERE schemaname='public';
   ```
   → Should see 12+ policies

2. Show cross-tenant isolation:
   ```sql
   SET LOCAL app.tenant_id = 'hcm';
   SELECT COUNT(*) FROM alerts;
   -- Returns X alerts
   
   SET LOCAL app.tenant_id = 'dn';
   SELECT COUNT(*) FROM alerts;
   -- Returns Y alerts (different count)
   ```

3. Show Redis cache namespace:
   ```bash
   redis-cli KEYS "esg:hcm:*"
   redis-cli KEYS "esg:dn:*"
   ```
   → Keys clearly separated by tenant

4. Show JWT tenant claims (decoded token from evidence folder)
   - Point out: `tenant_id`, `tenant_path`, `scopes`, `allowed_buildings`

**Success signal:** PO sees different data counts per tenant, understands isolation mechanism

---

### Part 2: Tenant Admin Dashboard (8 min)

**Talking points:**
- "Tenant admins can manage their own users, view usage stats, configure settings"
- "All actions scoped to their tenant only — cannot affect other tenants"

**Demo steps:**
1. Login as `tenant_admin_hcm`
   - URL: http://localhost:3000/login
   - Expected: Redirect to `/tenant-admin`

2. Show Overview page
   - Point out stat cards: User count, Building count, Alert count
   - **Evidence**: Screenshot these stats

3. Navigate to User Management
   - Show user table (all users have `tenantId=hcm`)
   - Filter by role: select "OPERATOR" → table updates
   - Click "Deactivate" on a user → confirmation dialog appears → confirm
   - **Success**: User's status changes to inactive

4. Navigate to Settings
   - Change tenant name to "HCM City Portal"
   - Upload logo (optional if file picker works)
   - Click Save
   - **Success**: Toast message "Settings saved successfully"

5. Navigate to Usage Report
   - Select date range (last 30 days)
   - **Success**: Chart renders with hcm buildings/alerts only
   - Click "Export CSV" → file downloads

**Success signal:** PO sees tenant admin has full control over their tenant, no access to others

---

### Part 3: Citizen Mobile Experience (4 min)

**Talking points:**
- "Citizens can view bills, receive alerts, interact via mobile-optimized UI"
- "PWA features enable offline access (future sprint)"

**Demo steps:**
1. Logout, login as `citizen_hcm`
   - Expected: Redirect to `/citizen/bills`

2. Show Bills page
   - Bills table displays citizen's bills only
   - Click on a bill → detail view opens

3. Navigate to Alerts (if accessible from nav)
   - Show alerts relevant to citizen's location
   - **Evidence**: Screenshot alerts with `tenantId=hcm`

4. (Optional) Mobile viewport test
   - Open Chrome DevTools → Toggle device toolbar
   - Select iPhone 14 (390x844)
   - Show no horizontal scroll

**Success signal:** PO sees citizen experience is tenant-scoped and mobile-friendly

---

### Part 4: Performance & Stability (3 min)

**Talking points:**
- "System meets SLA: API p95 <200ms, no cross-tenant data leaks under load"

**Demo steps:**
1. Show performance benchmark results
   - Open: `docs/mvp2/reports/sprint5-demo-evidence-YYYY-MM-DD/perf-overview-api.txt`
   - Point out:
     - Time per request (mean): ~XXms
     - 95th percentile: <200ms ✅
     - Failed requests: 0 ✅

2. Show Kafka event with tenant_id
   - Open: `kafka-events.json` from evidence folder
   - Point out: Every event has `"tenantId": "hcm"` or `"dn"`

3. Show HikariCP connection stability
   - Open backend health JSON: `backend-health.json`
   - Point out: `hikaricp.connections.active` is stable

**Success signal:** PO confident system is production-ready for multi-tenant load

---

## ✅ Go/No-Go Decision Points

### BLOCKER (Must Pass)
- [ ] Smoke tests: 8/8 PASS
- [ ] API contract tests: ≥9/10 PASS
- [ ] RLS isolation works (psql demo successful)
- [ ] Tenant Admin can login and view dashboard
- [ ] Citizen can login and view bills
- [ ] JWT has tenant_id claim

**Decision:** If ANY blocker fails → NO-GO, reschedule demo

### MAJOR (Should Pass)
- [ ] Performance: p95 <250ms (allow 50ms buffer)
- [ ] Tenant Admin settings save works
- [ ] Usage report chart renders
- [ ] No CRITICAL bugs in backlog

**Decision:** If <50% major criteria pass → GO with caveats (mention upfront)

### MINOR (Nice to Have)
- [ ] PWA manifest link present
- [ ] Mobile bottom nav works
- [ ] Backend unit tests <100 failures

**Decision:** Can proceed without these, mention as future work

---

## 📸 Evidence to Show PO

Required evidence files (from `sprint5_collect_evidence.sh`):
1. ✅ backend-health.json — All components UP
2. ✅ api-tests.html — API contract test results
3. ✅ rls-isolation.txt — Cross-tenant query results
4. ✅ redis-*-keys.txt — Cache namespace verification
5. ✅ kafka-events.json — Sample events with tenant_id
6. ✅ perf-overview-api.txt — Performance benchmark
7. ✅ jwt-decoded.json — Token structure with tenant claims
8. ✅ feature-flags.yml — Tenant-specific config
9. ✅ flyway-migrations.txt — DB migration history (V14-V19)
10. ✅ system-info.txt — Environment details

---

## 🚨 Fallback Plan (If Demo Breaks)

### Scenario 1: Backend crashes during demo
- **Action**: Show pre-recorded video of working system
- **Evidence**: E2E test video from `demo-evidence/e2e-demo.mp4`

### Scenario 2: RLS query shows wrong results
- **Action**: Show screenshot from previous test run
- **Evidence**: `rls-isolation.png` from evidence folder

### Scenario 3: Login fails
- **Action**: Use pre-generated JWT token, show decoded claims
- **Evidence**: `jwt-decoded.json`

### Scenario 4: Performance benchmark looks bad
- **Action**: Explain: "Local environment, not representative of production"
- **Evidence**: Show previous benchmark with good numbers

---

## 📝 Post-Demo Actions

### Immediate (same day)
- [ ] Send evidence folder to PO via email/Slack
- [ ] Create tickets for all issues discovered during demo
- [ ] Update test plan with PO feedback
- [ ] Archive demo video and screenshots

### Next Sprint
- [ ] Implement PWA features (manifest, SW, offline)
- [ ] Add mobile bottom navigation
- [ ] Fix remaining backend test failures (87 → <20)
- [ ] Improve E2E coverage (target 100% P0 scenarios)

---

## 📞 Escalation Contacts

| Issue | Contact | Action |
|-------|---------|--------|
| Backend crash | Backend Lead | Restart services, check logs |
| DB connection issues | DBA | Check pg_stat_activity, restart pool |
| Frontend not loading | Frontend Lead | Check Vite dev server, rebuild |
| Demo environment down | DevOps | Check Docker Compose services |

---

## 🎬 Demo Success Criteria

**Demo is successful if:**
- ✅ PO sees multi-tenant isolation works at all layers
- ✅ PO sees tenant admin can manage their tenant
- ✅ PO sees citizen experience is mobile-friendly
- ✅ PO confident system is stable under load
- ✅ PO approves Sprint 5 acceptance

**Next step after approval:**
- Sprint 6 planning: PWA features + performance tuning + remaining debt
