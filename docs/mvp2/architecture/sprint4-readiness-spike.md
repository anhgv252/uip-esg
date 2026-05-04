# Sprint 4 Architecture Readiness Spike

**Date**: 2026-05-04  
**Author**: Solution Architect  
**Sprint**: MVP2-4 (Tuần 7–8, 2026-06-09 → 2026-06-20, ~52 SP)  
**Sprint Goal**: Partner Foundation + Tenant Admin Backend API + Runbook

---

## TL;DR — Kết luận và hành động ngay

| # | Issue | Mức độ | Hành động |
|---|-------|--------|-----------|
| 1 | **Migration version conflict** | 🔴 BLOCKER | Plan nói V17/V18, thực tế đã có. Dùng **V20/V21** |
| 2 | **TenantConfig storage strategy** | 🟠 Cần quyết định | JSONB blob hiện tại vs KV table theo ADR-019 → chọn hybrid |
| 3 | **EsgReportExportPort design** | 🟡 Cần làm rõ | Plan nói "CSV default" nhưng current default là XLSX → giữ XLSX + thêm CSV |
| 4 | **TenantAdmin authorization model** | 🟠 Cần thiết kế | TENANT_ADMIN vs ADMIN access pattern chưa có precedent |
| 5 | **Invite token security design** | 🟡 Cần quyết định | UUID vs HMAC, sync vs async email |
| 6 | **Usage stats cross-schema query** | 🟡 Phase 1 exception | Cho phép nhưng phải document rõ |

**Không cần spike mới cho:**
- Partner profile YAML structure (đã có ADR-019)
- Partner extension directory (đã có ADR-019 + ADR-024)  
- EsgReportGenerator async flow (đã có, chỉ refactor)

---

## 1. Phân tích Sprint 4 Backlog

### 1.1 Các task trong Sprint 4

**Tenant Admin Backend API (17 SP, moved from Sprint 5):**
- BT-13a — 6 Admin API endpoints (8 SP) P0
- BT-13b — User Invite Flow: InviteToken + Email + Password Set (5 SP) P1
- BT-13c — Usage Stats Aggregate Queries (2 SP) P1
- BT-13d — Tenant Config Table CRUD (2 SP) P1

**Partner Foundation (27 SP):**
- MVP2-28 — partner-extensions/ Directory Structure (2 SP) P2
- MVP2-29 — infra/partner-profiles/ + Template (2 SP) P2
- MVP2-30 — EsgReportExportPort Extension Point (3 SP) P2
- MVP2-31 — Helm Partner Template (1 SP) P2
- MVP2-19 — Runbook + On-Call Playbook (5 SP) P1
- BT-30a — EsgReportGenerator Refactor: Strategy Pattern (3 SP) P1
- BT-30b — Spring @Profile Partner Loading (2 SP) P2
- FE-09 — Frontend Partner Scaffold: theme + nav per partner (3 SP) P2
- BT-13-ADR — ADR-025: TenantAdmin Auth Pattern (new, 1 SP) P0

---

## 2. Critical Issue: Migration Version Conflict

### Hiện trạng

```
V1  — create_app_users
V2  — create_domain_schemas
...
V14 — add_multi_tenant_columns
V15 — create_audit_log_table
V16 — enable_rls_policies
V17 — add_tenant_fields_app_users   ← PLAN NHẦM: gọi đây là "create_invite_tokens"
V18 — create_tenants_table           ← PLAN NHẦM: gọi đây là "create_tenant_config_table"
V19 — seed_tenant_admin_user
```

### Vấn đề

Plan nói:
- `V17__create_invite_tokens.sql` → **V17 đã tồn tại** (add_tenant_fields)
- `V18__create_tenant_config_table.sql` → **V18 đã tồn tại** (create_tenants_table)

Flyway sẽ fail với `Found more than one migration with version 17` khi chạy.

### Quyết định

```
V20__create_invite_tokens.sql          ← BT-13b
V21__create_tenant_config_kv_table.sql ← BT-13d
```

Backend dev **PHẢI** dùng V20 và V21 thay vì V17/V18. Detail plan cần update.

---

## 3. Tenant Config Storage: JSONB vs KV Table

### Hiện trạng

```java
// Tenant entity (V18 migration)
@Column(name = "config_json", columnDefinition = "jsonb")
private String configJson;

// TenantConfigService đọc từ JSONB blob
// parseFeatures() hiện là stub, trả hardcode defaults
```

### ADR-019 Layer 1 yêu cầu

```sql
CREATE TABLE tenants.tenant_config (
    tenant_id   TEXT NOT NULL,
    config_key  TEXT NOT NULL,
    config_value TEXT NOT NULL,
    PRIMARY KEY (tenant_id, config_key)
);
```

### Phân tích trade-off

| Tiêu chí | JSONB blob (hiện tại) | KV table (ADR-019) |
|----------|----------------------|-------------------|
| Đơn giản | ✅ 1 bảng | ❌ 2 bảng + join hoặc 2 queries |
| Type safety | ❌ String JSON parse | ✅ String key-value rõ ràng |
| Ops: toggle 1 flag | ❌ UPDATE cả JSONB | ✅ UPDATE 1 row |
| Audit: xem ai đổi flag nào | ❌ JSON diff khó | ✅ Row-level audit dễ |
| Schema migration | ❌ Cần parse logic | ✅ Data tự nhiên |
| REST endpoint CRUD | ❌ Phức tạp với JSONB patch | ✅ CRUD đơn giản |

### Quyết định — Hybrid approach

**Giữ `config_json` trong `tenants` cho branding** (không thay đổi, hoạt động tốt):
```json
{
  "branding": { "primaryColor": "#2E7D32", "logoUrl": "...", "partnerName": "..." }
}
```

**Tạo bảng `tenant_config` riêng cho feature flags và threshold** (theo ADR-019):
```sql
-- V21: Sprint 4 BT-13d
CREATE TABLE public.tenant_config (
    tenant_id    TEXT         NOT NULL,
    config_key   TEXT         NOT NULL,
    config_value TEXT         NOT NULL,
    updated_by   TEXT,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, config_key)
);
```

**Lý do hybrid**: branding là một document nhất quán (update cùng lúc), feature flags là các key độc lập (toggle từng cái). Hai use case khác nhau phù hợp hai storage model khác nhau.

**TenantConfigService refactor** (BT-13d scope):
```java
// Đọc feature flags từ tenant_config table
// Đọc branding từ tenants.config_json
// Merge thành TenantConfigResponse
```

---

## 4. EsgReportExportPort: Strategy Pattern Design

### Hiện trạng

`EsgReportGenerator.buildReport()` hiện chỉ generate XLSX (Apache POI), hardcode hoàn toàn.

### Vấn đề trong plan

BT-30a nói: "Default CSV adapter hoạt động (không break existing XLSX download)"  
MVP2-30 nói: `DefaultCsvExportAdapter` là default implementation

**Mâu thuẫn**: current default là XLSX, plan implies CSV là default mới, nhưng "không break XLSX download".

### Quyết định

**Giữ XLSX là format mặc định, CSV là format bổ sung**:

```java
// backend/src/main/java/com/uip/backend/esg/extension/EsgReportExportPort.java
public interface EsgReportExportPort {
    String getFormatId();       // "xlsx", "csv", "iso-50001", "gri"
    String getContentType();    // MIME type
    String getFileExtension();  // "xlsx", "csv"
    byte[] export(EsgReportData data);
}
```

**Hai adapter mặc định:**
```
DefaultXlsxExportAdapter.java   ← extract existing XSSFWorkbook logic, formatId = "xlsx"
DefaultCsvExportAdapter.java    ← implement mới, formatId = "csv"
```

**EsgReportGenerator refactor:**
```java
// Nhận List<EsgReportExportPort> thay vì hardcode XLSX
// format param mặc định = "xlsx" để backward compat
```

**API change:**
```
// Thêm optional ?format=csv param
GET /api/v1/esg/reports/{id}/download?format=xlsx   ← default, backward compat
GET /api/v1/esg/reports/{id}/download?format=csv    ← mới Sprint 4
```

**Acceptance Criteria đúng (replace plan's confusing description):**
- `DefaultXlsxExportAdapter` wraps existing XLSX logic — download hiện tại không break
- `DefaultCsvExportAdapter` là format bổ sung
- `EsgReportGenerator` inject `List<EsgReportExportPort>`, resolve by formatId
- Duplicate formatId → throw `DuplicateExportFormatException` on startup
- Partner extension implement interface → tự động available (zero core change)

---

## 5. TenantAdmin Authorization Model (new ADR-025)

### Vấn đề

`AdminController` hiện tại: `@PreAuthorize("hasRole('ADMIN')")` — chỉ SUPER ADMIN.

`TenantAdminController` mới cần:
- `ROLE_TENANT_ADMIN` được access nhưng chỉ thấy data của tenant mình
- `ROLE_ADMIN` (Super Admin) được access tất cả tenant

### Các option

**Option A: Path param validation trong service**
```java
@GetMapping("/tenants/{tenantId}/users")
@PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and #tenantId == authentication.details.tenantId)")
public ResponseEntity<List<TenantUserDto>> listUsers(@PathVariable String tenantId) { ... }
```

**Option B: Ignore path param cho TENANT_ADMIN (extract from JWT)**
```java
@GetMapping("/tenants/{tenantId}/users")
public ResponseEntity<List<TenantUserDto>> listUsers(@PathVariable String tenantId,
                                                      Authentication auth) {
    String effectiveTenantId = isAdmin(auth) ? tenantId : TenantContext.getCurrentTenant();
    return ResponseEntity.ok(tenantAdminService.listUsers(effectiveTenantId));
}
```

**Option C: Separate controller per role**
- `/api/v1/admin/tenants/{tenantId}/...` — ROLE_ADMIN only (existing AdminController pattern)
- `/api/v1/tenant-admin/...` — ROLE_TENANT_ADMIN, tenantId from JWT

### Quyết định — Option B (preferred cho Phase 1)

**Lý do:**
- Giảm complexity: không cần SpEL expression phức tạp trong @PreAuthorize
- TENANT_ADMIN không bao giờ có quyền truy cập tenant khác — enforce ở service layer
- Cùng endpoint, ADMIN có thể pass bất kỳ tenantId nào (Super Admin use case)
- Dễ test: test với TENANT_ADMIN token → effectiveTenantId luôn là JWT tenant

**Authorization rules:**
```
GET  /api/v1/admin/tenants/{tenantId}/users          → hasRole('ADMIN') or hasRole('TENANT_ADMIN')
POST /api/v1/admin/tenants/{tenantId}/users/invite   → hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))
PUT  /api/v1/admin/tenants/{tenantId}/users/{id}/role → hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))
GET  /api/v1/admin/tenants/{tenantId}/usage          → hasRole('ADMIN') or hasRole('TENANT_ADMIN')
GET  /api/v1/admin/tenants/{tenantId}/settings       → hasRole('ADMIN') or hasRole('TENANT_ADMIN')
PUT  /api/v1/admin/tenants/{tenantId}/settings       → hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))
```

**Service layer enforcement:**
```java
private String resolveEffectiveTenantId(String pathTenantId, Authentication auth) {
    if (hasRole(auth, "ROLE_ADMIN")) {
        return pathTenantId;  // Super Admin sees any tenant
    }
    // TENANT_ADMIN always sees their own tenant — ignore path param
    String jwtTenantId = TenantContext.getCurrentTenant();
    if (!jwtTenantId.equals(pathTenantId)) {
        log.warn("TENANT_ADMIN {} attempted cross-tenant access to {}", jwtTenantId, pathTenantId);
        // Return own tenant data silently (không throw 403 để không leak existence)
    }
    return jwtTenantId;
}
```

---

## 6. User Invite Flow Design (BT-13b)

### Quyết định: DB-based token, JavaMail sync

**Token format: UUID (v4) — không cần HMAC cho Phase 1**
- UUID random đủ entropy (122 bit), không cần signature verification
- Lưu trong DB để validate, enforce expiry và single-use
- HMAC/JWT token chỉ cần thiết nếu stateless verification — Phase 1 có DB nên không cần

**Migration V20:**
```sql
CREATE TABLE public.invite_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   TEXT        NOT NULL,
    email       TEXT        NOT NULL,
    role        TEXT        NOT NULL DEFAULT 'ROLE_OPERATOR',
    token       UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    invited_by  TEXT        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,          -- NULL = chưa dùng
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invite_tokens_token ON public.invite_tokens (token);
CREATE INDEX idx_invite_tokens_tenant ON public.invite_tokens (tenant_id);
```

**Flow:**
```
POST /api/v1/admin/tenants/{tenantId}/users/invite
  → InviteService.createInvite(email, role, tenantId)
  → Generate UUID token, store với expires_at = NOW() + 48h
  → EmailService.sendInviteEmail(email, token)  ← JavaMail sync
  → Return 202 Accepted (không return token trong response body)

POST /api/v1/auth/invite/accept  (public endpoint, no auth)
  → AcceptInviteRequest { token: UUID, password: String }
  → InviteService.acceptInvite(token, password)
  → Validate: token exists, not expired, not used
  → Create AppUser với hashed password
  → Mark invite as used (set used_at = NOW())
  → Return 200 + JWT token để login ngay
```

**Security considerations:**
- Token trong email link: `https://app.uip.vn/accept-invite?token={UUID}` — không expose trong response
- Rate limit: max 10 invites per hour per tenant (Spring Rate Limiter)
- Expired token cleanup: @Scheduled job chạy daily, xóa expired+used tokens > 30 ngày

**EmailService — JavaMail (sync, Phase 1):**
```java
// Spring Mail với Mailhog (dev) hoặc SMTP partner (prod)
// Async có thể add sau via @Async nếu cần
// Phase 2: có thể chuyển sang Kafka event + dedicated notification service
```

---

## 7. Usage Stats Cross-Schema Query (BT-13c)

### Vấn đề

`TenantUsageRepository` cần count sensor readings:
```sql
SELECT count(*) FROM environment.sensor_readings WHERE tenant_id = ? AND timestamp BETWEEN ? AND ?
```

Đây là cross-schema query (tenant module query environment schema) — vi phạm module boundary nguyên tắc.

### Quyết định: Phase 1 exception, document explicitly

**Cho phép** trong Phase 1 vì:
- Same DB instance, same PostgreSQL connection
- Không có inter-service overhead
- RLS đã enforce tenant isolation ở DB layer
- Effort implement Port Interface > value gained cho Phase 1

**Điều kiện:**
- Query chỉ dùng `SELECT count(*)` — không JOIN, không fetch full data
- Repository class đặt tên rõ ràng: `TenantUsageCrossSchemaRepository` để dễ identify và remove Phase 2
- Comment rõ ràng trong code:

```java
/**
 * Cross-schema read: environment.sensor_readings
 * Phase 1 exception — same DB instance, RLS enforced.
 * Phase 2: replace with gRPC call to environment-module.
 */
@Repository
public class TenantUsageCrossSchemaRepository {
    // Native query vì cross-schema
    @Query(nativeQuery = true, value = """
        SELECT count(*) FROM environment.sensor_readings
        WHERE tenant_id = :tenantId
          AND recorded_at BETWEEN :start AND :end
    """)
    Long countSensorReadings(@Param("tenantId") String tenantId,
                              @Param("start") Instant start,
                              @Param("end") Instant end);
}
```

---

## 8. Partner Extension Architecture Verification

### MVP2-28, MVP2-29, MVP2-30 — Không cần spike thêm

ADR-019 và ADR-024 đã cover đầy đủ. Implementation straightforward:

```
partner-extensions/
└── partner-energy-optimizer/
    └── pom.xml   (Maven module stub — Spring Boot parent, version placeholder)
```

```yaml
# infra/partner-profiles/application-partner-default.yml
partner:
  id: "default"
  ...
```

**BT-30b — Spring @Profile Partner Loading**: Dùng `@ConditionalOnProperty` per ADR-019. `PartnerBeanRegistrar` là optional — `@ConditionalOnProperty` trên `@Bean` method trong `@Configuration` là đủ cho Phase 1.

---

## 9. Dependency Order cho Team

Sprint 4 có internal dependencies quan trọng:

```
V20 migration (invite tokens)
  → InviteToken entity + repository
    → InviteService
      → EmailService (SMTP config cần xong trước)
        → InviteController (public endpoint)

V21 migration (tenant_config table)
  → TenantConfigRepository (KV-based)
    → TenantConfigCrudService
      → TenantAdminController /settings endpoints

EsgReportExportPort interface + DefaultXlsxExportAdapter
  → DefaultCsvExportAdapter
    → EsgReportGenerator refactor (List<Port> injection)
      → EsgController thêm format param
```

**Khuyến nghị start order:**
1. **Day 1**: Tạo V20 + V21 migrations → unblock tất cả BE tasks
2. **Day 1-2**: EmailService config (SMTP, Mailhog) → unblock BT-13b
3. **Day 2**: EsgReportExportPort interface → unblock BT-30a và partner extension work
4. **Day 3+**: Parallel: BT-13a API endpoints + BT-30b partner loading + FE-09 themes

---

## 10. Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Email SMTP config phức tạp ở staging | Medium | High | Dùng Mailhog locally, mock trong tests; staging dùng SendGrid với dummy creds |
| TenantConfigService refactor break Sprint 3 feature | Medium | High | Unit test `TenantConfigService` trước khi refactor; giữ `config_json` JSONB cho branding |
| EsgReportGenerator refactor break download | Low | High | Extract logic vào `DefaultXlsxExportAdapter` trước, test download trước khi remove old code |
| Usage stats query không đúng schema | Low | Medium | `EXPLAIN ANALYZE` trước khi commit; test với realistic data volume |
| Partner extension `mvn compile` fail do version mismatch | Medium | Medium | Spike Maven module setup ngay Day 1; không để tới cuối sprint |

---

## 11. ADR Updates Required

| ADR | Action |
|-----|--------|
| **ADR-019** | Update SQL example: `tenant_config` trong `public` schema (không phải `tenants` schema vì Phase 1 không có schema separation) |
| **ADR-025** (new) | TenantAdmin Authorization Pattern — create từ Section 5 của spike này |
| Detail plan | Update V17 → V20, V18 → V21 trong BT-13b và BT-13d tasks |

---

## Checklist Sprint 4 Readiness

- [x] Sprint 4 backlog đọc và phân tích
- [x] Migration conflict identified (V17/V18 taken → use V20/V21)
- [x] TenantConfig storage decision (hybrid JSONB + KV table)
- [x] EsgReportExportPort design (XLSX default, CSV additive)
- [x] TenantAdmin authorization model (Option B — service-layer enforcement)
- [x] Invite token design (UUID DB-based, 48h TTL, single-use)
- [x] Cross-schema query decision (Phase 1 exception, documented)
- [x] Partner extension architecture verified (ADR-019 sufficient)
- [x] Dependency order mapped out
- [ ] ADR-025 cần tạo (TenantAdmin Auth Pattern)
- [ ] Detail plan update: V17/V18 → V20/V21
- [ ] Partner registry file cần tạo (ADR-024 action item)

**Sprint 4 có thể bắt đầu ngay** sau khi:
1. BE dev đọc spike này và confirm migration numbering
2. ADR-025 tạo (có thể parallel với dev bắt đầu)
3. SMTP config (Mailhog) setup trong docker-compose.dev.yml
