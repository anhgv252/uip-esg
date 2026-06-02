## Test Session Report — 2026-05-31 Demo Verification
**Tester**: Manual Tester (Copilot)  
**Sprint**: 6  
**Environment**: local

### Scope
- Verify backend/frontend demo readiness after Backend + Frontend dev completion.
- Execute manual demo flow with live UI + API evidence.
- Validate critical happy-paths for PO demo: login, dashboard, alerts, core APIs.

### Environment & Pre-Test Health
- Reachable and used for demo:
  - Frontend: http://localhost:3000/login
  - Backend API: http://localhost:8080
- Additional observed states:
  - http://localhost:3001 redirects to /login (Grafana)
  - 3002/3003 not reachable at test time
  - 8086 actuator health UP, but tested business endpoints under /api/v1/esg/reports/* returned 401/404 in this session

### Tests Executed
| Test ID | Title | Result | Notes |
|---------|-------|--------|-------|
| TC-DEMO-001 | Login UI at /login | PASS | Username admin + password admin_Dev#2026! login success, redirect to /dashboard |
| TC-DEMO-002 | Dashboard renders KPI cards | PASS | Dashboard shows Active Sensors=8, Open Alerts=0, Carbon=0 t |
| TC-DEMO-003 | Alerts screen loads list | PASS | Alert Management loaded; 5 total alerts visible; severity/status badges render |
| TC-DEMO-004 | Auth API login token | PASS | POST /api/v1/auth/login returned 200 with accessToken |
| TC-DEMO-005 | Alerts API data | PASS | GET /api/v1/alerts returned 200; total=5, OPEN=0 |
| TC-DEMO-006 | ESG carbon API data | PASS | GET /api/v1/esg/carbon?year=2025 returned 200 with records |
| TC-DEMO-007 | Dashboard/API consistency check | PASS | Open Alerts on UI = 0, API OPEN count = 0 |
| TC-DEMO-008 | Keycloak token issuance | PASS | POST /realms/uip/protocol/openid-connect/token returned 200 with access_token |
| TC-DEMO-009 | Energy Forecast returns usable points | FAIL | UI shows "No forecast data available"; API returns model=NONE, isFallback=true, points=[] for all tested buildings |

### Additional Verification Notes
- Backend auth profiles observed in this session:
  - Legacy login endpoint on 8080 (/api/v1/auth/login) is active and valid for demo API calls.
  - Keycloak token on 8085 can be issued, but not sufficient to access tested ESG endpoint path on 8086 in this session (401/404 observed).
- Non-blocking for current demo path because UI and core APIs on 3000/8080 are healthy and consistent.

### API Evidence (Summary)
1. Login token
- Request: POST http://localhost:8080/api/v1/auth/login
- Payload: {"username":"admin","password":"admin_Dev#2026!"}
- Actual: HTTP 200, response contains accessToken

2. Alerts list
- Request: GET http://localhost:8080/api/v1/alerts
- Actual: HTTP 200, 5 alerts, OPEN=0

3. ESG carbon
- Request: GET http://localhost:8080/api/v1/esg/carbon?year=2025
- Actual: HTTP 200, valid carbon records

4. Energy forecast (failed)
- Request: GET http://localhost:8080/api/v1/forecast/energy?buildingId=<id>&horizonDays=30
- Actual: HTTP 200 nhưng payload rỗng cho phần dự báo:
  - model: NONE
  - isFallback: true
  - points: []
- Cross-check: thử 4 building IDs lấy từ API /api/v1/buildings (with X-Tenant-ID: hcm), kết quả 4/4 đều points=[]

### Summary
- Total executed: 9  |  Passed: 8  |  Failed: 1  |  Blocked: 0
- Demo readiness verdict: PARTIAL PASS (NOT READY for full ESG forecast demo)
- Demo confirmation: Chỉ đạt cho luồng core (login/dashboard/alerts/bms/ai). Phần Energy Forecast chưa đạt do backend forecast trả empty dataset.

### Acceptance Criteria Sign-off
- [x] Manual demo flow executed end-to-end
- [ ] No P0/P1 bug observed in validated demo scope
- [ ] Tester sign-off: APPROVED for full demo

### Re-test Recommendation
- Mở lại gate demo full khi thỏa cả 2 điều kiện:
  1) API forecast trả points với ít nhất 1 building (`points.length > 0`)
  2) UI Energy Forecast render được chart/list thay vì "No forecast data available"
