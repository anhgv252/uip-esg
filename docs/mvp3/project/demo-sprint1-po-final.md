# Sprint MVP3-1 — PO Demo Script (Final)
**Date:** 2026-05-14
**Sprint status:** COMPLETE — All gates PASS
**Audience:** Product Owner, City Authority ESG Lead
**Duration:** 30-40 phút
**Presenter:** Backend Lead + DevOps

---

## Demo Agenda

### Part 1: Foundation & Architecture (5 phút)
**Mục tiêu:** Cho PO thấy foundation vững cho Building Cluster v3.0

1. **ADR Overview** — 4 ADRs merged
   - ADR-026: ClickHouse pre-emptive adoption
   - ADR-027: Keycloak hybrid auth
   - ADR-028: Kong gateway scope
   - ADR-033: Cross-building tenant hierarchy

2. **Schema V26** — Multi-building table + RLS
   ```
   docker exec uip-timescaledb psql -U uip -d uip_smartcity -c \
     "SELECT building_code, tenant_id, cluster_id FROM public.buildings"
   ```

### Part 2: RLS Isolation Demo (5 phút)
**Mục tiêu:** Chứng minh tenant isolation — KHÔNG có data leak

3. **10 RLS scenarios** — live demo 3 key cases:
   ```bash
   # Tenant A sees only own buildings
   docker exec uip-timescaledb psql -U uip -d uip_smartcity -c \
     "SET app.tenant_id='hcm'; SELECT count(*) FROM public.buildings;"

   # Tenant B cannot see Tenant A
   docker exec uip-timescaledb psql -U uip -d uip_smartcity -c \
     "SET app.tenant_id='default'; SELECT count(*) FROM public.buildings WHERE tenant_id='hcm';"

   # Admin bypass (empty tenant_id)
   docker exec uip-timescaledb psql -U uip -d uip_smartcity -c \
     "SET app.tenant_id=''; SELECT count(*) FROM public.buildings;"
   ```

4. **Performance:** Rollup p95 = 2.3ms (target 500ms) trên 10M rows

### Part 3: Flink Dual-Sink Pipeline (10 phút) — CORE DEMO
**Mục tiêu:** Chứng minh real-time data flow Kafka → TimescaleDB + ClickHouse

5. **Flink UI** — http://localhost:8081
   - Show `EsgDualSinkJob` RUNNING
   - Show checkpoint stored in MinIO S3

6. **MinIO Console** — http://localhost:9001 (minioadmin/minioadmin)
   - Navigate to `uip-flink-checkpoints` bucket
   - Show checkpoint files (chk-xxx/_metadata)

7. **Inject real-time data:**
   ```bash
   python3 scripts/esg_dual_sink_test.py --messages 100 --timeout 30
   ```

8. **Verify dual-sink:**
   ```bash
   # TimescaleDB
   docker exec uip-timescaledb psql -U uip -d uip_smartcity -c \
     "SELECT count(*), max(timestamp) FROM esg.clean_metrics WHERE timestamp > NOW() - INTERVAL '5 minutes'"

   # ClickHouse
   docker exec uip-clickhouse clickhouse-client --query \
     "SELECT count(), max(recorded_at) FROM analytics.esg_readings WHERE recorded_at > now() - INTERVAL 5 MINUTE"
   ```

9. **Performance:** ClickHouse aggregate avg 4ms, p95 6ms

### Part 4: Kong + Keycloak Security (5 phút)
**Mục tiêu:** Chứng minh auth security cho Tier 2

10. **Kong blocks alg=none:**
    ```bash
    curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/api/v1/analytics/energy-aggregate \
      -H 'Authorization: Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJ0ZXN0In0.'
    ```
    Expected: 401

11. **Keycloak token grant:**
    ```bash
    curl -s -X POST http://localhost:8085/realms/uip/protocol/openid-connect/token \
      -d "client_id=uip-api&client_secret=uip-api-secret-dev&grant_type=password&username=operator-hcm&password=Operator#2026!"
    ```

### Part 5: Frontend + Analytics Dashboard (5 phút)
**Mục tiêu:** Cho PO thấy user-facing features

12. **Frontend** — http://localhost:3000
    - Navigate to `/buildings` — Cross-building dashboard
    - Multi-building selector: chọn 2-3 buildings, verify URL sync `?ids=B01,B02`
    - Reload → localStorage persist selection

13. **Analytics via Kong:**
    ```bash
    curl -s http://localhost:8000/api/v1/analytics/energy-aggregate \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" \
      -d '{"tenantId":"default","buildingIds":[],"fromEpoch":1747000000,"toEpoch":1749000000}'
    ```

### Part 6: Sprint 1 Gate Summary + Sprint 2 Preview (5 phút)

14. **Gate Results:**
    | Item | Status |
    |------|--------|
    | ADR-026, 027, 028, 033 | ✅ Merged |
    | Schema V26 | ✅ Deployed |
    | RLS 10 scenarios | ✅ 10/10 PASS |
    | RLS perf (10M rows) | ✅ p95=2.3ms |
    | Flink dual-sink | ✅ Running, checkpoint in MinIO |
    | Kong alg=none → 401 | ✅ |
    | analytics-service shadow | ✅ Diff 0.000000% |
    | API regression | ✅ 103/103 PASS |

15. **Sprint 2 Preview:**
    - analytics-service cutover (flip flag → ClickHouse queries live)
    - Cross-building ESG report (ISO 37120 + GRI format)
    - Hourly rollup MV cho intraday queries
    - Flink checkpoint remote storage (DONE — MinIO)
    - City Authority ESG format finalization

---

## Pre-Demo Checklist

- [ ] `docker compose up -d` — all services healthy
- [ ] Flink job RUNNING (http://localhost:8081)
- [ ] MinIO bucket `uip-flink-checkpoints` có checkpoint files
- [ ] Frontend accessible (http://localhost:3000)
- [ ] Keycloak realm loaded (http://localhost:8085)
- [ ] Kong proxy working (http://localhost:8000)
- [ ] Test data seeded (12.6M rows in TimescaleDB)
- [ ] Demo script tested end-to-end

## Backup Plan

- Nếu Docker stack crash → restart: `docker compose restart`
- Nếu Flink job FAILED → re-submit: `docker compose up -d flink-esg-job-submitter`
- Nếu ClickHouse empty → run: `python3 scripts/esg_dual_sink_test.py --messages 500`
- Nếu Kong auth bypass → check: `docker logs uip-kong`
