# Sprint 2 Demo Checklist for PO

**Date:** 2026-05-15  
**Duration:** 30-40 minutes  
**Audience:** Product Owner  
**Pre-Demo Setup Time:** 15 minutes (CORS fix)

---

## ✅ Pre-Demo Checklist (Run 15 minutes before)

### Step 1: Fix CORS Issue (if not already done)
```bash
# Check if fix is already applied
curl -i -X OPTIONS http://localhost:8080/api/v1/buildings \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Headers: x-tenant-id" 2>&1 | grep "x-tenant-id"

# If x-tenant-id is NOT in response, run:
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc/infrastructure
docker compose build --no-cache backend
docker compose up -d backend
sleep 15
```

### Step 2: Verify Infrastructure
```bash
✅ Backend: curl http://localhost:8080/api/v1/health
✅ Analytics: curl http://localhost:8082/api/v1/analytics/buildings -H "Authorization: Bearer $(TOKEN)"
✅ ClickHouse: curl "http://localhost:8123/?query=SELECT%201"
✅ Frontend: curl http://localhost:3000
```

### Step 3: Pre-Demo Setup Commands

```bash
# Terminal 1: Have this ready for data validation demo
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc

# Terminal 2: Have browser ready at localhost:3000
# Pre-login with: admin / admin_Dev#2026!

# Terminal 3: Have this command ready for API demo
TOKEN=$(curl -s http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' | jq -r .accessToken) && echo "Token ready: ${TOKEN:0:20}..."
```

---

## 📊 Demo Flow (30 minutes)

### Part 1: Infrastructure & Architecture (5 min)

**Talking Points:**
- Sprint 2 focuses on Analytics Foundation & ClickHouse go-live
- Real-time data ingestion: IoT sensors → Kafka → Flink → ClickHouse
- Analytics Service as separate microservice (port 8082)
- Dashboard pulls from ClickHouse for real-time insights

**Show:**
```bash
echo "=== Infrastructure Status ==="
docker compose ps | grep -E "backend|analytics|clickhouse|flink"

echo ""
echo "=== ClickHouse Data Volume ==="
curl -s "http://localhost:8123/?query=SELECT%20COUNT(*)%20FROM%20analytics.esg_readings" 
# Shows: 204506 ✅
```

### Part 2: API Demonstration (5 min)

**Talking Points:**
- Buildings API returns list of buildings in system
- Analytics Service exposes energy/carbon/water aggregate endpoints
- All APIs secured with JWT + tenant isolation

**Show:**
```bash
echo "=== Get Buildings List ==="
TOKEN=$(curl -s http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' | jq -r .accessToken)

curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/buildings | jq .
# Shows: [] (or building list if DB seeded)

echo ""
echo "=== Query Energy Aggregates ==="
curl -s -X POST http://localhost:8082/api/v1/analytics/energy-aggregate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "x-tenant-id: tenant_01" \
  -d '{
    "tenantId":"tenant_01",
    "buildingIds":["BLD-001"],
    "fromEpoch":1700100000,
    "toEpoch":1700186400,
    "groupBy":"day"
  }' | jq .
# Shows: ✅ Analytics service responding
```

### Part 3: Frontend Dashboard (15 min)

**Talking Points:**
- Cross-Building Analytics dashboard for comparing metrics
- Real-time data visualization  
- Configurable filters: date range, metric type, aggregation level
- Responsive design: desktop/tablet/mobile

**Show:**
1. Open http://localhost:3000 in browser
2. **Login Screen** → admin / admin_Dev#2026!
3. **Dashboard** → Buildings
4. **Cross-Building Analytics Page** → Show layout
   - Filter panel at top with:
     - "Select Buildings" button (multi-select, max 5)
     - Metric dropdown (Energy, Water, Carbon, etc.)
     - Group By dropdown (Day, Week, Month)
     - From/To date pickers
     - Reset button
5. **Click "Select Buildings"** → Show modal with building list
   - Select 2 buildings (e.g., "Toa Nha A", "Toa Nha B")
6. **Charts section** → Show empty state until data loaded
   - Note: "Charts will show when buildings are selected and data loads"
7. **Responsive demo** (optional):
   - Open DevTools (F12)
   - Toggle device toolbar: 1920x1080 → 768x1024 → 375x667
   - Show layout adapts

### Part 4: Quality Metrics (5 min)

**Show:**
```bash
echo "=== UAT Smoke Test Results ==="
python3 scripts/uat_smoke_test.py
# Target: 6-7/10 passed after demo pre-checks

echo ""
echo "=== Manual Test Cases Ready ==="
echo "- TC-S2-01: Analytics Dashboard loads ✅"
echo "- TC-S2-02 to TC-S2-08: Dashboard features (awaiting data)"
echo "- TC-S2-09: ClickHouse consistency (in progress)"
echo "- TC-S2-10, S2-11: Cutover testing (ready)"
```

---

## ❓ Q&A Topics (Prepare Answers)

### Q: When will real building data appear?
**A:** Once we integrate with production IoT sensors or run data simulator. Current demo uses synthetic seed data in ClickHouse.

### Q: Can we compare with TimescaleDB data?
**A:** Yes - both systems are running. TC-S2-09 validates consistency between them.

### Q: What about data freshness?
**A:** Analytics queries hit ClickHouse directly - ~2-5 second latency from sensor → ingestion → display.

### Q: Is multi-building comparison working?
**A:** The UI is ready. Once CORS fix is deployed, users can select up to 5 buildings for comparison.

### Q: Performance at scale?
**A:** Load test target: 10k events/sec. Currently seeded with 200k+ sample records.

---

## 🎯 Success Criteria

**Demo is successful if PO sees:**
- ✅ Infrastructure is healthy (no red flags)
- ✅ APIs respond correctly (direct testing)
- ✅ Dashboard UI loads without errors
- ✅ Data exists in ClickHouse (204k+ records)
- ✅ Filter controls are visible and functional
- ✅ Responsive design works at different breakpoints
- ⚠️ Data might not fully render (CORS fix may still be pending)

**Escalation Points:**
- 🔴 Backend not responding → Check Docker logs: `docker compose logs backend`
- 🔴 CORS still blocking → Rebuild backend and restart
- 🔴 ClickHouse empty → Re-run seed: `bash scripts/sprint2-api-test.sh --seed`

---

## 📝 Demo Notes Template

```
Demo Conducted: [Date/Time]
PO Feedback:
- What went well:
- What needs improvement:
- Blockers/concerns:
  
Next Steps:
1. 
2. 
3. 

Action Items:
- Owner:  Due Date: 
- Owner:  Due Date: 
```

---

## 🔧 Troubleshooting During Demo

| Issue | Quick Fix |
|-------|-----------|
| API 401 Unauthorized | Re-run login command to get fresh token |
| Dashboard page 404 | Refresh browser, check frontend logs |
| Buildings modal empty | CORS still not working - show API works directly in terminal |
| Charts not loading | Check browser F12 console for errors, test API endpoint separately |
| Responsive not working | Clear browser cache (Ctrl+Shift+Del), restart frontend if needed |

---

## 📌 Links & References

- Analytics Dashboard: http://localhost:3000/buildings/dashboard
- Backend API Docs: http://localhost:8080/swagger-ui.html
- Analytics Service Docs: http://localhost:8082/swagger-ui.html
- ClickHouse Web UI: http://localhost:8123 (no auth needed for localhost)
- Flink JobManager: http://localhost:8081
- Kafka UI: http://localhost:8090

---

**Demo Prepared By:** QA Lead  
**Date:** 2026-05-15  
**Status:** ✅ READY (pending final CORS verification)
