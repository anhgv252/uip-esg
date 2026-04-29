# ADR-023: RLS Migration — Zero-Downtime Strategy

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect, DevOps Lead
**Scope**: MVP2 — áp dụng cho story MVP2-07a (enable RLS trên production)

---

## Context

ADR-010 quyết định dùng Row-Level Security (RLS) cho T2 multi-tenancy. Migration V15 cần enable RLS trên 4 tables:

| Table | Schema | Đặc điểm |
|-------|--------|----------|
| `sensor_readings` | `environment` | Bảng lớn nhất, có thể >10M rows |
| `clean_metrics` | `esg` | Medium size, write-heavy |
| `alert_events` | `esg` | Medium size, write-heavy |
| `traffic_counts` | `traffic` | Medium size, write-heavy |

### Vấn đề kỹ thuật

Cả hai lệnh RLS đều yêu cầu **ACCESS EXCLUSIVE lock**:

```sql
ALTER TABLE ... ENABLE ROW LEVEL SECURITY;   -- ACCESS EXCLUSIVE lock
ALTER TABLE ... FORCE ROW LEVEL SECURITY;    -- ACCESS EXCLUSIVE lock
```

ACCESS EXCLUSIVE lock **block tất cả reads và writes** trên table — kể cả `SELECT`. Trên bảng lớn (`sensor_readings` >10M rows), nếu lock giữ quá lâu sẽ gây downtime rõ rệt.

Tuy nhiên, RLS DDL operations là **metadata-only** — PostgreSQL chỉ thay đổi flag trong `pg_class`, không rewrite data. Điều này có nghĩa thời gian hold lock cực ngắn (<1 giây per operation) nếu không có long-running transaction nào cản trở.

### Ràng buộc

- Production deployment cần **zero-downtime hoặc minimal downtime** (T1 customer SLA).
- Flyway chạy migration trong 1 transaction — toàn bộ V15 phải hoàn thành hoặc rollback.
- Không thể dùng `pg_repack` hoặc online DDL tools vì RLS là metadata operation, không phải data rewrite.

---

## Decision

### Chọn Option 1: Apply during low-traffic window

RLS DDL operations là metadata-only, không rewrite data. Thực tế lock time cực ngắn:

- 4 tables x 3 operations (ENABLE RLS + CREATE POLICY + FORCE RLS) = 12 operations
- Mỗi operation hold ACCESS EXCLUSIVE lock <1 giây
- **Tổng lock time dự kiến <5 giây** cho toàn bộ V15

Chạy trong maintenance window **2:00–3:00 AM** — thời điểm T1 customer gần như không có traffic.

### Cấu trúc V15 migration

```sql
-- V15__enable_rls_policies.sql
-- Chạy trong maintenance window 2:00-3:00 AM

-- ============================================
-- Step 0: Backfill tenant_id (nếu còn NULL)
-- ============================================
-- Đã hoàn thành ở V14 migration, verify tại đây
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM environment.sensor_readings WHERE tenant_id IS NULL LIMIT 1) THEN
        RAISE EXCEPTION 'tenant_id backfill chưa hoàn thành trên sensor_readings';
    END IF;
    -- Tương tự cho các bảng khác...
END $$;

-- ============================================
-- Step 1: ENABLE ROW LEVEL SECURITY
-- ============================================
ALTER TABLE environment.sensor_readings ENABLE ROW LEVEL SECURITY;
ALTER TABLE esg.clean_metrics          ENABLE ROW LEVEL SECURITY;
ALTER TABLE esg.alert_events           ENABLE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_counts     ENABLE ROW LEVEL SECURITY;

-- ============================================
-- Step 2: CREATE POLICY
-- ============================================
CREATE POLICY tenant_isolation ON environment.sensor_readings
    USING (tenant_id = current_setting('app.tenant_id'));

CREATE POLICY tenant_isolation ON esg.clean_metrics
    USING (tenant_id = current_setting('app.tenant_id'));

CREATE POLICY tenant_isolation ON esg.alert_events
    USING (tenant_id = current_setting('app.tenant_id'));

CREATE POLICY tenant_isolation ON traffic.traffic_counts
    USING (tenant_id = current_setting('app.tenant_id'));

-- ============================================
-- Step 3: FORCE ROW LEVEL SECURITY
-- ============================================
-- FORCE áp dụng cho table owner (superuser bypass)
-- Bắt buộc để đảm bảo application role cũng bị RLS filter
ALTER TABLE environment.sensor_readings FORCE ROW LEVEL SECURITY;
ALTER TABLE esg.clean_metrics          FORCE ROW LEVEL SECURITY;
ALTER TABLE esg.alert_events           FORCE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_counts     FORCE ROW LEVEL SECURITY;
```

### Deployment procedure

```
1. Thông báo maintenance window cho T1 customer (24h trước)
2. Verify: không có long-running transaction trên 4 tables
   SELECT pid, now() - xact_start AS duration, query
   FROM pg_stat_activity
   WHERE query LIKE '%sensor_readings%' AND state = 'active';
3. Chạy Flyway migration V15
4. Verify: RLS enabled và policy tồn tại
   SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname IN ('environment','esg','traffic');
5. Smoke test: application CRUD hoạt động đúng với tenant context
6. Thông báo maintenance hoàn tất
```

---

## Options đã đánh giá

### Option 1: Apply during low-traffic window (SELECTED)

| Tiêu chí | Đánh giá |
|----------|----------|
| Lock time | <5 giây tổng (metadata-only) |
| Complexity | Thấp — 1 migration file, 1 deployment step |
| Risk | Thấp — lock ngắn, rollback đơn giản |
| SLA impact | ~1-2 giây latency spike cho queries đang chạy lúc đúng thời điểm ALTER |

**Khi nào đủ**: T1 customer traffic thấp vào đêm. Metadata-only DDL cực nhanh.

### Option 2: Multi-step migration

Tách V15 thành 3 migration files chạy cách nhau vài phút:

```
V15a: ENABLE ROW LEVEL SECURITY  (metadata)
-- đợi 1-2 phút --
V15b: CREATE POLICY              (metadata)
-- đợi 1-2 phút --
V15c: FORCE ROW LEVEL SECURITY   (metadata)
```

| Tiêu chí | Đánh giá |
|----------|----------|
| Lock time | Mỗi bước <2 giây, nhưng tổng thời gian >5 phút |
| Complexity | Trung bình — cần orchestration giữa các bước |
| Risk | Trung bình — giữa các bước, RLS ở trạng thái partial (ENABLE nhưng chưa FORCE) |
| SLA impact | Tương tự Option 1 nhưng kéo dài hơn |

**Loại vì**: Lock time per operation đã cực ngắn (<1 giây). Tách bước không giảm peak lock time, chỉ tăng complexity và tạo window RLS partial — table owner bypass policy giữa ENABLE và FORCE.

### Option 3: pg_repack / online DDL

| Tiêu chí | Đánh giá |
|----------|----------|
| Applicability | **Không applicable** — `ALTER TABLE ENABLE RLS` là metadata operation, pg_repack chỉ hỗ trợ rewrite operations (ADD column với default, change data type...) |
| Complexity | Cao — thêm dependency, cần pg_repack extension |

**Loại vì**: RLS DDL không rewrite data. pg_repack không áp dụng được cho metadata-only operations.

---

## Consequences

### Tich cuc

- **Lock time cực ngắn**: metadata-only DDL, <5 giây tổng cho 4 tables.
- **Implementation đơn giản**: 1 migration file, 1 deployment procedure.
- **Rollback rõ ràng**: `DISABLE ROW LEVEL SECURITY` trên từng table (cũng metadata-only, cũng cần ACCESS EXCLUSIVE lock nhưng nhanh tương đương).
- **Không cần tool bên ngoài**: thuần Flyway + PostgreSQL DDL.

### Tieêu cuc / Risks

| Rủi ro | Mức độ | Mitigation |
|--------|--------|-----------|
| Long-running transaction cản trở ACCESS EXCLUSIVE lock acquire | Medium | Check `pg_stat_activity` trước khi chạy migration; nếu phát hiện transaction >30 giây, hủy maintenance window |
| Customer có 24/7 traffic (T2 multi-tenant) — latency spike ~1-2 giây | Low | Chỉ áp dụng cho T1 (MVP2); T2 khi lên multi-tenant thực sẽ cần online strategy hoặc pg_terminate_backend cho blocking queries |
| Maintenance window scheduling xung đột với backup/cron job | Low | Coordinate với ops team; maintenance window nằm ngoài backup schedule |
| Rollback cũng cần ACCESS EXCLUSIVE lock | Low | Rollback cũng nhanh (<5 giây); chạy trong cùng maintenance window nếu cần |

### Rollback plan

```sql
-- Rollback V15 (chạy trong maintenance window)
ALTER TABLE environment.sensor_readings NO FORCE ROW LEVEL SECURITY;
ALTER TABLE esg.clean_metrics          NO FORCE ROW LEVEL SECURITY;
ALTER TABLE esg.alert_events           NO FORCE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_counts     NO FORCE ROW LEVEL SECURITY;

DROP POLICY tenant_isolation ON environment.sensor_readings;
DROP POLICY tenant_isolation ON esg.clean_metrics;
DROP POLICY tenant_isolation ON esg.alert_events;
DROP POLICY tenant_isolation ON traffic.traffic_counts;

ALTER TABLE environment.sensor_readings DISABLE ROW LEVEL SECURITY;
ALTER TABLE esg.clean_metrics          DISABLE ROW LEVEL SECURITY;
ALTER TABLE esg.alert_events           DISABLE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_counts     DISABLE ROW LEVEL SECURITY;
```

Flyway không hỗ trợ automatic rollback cho versioned migration. Rollback phải chạy manual hoặc qua migration mới (V16).

---

## Implementation Checklist

### Pre-migration
- [ ] Verify V14 backfill hoàn thành: không còn `tenant_id IS NULL` trên 4 tables
- [ ] Tạo V15 migration file theo cấu trúc trên
- [ ] Test trên staging: đo lock time thực tế với `pg_stat_statements`
- [ ] Thông báo maintenance window cho T1 customer (24h trước)

### Migration day
- [ ] Check `pg_stat_activity`: không có long-running transaction
- [ ] Chạy Flyway migration V15
- [ ] Verify RLS: `SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname IN ('environment','esg','traffic')`
- [ ] Verify policy: `SELECT * FROM pg_policy WHERE polname = 'tenant_isolation'`
- [ ] Smoke test: application CRUD với tenant context đúng

### Post-migration
- [ ] Monitor application latency trong 1 giờ sau migration
- [ ] Monitor error rate (connection refused, query timeout) trong 1 giờ
- [ ] Ghi nhận lock time thực tế vào runbook

---

## Related

- ADR-010: Multi-Tenant Isolation Strategy — quyết định dùng RLS, V14 migration strategy
- ADR-014: Telemetry Enrichment Pattern — inject tenant_id vào sensor data stream
- [Story MVP2-07a: RLS policy + integration test](../project/demo-and-roadmap-2026-04-25.md)
