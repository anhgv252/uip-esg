# ADR-010: Multi-Tenant Isolation Strategy

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect, Product Owner
**Scope**: MVP2 — áp dụng ngay cho sprint MVP2-1 và MVP2-2
**Changelog**:
- 2026-04-25: Bản gốc — Accepted
- 2026-04-28: Bổ sung sau review phản biện: làm rõ scope LTREE (chỉ metadata tables), bổ sung rủi ro HikariCP + RLS data leak, cập nhật chiến lược migration V14 3 bước, tách ADR-014/ADR-015 ra riêng

---

## Context

MVP1 chạy single-tenant: một deployment = một khách hàng, toàn bộ data trong một schema không phân biệt. Khi UIP mở rộng sang mô hình SaaS, nhiều khách hàng sẽ chạy trên cùng infrastructure ở một số tier. Cần quyết định chiến lược isolation dữ liệu ngay từ MVP2 để tránh phải migration phức tạp sau này.

### Định nghĩa Tier

Tier mô tả **quy mô deployment của một khách hàng**, không phải số lượng khách hàng trên shared infrastructure. Một "T3 tenant" là một khách hàng lớn (urban district), không phải nhiều khách hàng nhỏ gộp lại.

| Tier | Đối tượng | Quy mô / tenant | Deployment model |
|------|-----------|-----------------|-----------------|
| T1 | Single Building | 1 toà nhà | Docker Compose — dedicated instance per customer |
| T2 | Building Cluster | 5–20 toà nhà | Kubernetes — multiple customers share cluster |
| T3 | Urban District | 50–200 toà nhà | Kubernetes — schema isolation per customer |
| T4 | Smart Metropolis | 500+ toà nhà | Multi-region — dedicated DB cluster per customer |

T1 và T4 không có vấn đề multi-tenancy thực sự (mỗi customer có infra riêng). Vấn đề isolation tập trung ở **T2 và T3**, nơi nhiều customer chia sẻ database.

### Yêu cầu

1. Data giữa các tenant phải được cách ly — tenant A không được đọc/ghi data của tenant B trong mọi tình huống.
2. Migration từ T1 (MVP1, single-tenant) lên T2 phải không cần downtime và không phá vỡ schema.
3. Hierarchical location queries (toà nhà → tầng → zone → thiết bị) phải hiệu quả.
4. Chiến lược T2 không được ngăn cản upgrade lên T3 hoặc T4.

---

## Decision

### 1. Chiến lược isolation theo tier

#### T1 — Static tenant_id, không cần runtime isolation
- Không thay đổi runtime logic.
- Tất cả bảng có `tenant_id = 'default'` — constant trong `application.yml`.
- Mục đích: schema sẵn sàng upgrade lên T2 mà không cần thay đổi cấu trúc bảng.

#### T2 — Row-Level Security (RLS) + tenant_id
- PostgreSQL RLS enforce isolation tại DB layer — không phụ thuộc application code.
- Mỗi tenant có một PostgreSQL role riêng, không có SUPERUSER.
- `TenantContext` (Spring ThreadLocal) set `app.tenant_id` trước mỗi query.
- **Bắt buộc dùng `SET LOCAL`** để tránh cross-tenant data leak qua HikariCP — xem mục Security Constraint bên dưới.

```sql
CREATE POLICY tenant_isolation ON <table>
    USING (tenant_id = current_setting('app.tenant_id'));
```

#### T3 — Schema-per-Tenant
- Mỗi tenant có PostgreSQL schema riêng: `tenant_abc.*`, `tenant_xyz.*`.
- Áp dụng cho toàn bộ data layer của tenant đó.
- Spring `AbstractRoutingDataSource` routing theo TenantContext.
- Trigger khi: tenant có >10M rows/tháng hoặc yêu cầu compliance isolation.

#### T4 — Database-per-Tenant
- Dedicated DB cluster per Enterprise customer.
- Ngoài scope MVP2. Schema V14 phải không ngăn cản hướng này.

---

### 2. Hierarchical Location với LTREE

Dùng PostgreSQL LTREE extension để model cây địa lý:

```
city.district.cluster.building.floor.zone
-- Ví dụ: hcmc.d7.riverpark.tower_a.f3.east
```

**Quy tắc bắt buộc: LTREE chỉ lưu trên metadata/entity tables, không lưu trên time-series tables.**

| Loại bảng | Lưu `location_path`? | Lý do |
|-----------|----------------------|-------|
| `devices`, `zones`, `floors`, `buildings` | ✅ Có | Slow-changing, row count thấp, cần subtree query |
| `sensor_readings`, `traffic_counts`, `clean_metrics` (time-series) | ❌ Không | Dùng JOIN qua `device_id`/`sensor_id` khi cần ngữ cảnh location |
| `alert_events`, `incidents` và event log tables | ❌ Không | Dùng JOIN; tránh denormalization trên high-write tables |

Query cần location context dùng JOIN, không nhúng LTREE vào time-series:

```sql
-- ✅ Đúng: JOIN để lấy location context
SELECT avg(r.value)
FROM esg.clean_metrics r
JOIN environment.sensors d ON r.sensor_id = d.id AND r.tenant_id = d.tenant_id
WHERE d.location_path <@ 'hcmc.d7.riverpark.tower_a.f3'
  AND r.tenant_id = current_setting('app.tenant_id')
  AND r.timestamp BETWEEN '2026-04-01' AND '2026-04-28';

-- ❌ Sai: LTREE filter trực tiếp trên time-series
SELECT avg(value)
FROM esg.clean_metrics
WHERE location_path <@ 'hcmc.d7.riverpark.tower_a.f3';
```

---

### 3. Index Strategy

```sql
-- Metadata tables: GIST cho LTREE subtree query
CREATE INDEX ON environment.sensors USING GIST (location_path);
CREATE INDEX ON citizens.buildings  USING GIST (location_path);

-- Time-series tables: composite B-tree, không có GIST
CREATE INDEX ON environment.sensor_readings (tenant_id, timestamp DESC);
CREATE INDEX ON esg.clean_metrics           (tenant_id, timestamp DESC);
CREATE INDEX ON traffic.traffic_counts      (tenant_id, timestamp DESC);

-- Event/operational tables: B-tree trên tenant_id
CREATE INDEX ON alerts.alert_events  (tenant_id);
CREATE INDEX ON traffic.incidents    (tenant_id);

-- Config/metadata tables không có location
CREATE INDEX ON alerts.alert_rules         (tenant_id);
CREATE INDEX ON citizens.households        (tenant_id);
CREATE INDEX ON citizens.citizen_accounts  (tenant_id);
CREATE INDEX ON esg.reports                (tenant_id);
CREATE INDEX ON error_mgmt.error_records   (tenant_id);
```

---

### 4. V14 Migration — chiến lược 3 bước

`ADD COLUMN ... DEFAULT 'constant'` trực tiếp có thể lock bảng trên PostgreSQL cũ hoặc bảng rất lớn. Dùng 3 bước tách biệt làm default để áp dụng đúng mọi môi trường:

```sql
-- Bước 1: Thêm column nullable — không lock, gần như instant
ALTER TABLE environment.sensors   ADD COLUMN tenant_id TEXT;
ALTER TABLE citizens.buildings    ADD COLUMN tenant_id TEXT;
ALTER TABLE environment.sensor_readings ADD COLUMN tenant_id TEXT;
-- ... tất cả domain tables tương tự

-- Chỉ thêm location_path vào metadata/entity tables
ALTER TABLE environment.sensors   ADD COLUMN location_path ltree;
ALTER TABLE citizens.buildings    ADD COLUMN location_path ltree;
-- ⚠️ KHÔNG thêm location_path vào sensor_readings, clean_metrics, alert_events hay bất kỳ time-series/event table nào

-- Bước 2: Backfill (chạy offline hoặc migration job riêng)
UPDATE environment.sensors   SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE citizens.buildings    SET tenant_id = 'default' WHERE tenant_id IS NULL;
-- ...

-- Bước 3: Set DEFAULT + NOT NULL sau khi data đã đầy đủ
ALTER TABLE environment.sensors ALTER COLUMN tenant_id SET DEFAULT 'default';
ALTER TABLE environment.sensors ALTER COLUMN tenant_id SET NOT NULL;
-- ... tương tự các bảng còn lại
```

---

## Security Constraint: RLS + HikariCP

> Implementation constraint bắt buộc cho story MVP2-07b. Vi phạm gây cross-tenant data leak.

**Vấn đề**: HikariCP tái sử dụng connection. Nếu thread phục vụ tenant A set `SET SESSION app.tenant_id = 'A'` rồi trả connection về pool mà không reset, thread tiếp theo phục vụ tenant B lấy trúng connection đó sẽ đọc/ghi data của tenant A.

**Giải pháp**: Dùng `SET LOCAL` — variable chỉ sống trong transaction, tự reset khi transaction kết thúc (COMMIT hoặc ROLLBACK), không cần try/finally thủ công.

```sql
BEGIN;
SET LOCAL app.tenant_id = 'tenant_xyz';
-- ... queries chạy với RLS filter đúng ...
COMMIT;  -- app.tenant_id bị discard, connection sạch khi về pool
```

**Ràng buộc triển khai**:
- Toàn bộ repository method phải chạy trong `@Transactional` hoặc `@Transactional(readOnly = true)`.
- `SET SESSION` cho RLS context bị cấm — bổ sung vào code review checklist.
- Ngoại lệ (non-transactional context bắt buộc) phải dùng try/finally với `RESET app.tenant_id` tường minh và phải được ghi chú rõ trong code.

---

## Consequences

### Tích cực

- **Zero-downtime upgrade T1→T2**: enable RLS policy + TenantContext, không thay đổi schema.
- **Isolation tại DB layer**: RLS enforce đúng ngay cả khi application code có bug.
- **LTREE hiệu quả trên metadata**: GIST index cho subtree query O(log n).
- **Backward compatible**: T1 deployment tiếp tục dùng `tenant_id = 'default'`.
- **Migration an toàn**: chiến lược 3 bước không gây downtime bất kể PG version hay table size.

### Tiêu cực / Risks

| Rủi ro | Mức độ | Mitigation |
|--------|--------|-----------|
| `SET SESSION` thay vì `SET LOCAL` → cross-tenant data leak | Critical | Code review checklist; enforce trong TenantContext implementation |
| Developer quên set real `tenant_id` khi T2+ | Medium | TenantContext filter bắt buộc cho mọi query trong Spring Data |
| LTREE path format không nhất quán | Medium | TenantSetupService validate format khi onboard tenant mới |
| Schema-per-tenant ở T3 tăng complexity Flyway migration | Medium | Acceptable; chỉ trigger khi thực sự cần compliance isolation |
| RLS overhead 5–10% ở T2 | Low | Acceptable cho scale T2; T3 schema boundary loại bỏ overhead này |

### Không chọn

| Giải pháp thay thế | Lý do loại |
|--------------------|------------|
| Separate database ngay từ T2 | Chi phí vận hành không tương xứng với scale SME |
| Schema-per-tenant ngay từ T2 | Flyway migration overhead không tương xứng ở quy mô nhỏ |
| Application-level filtering only | Không đủ isolation — bypass được bằng raw query |
| LTREE trực tiếp trên time-series tables | Storage bloat; INSERT throughput giảm; JOIN qua metadata sạch hơn |
| `SET SESSION` cho RLS context | Cross-tenant data leak với HikariCP connection pooling |

---

## Out of Scope — Deferred ADRs

Các vấn đề liên quan được nhận diện nhưng thuộc quyết định riêng:

| Vấn đề | ADR | Trigger |
|--------|-----|---------|
| Flink không có `tenant_id` từ raw IoT payload — enrichment pattern | ADR-014 | MVP3, khi có multi-tenant thực trên shared Flink cluster |
| Dashboard read-heavy caching strategy cho ESG API | ADR-015 | MVP2-2, đã ghi rõ |
| TimescaleDB partitioning nâng cao cho shared infra | ADR-013 | T3, nếu nhiều T2 tenant cùng share một TimescaleDB instance |
| ClickHouse adoption | ADR-012 | Trigger criteria chưa được chốt |

---

## Frontend Impact (MVP2-2)

ADR-010 quyết định rằng `tenant_id` được lấy từ JWT claim. Điều này kéo theo **5 thay đổi bắt buộc** ở frontend:

### 1. JWT Claims mới phải được parse trong AuthContext

Backend phải nhúng claims sau vào access token:
```json
{
  "sub": "user-123",
  "tenant_id": "hcm",
  "tenant_path": "city.hcm",
  "scopes": ["environment:read", "esg:read", "alert:ack"],
  "allowed_buildings": ["bld-001", "bld-002"],
  "roles": ["OPERATOR"]
}
```

Frontend tại `src/contexts/AuthContext.tsx` — `userFromToken()` phải đọc các claims này và expose qua `AuthUser` interface. Chi tiết implementation ở task FE-01 trong detail plan.

### 2. TenantConfigContext phải được mount trước Router

`src/contexts/TenantConfigContext.tsx` gọi `GET /api/v1/tenant/config` sau khi user đăng nhập, lấy feature flags và branding config. Provider order trong `App.tsx` bắt buộc:
```
AuthProvider → TenantConfigProvider → ThemeProvider → RouterProvider
```

### 3. `allowed_buildings` phải filter Building Selector

Các component có building dropdown (ESG dashboard scope selector, Environment page) phải filter danh sách building theo `user.allowedBuildings`. Nếu array empty → user xem được tất cả buildings của tenant (admin case).

```typescript
// Ví dụ trong ESG building selector
const { user } = useAuth()
const filteredBuildings = user.allowedBuildings.length > 0
  ? buildings.filter(b => user.allowedBuildings.includes(b.id))
  : buildings
```

### 4. `scopes` phải gate action buttons, không chỉ gating menu

RLS ở backend chặn data leak. Nhưng frontend cũng cần dùng `scopes` để disable action buttons:

| Scope | Button |
|-------|--------|
| `esg:write` | "Generate ESG Report" |
| `alert:ack` | "Acknowledge Alert" |
| `sensor:write` | "Toggle Sensor Online/Offline" |
| `citizen:admin` | "Change User Role" (Admin page) |

Dùng `useScope('esg:write')` hook (task FE-08).

### 5. T1 Backward Compatibility

T1 deployment (`tenant_id = 'default'`) không có `scopes` field trong JWT legacy token. Frontend phải:
- `user.tenantId` fallback `'default'` khi claim thiếu
- `user.scopes` fallback `[]` khi claim thiếu
- `isFeatureEnabled()` fallback `true` (fail-open) khi không có config

---

## Implementation Checklist

### MVP2-1 — V14 Migration
- [ ] `V14__add_multi_tenant_columns.sql`: implement đúng chiến lược 3 bước
- [ ] Verify: `location_path` chỉ xuất hiện trên metadata tables (`sensors`, `buildings`), không trên time-series/event tables
- [ ] Story MVP2-20: seed `tenant_id` thực cho staging data

### MVP2-2 — RLS + TenantContext
- [ ] Story MVP2-07a: RLS policy trên tất cả domain tables + integration test cross-tenant isolation
- [ ] Story MVP2-07b: `TenantContext` ThreadLocal — implement với `SET LOCAL`, không dùng `SET SESSION`
- [ ] Story MVP2-14: API rate limiting per tenant
- [ ] Code review checklist: bổ sung mục `SET LOCAL` vs `SET SESSION`

---

## Related

- ADR-011: Module extraction order (planned)
- ADR-012: ClickHouse adoption trigger criteria (planned)
- ADR-013: TimescaleDB partitioning for shared infra (planned, T3)
- ADR-014: Flink device-to-tenant enrichment pattern (proposed — `docs/mvp2/architecture/ADR-014-telemetry-enrichment-pattern.md`)
- ADR-015: Caching & Read-Heavy Performance Strategy (proposed — `docs/mvp2/architecture/ADR-015-caching-read-heavy-performance.md`)
- [MVP2-07a, MVP2-07b, MVP2-14](../project/demo-and-roadmap-2026-04-25.md)
- [Demo & Roadmap 2026-04-25](../project/demo-and-roadmap-2026-04-25.md)
