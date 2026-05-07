# Sprint 6 — Runbook Drill Checklist

**Ngày thực hiện:** _______________
**Người thực hiện:** _______________

## Drill 1: Deploy Procedure

### Prerequisites
- [ ] Docker Desktop đang chạy
- [ ] PostgreSQL (TimescaleDB) healthy: `docker ps | grep uip-timescaledb`
- [ ] Redis healthy: `docker ps | grep uip-redis`
- [ ] Kafka healthy: `docker ps | grep uip-kafka`
- [ ] EMQX running: `docker ps | grep uip-emqx`

### Backend Deploy
- [ ] Build JAR: `cd backend && ./gradlew bootJar -x test`
- [ ] Verify JAR exists: `ls backend/build/libs/*.jar`
- [ ] Stop old backend process (nếu đang chạy)
- [ ] Start new: `java -jar build/libs/uip-backend-*.jar`
- [ ] Wait for startup: `until curl -sf http://localhost:8080/actuator/health; do sleep 2; done`
- [ ] Health check trả `{"status":"UP"}`

### Frontend Deploy
- [ ] Build: `cd frontend && npm run build`
- [ ] Verify dist/: `ls frontend/dist/index.html`
- [ ] Serve: `npx vite preview --port 3000`
- [ ] Frontend load được tại http://localhost:3000

### Smoke Test Post-Deploy
- [ ] Health: `curl http://localhost:8080/actuator/health` → 200
- [ ] Login: `curl -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin_Dev#2026!"}'` → 200
- [ ] Sensors: `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/environment/sensors` → 200
- [ ] ESG: `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/esg/summary` → 200
- [ ] Alerts: `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/alerts` → 200

### Rollback Trigger Criteria
- Health endpoint trả status != UP
- Error rate > 5% trên bất kỳ API endpoint nào
- Login flow fail
- Response time p95 > 2s

**Drill 1 Result:** PASS / FAIL
**Notes:** _________________________________

---

## Drill 2: Rollback Procedure

### Detect Failure
- [ ] Health check fail: `curl -sf http://localhost:8080/actuator/health || echo "FAIL"`
- [ ] Hoặc error rate cao: check application logs
- [ ] Xác nhận version hiện tại: `grep version build.gradle`

### Rollback Steps
- [ ] Stop backend hiện tại: kill process
- [ ] Restore JAR version trước đó: `git log --oneline -5` → checkout previous commit
- [ ] Rebuild: `./gradlew bootJar -x test`
- [ ] Start restored version
- [ ] Wait for health: `until curl -sf http://localhost:8080/actuator/health; do sleep 2; done`

### Frontend Rollback
- [ ] Checkout previous frontend commit
- [ ] Rebuild: `npm run build`
- [ ] Redeploy dist/

### Database Migration Rollback (if applicable)
- [ ] Kiểm tra Flyway migration history: `SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;`
- [ ] Nếu migration mới gây lỗi, restore từ backup (xem Drill 3)
- [ ] Verify schema tương thích với rollback version

### Verification After Rollback
- [ ] Health check pass
- [ ] Login flow hoạt động
- [ ] Main APIs trả 200
- [ ] Frontend render đúng

**Drill 2 Result:** PASS / FAIL
**Notes:** _________________________________

---

## Drill 3: Database Restore Procedure

### Pre-Conditions
- [ ] PostgreSQL container đang chạy
- [ ] Có quyền truy cập pg_dump

### Step 1: Backup Current Database
```bash
docker exec uip-timescaledb pg_dump -U uip uip_smartcity > backup_pre_drill.sql
```
- [ ] Backup file tạo thành công
- [ ] Verify: `wc -l backup_pre_drill.sql` > 0

### Step 2: Simulate Data Issue
- [ ] Connect to DB: `docker exec -it uip-timescaledb psql -U uip uip_smartcity`
- [ ] Note current counts:
  ```sql
  SELECT 'users' as tbl, count(*) FROM users
  UNION ALL SELECT 'sensors', count(*) FROM sensors
  UNION ALL SELECT 'alerts', count(*) FROM alert_event;
  ```
- [ ] Record counts: users=____, sensors=____, alerts=____

### Step 3: Restore from Backup
```bash
# Drop and recreate
docker exec uip-timescaledb psql -U uip -c "DROP DATABASE uip_smartcity;"
docker exec uip-timescaledb psql -U uip -c "CREATE DATABASE uip_smartcity;"
# Restore
cat backup_pre_drill.sql | docker exec -i uip-timescaledb psql -U uip uip_smartcity
```
- [ ] Restore hoàn thành không lỗi

### Step 4: Verify Data Integrity
- [ ] Row counts khớp với Step 2
- [ ] Foreign keys intact: `SELECT count(*) FROM users WHERE tenant_id IS NOT NULL;`
- [ ] Tenant isolation: data không bị cross-contamination
- [ ] Flyway history intact: `SELECT count(*) FROM flyway_schema_history;`

### Step 5: Application Restart & Smoke Test
- [ ] Restart backend
- [ ] Health check pass
- [ ] Login hoạt động
- [ ] Data hiển thị đúng trên dashboard

**Drill 3 Result:** PASS / FAIL
**Notes:** _________________________________

---

## Drill Summary

| Drill | Result | Duration | Issues |
|-------|--------|----------|--------|
| 1. Deploy | PASS/FAIL | ___ min | |
| 2. Rollback | PASS/FAIL | ___ min | |
| 3. DB Restore | PASS/FAIL | ___ min | |

**Overall:** All 3 drills PASS? YES / NO
**Sign-off:** _______________ Date: _______________
