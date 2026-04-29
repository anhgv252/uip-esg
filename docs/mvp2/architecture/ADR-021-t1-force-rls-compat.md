# ADR-021: T1 Single-Tenant — FORCE RLS Compatibility

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect
**Scope**: T1 deployment — ảnh hưởng TenantContextFilter, V15 migration, values-tier1.yaml

---

## Context

ADR-010 định nghĩa Row-Level Security (RLS) làm chiến lược isolation cho T2+, với `FORCE ROW LEVEL SECURITY` trên các bảng có `tenant_id`. ADR-011 định nghĩa capability flag `uip.capabilities.multi-tenancy` để bật/tắt multi-tenancy theo tier.

V15 migration (sẽ tạo) thực hiện 2 việc:
1. `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` trên 4 bảng domain chính.
2. Backfill `tenant_id = 'default'` cho dữ liệu hiện có.

### Vấn đề

ADR-010 ghi `T1: tenant_id = 'default'` — static, không cần runtime isolation. ADR-11 ghi `values-tier1.yaml: multi-tenancy: false`.

Nếu `multi-tenancy: false`:
- `TenantContextFilter` được đánh `@ConditionalOnProperty(havingValue = "true")` --> **không load**.
- Không ai chạy `SET LOCAL app.tenant_id`.
- Nhưng V15 đã `FORCE ROW LEVEL SECURITY` --> RLS policy filter trên `tenant_id = current_setting('app.tenant_id')`.
- Kết quả: **toàn bộ query trả về 0 row** vì `app.tenant_id` chưa được set.

```
multi-tenancy: false
  --> TenantContextFilter khong load
  --> SET LOCAL app.tenant_id KHONG chay
  --> FORCE RLS active (V15)
  --> current_setting('app.tenant_id') = NULL hoac loi
  --> SELECT * FROM sensors --> 0 rows
  --> UNG DUNG CHET
```

Cần quyết định: T1 nên skip RLS hay luôn set tenant_id = 'default'?

---

## Decision

### Chọn Option 1: Always use tenant_id = 'default'

T1 deployment **luôn chạy TenantContextFilter**. Khi JWT claim không chứa `tenant_id`, filter tự fallback sang `'default'`.

```java
// TenantContextFilter.java — logic duy nhất, khong can @ConditionalOnProperty
String tenantId = jwt.getClaimAsString("tenant_id");
if (tenantId == null || tenantId.isBlank()) {
    tenantId = "default";  // T1 fallback
}
TenantContext.setCurrentTenant(tenantId);
```

```sql
-- Moi request tren T1:
SET LOCAL app.tenant_id = 'default';
-- RLS policy: tenant_id = 'default' --> pass --> data hien binh thuong
```

### Chi tiết triển khai

**1. values-tier1.yaml thay doi:**

```yaml
# truoc (ADR-011 ban dau):
uip:
  capabilities:
    multi-tenancy: false

# sau (ADR-021):
uip:
  capabilities:
    multi-tenancy: true   # T1 cung chay RLS, nhung chi co 1 tenant 'default'
```

**2. TenantContextFilter — khong can @ConditionalOnProperty:**

```java
@Component
// KHONG co @ConditionalOnProperty — luon load
public class TenantContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        String tenantId = extractTenantId(request);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }
        TenantContext.setCurrentTenant(tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
```

**3. V15 migration — backfill 'default':**

```sql
-- Bước 1: Enable RLS
ALTER TABLE environment.sensors ENABLE ROW LEVEL SECURITY;
ALTER TABLE environment.sensors FORCE ROW LEVEL SECURITY;
ALTER TABLE citizens.buildings ENABLE ROW LEVEL SECURITY;
ALTER TABLE citizens.buildings FORCE ROW LEVEL SECURITY;
ALTER TABLE environment.sensor_readings ENABLE ROW LEVEL SECURITY;
ALTER TABLE environment.sensor_readings FORCE ROW LEVEL SECURITY;
ALTER TABLE esg.clean_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE esg.clean_metrics FORCE ROW LEVEL SECURITY;

-- Bước 2: Tạo policy
CREATE POLICY tenant_isolation ON environment.sensors
    USING (tenant_id = current_setting('app.tenant_id'));
-- ... tương tự các bảng khác

-- Bước 3: Backfill — đảm bảo T1 data luôn có tenant_id
UPDATE environment.sensors SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE citizens.buildings SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE environment.sensor_readings SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE esg.clean_metrics SET tenant_id = 'default' WHERE tenant_id IS NULL;
```

**4. Code path so sanh T1 vs T2+:**

```
                    T1                              T2+
JWT claim:      khong co tenant_id            co tenant_id = 'hcm'
                     |                              |
Fallback:       'default'                       (khong can)
                     |                              |
TenantContext:  'default'                        'hcm'
                     |                              |
SET LOCAL:      app.tenant_id = 'default'      app.tenant_id = 'hcm'
                     |                              |
RLS filter:     tenant_id = 'default'           tenant_id = 'hcm'
                     |                              |
Result:         All T1 data (1 tenant)          Chi data cua tenant 'hcm'
```

**1 code path, 2 behaviour — khong can @ConditionalOnProperty cho TenantContextFilter.**

---

## Consequences

### Tich cuc

- **1 code path duy nhat** cho TenantContextFilter — khong phan tang T1/T2+. Giam complexity, giam bug surface.
- **Khong can conditional RLS logic** — V15 migration chay giong nhau tren moi tier.
- **Khong can conditional migration** — `IF multi-tenancy THEN enable RLS` la anti-pattern (ADR-011 Phan H da loai bo).
- **T1 toan compatible voi T2 upgrade** — khong can thay doi code khi customer chuyen tu T1 len T2, chi can tao them tenant va set JWT claim.
- **Test don gian hon** — integration test chi can chay 1 tenant (default), khong can test 2 code paths.

### Tieu cuc / Risks

| Rui ro | Muc do | Mitigation |
|--------|--------|-----------|
| T1 luon chay them SET LOCAL 'default' — nho overhead | Negligible | 1 roundtrip them trong transaction, khong dang ke so voi logic business |
| Developer nhầm T1 la "khong can tenant_id" va skip TenantContext | Medium | Code review checklist: TenantContext luon bat buoc; ADR-021 ghi ro |
| values-tier1.yaml thay doi tu false -> true — can update existing deployment | Low | Release note ghi ro; Helm upgrade tu dong apply |

### Khong chon

| Phuong an | Ly do loai |
|-----------|------------|
| **Option 2: Skip RLS cho T1** — `@ConditionalOnProperty` cho TenantContextFilter + khong enable RLS | Tao 2 code paths, tang bug surface. V15 migration phai conditional (if multi-tenancy then enable RLS) -- vi pham nguyen tac "migration chi tao structure, capability flag quyet dinh dung" cua ADR-011. T1 integration test khong phat hien RLS bug tru khi len T2. |
| Skip `FORCE ROW LEVEL SECURITY`, chi dung `ENABLE ROW LEVEL SECURITY` | Table owner (app user) duoc exemption -- RLS khong enforce cho table owner. Mat guarantee isolation tai DB layer, chi phu thuoc application code. |
| Dung `SET SESSION` thay vi `SET LOCAL` cho T1 vi "chi co 1 tenant" | Tao 2 implementation; con gay cross-tenant leak neu sau nay co upgrade T2. ADR-010 da loai bo `SET SESSION`. |

---

## Impact len ADR cu

### ADR-010 (Multi-Tenant Strategy)
- Phan "T1 — Static tenant_id, khong can runtime isolation" can sua: T1 **van chay** TenantContextFilter, nhung auto-fallback 'default'.
- Phan "T1 backward compatibility" o Frontend Impact van dung — khong thay doi.

### ADR-011 (Monorepo & Capability Flags)
- `values-tier1.yaml`: `multi-tenancy: false` --> `multi-tenancy: true`.
- Capability flag `multi-tenancy` **khong dung de** load/unload TenantContextFilter nua — dung de bao hieu "tenant partitioning co active khong". T1 = true nhung chi co 1 partition.
- `@ConditionalOnProperty("uip.capabilities.multi-tenancy")` **khong ap dung** cho TenantContextFilter. Flag nay co the dung cho muc dich khac (VD: tenant management UI, onboarding flow).

---

## Implementation Checklist

- [ ] **values-tier1.yaml**: doi `multi-tenancy: false` thanh `multi-tenancy: true`
- [ ] **TenantContextFilter**: xoa `@ConditionalOnProperty` (neu co), them fallback logic `tenantId = 'default'` khi JWT claim khong co tenant_id
- [ ] **V15 migration**: tao migration enable RLS + FORCE RLS + tao policy + backfill 'default'
- [ ] **TenantContext**: verify `setCurrentTenant("default")` duoc goi dung truoc moi `@Transactional` method
- [ ] **Integration test T1**: verify `SELECT * FROM sensors` tra ve data khi `app.tenant_id = 'default'`
- [ ] **Integration test T2**: verify cross-tenant isolation — tenant A khong doc duoc data cua tenant B
- [ ] **Update ADR-010**: sua phan "T1 — Static tenant_id" phan anh quyet dinh moi
- [ ] **Release note**: ghi ro `values-tier1.yaml` thay doi `multi-tenancy` tu false sang true

---

## Related

- ADR-010: Multi-Tenant Isolation Strategy — dinh nghia RLS + SET LOCAL
- ADR-011: Monorepo & Module Extraction — dinh nghia capability flags
- V14 migration: them cot `tenant_id` (da ton tai)
- V15 migration: enable RLS + backfill (se tao)
