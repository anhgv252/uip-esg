# UIP ESG POC — Rebuild Report 2026-06-01

**Executor**: UIP DevOps Engineer  
**Timestamp**: 2026-06-01  
**Status**: ❌ **FAILED — BLOCKER**

---

## 1. Backend Build: ❌ FAIL

### Command
```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc/backend
./gradlew clean build -x test
```

### Result
**BUILD FAILED in 29s**

### Root Cause
**SpotBugs Test Analysis Failed** — exit code 3

```
Execution failed for task ':spotbugsTest'.
> A failure occurred while executing com.github.spotbugs.snom.internal.SpotBugsRunnerForHybrid$SpotBugsExecutor
   > Verification failed: SpotBugs ended with exit code 3. 
   See report: file:///Users/anhgv/.../backend/build/reports/spotbugs/test.html
```

### SpotBugs Report Summary
- **Total Warnings**: 128
- **High Priority**: 54
- **Report Path**: `backend/build/reports/spotbugs/test.html`

### Impact
- No JAR artifact produced
- Cannot proceed with Docker rebuild
- Blocks deployment to test environment

---

## 2. Frontend Build: ❌ FAIL

### Command
```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc/frontend
npm install
```

### Result
**npm install FAILED**

### Root Cause
**Peer Dependency Conflict** — react-leaflet-cluster incompatible with @react-leaflet/core

```
npm error ERESOLVE could not resolve
npm error 
npm error While resolving: react-leaflet-cluster@4.1.3
npm error Found: @react-leaflet/core@2.1.0
npm error   from react-leaflet@4.2.1
npm error 
npm error Could not resolve dependency:
npm error peer @react-leaflet/core@"^3.0.0" from react-leaflet-cluster@4.1.3
npm error 
npm error Conflicting peer dependency: @react-leaflet/core@3.0.0
```

### Impact
- No node_modules installed
- Cannot run `npm run build`
- Cannot proceed with Docker rebuild

---

## 3. Docker Compose Rebuild: ⏸️ NOT EXECUTED

### Reason
Both backend and frontend builds failed. Docker rebuild skipped per DevOps protocol:
> "Nếu build fail, không tiếp tục docker rebuild mà dừng và báo lỗi"

---

## 4. Health Check: ⏸️ NOT EXECUTED

Skipped — no new services to health check.

---

## 5. Docker Compose Status (Current State)

Not queried — rebuild was blocked before this step.

---

## 🚨 BLOCKERS (Must Fix Before Test)

### Blocker #1: Backend SpotBugs Failures (128 warnings)
**Severity**: HIGH  
**Owner**: Backend Engineer (+ Solution Architect for code review)

**Options**:
1. **Quick workaround** (NOT recommended for production):
   ```bash
   ./gradlew clean build -x test -x spotbugsMain -x spotbugsTest
   ```
   - Bypasses static analysis
   - Security/quality risks unaddressed

2. **Proper fix** (RECOMMENDED):
   - Review SpotBugs report: `backend/build/reports/spotbugs/test.html`
   - Fix 54 High Priority warnings
   - Re-run build with SpotBugs enabled
   - Estimated effort: 2-4 hours

### Blocker #2: Frontend Dependency Conflict
**Severity**: HIGH  
**Owner**: Frontend Engineer

**Root Cause**:
- `react-leaflet@4.2.1` uses `@react-leaflet/core@^2.1.0`
- `react-leaflet-cluster@4.1.3` requires `@react-leaflet/core@^3.0.0`
- Incompatible peer dependencies

**Fix Options**:
1. **Downgrade** `react-leaflet-cluster` to version compatible with `@react-leaflet/core@2.x`
2. **Upgrade** `react-leaflet` to version 5.x (if stable)
3. **Temporary workaround**:
   ```bash
   npm install --legacy-peer-deps
   ```
   - Bypasses peer dependency check
   - May cause runtime issues

**Recommended Action**:
```bash
# Option 1: Check compatible versions
npm info react-leaflet-cluster peerDependencies
# Then adjust package.json

# Option 2: Emergency deployment
npm install --legacy-peer-deps && npm run build
```

---

## Next Steps

1. **Assign Blockers**:
   - Backend: SA + Backend Engineer → Fix SpotBugs warnings
   - Frontend: Frontend Engineer → Resolve dependency conflict

2. **Re-run Rebuild**:
   After fixes, re-execute:
   ```bash
   # Backend
   cd backend && ./gradlew clean build -x test
   
   # Frontend
   cd frontend && npm install && npm run build
   
   # Docker
   cd infrastructure && docker compose build --no-cache backend frontend
   docker compose up -d backend frontend
   ```

3. **Health Check**:
   ```bash
   sleep 30
   curl -sf http://localhost:8080/actuator/health
   curl -sf http://localhost:3000
   ```

---

## DevOps Review Checklist (After Fix)

When builds succeed, verify:
- [ ] Backend JAR created: `backend/build/libs/uip-backend-*.jar`
- [ ] Frontend dist created: `frontend/dist/index.html`
- [ ] Docker images built: `docker images | grep uip`
- [ ] Containers running: `docker compose ps`
- [ ] Backend health: `curl http://localhost:8080/actuator/health`
- [ ] Frontend HTTP 200: `curl -I http://localhost:3000`
- [ ] No resource limit violations: `docker stats --no-stream`

---

**Report End** — Tester should NOT proceed until blockers are resolved.
