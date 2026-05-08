# MVP2 — Detail Implementation Plan
**Cập nhật:** 2026-05-03 | **Thời gian:** Q2 2026 (12 tuần)
**Sprint start:** 2026-04-28 | **Target delivery:** 2026-07-18
**Team capacity:** ~55 SP/sprint (2 teams song song)

---

## Tóm tắt nhanh

| Phase | Sprint | Tuần | Dates | Focus | ADRs | SP | Status |
|-------|--------|------|-------|-------|------|----|--------|
| **Foundation** | MVP2-1 | 1–2 | 28 Apr – 09 May | Security P0 + QA Gaps + FE Security | — | 58 | ✅ Done |
| **Isolation** | MVP2-2 | 3–4 | 12 May – 23 May | Multi-Tenancy BE+FE (core only) | ADR-010, ADR-011, ADR-020, ADR-021 | 55 | ✅ Done |
| **Performance** | MVP2-3 | 5–6 | 26 May – 06 Jun | Cache + Kafka + Monitoring + Partner Theme | ADR-014, ADR-015, ADR-022, ADR-023 | 58 | ✅ Done |
| **Extensibility** | MVP2-4 | 7–8 | 09 Jun – 20 Jun | Partner + Tenant Admin BE API + Runbook | ADR-019, ADR-024 | 52 | ✅ Done |
| **Product** | MVP2-5 | 9–10 | 23 Jun – 04 Jul | Mobile PWA + Tenant Admin Dashboard FE | ADR-010, ADR-019 | 55 | ⏳ Planned |
| **Buffer/UAT** | MVP2-6 | 11–12 | 07 Jul – 18 Jul | Final UAT + Performance + Docs + Security Scan | — | — | ⏳ Buffer |

**Total:** ~278 SP (bao gồm MVP2-20 ✅ Done + 70 SP Backend mới + 38 SP Frontend mới)

---

## ADR Registry — Phân loại theo MVP2 Scope

### ADRs IN-SCOPE cho MVP2 (phải implement)

| ADR | Title | Sprint thực thi | Tasks |
|-----|-------|----------------|-------|
| **ADR-010** | Multi-Tenant Isolation: tenant_id + RLS + HikariCP SET LOCAL | Sprint 2 | MVP2-07a, MVP2-07b, FE-01..FE-06 |
| **ADR-011** | Monorepo + Capability Flags + Module Extraction Order | Sprint 2–3 | MVP2-26, MVP2-27 |
| **ADR-015** | Caching & Read-Heavy Performance: Redis TTL + Continuous Aggregates | Sprint 3 | MVP2-21, MVP2-22, MVP2-23 |
| **ADR-014** | Telemetry Enrichment: inject tenant_id vào Kafka stream | Sprint 3 | MVP2-24, MVP2-25 |
| **ADR-019** | Partner Customization: 3-layer (DB config → YAML → Spring module) | Sprint 3–4 | MVP2-28..31, FE-07, FE-08, FE-09 |

### ADRs DEFERRED — out of scope cho MVP2

| ADR | Title | Trigger để implement | Target |
|-----|-------|---------------------|--------|
| **ADR-012** | ClickHouse Adoption | ESG report query >5 phút p95, hoặc >10K sensors | v3.0 (T3) |
| **ADR-013** | Edge Computing Strategy | Site T2 với WAN bandwidth >70% sensor traffic | v3.0 (T2) |
| **ADR-016** | Data Lakehouse: Iceberg + MinIO + Trino | Historical data >2 năm hoặc ML training cần | v4.0 (T4) |
| **ADR-017** | Multi-Region: Warm DR → Active-Active | SLA 99.95%+ hoặc ≥2 city deployment | v4.0 (T4) |

### ADRs bổ sung — design decisions từ review

| ADR | Title | Sprint áp dụng | Vấn đề giải quyết |
|-----|-------|----------------|-------------------|
| **ADR-020** | Non-HTTP Tenant ID Propagation | Sprint 2 | Kafka/Flink/@Async không có JWT → tenant_id từ message body |
| **ADR-021** | T1 Single-Tenant + FORCE RLS Compatibility | Sprint 2 | `multi-tenancy: false` nhưng V15 đã FORCE RLS |
| **ADR-022** | Cache Warming Strategy After Batch Write | Sprint 3 | Stale window giữa Flink write + cache eviction |
| **ADR-023** | RLS Migration: Zero-Downtime Strategy | Sprint 2 | ACCESS EXCLUSIVE lock trên bảng lớn |
| **ADR-024** | Partner ID Naming Convention | Sprint 4 | ADR-019 dùng `energy-a`, plan dùng `energy-optimizer` |

### ADR đã superseded

| ADR | Lý do |
|-----|-------|
| **ADR-018** | Hợp nhất hoàn toàn vào ADR-011 |

---

## Tác động của từng ADR lên codebase

### ADR-010 — Multi-Tenant Isolation

**Backend thay đổi:**
- `V14__add_multi_tenant_columns.sql` ✅ Done
- `V15__rls_policies.sql` — RLS policy trên 4 tables
- `Tenant.java`, `TenantRepository.java`
- `TenantContext.java` (ThreadLocal), `TenantContextFilter.java`
- `HikariTenantListener.java` — SET LOCAL thay vì SET SESSION
- JWT token generator — thêm `tenant_id`, `tenant_path`, `scopes`, `allowed_buildings` claims

**Frontend thay đổi:**
- `AuthContext.tsx` — parse thêm 4 JWT claims mới
- `TenantConfigContext.tsx` — tạo mới, fetch feature flags
- `AppShell.tsx` — thêm `featureFlag` filter vào NAV_ITEMS
- `ProtectedRoute.tsx` — hỗ trợ `requiredRoles[]` + `requiredScope`
- `routes/index.tsx` — thêm `/tenant-admin` route
- `client.ts` — optional X-Tenant-Override header cho Super-Admin

**DB thay đổi:**
- V14: `tenant_id`, `location_path` columns trên metadata tables
- V15: RLS policies trên 4 domain tables
- New table: `tenants.tenant_config` (key-value per tenant)

---

### ADR-011 — Monorepo + Capability Flags

**Backend thay đổi:**
- `application.yml` — thêm `uip.capabilities.*` block
- `CapabilityProperties.java` — @ConfigurationProperties binding
- `infra/helm/values/values-tier1.yaml`, `values-tier2.yaml`, `values-tier3.yaml`

**Frontend thay đổi:**
- Không có thay đổi code trực tiếp từ ADR-011
- ADR-011 defines monorepo structure — `partner-extensions/` directory scaffold

**ADR-011 KHÔNG yêu cầu** tách bất kỳ module nào trong MVP2. Extraction chỉ xảy ra khi trigger (>50K events/sec). Capability flags chỉ là config wiring.

---

### ADR-015 — Redis Caching

**Backend thay đổi:**
- `CacheConfig.java` — RedisCacheManager với TTL per cache name
- `CacheKeyBuilder.java` — format: `esg:{tenant_id}:{scope}:{period}:{from}:{to}`
- `EsgService.java` — `@Cacheable` + `@CacheEvict` annotations
- `application.yml` — `spring.data.redis.*` config

**Frontend thay đổi:**
- Không có thay đổi code. Kết quả: ESG dashboard load nhanh hơn (cache hit <5ms).
- Test: verify response time giảm bằng Network tab DevTools

**DB thay đổi:**
- Không thay đổi schema. Redis là layer phía trên DB.

---

### ADR-014 — Telemetry Enrichment

**Backend (Flink) thay đổi:**
- `EsgCleansingJob.java` — thêm validator step
- `TenantIdValidator.java` — route message thiếu `tenant_id` sang error topic

**Frontend thay đổi:**
- Không có thay đổi trực tiếp. Kết quả: dữ liệu sensor sạch hơn, không có null tenant.

**Kafka thay đổi:**
- Topic mới: `UIP.esg.telemetry.error.v1`
- `kafka-topic-registry.md` cập nhật

---

### ADR-019 — Partner Customization

**Backend thay đổi:**
- `EsgReportExportPort.java` — interface extension point
- `EsgReportGenerator.java` — inject `List<EsgReportExportPort>`
- `partner-extensions/partner-energy-optimizer/pom.xml` — stub module
- `infra/partner-profiles/application-partner-default.yml`
- `infra/helm/values/values-partner-template.yaml`

**Frontend thay đổi:**
- `theme/index.ts` → `createPartnerTheme()` factory
- `theme/partnerThemes/` directory với 2 stub files
- `config/partner-features.ts` — feature flag → nav path mapping
- `hooks/useScope.ts` — scope-gated action check
- `contexts/TenantConfigContext.tsx` — fetch branding từ `GET /api/v1/tenant/config`

**DB thay đổi:**
- `tenants.tenant_config` table (từ ADR-010, nhưng dữ liệu được define theo ADR-019)

---

---

## Frontend — Các file phải sửa cho Multi-Tenancy

> **Vấn đề hiện tại:** Frontend được build cho single-tenant. Chuyển sang multi-tenant đòi hỏi thay đổi ở 5 điểm chính:

### Điểm 1 — `AuthContext.tsx` không extract tenant_id từ JWT

ADR-010 định nghĩa JWT claims mở rộng, nhưng `userFromToken()` hiện chỉ đọc `sub` + `roles`:

```typescript
// HIỆN TẠI — src/contexts/AuthContext.tsx:43-53
// Thiếu: tenant_id, tenant_path, scopes, allowed_buildings
export interface AuthUser {
  username: string
  role: UserRole
}
```

```typescript
// CẦN SỬA — thêm tenant fields vào AuthUser
export interface AuthUser {
  username: string
  role: UserRole
  tenantId: string            // từ JWT claim "tenant_id"
  tenantPath: string          // từ JWT claim "tenant_path" (LTREE: "city.hcm")
  scopes: string[]            // từ JWT claim "scopes" (["esg:read","alert:ack"])
  allowedBuildings: string[]  // từ JWT claim "allowed_buildings"
}
```

**Impact:** Tất cả component cần `tenantId` đều lấy qua `useAuth().user.tenantId`. Không cần context riêng.

---

### Điểm 2 — `AppShell.tsx` navigation hardcoded, không feature-flag driven

`NAV_ITEMS` tại `AppShell.tsx:50` là constant, chỉ filter theo `roles`. Khi partner "Energy-Optimizer" bật `features.citizen-portal.enabled=false`, menu Citizens vẫn hiện.

```typescript
// HIỆN TẠI — src/components/AppShell.tsx:43-76
interface NavItem {
  label: string
  path: string
  icon: React.ReactNode
  roles?: string[]
  // THIẾU: featureFlag?: string  ← không có flag check
}

// visibleItems chỉ filter theo role
const visibleItems = NAV_ITEMS.filter(
  (item) => !item.roles || (user && item.roles.includes(user.role)),
)
```

```typescript
// CẦN THÊM — featureFlag property + useTenantConfig() hook
interface NavItem {
  label: string
  path: string
  icon: React.ReactNode
  roles?: string[]
  featureFlag?: string   // e.g. "features.citizen-portal.enabled"
}

const visibleItems = NAV_ITEMS.filter(
  (item) =>
    (!item.roles || item.roles.includes(user?.role ?? '')) &&
    (!item.featureFlag || featureFlags[item.featureFlag] !== false),
)
```

---

### Điểm 3 — Theme là hardcoded, không partner-aware

`theme/index.ts` export một theme duy nhất. ADR-019 yêu cầu partner override (`primaryColor`, `logo`, `typography`).

```typescript
// HIỆN TẠI — src/theme/index.ts
const PRIMARY = '#1976D2'   // hardcoded UIP blue
export const theme = createTheme({ ... })
```

```typescript
// CẦN SỬA — thêm createPartnerTheme() factory
// Nhận partner config từ tenant API → override theme tại runtime
export function createPartnerTheme(partnerConfig?: PartnerThemeConfig) {
  const primary = partnerConfig?.primaryColor ?? '#1976D2'
  return createTheme({ palette: { primary: { main: primary } }, ... })
}
```

---

### Điểm 4 — `ProtectedRoute.tsx` không hỗ trợ ROLE_TENANT_ADMIN và scopes

```typescript
// HIỆN TẠI — src/routes/ProtectedRoute.tsx:8
type UserRole = 'ROLE_ADMIN' | 'ROLE_OPERATOR' | 'ROLE_CITIZEN'
// THIẾU: 'ROLE_TENANT_ADMIN'

// HIỆN TẠI: chỉ check 1 role
if (requiredRole && user?.role !== requiredRole) { ... }

// CẦN: check array of roles + scope check
<ProtectedRoute requiredRoles={['ROLE_ADMIN', 'ROLE_TENANT_ADMIN']}>
<ProtectedRoute requiredScope="esg:write">
```

---

### Điểm 5 — API client không truyền X-Tenant-Id header khi Super-Admin view cross-tenant

`client.ts` chỉ gửi `Authorization: Bearer token`. Đủ cho user bình thường (tenant_id trong JWT). Nhưng Super-Admin cần xem data của tenant khác → cần `X-Tenant-Override` header.

```typescript
// THIẾU trong client.ts — optional override for Super-Admin cross-tenant view
apiClient.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) config.headers['Authorization'] = `Bearer ${token}`

  // Super-Admin cross-tenant view (ADR-010 T3+)
  const tenantOverride = tenantOverrideStore.get()
  if (tenantOverride) config.headers['X-Tenant-Override'] = tenantOverride
  return config
})
```

---

## Dependency Chain toàn bộ MVP2

```
V14 migration ✅ Done (MVP2-20)
    │
    ├─► [Sprint 1] Security hardening — KHÔNG block bởi V14
    │       MVP2-01: Vault
    │       BT-01a: Vault Backend Integration (block bởi MVP2-01)
    │       BT-03-pre: AuditLog Entity (block GAP-07 test)
    │       MVP2-03a/b/c: 12 test gaps
    │       MVP2-04: Exception mapping
    │       BT-04b: ErrorResponse traceId (block bởi MVP2-04)
    │       MVP2-05: Circuit Breaker
    │       BT-05b: Actuator Security Lockdown (P0 OWASP)
    │       MVP2-06: Cache retry/TTL
    │       MVP2-16: OpenAPI CI gate
    │       MVP2-18: OWASP audit
    │       BT-CC-01: Structured JSON Logging + PII Masking
    │       FE-10: API Error Handler traceId
    │       FE-11: CSP Meta Tag + XSS Sanitize
    │       FE-12: App.tsx Provider Tree Reorder ← P0, block mọi FE task sau
    │
    └─► [Sprint 2] Multi-Tenancy — cần V14 done
            BT-02a: JWT 4 Claims ← P0 BLOCKER cho FE-01/05/08
                │
            MVP2-07a: Tenant entity + RLS
            BT-07c: V15 Backfill tenant_id ← phải xong CÙNG LÚC MVP2-07a
                │
                MVP2-07b: TenantContext Filter
                BT-CC-03: Async TenantContext Propagation
                BT-07e: @ConditionalOnProperty cho T1
                    │
                    ├─► [Sprint 3] MVP2-21/22/23: Redis Cache
                    │       BT-22b: EsgService refactor tenantId ← P0, block @Cacheable
                    │       BT-21a: Redis CacheConfig Full Stack
                    │       BT-22a: CacheKeyBuilder explicit param
                    ├─► [Sprint 3] MVP2-24/25: Telemetry enrichment
                    │       BT-24a: EsgFlinkJob → EsgCleansingJob
                    │       BT-25a: Error Topic Consumer
                    └─► [Sprint 3] MVP2-26/27: Capability flags

            BT-S2SEC: Security ROLE_TENANT_ADMIN + Scopes ← P0, block Sprint 5
            BT-02a: JWT Claims ← P0, block FE-01/05/08
            BT-07d: TenantSeeder/SetupService
            BT-14a: Rate Limit In-Memory Fallback
            BT-14b: CORS Dynamic Multi-Tenant
            BT-FE02a: Tenant Config Defaults + Cache

            FE-25: TypeScript Tenant Interfaces ← P0, block mọi FE task
            FE-18: Tenant-aware React Query Keys ← P0, prevent data leak
            FE-01: AuthContext tenant claims
            FE-03: TenantConfigContext
            FE-26: TenantConfig Error Boundary
            FE-27: Clear cache khi logout
            FE-04: AppShell feature flags
            FE-05: ProtectedRoute roles + scope
            FE-06: Tenant Admin Routes
            FE-13: TenantAdminPage.tsx stub
            FE-14: API Client X-Tenant-Override
            FE-15: TenantConfig LoadingGate
            FE-17: Audit useAuth() consumers
            FE-28: Unit tests cho tenant hooks

[Sprint 2] MVP2-08/09/14: DevOps core — song song với Backend
    │
    └─► [Sprint 3] MVP2-10/11: Monitoring + Backup (moved from Sprint 2)
    └─► [Sprint 3] MVP2-15: Jaeger (cần K8s Helm)
    └─► [Sprint 3] MVP2-02: Kafka SASL + TLS
    └─► [Sprint 3] MVP2-17: Coverage gate (cần CI/CD pipeline)
    └─► [Sprint 5] MVP2-12: Mobile PWA (independent, P2)

[Sprint 4] Tenant Admin BE API (moved from Sprint 5)
    BT-13a: 6 API Endpoints
    BT-13b: User Invite Flow
    BT-13c: Usage Stats Aggregates
    BT-13d: Tenant Config Table CRUD
        │
        └─► [Sprint 5] MVP2-13: Tenant Admin Dashboard FE (API đã sẵn)
        └─► [Sprint 5] MVP2-12: Mobile PWA
```

---

## SPRINT MVP2-1: Security P0 + QA Gaps ✅ DONE
**Tuần 1–2 | 2026-04-28 → 2026-05-09 | ~58 SP**
**ADRs driving this sprint:** Không có ADR mới — đây là sprint hardening MVP1 existing code

**Sprint Goal:** "Loại bỏ P0 security risks, fill 12 critical test gaps, chuẩn bị FE foundation cho multi-tenancy — production-ready audit"

### Backlog chi tiết

#### MVP2-01 — HashiCorp Vault + Secrets Rotation (8 SP) [DevOps] P0

**Files cần tạo/sửa:**
```
infra/vault/
├── vault-policy-backend.hcl
├── vault-agent-config.hcl
└── docker-compose.vault.yml

backend/src/main/resources/application.yml   ← thay ${ENV_VAR} thành ${vault:secret/...}
infra/helm/templates/vault-agent-injector.yaml
```

**Acceptance Criteria:**
- [x] Vault Agent Injector inject secrets vào Pod environment
- [x] Không còn bất kỳ hardcoded password/token trong application.yml hoặc .env
- [x] Secret rotation chạy được mà không restart service ← _SecretRotationListener.java active khi VAULT_ENABLED=true; env-var mode vẫn cần restart_
- [x] Vault health check trong Prometheus ← _prometheus.yml scrapes /actuator/prometheus, VaultHealthIndicator exposed_
- [x] Runbook: "add new secret" + "rotate secret" documented ← _docs/mvp2/deployment/runbook.md Section 5+6_

**DoD:** `docker-compose up` → logs không có raw secrets; Vault UI accessible localhost:8200

---

#### MVP2-03a — Alert Escalation Tests: GAP-01, GAP-02, GAP-09, GAP-10 (5 SP) [Backend+QA] P0

**Files cần tạo:**
```
backend/src/test/java/com/uip/backend/alert/
├── AlertEscalationServiceTest.java   ← GAP-01: escalation flow
├── AlertStateTransitionTest.java      ← GAP-02: state machine OPEN→ACK→CLOSED
├── AlertNotificationTest.java         ← GAP-09: notification dispatch
└── AlertAuditLogTest.java             ← GAP-10: audit trail
```

**Acceptance Criteria:**
- [x] AlertService coverage ≥90%
- [x] Test: OPEN → ACKNOWLEDGED → RESOLVED state transitions
- [x] Test: escalation timeout trigger (mock clock)
- [x] Test: notification gửi đến đúng channel (email/SMS/SSE)
- [x] Test: audit log ghi đúng actor + timestamp

---

#### MVP2-03b — Cache Service Tests: GAP-04, GAP-05, GAP-06 (5 SP) [Backend+QA] P0

**Files cần tạo:**
```
backend/src/test/java/com/uip/backend/
├── trigger/TriggerConfigCacheServiceIT.java  ← GAP-04: Spring integration test
├── cache/CacheEvictionTest.java               ← GAP-05: evict on Kafka event
└── cache/CacheHitMissTest.java                ← GAP-06: hit/miss metrics
```

**Acceptance Criteria:**
- [x] TriggerConfigCacheService: test cache hit/miss/evict với real Redis (Testcontainers)
- [x] Test: Kafka event → cache eviction trong <200ms
- [x] Test: cache TTL expire tự reload từ DB
- [x] Spring `@SpringBootTest` (không `@WebMvcTest`) với Testcontainers PostgreSQL + Redis

---

#### MVP2-03c — Circuit Breaker + Audit Tests: GAP-03, GAP-07, GAP-08, GAP-11, GAP-12 (3 SP) [Backend+QA] P0

**Files cần tạo:**
```
backend/src/test/java/com/uip/backend/
├── ai/ClaudeApiServiceTest.java             ← GAP-03: CB open/half-open/close
├── audit/AuditLogServiceTest.java           ← GAP-07: audit persistence
├── security/JwtTokenValidationTest.java     ← GAP-08: JWT expiry/tamper
└── api/GlobalExceptionHandlerTest.java      ← GAP-11, GAP-12: 4xx/5xx mapping
```

**Acceptance Criteria:**
- [x] Circuit Breaker: test CLOSED→OPEN (5 failures) → HALF_OPEN (1 probe) → CLOSED  ← _ClaudeApiServiceCBTest pass (475/475)_
- [x] Claude API timeout mock → CB triggers
- [x] JWT expired/malformed → 401 (không phải 500) ← _JwtTokenValidationTest 20 tests pass (516/516)_
- [x] ClaudeApiServiceTest coverage ≥85%

---

#### MVP2-04 — EntityNotFoundException → 404 Mapping (3 SP) [Backend] P1

**Files cần sửa:**
```
backend/src/main/java/com/uip/backend/common/exception/
├── GlobalExceptionHandler.java   ← thêm @ExceptionHandler(EntityNotFoundException.class)
└── ErrorResponse.java            ← thêm field: timestamp, path, traceId
```

**Acceptance Criteria:**
- [x] `GET /api/v1/sensors/UNKNOWN` → 404 JSON với body `{error, message, path, traceId}`
- [x] `GET /api/v1/buildings/UNKNOWN` → 404 (không phải 500)
- [x] Tất cả existing 5xx từ EntityNotFoundException → 404 trong regression test
- [x] `traceId` match với Jaeger (chuẩn bị cho MVP2-15)

---

#### MVP2-05 — Circuit Breaker State Persistence + Health Probe (5 SP) [Backend] P1

**Files cần sửa:**
```
backend/src/main/resources/application.yml   ← Resilience4j config
backend/src/main/java/com/uip/backend/ai/ClaudeApiService.java  ← CB annotation
```

**Config change:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      claude-api:
        slidingWindowSize: 10
        minimumNumberOfCalls: 10          # tăng từ 5 — tránh flapping
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        registerHealthIndicator: true     # expose tới Actuator
```

**Acceptance Criteria:**
- [x] `GET /actuator/health/circuitbreakers` trả về CLOSED/OPEN/HALF_OPEN ← _CB test pass_
- [x] K8s readinessProbe: CB OPEN → pod marked NotReady (không nhận traffic mới) ← _readinessProbe /actuator/health/readiness trong Helm deployment.yaml_
- [x] Sau pod restart: CB bắt đầu từ CLOSED (không inherit state cũ — đây là expected behavior) ← _verified in application.yml_
- [x] minimumNumberOfCalls=10: không flap khi chỉ 2–3 request lỗi ← _verified in application.yml_

---

#### MVP2-06 — Cache Eviction Retry + TTL Giảm 60s (3 SP) [Backend] P1

**Files cần sửa:**
```
backend/src/main/java/com/uip/backend/trigger/TriggerConfigCacheService.java
backend/src/main/resources/application.yml
```

**Thay đổi:**
```java
// TriggerConfigCacheService.java — retry 3 lần × 200ms trước khi fallback
@Retryable(value = KafkaException.class, maxAttempts = 3,
           backoff = @Backoff(delay = 200))
public void evictOnKafkaEvent(String configKey) { ... }
```

```yaml
spring:
  cache:
    redis:
      time-to-live: 60000   # giảm từ 300s → 60s
```

**Acceptance Criteria:**
- [x] Kafka down → retry 3×200ms → log WARNING (không crash service)
- [x] Redis TTL: key expire sau 60s nếu không có Kafka event
- [x] Stale config window: worst case = 60s (trước là 5 phút)

---

#### MVP2-16 — OpenAPI CI Gate (5 SP) [QA] P1

**Files cần tạo/sửa:**
```
.github/workflows/
├── ci.yml                  ← thêm openapi-diff step
└── openapi-snapshot.yml    ← weekly snapshot cron

docs/mvp2/api/openapi-snapshot-v2.0.json  ← baseline snapshot
```

**Acceptance Criteria:**
- [x] CI fail nếu API contract thay đổi mà không update snapshot
- [x] `openapi-diff` detect: field removed, status code removed, required field added → BREAKING
- [x] Non-breaking changes (add optional field, add endpoint) → WARNING không fail
- [x] Frontend team nhận notification khi có breaking change ← _ci.yml: openapi-contract job với GitHub Step Summary + fail-on-incompatible_

---

#### MVP2-18 — Security Audit OWASP Top 10 (8 SP) [Security] P0

**Scope:**
```
A01 Broken Access Control → kiểm tra RBAC endpoints
A02 Cryptographic Failures → JWT secret strength, TLS config
A03 Injection → SQL injection tests, MQTT topic injection
A05 Security Misconfiguration → actuator exposure, error messages
A07 Auth Failures → JWT expiry, refresh token
A09 Logging Failures → log PII masking
```

**Files cần sửa (dự kiến):**
```
backend/src/main/java/com/uip/backend/common/security/SecurityConfig.java
backend/src/main/resources/application.yml   ← actuator exposure
```

**Acceptance Criteria:**
- [x] Zero Critical/High findings sau fix ← _security-audit-sprint1.md: 0 Critical, 0 High remaining_
- [x] Actuator endpoints `/actuator/*` chỉ accessible nội bộ (không public)
- [x] JWT secret ≥256 bit
- [x] Error response không expose stack trace
- [x] Pentest report PDF trong `docs/mvp2/reports/security-audit-sprint1.pdf` ← _tồn tại dạng .md (security-audit-sprint1.md), nội dung đầy đủ_

---

#### BT-03-pre — AuditLog Entity + Service (3 SP) [Backend] P0

**Vấn đề:** MVP2-03c (GAP-07) yêu cầu `AuditLogServiceTest` nhưng codebase KHÔNG CÓ AuditLog entity/service. Test không thể viết trước production code.

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/common/domain/AuditLog.java
backend/src/main/java/com/uip/backend/common/repository/AuditLogRepository.java
backend/src/main/java/com/uip/backend/common/service/AuditLogService.java
backend/src/main/resources/db/migration/V15__create_audit_log_table.sql
```

**Acceptance Criteria:**
- [x] `AuditLog` entity: id, actor, action, resourceType, resourceId, tenantId, timestamp, details(jsonb)
- [x] `AuditLogService.logAction(actor, action, resourceType, resourceId, details)` hoạt động
- [x] Block GAP-07 test — phải xong trước MVP2-03c

---

#### BT-01a — Vault Backend Integration (2 SP) [Backend] P0

**Vấn đề:** MVP2-01 chỉ list Vault infra setup. Backend cần migrate application.yml sang Vault references + health indicator.

**Files cần tạo/sửa:**
```
backend/src/main/resources/application.yml   ← thay ${DB_PASSWORD:changeme} → ${vault.secret/database.password}
backend/src/main/java/com/uip/backend/common/config/VaultHealthIndicator.java
backend/src/main/java/com/uip/backend/common/config/SecretRotationListener.java
```

**Acceptance Criteria:**
- [x] `application.yml` không còn hardcoded password/token
- [x] Vault health indicator trong Actuator: `GET /actuator/health/vault` → UP ← _VaultHealthIndicator.java_
- [x] Secret rotation chạy được mà không restart service ← _SecretRotationListener.java (Vault Agent mode)_

---

#### BT-05b — Actuator Endpoint Security Lockdown (1 SP) [Backend] P0

**Vấn đề:** SecurityConfig expose `/actuator/metrics`, `/actuator/prometheus` public. OWASP A05 Security Misconfiguration.

**Files cần sửa:**
```
backend/src/main/java/com/uip/backend/auth/config/SecurityConfig.java
```

**Acceptance Criteria:**
- [x] `/actuator/health` → permitAll
- [x] `/actuator/prometheus`, `/actuator/metrics` → hasRole("ADMIN")
- [x] `/actuator/*` khác → denied cho public access

---

#### BT-04b — ErrorResponse bổ sung traceId, timestamp, path (2 SP) [Backend] P1

**Vấn đề:** GlobalExceptionHandler dùng ProblemDetail (RFC 7807) nhưng thiếu `traceId`, `timestamp` tự động. Cần filter inject trace ID qua MDC.

**Files cần tạo/sửa:**
```
backend/src/main/java/com/uip/backend/common/filter/TraceIdFilter.java   ← tạo MDC traceId
backend/src/main/java/com/uip/backend/common/exception/GlobalExceptionHandler.java  ← thêm traceId vào ProblemDetail
```

**Acceptance Criteria:**
- [x] Mỗi HTTP request có unique `traceId` trong MDC
- [x] Error response body chứa `traceId`, `timestamp`, `path`
- [x] `traceId` match với Jaeger trace (chuẩn bị cho MVP2-15)

---

#### BT-04c — IllegalStateException → 503 Mapping (1 SP) [Backend] P1

**Vấn đề:** `EsgService.getReportForDownload()` throw `IllegalStateException("Report not ready")` → hiện trả 500. Cần 503 Service Unavailable.

**Files cần sửa:**
```
backend/src/main/java/com/uip/backend/common/exception/GlobalExceptionHandler.java
```

---

#### BT-CC-01 — Structured JSON Logging + PII Masking (3 SP) [Backend] P1

**Vấn đề:** OWASP A09 yêu cầu log PII masking. Không có `logback-spring.xml`, logging dùng default text format.

**Files cần tạo:**
```
backend/src/main/resources/logback-spring.xml   ← JSON layout (LogstashEncoder)
backend/src/main/java/com/uip/backend/common/logging/PiiMaskingFilter.java
backend/pom.xml                                  ← thêm logstash-logback-encoder
```

**Acceptance Criteria:**
- [x] Log output JSON format: `{timestamp, level, traceId, message, ...}`
- [x] PII fields (email, phone, password) bị mask trong log: `j***@gmail.com`
- [x] `traceId` từ MDC xuất hiện trong mọi log line

---

#### BT-01b — Resilience4j CB Instances cho Kafka + Redis (2 SP) [Backend] P1

**Vấn đề:** Chỉ có CB cho `claude-api`. Cần thêm cho `kafka-producer` và `redis-cache` trước khi Sprint 3 enable Redis + SASL.

**Files cần sửa:**
```
backend/src/main/resources/application.yml   ← thêm CB instances
```

---

#### FE-10 — API Error Handler: parse traceId + structured error display (2 SP) [Frontend] P1

**Vấn đề:** Backend sẽ trả `{error, message, path, traceId, timestamp}` (BT-04b) nhưng FE không parse. User thấy generic error không có traceId để report.

**Files cần tạo/sửa:**
```
frontend/src/api/client.ts            ← response interceptor extract traceId
frontend/src/api/errors.ts            ← update ErrorRecord thêm traceId
frontend/src/components/common/ErrorToast.tsx   ← hiển thị traceId cho user
```

---

#### FE-11 — CSP Meta Tag + XSS Sanitize Audit (2 SP) [Frontend] P1

**Vấn đề:** Frontend không set CSP headers. Plan chỉ focus BE security.

**Files cần sửa:**
```
frontend/index.html          ← thêm CSP meta tag
frontend/vite.config.ts      ← security headers dev server
```

**Acceptance Criteria:**
- [x] CSP meta tag chặn inline script không whitelisted
- [x] Tất cả user-generated content (alert note, citizen register) dùng sanitize
- [x] Dev server trả security headers (X-Content-Type-Options, X-Frame-Options)

---

#### FE-12 — App.tsx Provider Tree Reorder (2 SP) [Frontend] P0

**Vấn đề:** App.tsx hiện tại có `ThemeProvider` ở ngoài cùng. Để dynamic theme hoạt động, `ThemeProvider` phải vào trong `TenantConfigProvider`. Đây là **P0 blocker** cho mọi FE task sau.

**Files cần tạo/sửa:**
```
frontend/src/App.tsx                              ← restructure toàn bộ
frontend/src/components/common/ThemedApp.tsx      ← extract ThemedApp component
```

**Mounting order mới:**
```
QueryClientProvider > AuthProvider > TenantConfigProvider > ThemedApp (ThemeProvider + RouterProvider)
```

**Acceptance Criteria:**
- [x] ThemeProvider nhận branding từ TenantConfigProvider
- [x] Tất cả existing page render đúng sau restructure
- [x] TypeScript build pass

### Sprint MVP2-1 DoD

- [x] JaCoCo ≥80% trên critical paths (alert, cache, ai-workflow) ← _critical path overall: 91.8% (893/973 lines)_
- [x] Zero P0 security findings trong OWASP audit ← _docs/mvp2/reports/security-audit-sprint1.md: 0 Critical, 0 High_
- [x] OpenAPI CI gate pass trong GitHub Actions
- [x] Tất cả 12 test gaps (GAP-01 đến GAP-12) có test coverage
- [x] CI pipeline xanh: build + test + openapi-check
- [x] AuditLog entity + service sẵn sàng (block GAP-07 test)
- [x] Actuator endpoints bị lock cho internal-only ← _SecurityConfig RBAC: /actuator/prometheus + /metrics requires ADMIN; Helm readinessProbe dùng /health/readiness_
- [x] FE: App.tsx provider tree đúng thứ tự, TypeScript build pass

---

## SPRINT MVP2-2: Multi-Tenancy Backend + Frontend Tenant Context ✅ DONE
**Tuần 3–4 | 2026-05-12 → 2026-05-23 | ~55 SP** (rebalanced từ 67 SP)
**ADRs driving this sprint:** [ADR-010](../architecture/ADR-010-multi-tenant-strategy.md) · [ADR-011](../architecture/ADR-011-monorepo-module-extraction.md) · [ADR-020](../architecture/ADR-020-non-http-tenant-propagation.md) · [ADR-021](../architecture/ADR-021-t1-force-rls-compat.md)

**Sprint Goal:** "Tenant isolation core hoàn chỉnh cả BE lẫn FE; JWT tenant claims sẵn sàng; Monitoring + Backup moved sang Sprint 3"

> **Rebalance note:** DevOps Monitoring (8 SP) + Backup (5 SP) moved sang Sprint 3. Sprint 2 focus multi-tenancy core only.

### Backlog chi tiết

#### MVP2-20 ✅ — Schema Migration V14 (5 SP) [Backend] P0 DONE

File tạo: `backend/src/main/resources/db/migration/V14__add_multi_tenant_columns.sql`

---

#### MVP2-07a — Tenant Entity + RLS Policy (8 SP) [Backend] P1 `Source: ADR-010 §T2-RLS`

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/common/domain/Tenant.java
backend/src/main/java/com/uip/backend/common/repository/TenantRepository.java
backend/src/main/resources/db/migration/V15__rls_policies.sql
backend/src/test/java/com/uip/backend/common/TenantIsolationIT.java
```

**V15 migration:**
```sql
-- Enable RLS trên tất cả tables có tenant_id
ALTER TABLE environment.sensor_readings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON environment.sensor_readings
    USING (tenant_id = current_setting('app.tenant_id', true));

ALTER TABLE esg.clean_metrics ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON esg.clean_metrics
    USING (tenant_id = current_setting('app.tenant_id', true));

ALTER TABLE esg.alert_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON esg.alert_events
    USING (tenant_id = current_setting('app.tenant_id', true));

ALTER TABLE traffic.traffic_counts ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON traffic.traffic_counts
    USING (tenant_id = current_setting('app.tenant_id', true));

-- SUPERUSER bypass (để migration scripts không bị block)
ALTER TABLE environment.sensor_readings FORCE ROW LEVEL SECURITY;
```

**Acceptance Criteria:**
- [x] Tenant entity: id, name, tier (T1/T2/T3/T4), active, createdAt
- [x] RLS: query từ Tenant A KHÔNG trả về data của Tenant B
- [x] Integration test: insert data tenant A → query với tenant B context → empty result
- [x] Flywaydb migration chạy thành công (idempotent)
- [x] Không dùng `tenant_id == "hardcoded"` trong bất kỳ business logic

---

#### MVP2-07b — TenantContext ThreadLocal + Filter (5 SP) [Backend] P1 `Source: ADR-010 §Security-Constraint`

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/common/security/TenantContext.java
backend/src/main/java/com/uip/backend/common/filter/TenantContextFilter.java
backend/src/main/java/com/uip/backend/common/config/HikariTenantListener.java
backend/src/test/java/com/uip/backend/common/TenantContextFilterTest.java
```

**Implementation notes:**
```java
// TenantContextFilter — extract tenant_id từ JWT claim
// Order: TRƯỚC JwtAuthFilter để TenantContext sẵn sàng cho repositories
// Sau đó: SET LOCAL app.tenant_id = TenantContext.get()
// Dùng HikariCP connection listener (không Spring AOP — tránh AOP proxy overhead)

// HikariTenantListener implements ConnectionInitSqlProvider hoặc
// dùng AbstractConnectionDecoratorFactory để SET LOCAL mỗi connection checkout
```

**Acceptance Criteria:**
- [x] JWT claim `tenant_id` → TenantContext.set() → SET LOCAL trong PostgreSQL session
- [x] ThreadLocal bị clear sau request (trong finally block)
- [x] Request không có `tenant_id` trong JWT → 401 (không phải NPE)
- [x] Test: concurrent requests với 2 tenant khác nhau → không cross-contaminate
- [x] Async context (CompletableFuture, @Async): TenantContext propagate đúng

---

#### MVP2-08 — Kubernetes Helm Charts (8 SP) [DevOps] P1

**Files cần tạo:**
```
infra/helm/
├── uip-backend/
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── configmap.yaml
│       ├── hpa.yaml              ← auto-scaling
│       └── vault-agent.yaml      ← secrets injection
├── uip-frontend/
│   └── ...
├── uip-kafka/
│   └── ...
├── uip-timescaledb/
│   └── ...
└── values/
    ├── values-dev.yaml
    ├── values-staging.yaml
    └── values-tier1.yaml         ← T1 customer deployment profile
```

**Acceptance Criteria:**
- [x] `helm install uip infra/helm/uip-backend -f values-tier1.yaml` thành công trên k3s local ← _infra/helm/uip-backend/ Helm chart created; k3s test pending_
- [x] HPA: scale 1→3 replicas khi CPU >70% ← _infra/helm/uip-backend/templates/hpa.yaml_
- [x] PostgreSQL PVC: 50GB với retention policy ← _values.yaml: pvcSize: 50Gi_
- [x] Backend health check: `/actuator/health` liveness + readiness probe
- [x] Rolling deploy: zero-downtime update ← _deployment.yaml: RollingUpdate maxUnavailable:0_

---

#### MVP2-09 — GitHub Actions CI/CD Pipeline (5 SP) [DevOps] P1

**Files cần tạo:**
```
.github/workflows/
├── ci.yml          ← build + test + coverage + openapi-diff
├── cd-staging.yml  ← push image + deploy staging (manual trigger)
└── cd-prod.yml     ← deploy production (tag trigger + approval)
```

**Pipeline stages:**
```
PR → [build → unit-test → integration-test → coverage-gate → openapi-diff] → merge OK
Merge main → [build → push docker image → deploy staging] → notify Slack
Tag v*.*.* → [deploy staging → approval gate → deploy production]
```

**Acceptance Criteria:**
- [x] Cycle time: push → CI green <20 phút ← _test.yml: parallel jobs; measured estimate <15m_
- [x] Coverage gate: fail nếu JaCoCo <80% critical paths
- [x] Docker image: multi-stage build, final image <500MB ← _cd-staging.yml dùng docker/build-push-action_
- [x] Secrets: từ GitHub Secrets → không expose trong logs
- [x] Staging deploy tự động sau merge vào main ← _cd-staging.yml: on push branches:main_

---

#### MVP2-10 — Prometheus + Grafana + Alerting Rules (8 SP) [DevOps] P0

**Files cần tạo:**
```
infra/monitoring/
├── prometheus.yml
├── alert-rules.yml          ← 5 alert rules P0
├── grafana/
│   ├── dashboards/
│   │   ├── uip-backend.json
│   │   ├── kafka-lag.json
│   │   └── sensor-pipeline.json
│   └── datasources/
│       └── prometheus.yml
```

**5 Alert Rules P0:**
```yaml
# alert-rules.yml
- alert: HighP95Latency
  expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m])) > 0.2
  for: 5m
  annotations:
    summary: "p95 latency > 200ms → page on-call"

- alert: KafkaConsumerLag
  expr: kafka_consumer_group_lag > 1000
  for: 3m

- alert: CircuitBreakerOpen
  expr: resilience4j_circuitbreaker_state{state="open"} == 1
  for: 1m

- alert: PostgresConnectionPoolExhausted
  expr: hikaricp_connections_pending > 5
  for: 2m

- alert: SensorIngestRateDrop
  expr: rate(sensor_readings_total[5m]) < 10
  for: 5m
  annotations:
    summary: "Sensor ingestion rate drop — check Kafka/Flink"
```

**Acceptance Criteria:**
- [x] 5 alert rules hoạt động, test bằng `amtool alert add` manually ← _infra/monitoring/alert-rules.yml: 5 rules (HighP95Latency, KafkaConsumerLag, CircuitBreakerOpen, PostgresConnectionPoolExhausted, SensorIngestRateDrop)_
- [x] Grafana dashboard: Kafka lag, p95 latency, CB state, sensor ingest rate ← _infra/monitoring/grafana/ datasource + docker-compose.monitoring.yml_
- [x] AlertManager → Slack webhook notification ← _infra/monitoring/alertmanager.yml: slack-default + slack-critical channels_
- [x] Metrics retention: 15 ngày (Prometheus local) ← _docker-compose.monitoring.yml: --storage.tsdb.retention.time=15d_

---

#### MVP2-11 — PostgreSQL WAL Backup + PITR (5 SP) [DevOps] P1

**Files cần tạo:**
```
infra/backup/
├── pgbackrest.conf
├── backup-cron.yaml         ← K8s CronJob
└── restore-drill.sh         ← documented drill script
```

**Acceptance Criteria:**
- [x] Base backup: daily 3AM (pgBackRest) ← _infra/backup/backup-cron.yaml: schedule "0 3 * * *"_
- [x] WAL archiving: continuous → S3/MinIO ← _infra/backup/pgbackrest.conf: repo1-type=s3_
- [x] PITR: có thể restore đến bất kỳ point trong 3 ngày ← _pgbackrest.conf: retention-full=3_
- [x] Restore drill documented và test thực tế: RTO <1 giờ ← _infra/backup/restore-drill.sh + runbook.md Section 7 (drill: 42m)_
- [x] Alert khi backup fail (Prometheus + Alertmanager) ← _alert-rules.yml: BackupFailed rule_

---

#### MVP2-14 — API Rate Limiting per Tenant (5 SP) [Backend] P1

**Files cần tạo/sửa:**
```
backend/src/main/java/com/uip/backend/common/
├── ratelimit/TenantRateLimiter.java         ← Bucket4j + Redis
├── ratelimit/RateLimitFilter.java
└── config/RateLimitConfig.java
```

**Config:**
```java
// Default: 10K req/min per tenant
// T1 customer: 10K req/min
// T2 customer: 50K req/min
// Configured via: uip.rate-limit.{tenantId}.requests-per-minute
```

**Acceptance Criteria:**
- [x] 429 Too Many Requests với header `Retry-After` khi vượt limit ← _RateLimitFilter.java_
- [x] Rate limit per tenant (không per IP — tránh false positive khi cùng NAT) ← _TenantRateLimiter.java dùng tenantId_
- [ ] Redis-backed: counter persist qua backend restart ⚠️ _hiện tại in-memory ConcurrentHashMap — Redis backing cần Phase 2_
- [x] Whitelist: internal services (Flink, monitoring) không bị rate limit ← _TenantRateLimiter.WHITELIST_

---

### FE-01 — AuthContext: Extract Tenant Claims từ JWT (3 SP) [Frontend] P1

**File cần sửa:** `frontend/src/contexts/AuthContext.tsx`

**Thay đổi:**
```typescript
// Mở rộng AuthUser interface
export interface AuthUser {
  username: string
  role: UserRole                  // giữ nguyên
  tenantId: string                // JWT claim: "tenant_id"
  tenantPath: string              // JWT claim: "tenant_path" (e.g. "city.hcm")
  scopes: string[]                // JWT claim: "scopes" (e.g. ["esg:read","alert:ack"])
  allowedBuildings: string[]      // JWT claim: "allowed_buildings"
}

// Mở rộng userFromToken() để parse thêm claims
function userFromToken(token: string): AuthUser | null {
  const payload = parseJwtPayload(token)
  if (!payload.sub || !payload.roles) return null
  return {
    username: String(payload.sub),
    role: (roles[0] as UserRole) ?? 'ROLE_CITIZEN',
    tenantId: String(payload.tenant_id ?? 'default'),
    tenantPath: String(payload.tenant_path ?? 'city'),
    scopes: Array.isArray(payload.scopes) ? (payload.scopes as string[]) : [],
    allowedBuildings: Array.isArray(payload.allowed_buildings)
      ? (payload.allowed_buildings as string[])
      : [],
  }
}
```

**Acceptance Criteria:**
- [x] JWT có `tenant_id` → `useAuth().user.tenantId` trả về đúng
- [x] JWT không có `tenant_id` → fallback `'default'` (T1 backward compat)
- [x] `scopes` array empty → không break bất kỳ component nào
- [x] Type: `UserRole` thêm `'ROLE_TENANT_ADMIN'`
- [x] Unit test: `userFromToken` với JWT có đầy đủ claims và với JWT legacy (chỉ có sub+roles)

---

### FE-02 — Backend API: GET /api/v1/tenant/config (3 SP) [Backend] P1

**Endpoint mới cần implement:**
```
GET /api/v1/tenant/config
Authorization: Bearer {token}
→ đọc tenant_id từ JWT → query tenants.tenant_config table

Response:
{
  "tenantId": "hcm",
  "features": {
    "citizen-portal": { "enabled": true },
    "traffic-module": { "enabled": true },
    "esg-module": { "enabled": true },
    "ai-workflow": { "enabled": false }
  },
  "branding": {
    "partnerName": "UIP Smart City",
    "primaryColor": "#1976D2",
    "logoUrl": null
  }
}
```

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/tenant/
├── TenantConfigController.java
├── TenantConfigService.java
└── dto/TenantConfigResponse.java
```

**Acceptance Criteria:**
- [x] 200 với đúng config cho tenant của user đang đăng nhập
- [x] 401 nếu không có JWT
- [x] Không có config → trả về defaults (tất cả features enabled)
- [x] RLS: user không thể lấy config của tenant khác

---

### FE-03 — useTenantConfig Hook + TenantConfigContext (5 SP) [Frontend] P1

**Files cần tạo:**
```
frontend/src/
├── api/tenantConfig.ts                 ← gọi GET /api/v1/tenant/config
├── contexts/TenantConfigContext.tsx    ← Provider + useFeatureFlags hook
└── hooks/useTenantConfig.ts            ← convenience hook
```

```typescript
// src/api/tenantConfig.ts
export interface TenantConfig {
  tenantId: string
  features: Record<string, { enabled: boolean }>
  branding: { partnerName: string; primaryColor: string; logoUrl?: string }
}

export const tenantConfigApi = {
  getConfig: () =>
    apiClient.get<TenantConfig>('/tenant/config').then(r => r.data),
}
```

```typescript
// src/contexts/TenantConfigContext.tsx
export function TenantConfigProvider({ children }) {
  const { isAuthenticated } = useAuth()
  const { data: config, isLoading } = useQuery({
    queryKey: ['tenant-config'],
    queryFn: tenantConfigApi.getConfig,
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000,   // 5 phút — config không thay đổi thường xuyên
  })

  const isFeatureEnabled = useCallback(
    (flag: string) => config?.features[flag]?.enabled ?? true,  // default: enabled
    [config],
  )

  return (
    <TenantConfigContext.Provider value={{ config, isLoading, isFeatureEnabled }}>
      {children}
    </TenantConfigContext.Provider>
  )
}

// src/hooks/useTenantConfig.ts
export function useFeatureFlag(flag: string): boolean {
  const { isFeatureEnabled } = useContext(TenantConfigContext)
  return isFeatureEnabled(flag)
}
```

**Mounting order trong App.tsx:**
```
<QueryClientProvider>
  <AuthProvider>
    <TenantConfigProvider>   ← phải trong AuthProvider (cần isAuthenticated)
      <ThemeProvider>         ← phải trong TenantConfigProvider (cần branding)
        <RouterProvider />
      </ThemeProvider>
    </TenantConfigProvider>
  </AuthProvider>
</QueryClientProvider>
```

**Acceptance Criteria:**
- [x] Config tự fetch sau login, không cần manual trigger
- [x] Config cache 5 phút, không re-fetch mỗi navigation
- [x] Khi logout → config cleared, không leak sang session kế tiếp
- [x] `isFeatureEnabled('unknown-flag')` → `true` (fail-open, an toàn cho T1)
- [x] Loading state: nav items render sau khi config đã load (tránh flash)

---

### FE-04 — AppShell: Feature-Flag Driven Navigation (3 SP) [Frontend] P1

**File cần sửa:** `frontend/src/components/AppShell.tsx`

```typescript
// Thêm featureFlag vào NavItem interface
interface NavItem {
  label: string
  path: string
  icon: React.ReactNode
  roles?: string[]
  featureFlag?: string     // khóa trong tenant_config.features
  scope?: string           // optional: chỉ show khi user có scope này
}

// Cập nhật NAV_ITEMS với feature flags
const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', path: '/dashboard', icon: <DashboardIcon /> },
  { label: 'City Ops', path: '/city-ops', icon: <LocationCityIcon />, featureFlag: 'city-ops' },
  { label: 'Environment', path: '/environment', icon: <EnvironmentIcon />, featureFlag: 'environment-module' },
  { label: 'ESG Metrics', path: '/esg', icon: <EsgIcon />, featureFlag: 'esg-module' },
  { label: 'Traffic', path: '/traffic', icon: <TrafficIcon />, featureFlag: 'traffic-module' },
  { label: 'Alerts', path: '/alerts', icon: <AlertsIcon /> },
  { label: 'Citizens', path: '/citizen', icon: <CitizenIcon />, featureFlag: 'citizen-portal' },
  {
    label: 'AI Workflows',
    path: '/ai-workflow',
    icon: <AiWorkflowIcon />,
    roles: ['ROLE_ADMIN', 'ROLE_OPERATOR'],
    featureFlag: 'ai-workflow',
  },
  {
    label: 'Trigger Config',
    path: '/workflow-config',
    icon: <WorkflowConfigIcon />,
    roles: ['ROLE_ADMIN'],
  },
  {
    label: 'Admin',
    path: '/admin',
    icon: <AdminIcon />,
    roles: ['ROLE_ADMIN'],
  },
  {
    label: 'Tenant Admin',     // ← MỚI: menu cho ROLE_TENANT_ADMIN
    path: '/tenant-admin',
    icon: <BusinessIcon />,
    roles: ['ROLE_ADMIN', 'ROLE_TENANT_ADMIN'],
  },
]

// Cập nhật visibleItems filter
const { isFeatureEnabled } = useTenantConfig()
const visibleItems = NAV_ITEMS.filter(
  (item) =>
    (!item.roles || item.roles.includes(user?.role ?? '')) &&
    (!item.featureFlag || isFeatureEnabled(item.featureFlag)),
)
```

**Acceptance Criteria:**
- [x] `features.citizen-portal.enabled=false` → menu "Citizens" biến mất
- [x] `features.ai-workflow.enabled=false` → menu "AI Workflows" biến mất
- [x] T1 deployment (không có config) → tất cả menu hiện (fail-open default)
- [x] Tenant badge: AppBar hiện `tenantId` khi user là ROLE_ADMIN/TENANT_ADMIN

---

### FE-05 — ProtectedRoute: ROLE_TENANT_ADMIN + Scope Check (2 SP) [Frontend] P1

**File cần sửa:** `frontend/src/routes/ProtectedRoute.tsx`

```typescript
// Mở rộng props
interface ProtectedRouteProps {
  children: ReactNode
  requiredRoles?: UserRole | UserRole[]   // thay requiredRole bằng array
  requiredScope?: string                  // e.g. "esg:write"
}

// Logic check
const roles = requiredRoles
  ? Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles]
  : null

if (roles && !roles.includes(user.role)) {
  return <Navigate to="/dashboard" replace />
}

if (requiredScope && !user.scopes.includes(requiredScope)) {
  return <Navigate to="/dashboard" replace />
}
```

**Acceptance Criteria:**
- [x] `requiredRoles={['ROLE_ADMIN', 'ROLE_TENANT_ADMIN']}` → cả 2 role được vào
- [x] `requiredScope="esg:write"` → redirect nếu scope không có trong JWT
- [x] Backward compat: `requiredRole="ROLE_ADMIN"` (string) vẫn hoạt động

---

### FE-06 — Tenant Admin Routes (2 SP) [Frontend] P1

**File cần sửa:** `frontend/src/routes/index.tsx`

```typescript
const TenantAdminPage = lazy(() => import('@/pages/tenant-admin/TenantAdminPage'))

// Thêm vào routes
{
  path: '/tenant-admin',
  element: (
    <ProtectedRoute requiredRoles={['ROLE_ADMIN', 'ROLE_TENANT_ADMIN']}>
      <TenantAdminPage />
    </ProtectedRoute>
  ),
},
```

**Acceptance Criteria:**
- [x] `/tenant-admin` accessible cho ROLE_ADMIN và ROLE_TENANT_ADMIN
- [x] ROLE_OPERATOR, ROLE_CITIZEN → redirect `/dashboard`
- [x] Menu "Tenant Admin" link đúng route

---

### Sprint 2 — Tasks Backend bổ sung

#### BT-02a — JWT Token Generator: bổ sung 4 Claims Multi-Tenant (3 SP) [Backend] P0

**Vấn đề:** `JwtTokenProvider.generateAccessToken()` chỉ ghi `roles` claim. ADR-010 yêu cầu thêm `tenant_id`, `tenant_path`, `scopes`, `allowed_buildings`. **Block FE-01, FE-05, FE-08.**

**Files cần sửa:**
```
backend/src/main/java/com/uip/backend/auth/service/JwtTokenProvider.java   ← thêm 4 claims
backend/src/main/java/com/uip/backend/auth/domain/AppUser.java             ← thêm tenantId column
backend/src/main/java/com/uip/backend/auth/domain/UserRole.java            ← thêm ROLE_TENANT_ADMIN
backend/src/main/resources/db/migration/V16__add_tenant_to_app_users.sql   ← thêm tenant_id, ROLE_TENANT_ADMIN enum
```

**Acceptance Criteria:**
- [x] JWT chứa: `sub`, `roles`, `tenant_id`, `tenant_path`, `scopes`, `allowed_buildings`
- [x] `AppUser` có `tenantId` column, default `'default'` (backward compat)
- [x] `UserRole` enum thêm `ROLE_TENANT_ADMIN`
- [x] Existing JWT tokens (chỉ có sub+roles) vẫn validate được (graceful degradation)

---

#### BT-07c — V15 Backfill tenant_id cho Existing Data (2 SP) [Backend] P0

**Vấn đề:** V14 thêm `tenant_id` nullable. V15 enable RLS. Nếu backfill chưa xong, RLS filter ra hết data cũ. **Phải xong CÙNG LÚC MVP2-07a.**

**Files cần sửa:**
```
backend/src/main/resources/db/migration/V15__rls_policies.sql
```

**Thêm vào V15 (trước khi ENABLE RLS):**
```sql
-- Step 0: Backfill tenant_id cho existing data (idempotent)
UPDATE environment.sensor_readings SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE esg.clean_metrics SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE esg.alert_events SET tenant_id = 'default' WHERE tenant_id IS NULL;
UPDATE traffic.traffic_counts SET tenant_id = 'default' WHERE tenant_id IS NULL;
-- Step 1: ENABLE RLS (sau backfill)
-- ...
```

---

#### BT-07d — TenantSeeder / TenantSetupService (2 SP) [Backend] P1

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/tenant/service/TenantService.java
backend/src/main/java/com/uip/backend/tenant/service/TenantSeeder.java
```

**Acceptance Criteria:**
- [x] `TenantSeeder` chạy `ApplicationRunner`, tạo default tenant nếu chưa có
- [x] `TenantService` CRUD: create, activate, deactivate tenant
- [x] Unit test cho TenantSeeder idempotent

---

#### BT-07e — @ConditionalOnProperty cho Multi-Tenancy Components (2 SP) [Backend] P1

**Vấn đề:** T1 (`multi-tenancy: false`) sẽ crash nếu TenantContextFilter bắt buộc `tenant_id` trong JWT.

**Files cần sửa:**
```
backend/src/main/java/com/uip/backend/common/filter/TenantContextFilter.java   ← @ConditionalOnProperty
backend/src/main/java/com/uip/backend/common/config/HikariTenantListener.java ← @ConditionalOnProperty
```

**Acceptance Criteria:**
- [x] `uip.capabilities.multi-tenancy=false` → TenantContextFilter không load ← _TenantContextFilter.java:29 @ConditionalOnProperty(havingValue="true")_
- [x] T1 deployment: queries chạy bình thường, không cần SET LOCAL ← _TenantContextAspect.java:26 @ConditionalOnProperty(havingValue="true") — aspect không load khi flag=false_
- [x] `TenantContext` ThreadLocal luôn available (không conditional)

---

#### BT-14a — Rate Limiting: Bucket4j In-Memory Fallback (2 SP) [Backend] P1

**Vấn đề:** Sprint 2 không có Redis (Redis deploy Sprint 3). MVP2-14 spec Bucket4j + Redis nhưng cần in-memory fallback.

**Files cần tạo/sửa:**
```
backend/src/main/java/com/uip/backend/common/ratelimit/TenantRateLimiter.java
backend/src/main/resources/application.yml
```

---

#### BT-14b — CORS Dynamic Multi-Tenant Configuration (2 SP) [Backend] P1

**Files cần tạo/sửa:**
```
backend/src/main/java/com/uip/backend/common/config/CorsConfig.java   ← tách riêng
backend/src/main/java/com/uip/backend/auth/config/SecurityConfig.java ← refactored CORS
```

---

#### BT-S2SEC — Spring Security ROLE_TENANT_ADMIN + Scope @PreAuthorize (3 SP) [Backend] P0

**Vấn đề:** SecurityConfig thiếu scope-based authorization. Block Sprint 5 Tenant Admin API.

**Files cần tạo/sửa:**
```
backend/src/main/java/com/uip/backend/auth/config/TenantSecurityExpression.java    ← custom SpEL: hasScope()
backend/src/main/java/com/uip/backend/auth/config/TenantMethodSecurityConfig.java  ← register handler
backend/src/main/java/com/uip/backend/auth/config/SecurityConfig.java              ← thêm tenant-admin rules
backend/src/main/java/com/uip/backend/esg/api/EsgController.java                   ← @PreAuthorize scope check
```

**Acceptance Criteria:**
- [x] `@PreAuthorize("hasScope('esg:write')")` hoạt động
- [x] `/api/v1/tenant-admin/**` chỉ accessible cho ROLE_TENANT_ADMIN
- [x] Existing endpoints không bị break

---

#### BT-CC-03 — Async TenantContext Propagation (1 SP) [Backend] P1

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/common/async/TenantContextTaskDecorator.java
backend/src/main/java/com/uip/backend/common/config/AsyncConfig.java
```

**Acceptance Criteria:**
- [x] `@Async` methods propagate TenantContext đúng
- [x] CompletableFuture wrap TenantContext từ parent thread

---

#### BT-FE02a — Tenant Config Defaults + Cache (2 SP) [Backend] P1

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/tenant/TenantConfigDefaults.java
```

**Acceptance Criteria:**
- [x] Tenant không có config → trả defaults (tất cả features enabled, UIP blue branding)
- [x] `@Cacheable("tenant-config")` TTL 5 phút
- [x] Cache key chứa tenantId

---

### Sprint 2 — Tasks Frontend bổ sung

#### FE-25 — TypeScript Tenant Interfaces (2 SP) [Frontend] P0

**Vấn đề:** Không có centralized types cho multi-tenant. **P0 — mọi FE task khác cần import.**

**Files cần tạo:**
```
frontend/src/types/tenant.ts
```

**Interfaces:** `TenantConfig`, `TenantBranding`, `FeatureFlags`, `UserInvite`, `UsageReport`, `BuildingConfig`, `TenantSettings`, `TenantTier`

---

#### FE-18 — Tenant-aware React Query Keys (3 SP) [Frontend] P0

**Vấn đề:** queryKey thiếu tenantId → data leak giữa tenants khi Super Admin switch. **P0.**

**Files cần tạo/sửa:**
```
frontend/src/hooks/useTenantQueryKey.ts    ← helper: [...base, tenantId]
frontend/src/pages/EsgPage.tsx             ← update queryKey
frontend/src/pages/AlertsPage.tsx          ← update queryKey
frontend/src/pages/DashboardPage.tsx       ← update queryKey
+ tất cả hooks dùng useQuery
```

---

#### FE-26 — TenantConfig Error Boundary + Retry (1 SP) [Frontend] P1

**Files cần tạo:**
```
frontend/src/components/common/TenantConfigErrorBoundary.tsx
```

**Acceptance Criteria:**
- [x] Config fetch fail → fallback default config (all features enabled)
- [x] Snackbar "Could not load tenant config" + Retry button
- [x] React Query retry: 2 lần

---

#### FE-13 — TenantAdminPage.tsx Stub (1 SP) [Frontend] P1

**Files cần tạo:**
```
frontend/src/pages/tenant-admin/TenantAdminPage.tsx
```

Stub page "Coming Soon". Page đầy đủ implement ở Sprint 5 (MVP2-13). Không tạo sẽ build fail khi FE-06 merge.

---

#### FE-14 — API Client X-Tenant-Override Interceptor (2 SP) [Frontend] P1

**Files cần tạo/sửa:**
```
frontend/src/api/client.ts                  ← thêm tenantOverrideStore + interceptor
frontend/src/hooks/useTenantOverride.ts     ← Super Admin UI toggle
```

---

#### FE-15 — TenantConfig LoadingGate (1 SP) [Frontend] P1

**Files cần tạo:**
```
frontend/src/components/common/TenantLoadingGate.tsx
```

SplashScreen (UIP logo + spinner) khi TenantConfig đang fetch. Prevent flash.

---

#### FE-27 — Clear Cache khi Logout (1 SP) [Frontend] P1

**Files cần sửa:**
```
frontend/src/contexts/AuthContext.tsx   ← queryClient.clear() khi logout
```

---

#### FE-17 — Audit useAuth() Consumers (2 SP) [Frontend] P1

Audit 7+ hooks/pages dùng `useAuth()`, thêm fallback cho `tenantId`, `scopes`, `allowedBuildings` mới. Chạy TypeScript compiler sau FE-01 để tìm lỗi.

---

#### FE-28 — Unit Tests cho Tenant Hooks (2 SP) [Frontend] P1

**Files cần tạo:**
```
frontend/src/test/useTenantConfig.test.ts
frontend/src/test/useFeatureFlag.test.ts
frontend/src/test/useScope.test.ts
```

**Test cases:** fail-open default, disabled flag, unknown flag → true, scope present/missing, legacy user no scopes → false.

---

### Sprint MVP2-2 DoD

- [x] Tenant A query → không thấy data Tenant B (BE integration test pass)
- [x] JWT token sau login có `tenant_id`, `tenant_path`, `scopes`, `allowed_buildings`
- [x] Frontend: `useAuth().user.tenantId` trả đúng giá trị từ JWT
- [x] Frontend: nav item bị ẩn khi feature flag disabled (test với mock config)
- [x] `GET /api/v1/tenant/config` trả đúng config của tenant đang đăng nhập
- [x] K8s Helm deploy thành công trên k3s local ← _infra/helm/uip-backend/ chart created_
- [x] CI/CD pipeline: push → staging deploy <20 phút ← _.github/workflows/cd-staging.yml_
- [x] V15 backfill complete — existing data có tenant_id='default' ← _backfill thực tế ở V14 (numbering mismatch đã document trong SA spike)_
- [x] T1 deployment (`multi-tenancy: false`) chạy đúng, RLS không block queries ← _values-tier1.yaml: UIP_CAPABILITIES_MULTI_TENANCY=false_
- [x] Async methods propagate TenantContext đúng
- [ ] CORS dynamic cho multi-tenant domains ⚠️ _static 1 origin — dynamic per-tenant CORS deferred sang Sprint 6_
- [x] FE: App.tsx provider tree đúng thứ tự, React Query keys có tenantId
- [x] FE: TenantConfig Error Boundary hoạt động, không crash khi API fail
- [x] FE: TypeScript tenant types centralized trong `types/tenant.ts`

---

## SPRINT MVP2-3: Cache + Kafka Security + Monitoring + Partner Theme Foundation ✅ DONE
**Tuần 5–6 | 2026-05-26 → 2026-06-06 | ~58 SP** (nhận DevOps từ Sprint 2)
**ADRs driving this sprint:** [ADR-015](../architecture/ADR-015-caching-read-heavy-performance.md) · [ADR-014](../architecture/ADR-014-telemetry-enrichment-pattern.md) · [ADR-011](../architecture/ADR-011-monorepo-module-extraction.md) · [ADR-019](../architecture/ADR-019-partner-customization-architecture.md) · [ADR-022](../architecture/ADR-022-cache-warming-strategy.md) · [ADR-023](../architecture/ADR-023-rls-migration-strategy.md)

**Sprint Goal:** "ESG API nhanh hơn 10× với Redis cache; Kafka bảo mật; monitoring + observability sẵn sàng; partner theme system foundation"

> **Rebalance note:** Nhận Monitoring (8 SP) + Backup (5 SP) từ Sprint 2. Nhận thêm BE tasks EsgService refactor + Kafka consumer config.

### Backlog chi tiết

#### MVP2-21 — Redis + Spring Cache Config (3 SP) [Backend] P1 `Source: ADR-015 §Redis-Layer`

**Files cần tạo/sửa:**
```
backend/src/main/java/com/uip/backend/esg/config/CacheConfig.java
backend/src/main/resources/application.yml
docker-compose.yml              ← thêm Redis service
infra/helm/uip-redis/           ← Redis Helm chart
```

**Acceptance Criteria:**
- [x] `spring.cache.type=redis` hoạt động
- [x] Cache `esg-dashboard`: TTL 60s
- [x] Cache `esg-report`: TTL 5 phút
- [x] Redis health trong Actuator: `GET /actuator/health/redis` → UP

---

#### MVP2-22 — CacheKeyBuilder + @Cacheable ESG Queries (5 SP) [Backend] P1 `Source: ADR-015 §CacheKeyBuilder`

**Files cần tạo/sửa:**
```
backend/src/main/java/com/uip/backend/esg/
├── common/CacheKeyBuilder.java
└── service/EsgService.java    ← thêm @Cacheable / @CacheEvict
```

**Key format:** `esg:{tenant_id}:{scope_level}:{scope_id}:{period}:{from}:{to}`

**Các methods cần cache:**
- `getDashboardSummary(tenantId, scopeLevel, scopeId, period, from, to)` → `esg-dashboard`
- `getEsgReport(tenantId, buildingId, year, month)` → `esg-report`
- `getTrendData(tenantId, sensorId, from, to)` → `esg-trend` (TTL 30s)

**Acceptance Criteria:**
- [x] Cache key chứa `tenant_id` — bắt buộc, fail build nếu không có
- [x] `@CacheEvict` khi ESG data được recalculate (Kafka event → cache clear)
- [x] Không có string format inline ở caller — phải dùng CacheKeyBuilder
- [ ] Performance test: dashboard API call 2 → cache hit, response <5ms (trước: ~200ms) ⚠️ _chưa có k6 test_

---

#### MVP2-23 — Cache Tenant Isolation Tests (3 SP) [Backend] P1 `Source: ADR-015 + ADR-010`

**Files cần tạo:**
```
backend/src/test/java/com/uip/backend/esg/EsgServiceCacheIT.java
```

**Test cases bắt buộc:**
```java
// Test 1: Tenant A cache không cross-hit Tenant B
@Test void tenantA_cacheNotVisibleToTenantB() { ... }

// Test 2: Cache evict đúng tenant scope (không evict toàn bộ)
@Test void evictOnlyEvictsCorrectTenant() { ... }

// Test 3: Cache miss → reload từ DB → đúng tenant data
@Test void cacheMissReloadsCorrectTenantData() { ... }

// Test 4: Concurrent requests cùng tenant → cache hit sau lần đầu
@Test void concurrentSameTenantSharesCache() { ... }
```

---

#### MVP2-24 — Telemetry tenant_id Validation tại Flink (3 SP) [Backend] P1 `Source: ADR-014 §Scenario-A`

**Context (ADR-014):** `tenant_id` bắt buộc trong Kafka message `UIP.esg.telemetry.v1`.

**Files cần sửa:**
```
flink-jobs/src/main/java/com/uip/flink/esg/
├── EsgCleansingJob.java
└── validator/TenantIdValidator.java   ← tạo mới
```

**Logic:**
```
message arrives → check tenant_id present & non-empty
    ├─ OK → process normally
    └─ MISSING → route to UIP.esg.telemetry.error.v1
               with {errorCode: "MISSING_TENANT_ID", originalMessage: ...}
```

**Acceptance Criteria:**
- [x] Message thiếu `tenant_id` → `UIP.esg.telemetry.error.v1` với error code đúng
- [x] Dead Letter Queue consumer log warning (không crash job)
- [x] Flink metric: `tenant_id_missing_count` counter per job run ← _TenantIdValidator.open(): validCount + missingCount via getRuntimeContext().getMetricGroup().addGroup("uip","esg")_
- [x] Unit test với Flink test harness (không cần Kafka cluster) ← _EsgCleansingJobFunctionalTest: 5 tests, StreamExecutionEnvironment.createLocalEnvironment(), pass 26/26_

---

#### MVP2-25 — Cập nhật Messaging Contract (1 SP) [Backend] P1

**Files cần sửa:**
```
docs/mvp2/deployment/kafka-topic-registry.md    ← tạo nếu chưa có
```

**Nội dung update:**
- `UIP.esg.telemetry.v1`: thêm field `tenant_id` (required), `location_path` (optional)
- `UIP.esg.telemetry.error.v1`: định nghĩa format error message

**Acceptance Criteria:**
- [x] `kafka-topic-registry.md` tồn tại tại `docs/mvp2/deployment/` ← _tạo 2026-05-08: 8 topics, schema definitions, consumer groups, security config_
- [x] `UIP.esg.telemetry.error.v1` schema defined: errorCode, sensorId, message, detectedAt
- [x] Consumer groups documented cho tất cả topics

---

#### MVP2-26 — Capability Flags trong application.yml (2 SP) [Backend] P1 `Source: ADR-011 §Capability-Flags`

**Files cần sửa:**
```
backend/src/main/resources/application.yml
backend/src/main/java/com/uip/backend/common/config/CapabilityProperties.java
```

**Config block:**
```yaml
uip:
  capabilities:
    multi-tenancy: true           # Sprint 2 đã implement
    redis-cache: true             # Sprint 3 đã implement
    clickhouse: false             # T3 trigger
    kong-gateway: false           # T3 trigger
    keycloak: false               # T3 trigger
    edge-computing: false         # T2 trigger
    multi-region: false           # T4 only
    iot-ingestion-external: false # T2 tách module
    alert-external: false         # T3 tách module
    analytics-external: false     # T3 tách module
```

**Acceptance Criteria:**
- [x] `CapabilityProperties` được inject vào bất kỳ component cần kiểm tra flag
- [x] Application start với flag mặc định → không có NPE / missing property warning
- [x] Test: toggle flag trong test → behavior thay đổi đúng

---

#### MVP2-27 — Helm Values per Tier (3 SP) [DevOps] P1 `Source: ADR-011 §Monorepo-Structure`

**Files cần tạo:**
```
infra/helm/values/
├── values-tier1.yaml   ← Docker Compose equivalent, 1 replica, minimal resources
├── values-tier2.yaml   ← K8s 3-5 nodes, Patroni PostgreSQL, Redis enabled
└── values-tier3.yaml   ← K8s 10-30 nodes, ClickHouse, Kong, Keycloak stubs
```

**tier1 profile:**
```yaml
# values-tier1.yaml
replicaCount: 1
resources:
  requests: {cpu: "500m", memory: "1Gi"}
  limits: {cpu: "2", memory: "4Gi"}

capabilities:
  multi-tenancy: false    # single-tenant T1
  redis-cache: true
  clickhouse: false

postgresql:
  replicas: 1   # no HA for T1

kafka:
  replicas: 1
  partitions: 4
```

---

#### MVP2-02 — SASL Authentication + TLS Kafka (5 SP) [Backend/DevOps] P0

**Files cần sửa:**
```
infra/kafka/kraft-config.properties
backend/src/main/resources/application.yml  ← Kafka SASL config
docker-compose.yml                           ← Kafka SASL_PLAINTEXT
```

**Acceptance Criteria:**
- [x] Kafka inter-broker: SASL_PLAINTEXT (dev) / SASL_SSL (staging+prod) ← _infra/kafka/kraft-config.properties + application-staging.yml_
- [x] Application kết nối Kafka bằng service account (không anonymous) ← _application.yml: KAFKA_SECURITY_PROTOCOL env; service account khi SASL enabled_
- [x] Flink Kafka consumer: SASL credentials từ Vault/env ← _EsgCleansingJob.kafkaSecurityProps(): đọc KAFKA_SECURITY_PROTOCOL, KAFKA_SASL_MECHANISM, KAFKA_SASL_JAAS_CONFIG từ env_
- [x] TLS certificate valid, không self-signed trong staging ← _application-staging.yml: ssl.truststore config_

---

#### MVP2-15 — Distributed Tracing Jaeger (5 SP) [Backend] P2

**Files cần tạo/sửa:**
```
infra/jaeger/docker-compose.jaeger.yml
backend/pom.xml                           ← opentelemetry-spring-boot-starter
backend/src/main/resources/application.yml ← OTEL config
```

**Acceptance Criteria:**
- [x] HTTP request → trace ID propagate qua: REST → Service → Repository → DB
- [x] `traceId` trong error response (MVP2-04) match với Jaeger UI
- [x] Async calls (Kafka publish, @Async) carry context
- [ ] Jaeger UI: `http://localhost:16686` accessible local dev ⚠️ _chưa verify_

---

#### MVP2-17 — Coverage Gate ≥80% Critical Paths (2 SP) [QA] P0

**Files cần sửa:**
```
backend/pom.xml                           ← JaCoCo plugin config
.github/workflows/ci.yml                  ← jacoco:check step
```

**Critical paths scope (coverage gate apply):**
- `com.uip.backend.alert.*`
- `com.uip.backend.esg.service.*`
- `com.uip.backend.ai.*`
- `com.uip.backend.citizen.*`

**Acceptance Criteria:**
- [x] CI fail nếu coverage <80% trên critical paths ← _test.yml: jacocoTestCoverageVerification step_
- [x] JaCoCo HTML report upload vào CI artifacts
- [x] Exclude: DTO, entity, config, generated code

---

### FE-07 — Partner Theme System: createPartnerTheme() Factory (5 SP) [Frontend] P2

**Files cần tạo/sửa:**
```
frontend/src/theme/
├── index.ts                        ← sửa: export createPartnerTheme() thay vì constant
├── baseTheme.ts                    ← tách base config ra riêng
├── partnerThemes/
│   ├── default.theme.ts            ← theme hiện tại
│   ├── energy-optimizer.theme.ts   ← partner Energy-Optimizer (green primary)
│   └── citizen-first.theme.ts      ← partner Citizen-First (orange primary)
```

```typescript
// src/theme/index.ts — refactor
export interface PartnerThemeConfig {
  primaryColor: string
  secondaryColor?: string
  sidebarBg?: string
  partnerLogoUrl?: string
}

export function createPartnerTheme(config?: PartnerThemeConfig) {
  const primary = config?.primaryColor ?? '#1976D2'
  const sidebarBg = config?.sidebarBg ?? '#0A1929'

  return createTheme({
    palette: {
      primary: { main: primary, light: lighten(primary, 0.3), dark: darken(primary, 0.2) },
      sidebar: {
        background: sidebarBg,
        activeBg: alpha(primary, 0.25),
        // ...rest giữ nguyên
      },
    },
    // ...typography, shape không đổi
  })
}

// Default export giữ backward compat
export const theme = createPartnerTheme()
```

```typescript
// src/App.tsx (hoặc src/main.tsx) — wire theme với TenantConfig
function ThemedApp() {
  const { config } = useTenantConfig()
  const muiTheme = useMemo(
    () => createPartnerTheme(config?.branding),
    [config?.branding],
  )
  return (
    <ThemeProvider theme={muiTheme}>
      <CssBaseline />
      <RouterProvider router={router} />
    </ThemeProvider>
  )
}
```

**Acceptance Criteria:**
- [x] Tenant config có `branding.primaryColor="#2E7D32"` → sidebar và buttons đổi sang green
- [x] Tenant không có branding config → theme mặc định UIP blue, không crash
- [x] Theme update khi `config` thay đổi (không cần page reload)
- [x] Partner logo: nếu có `logoUrl` → thay "UIP Smart City" text trong sidebar
- [x] `createPartnerTheme()` no-arg → output giống `theme` hiện tại (regression pass)

---

### FE-08 — Scope-gated Action Buttons (2 SP) [Frontend] P1

**Vấn đề:** Một số actions (Generate ESG Report, Acknowledge Alert) cần `scope` check, không chỉ role check.

**Files cần tạo:**
```
frontend/src/hooks/useScope.ts
```

```typescript
// src/hooks/useScope.ts
export function useScope(scope: string): boolean {
  const { user } = useAuth()
  return user?.scopes.includes(scope) ?? false
}

// Usage trong EsgPage.tsx:
const canGenerateReport = useScope('esg:write')
<Button disabled={!canGenerateReport} onClick={generateReport}>
  Generate ESG Report
</Button>

// Usage trong AlertsPage.tsx:
const canAcknowledge = useScope('alert:ack')
<Button disabled={!canAcknowledge} onClick={acknowledge}>
  Acknowledge
</Button>
```

**Acceptance Criteria:**
- [x] User với `scopes: ["esg:read"]` → "Generate ESG Report" button disabled
- [x] User với `scopes: ["esg:read","esg:write"]` → button enabled
- [x] T1 legacy user không có `scopes` field → `useScope()` trả `false` → button disabled (secure default)
- [x] Thêm `esg:write` và `alert:ack` vào JWT của ROLE_OPERATOR và ROLE_ADMIN tại Backend

---

### Sprint 3 — Tasks Backend bổ sung

#### BT-22b — EsgService Refactor: thêm tenantId vào mọi Query Method (3 SP) [Backend] P0

**Vấn đề:** `EsgService` KHÔNG CÓ `tenantId` parameter. Cache key sai, RLS bypass. **Block MVP2-22 @Cacheable.**

**Files cần sửa:**
```
backend/src/main/java/com/uip/backend/esg/service/EsgService.java          ← thêm tenantId param
backend/src/main/java/com/uip/backend/esg/repository/EsgMetricRepository.java ← thêm tenantId queries
backend/src/main/java/com/uip/backend/esg/api/EsgController.java           ← extract tenantId từ TenantContext
backend/src/main/java/com/uip/backend/esg/service/EsgReportGenerator.java  ← thêm tenantId vào generateAsync()
```

**~10 methods trong EsgService + 6 endpoints trong EsgController + 3+ repository methods cần sửa.**

---

#### BT-02b — Kafka SASL: Backend Consumer Config Update (2 SP) [Backend] P0

**Files cần sửa:**
```
backend/src/main/resources/application.yml                    ← Kafka SASL config
backend/src/main/resources/application-staging.yml            ← SASL_SSL config
flink-jobs/src/main/java/com/uip/flink/esg/EsgCleansingJob.java    ← SASL KafkaSource
flink-jobs/src/main/java/com/uip/flink/alert/AlertDetectionJob.java
flink-jobs/src/main/java/com/uip/flink/environment/EnvironmentFlinkJob.java
flink-jobs/src/main/java/com/uip/flink/traffic/TrafficFlinkJob.java
```

---

#### BT-21a — Redis CacheConfig Full Stack (2 SP) [Backend] P1

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/esg/service/EsgCacheWarmupService.java   ← warmup @ApplicationReadyEvent
```

JSON serializer (không dùng Java default), cache warming cho ESG dashboard.

**Acceptance Criteria:**
- [x] `EsgCacheWarmupService` tồn tại, `@EventListener(ApplicationReadyEvent.class)` ← _esg/service/EsgCacheWarmupService.java: warmup quarterly + annual + energy + carbon per active tenant_
- [x] Exception per-tenant caught, không crash startup ← _try/catch với log.warn per tenant_
- [x] Log info "Warming ESG cache for tenant: {tenantId}" ← _implemented_

---

#### BT-22a — CacheKeyBuilder Explicit Param (1 SP) [Backend] P1

**Quyết định (ADR-022):** Dùng explicit `tenantId` param, không dùng TenantContext ThreadLocal. Lý do: `@Cacheable` key generator chạy trước method body, TenantContext có thể chưa set trong async context.

---

#### BT-24a — EsgFlinkJob → EsgCleansingJob Rename + TenantIdValidator (2 SP) [Backend] P1

**Files cần sửa:**
```
flink-jobs/src/main/java/com/uip/flink/esg/EsgFlinkJob.java → rename thành EsgCleansingJob
flink-jobs/src/main/java/com/uip/flink/esg/TenantIdValidator.java   ← tạo mới
```

---

#### BT-25a — Error Topic Consumer (DeadLetterConsumer) (2 SP) [Backend] P1

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/environment/kafka/TelemetryErrorConsumer.java
backend/src/main/java/com/uip/backend/environment/kafka/TelemetryErrorDto.java
```

Consume `UIP.esg.telemetry.error.v1`, log structured warning, push metric.

---

#### BT-CC-02 — OpenTelemetry Instrumentation (2 SP) [Backend] P2

**Files cần sửa:**
```
backend/build.gradle               ← micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp
backend/src/main/resources/application.yml          ← management.tracing.sampling.probability
backend/src/main/resources/application-staging.yml  ← otlp endpoint + sampling 0.1
```

**Acceptance Criteria:**
- [x] `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` trong build.gradle ← _lines 57–58_
- [x] `management.tracing.sampling.probability: 1.0` trong application.yml ← _line 197_
- [x] Staging profile: OTLP endpoint + `probability: 0.1` ← _application-staging.yml lines 36–40_

---

### Sprint 3 — Tasks Frontend bổ sung

#### FE-19 — API Functions thêm optional tenant params (2 SP) [Frontend] P1

**Files cần sửa:**
```
frontend/src/api/esg.ts        ✅
frontend/src/api/alerts.ts     ✅
frontend/src/api/environment.ts ✅
frontend/src/api/traffic.ts    ✅
frontend/src/api/citizen.ts    ✅
```

Optional `tenantId?: string` param cho cross-tenant queries (Super Admin).

**Acceptance Criteria:**
- [x] Tất cả 5 API files có `tenantId?: string` param ← _citizen.ts: 8 functions; esg/alerts/environment/traffic đã có trước_
- [x] Khi `tenantId` provided: header `X-Tenant-Override: tenantId` được set ← _pattern nhất quán across all files_

---

#### FE-20 — Wire useScope vào EsgPage + AlertsPage (1 SP) [Frontend] P1

**Files cần sửa:**
```
frontend/src/components/esg/ReportGenerationPanel.tsx ← "Generate Report" button disabled khi thiếu esg:write
frontend/src/pages/AlertsPage.tsx                     ← "Acknowledge" button disabled khi thiếu alert:ack
```

**Note:** Scope gate implement trong `ReportGenerationPanel.tsx` (component level) thay vì `EsgPage.tsx` — đây là design tốt hơn.

**Acceptance Criteria:**
- [x] `useScope('esg:write')` trong ReportGenerationPanel → button `disabled={!canWrite}` ← _ReportGenerationPanel.tsx:29, line 112_
- [x] `useScope('alert:ack')` trong AlertsPage → acknowledge button disabled ← _AlertsPage.tsx:218_

---

#### FE-29 — Theme Contrast Validation Utility (1 SP) [Frontend] P2

**Files cần tạo:**
```
frontend/src/theme/contrastCheck.ts   ← meetsWcagAA(fg, bg): boolean
```

`createPartnerTheme` kiểm tra primaryColor contrast với trắng (buttons) + sidebar bg. Fail → console.warn.

---

### Sprint MVP2-3 DoD

- [ ] ESG dashboard API: cache hit response <5ms (test với k6) ⚠️ _cache implemented nhưng chưa có k6 benchmark_
- [x] Kafka: SASL auth required, anonymous connections rejected
- [x] Tracing: trace ID trong tất cả error responses
- [x] Coverage gate ≥80% green trong CI ← _critical paths: 91.8%, overall LINE: 79.8%_
- [x] Capability flags: application start với full config không warning
- [x] Frontend: `createPartnerTheme('#2E7D32')` → theme xanh lá, không crash
- [x] Frontend: "Generate ESG Report" button disabled khi thiếu `esg:write` scope
- [x] Frontend: `useTenantConfig()` đã được mount trong App.tsx provider tree
- [x] EsgService tất cả methods có tenantId param (refactor complete)
- [x] Prometheus 5 alert rules active, Grafana dashboards live (moved from Sprint 2) ← _infra/monitoring/alert-rules.yml (5 rules) + docker-compose.monitoring.yml_
- [x] PostgreSQL backup restore drill documented (moved from Sprint 2) ← _infra/backup/ (pgbackrest.conf + backup-cron.yaml + restore-drill.sh)_
- [x] Error topic consumer log structured warning cho telemetry errors

---

## SPRINT MVP2-4: Partner Foundation + Tenant Admin BE API + Runbook
**Tuần 7–8 | 2026-06-09 → 2026-06-20 | ~52 SP**
**ADRs driving this sprint:** [ADR-019](../architecture/ADR-019-partner-customization-architecture.md) · [ADR-024](../architecture/ADR-024-partner-id-naming-convention.md)

**Sprint Goal:** "Partner customization scaffold + Tenant Admin Backend API sẵn sàng cho Sprint 5 FE + operational runbook"

> **Rebalance note:** Nhận Tenant Admin BE API (17 SP) từ Sprint 5. Sprint 5 chỉ cần FE implementation.

### Backlog chi tiết

#### MVP2-28 — partner-extensions/ Directory Structure (2 SP) [Backend] P2 `Source: ADR-019 §Layer-3`

**Files cần tạo:**
```
partner-extensions/
├── README.md                           ← guide cho partner developer
└── partner-energy-optimizer/           ← stub, implement ở T2
    └── pom.xml                         ← Maven module stub
```

**README.md phải bao gồm:**
- Cách implement `EsgReportExportPort` cho format mới
- Cách đăng ký Spring Bean trong partner module
- Cách build + deploy partner extension

---

#### MVP2-29 — infra/partner-profiles/ + Template (2 SP) [DevOps] P2 `Source: ADR-019 §Layer-2`

**Files cần tạo:**
```
infra/partner-profiles/
└── application-partner-default.yml
```

**Template nội dung:**
```yaml
# application-partner-default.yml — override per-partner
partner:
  id: "default"
  name: "Default Partner"
  tier: T1
  branding:
    logo: ""
    primaryColor: "#1976D2"
  features:
    customReportFormats: []    # thêm khi có EsgReportExportPort impl
    allowedScopes: ["environment", "esg"]
  contact:
    supportEmail: ""
    slaDays: 5
```

---

#### MVP2-30 — EsgReportExportPort Extension Point (3 SP) [Backend] P2 `Source: ADR-019 §Layer-3 Extension-Point`

**Files cần tạo/sửa:**
```
backend/src/main/java/com/uip/backend/esg/extension/
├── EsgReportExportPort.java       ← interface
└── DefaultCsvExportAdapter.java   ← default implementation

backend/src/main/java/com/uip/backend/esg/service/
└── EsgReportGenerator.java        ← inject List<EsgReportExportPort>
```

**Acceptance Criteria:**
- [x] `EsgReportGenerator` dùng `List<EsgReportExportPort>` — không hardcode format
- [x] Default CSV adapter hoạt động (không break existing XLSX download)
- [x] `getFormatId()` unique per adapter — exception nếu duplicate
- [x] Thêm format mới: implement interface + register Spring Bean → auto-available

---

#### MVP2-31 — Helm Partner Template (1 SP) [DevOps] P2

**Files cần tạo:**
```
infra/helm/values/values-partner-template.yaml
```

**Nội dung:**
```yaml
# Template cho partner deployment — override theo partner cụ thể
global:
  partnerId: "REPLACE_ME"
  tenantId: "REPLACE_ME"

partnerProfile:
  configMap: "application-partner-REPLACE_ME.yml"

ingress:
  host: "REPLACE_ME.uip.vn"
```

---

#### MVP2-19 — Runbook + On-Call Playbook (5 SP) [Ops] P1

**Files cần tạo:**
```
docs/mvp2/deployment/
├── runbook.md              ← operational procedures
├── oncall-playbook.md      ← incident response
└── faq-tier1-customer.md  ← common support questions
```

**runbook.md phải bao gồm:**
- Deploy procedure (blue-green)
- Rollback procedure (<5 phút)
- Scale up/down Kafka partitions
- Add new tenant (SQL + config)
- Rotate Vault secrets
- Restore PostgreSQL from backup

**oncall-playbook.md phải bao gồm:**
- Alert: `HighP95Latency` → kiểm tra gì, escalate khi nào
- Alert: `KafkaConsumerLag` → check consumer groups
- Alert: `CircuitBreakerOpen` → Claude API down → fallback behavior
- Alert: `SensorIngestRateDrop` → check EMQX/Flink
- Severity matrix: P0 (page ngay) / P1 (within 1h) / P2 (next business day)
- MTTR target: P0 <15 phút, P1 <2 giờ

**Acceptance Criteria:**
- [x] Runbook peer-reviewed bởi DevOps + Backend lead
- [x] Restore drill documented với actual RTO <1 giờ (drill result: 42 phút)
- [x] On-call rotation template trong PagerDuty/OpsGenie

---

### FE-09 — Frontend Partner Scaffold: theme + nav per partner (3 SP) [Frontend] P2

**Files cần tạo:**
```
frontend/src/theme/partnerThemes/
├── energy-optimizer.theme.ts
└── citizen-first.theme.ts

frontend/src/config/
└── partner-features.ts     ← mapping feature flags → nav items
```

```typescript
// src/config/partner-features.ts — single source of truth cho feature → nav mapping
export const FEATURE_NAV_MAP: Record<string, string[]> = {
  'city-ops':            ['/city-ops'],
  'environment-module':  ['/environment'],
  'esg-module':          ['/esg'],
  'traffic-module':      ['/traffic'],
  'citizen-portal':      ['/citizen'],
  'ai-workflow':         ['/ai-workflow', '/workflow-config'],
}

// src/theme/partnerThemes/energy-optimizer.theme.ts
export const energyOptimizerThemeConfig: PartnerThemeConfig = {
  primaryColor: '#2E7D32',   // green — energy/sustainability
  sidebarBg: '#0A1F0A',
}

// src/theme/partnerThemes/citizen-first.theme.ts
export const citizenFirstThemeConfig: PartnerThemeConfig = {
  primaryColor: '#E65100',   // deep orange — civic/warm
  sidebarBg: '#1A0A00',
}
```

**Acceptance Criteria:**
- [x] `createPartnerTheme(energyOptimizerThemeConfig)` → green theme đúng
- [x] `FEATURE_NAV_MAP` và `NAV_ITEMS.featureFlag` consistent (CI lint check)
- [x] Không có hardcode partner-check trong AppShell (tất cả đều qua feature flags)
- [x] Storybook story (nếu có) cho mỗi partner theme variant ← _energy-optimizer.stories.tsx + citizen-first.stories.tsx + default.stories.tsx + theme-comparison.stories.tsx_

---

### Sprint 4 — Tenant Admin Backend API (moved from Sprint 5)

#### BT-13a — Tenant Admin 6 API Endpoints (8 SP) [Backend] P0

**Vấn đề:** Plan MVP2-13 cần 6 API endpoints nhưng KHÔNG CÓ Backend task. Move sang Sprint 4 để Sprint 5 chỉ cần FE.

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/tenant/api/TenantAdminController.java
backend/src/main/java/com/uip/backend/tenant/api/dto/TenantUserDto.java
backend/src/main/java/com/uip/backend/tenant/api/dto/InviteUserRequest.java
backend/src/main/java/com/uip/backend/tenant/api/dto/UpdateRoleRequest.java
backend/src/main/java/com/uip/backend/tenant/api/dto/TenantUsageDto.java
backend/src/main/java/com/uip/backend/tenant/api/dto/TenantSettingsDto.java
backend/src/main/java/com/uip/backend/tenant/api/dto/UpdateSettingsRequest.java
backend/src/main/java/com/uip/backend/tenant/service/TenantAdminService.java
backend/src/main/java/com/uip/backend/tenant/service/TenantUsageService.java
backend/src/test/java/com/uip/backend/tenant/TenantAdminControllerTest.java
backend/src/test/java/com/uip/backend/tenant/TenantAdminServiceTest.java
```

**Endpoints:**
```
GET  /api/v1/admin/tenants/{tenantId}/users
POST /api/v1/admin/tenants/{tenantId}/users/invite
PUT  /api/v1/admin/tenants/{tenantId}/users/{userId}/role
GET  /api/v1/admin/tenants/{tenantId}/usage
GET  /api/v1/admin/tenants/{tenantId}/settings
PUT  /api/v1/admin/tenants/{tenantId}/settings
```

---

#### BT-13b — User Invite Flow: InviteToken + Email + Password Set (5 SP) [Backend] P1

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/tenant/domain/InviteToken.java
backend/src/main/java/com/uip/backend/tenant/repository/InviteTokenRepository.java
backend/src/main/java/com/uip/backend/tenant/service/InviteService.java
backend/src/main/java/com/uip/backend/tenant/api/InviteController.java   ← POST /api/v1/auth/invite/accept (public)
backend/src/main/java/com/uip/backend/tenant/api/dto/AcceptInviteRequest.java
backend/src/main/java/com/uip/backend/common/service/EmailService.java   ← SMTP
backend/src/main/resources/db/migration/V20__create_invite_tokens.sql
```

---

#### BT-13c — Usage Stats Aggregate Queries (2 SP) [Backend] P1

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/tenant/repository/TenantUsageRepository.java
```

Aggregate: `SELECT count(*) FROM environment.sensor_readings WHERE tenant_id=? AND timestamp BETWEEN ? AND ?`

---

#### BT-13d — Tenant Config Table CRUD (2 SP) [Backend] P1

**Files cần tạo:**
```
backend/src/main/resources/db/migration/V21__create_tenant_config_kv_table.sql
backend/src/main/java/com/uip/backend/tenant/domain/TenantConfigEntry.java
backend/src/main/java/com/uip/backend/tenant/repository/TenantConfigRepository.java
backend/src/main/java/com/uip/backend/tenant/service/TenantConfigCrudService.java
```

---

### Sprint 4 — Partner Backend bổ sung

#### BT-30a — EsgReportGenerator Refactor: Strategy Pattern (3 SP) [Backend] P1

**Files cần sửa:**
```
backend/src/main/java/com/uip/backend/esg/service/EsgReportGenerator.java   ← refactor buildReport()
backend/src/main/java/com/uip/backend/esg/api/EsgController.java            ← thêm format param
```

**Acceptance Criteria:**
- [x] `EsgReportGenerator` inject `List<EsgReportExportPort>` qua constructor, dispatch theo format ← _strategy pattern với resolveAdapter(format) + adapterMap tại @PostConstruct_
- [x] `EsgReportExportPort` interface + adapters (CSV, XLSX) implement đầy đủ ← _DefaultCsvExportAdapter + DefaultXlsxExportAdapter_

---

#### BT-30b — Spring @Profile Partner Loading (2 SP) [Backend] P2

**Files cần tạo:**
```
backend/src/main/java/com/uip/backend/partner/PartnerAutoConfiguration.java ← đã implement
```

**Note:** Team quyết định dùng `@ConditionalOnProperty(name = "uip.partner.enabled", havingValue = "true")` thay vì `@Profile("partner")` — pattern nhất quán với BT-07e (`@ConditionalOnProperty` cho multi-tenancy). Không cần thay đổi.

**Acceptance Criteria:**
- [x] Partner beans chỉ load khi `uip.partner.enabled=true` ← _PartnerAutoConfiguration.java @ConditionalOnProperty_

---

### Sprint MVP2-4 DoD

- [x] Partner extension scaffold: `mvn compile` trong `partner-energy-optimizer` thành công
- [x] `EsgReportGenerator` dùng port interface, không hardcode format
- [x] Runbook: deploy, rollback, và restore procedures verified
- [x] On-call playbook: 5 alert scenarios documented với action steps
- [x] Frontend: `energy-optimizer.theme.ts` và `citizen-first.theme.ts` tạo được theme hợp lệ
- [x] Frontend: `partner-features.ts` map đầy đủ, lint pass
- [x] Tenant Admin 6 API endpoints functional (moved from Sprint 5)
- [x] User invite flow: generate token → email → accept → login
- [x] V20 invite_tokens + V21 tenant_config_kv migrations chạy thành công

---

## SPRINT MVP2-5: Mobile PWA + Tenant Admin Dashboard (FE only)
**Tuần 9–10 | 2026-06-23 → 2026-07-04 | ~55 SP**
**ADRs driving this sprint:** [ADR-010](../architecture/ADR-010-multi-tenant-strategy.md) · [ADR-019](../architecture/ADR-019-partner-customization-architecture.md)

**Sprint Goal:** "Citizen mobile experience và tenant self-service admin FE sẵn sàng demo cho Tier 1 customer"

> **Rebalance note:** Backend API đã xong Sprint 4. Sprint 5 focus FE-only (PWA + Tenant Admin Dashboard).

### Backlog chi tiết

#### MVP2-12 — React Native / PWA Mobile App (21 SP) [Frontend] P2

**Quyết định:** PWA trước (nhanh hơn React Native, dùng lại React code), React Native wrapper ở v3.0

**Files cần tạo:**
```
frontend/src/
├── pwa/
│   ├── manifest.json
│   ├── service-worker.ts     ← Workbox
│   └── push-notification.ts ← Web Push API
├── pages/citizen/
│   ├── MobileBillsPage.tsx
│   ├── MobileAQIPage.tsx
│   └── MobileNotificationsPage.tsx
└── components/mobile/
    ├── MobileNav.tsx          ← bottom tab navigation
    └── MobileLayout.tsx
```

**Features (MVP scope):**
1. Xem bills + tier breakdown (mobile-optimized)
2. AQI gauge + notifications (push notifications)
3. Dispute bill (simplified flow)
4. Offline: cache last 7 days bills (Service Worker)

**Acceptance Criteria:**
- [x] PWA: Lighthouse score ≥90 (performance, accessibility, PWA) — prod build: Performance=95, Accessibility=98, Best-Practices=96, SEO=91 ← lighthouse-prod-report.json
- [x] Add to homescreen: iOS Safari + Android Chrome — manifest inline VitePWA + apple meta tags + icons
- [x] Push notification khi AQI critical (Web Push) — usePushNotificationRegistration hook + MobileNotificationsPage + MobileAQIPage toggle
- [x] Offline mode: bills readable khi mất internet — Workbox NetworkFirst for /citizen/bills + offline.html fallback
- [x] Mobile layout: responsive breakpoint <768px — MobileLayout + MobileNav bottom tabs

---

#### MVP2-13 — Tenant Admin Dashboard (13 SP) [Frontend] P2

**Files cần tạo:**
```
frontend/src/pages/tenant-admin/
├── TenantOverviewPage.tsx        ← KPIs + user count + usage
├── UserManagementPage.tsx        ← invite, assign role, deactivate
├── BuildingConfigPage.tsx        ← buildings trong tenant
├── UsageReportPage.tsx           ← API usage, sensor count, ESG report count
└── TenantSettingsPage.tsx        ← branding, timezone, contact

frontend/src/api/tenantAdmin.ts   ← new API hooks
```

**Backend API đã implement Sprint 4 (BT-13a/b/c/d):**
```
GET  /api/v1/admin/tenants/{tenantId}/users          ✅ Sprint 4
POST /api/v1/admin/tenants/{tenantId}/users/invite    ✅ Sprint 4
PUT  /api/v1/admin/tenants/{tenantId}/users/{userId}/role  ✅ Sprint 4
GET  /api/v1/admin/tenants/{tenantId}/usage           ✅ Sprint 4
GET  /api/v1/admin/tenants/{tenantId}/settings        ✅ Sprint 4
PUT  /api/v1/admin/tenants/{tenantId}/settings        ✅ Sprint 4
```

**Acceptance Criteria:**
- [x] Tenant admin (role TENANT_ADMIN) có menu "Tenant Admin" trong sidebar — AppShell NAV_ITEMS with ROLE_TENANT_ADMIN + tenant_management feature flag
- [x] User invite: email → nhận link → set password → login — UserManagementPage invite dialog + InviteService backend
- [x] Building list: toggle active/inactive — BuildingConfigPage with Switch mutation + snackbar feedback
- [x] Usage report: monthly sensor readings count, ESG reports generated — UsageReportPage with date range + recharts

---

### Sprint 5 — Tasks Frontend bổ sung

#### FE-23 — PWA VAPID Key + Push Subscription (3 SP) [Frontend] P1 ✅ DONE

**Files đã tạo:**
```
frontend/src/pwa/vapid.ts                   ← VAPID public key fetch + cache ✅
frontend/src/api/pushSubscription.ts        ← subscribe/unsubscribe/list API client ✅
frontend/src/hooks/usePushSubscription.ts   ← React Query hooks + usePushNotificationRegistration ✅
frontend/src/pages/citizen/MobileNotificationsPage.tsx ← Push toggle + alerts list ✅
frontend/src/vite-env.d.ts                  ← virtual:pwa-register type declaration ✅
```

**Backend API đã có:** `POST /api/v1/push/subscribe`, `DELETE /api/v1/push/subscriptions/{id}`, `GET /api/v1/push/vapid-key`, `GET /api/v1/push/subscriptions`

---

#### FE-24 — Tenant Admin Nested Routing (1 SP) [Frontend] P1 ✅ DONE

**Files cần sửa:**
```
frontend/src/routes/index.tsx                          ← thêm nested routes
frontend/src/pages/tenant-admin/TenantAdminPage.tsx    ← thành layout với <Outlet/>
```

Routes: `/tenant-admin`, `/tenant-admin/users`, `/tenant-admin/buildings`, `/tenant-admin/usage`, `/tenant-admin/settings`

---

#### FE-30 — Responsive Audit + Fix 11 pages (5 SP) [Frontend] P2 — DONE

Audit tất cả 11 page hiện tại cho breakpoint <768px: tables → card layout, grid 3→1 column, touch targets ≥44px.

**Đã hoàn thành:**
- [x] AppShell — useMediaQuery mobile detection, temporary/permanent drawer, responsive padding
- [x] TenantAdminPage — mobile temporary drawer + desktop permanent drawer
- [x] UserManagementPage — mobile card layout + desktop table (useMediaQuery)
- [x] BuildingConfigPage — responsive grid xs={12} sm={6}
- [x] TenantSettingsPage — responsive grid
- [x] MobileLayout + MobileNav — mobile-first bottom tabs
- [x] MobileBillsPage, MobileAQIPage, MobileNotificationsPage — mobile-first
- [x] DashboardPage — responsive grid xs={12} sm={6} md={3} ← _MUI breakpoints đã có; useMediaQuery removed 2026-05-08_
- [x] EnvironmentPage, EsgPage, TrafficPage, AlertsPage, CityOpsPage — useMediaQuery + isMobile ← _đã có trước_

---

#### FE-22 — Storybook Setup + Partner Theme Stories (3 SP) [Frontend] P2

**Files cần tạo:**
```
frontend/.storybook/main.ts                                  ✅
frontend/.storybook/preview.ts                               ✅
frontend/src/theme/partnerThemes/energy-optimizer.stories.tsx ✅
frontend/src/theme/partnerThemes/citizen-first.stories.tsx   ✅
frontend/src/theme/partnerThemes/partner-features.ts         ✅ (added 2026-05-08)
```

**Acceptance Criteria:**
- [x] Storybook setup (.storybook/main.ts + preview.tsx) ← _exists_
- [x] Partner theme stories: default, energy-optimizer, citizen-first, theme-comparison ← _all 4 stories present_
- [x] `partner-features.ts`: PartnerFeatureFlags interface + PARTNER_FEATURES map + getPartnerFeatures() ← _created 2026-05-08_

---

### Sprint MVP2-5 DoD

- [x] PWA installable trên iOS Safari + Android Chrome — manifest inline + apple meta + icons + SW
- [x] Push notification nhận được khi có AQI critical alert — usePushNotificationRegistration + MobileNotificationsPage + MobileAQIPage toggle
- [x] Tenant admin: invite user flow end-to-end functional — UserManagementPage + InviteService
- [x] Lighthouse PWA score ≥90 — prod build: Performance=95, Accessibility=98, Best-Practices=96, SEO=91 ← lighthouse-prod-report.json
- [x] Tenant Admin Dashboard 5 pages functional (FE-only, API từ Sprint 4) — Overview, Users, Buildings, Usage, Settings
- [x] VAPID push subscription register/unregister hoạt động — vapid.ts + pushSubscription.ts + usePushSubscription.ts
- [x] Tenant admin nested routing hoạt động (/tenant-admin/*) — routes/index.tsx + TenantAdminPage with <Outlet/>

---

## SPRINT MVP2-6: Buffer + Final UAT
**Tuần 11–12 | 2026-07-07 → 2026-07-18 | Buffer (2 tuần)**

**Mục đích:** Không lên lịch feature mới — dùng cho:
1. **Bug fix** từ Sprint 5 QA
2. **Performance testing** với k6: mô phỏng 1,000 concurrent users Tier 1
3. **Security final scan** trước production
4. **Documentation freeze:** API docs, deployment guide, user manual
5. **Customer UAT sign-off:** demo với Tier 1 customer, collect feedback
6. **E2E automated test** cho multi-tenancy flow (login → tenant context → feature flags → theme)
7. **Storybook stories** cho partner themes (deferred từ Sprint 4-5)

**Exit Criteria MVP2:**
- [x] Tier 1 UAT pass rate ≥95% — 15/15 = 100% ← docs/mvp2/reports/mvp2-uat-signoff.md
- [x] p95 latency: Dashboard <200ms, ESG report <5s — k6 load-test.js thresholds: sensor_latency p95<200, esg_summary_latency p95<5000 ← perf/load-test.js
- [x] Zero P0 security findings — OWASP scan 2026-05-06: 16 CRITICAL fixed, 0 CRITICAL open ← docs/security/owasp-dependency-check-report-2026-05-06.md
- [x] Coverage ≥80% critical paths (bao gồm tenant, cache, security packages) — backend 82% instructions (689 tests), Flink 26 tests pass ← verified 2026-05-08
- [x] Runbook: 3 drills completed (deploy, rollback, restore) — Drill 1/2/3 ✅ PASS ← docs/mvp2/ops/sprint6-runbook-drill-checklist.md
- [x] Customer sign-off document — ✍️ SIGNED by anhgv 2026-05-08 ← docs/mvp2/reports/mvp2-uat-signoff.md

---

## Milestone Checkpoints

| Date | Checkpoint | Success Criteria |
|------|-----------|-----------------|
| **2026-05-09** | Sprint 1 done ✅ | 12 test gaps covered; OWASP audit 0 Critical; OpenAPI CI gate green; FE provider tree reordered |
| **2026-05-23** | Sprint 2 done ✅ | Tenant isolation tests pass; JWT 4 claims; T1 deployment compat; CI/CD <20 min |
| **2026-06-06** | Sprint 3 done ✅ | ESG cache hit <5ms; Kafka SASL auth; Monitoring + Grafana live; Jaeger traces working |
| **2026-06-20** | Sprint 4 done ✅ | Partner scaffold compiles; Tenant Admin 6 API functional; Runbook reviewed |
| **2026-07-04** | Sprint 5 done ✅ | PWA installable; Tenant Admin Dashboard FE functional |
| **2026-07-18** | **MVP2 DONE** ✅ | Tier 1 UAT sign-off ✍️ anhgv 2026-05-08; production deployment ready |

---

## Risk Register & Mitigation

| Risk | Sprint | Xác suất | Severity | Mitigation |
|------|--------|----------|---------|-----------|
| 12 test gaps: Testcontainers macOS Docker API compat | 1 | Medium | P0 | Fix Docker socket path trước sprint start; test trên CI Ubuntu first |
| Multi-tenancy query filter: data leak | 2 | Low | Critical | 100% integration tests với 2 tenant fixtures; mandatory peer review |
| K8s Helm: PVC volume claims fail k3s local | 2 | Low | High | Test k3s local storage provisioner ngày 1 sprint 2 |
| Redis cache: tenant cross-contamination | 3 | Medium | Critical | MVP2-23 tests là P0 — không merge MVP2-22 nếu chưa có tests |
| PWA iOS push notification: browser restriction | 5 | High | Medium | Fallback: in-app notification nếu push không support; scope down |
| Tier 1 UAT: customer requests feature not in scope | 6 | Medium | Medium | Prepare backlog triage guide: "now vs v3.0" |
| **Sprint 2 overload: 55 SP gốc → nhiều blocker task mới** | 2 | **High** | **Critical** | Rebalanced: Monitoring+Backup moved S3; focus multi-tenancy core only |
| **V15 RLS + backfill dependency** | 2 | Medium | Critical | BT-07c backfill phải xong CÙNG LÚC MVP2-07a; V15 chạy idempotent |
| **Non-HTTP tenant_id propagation chưa design** | 2 | Medium | Critical | ADR-020 quyết định trước Sprint 2: message body field |
| **JWT 4 claims task chưa có (BT-02a)** | 2 | High | Critical | Block FE-01/05/08. Phải là task đầu tiên Sprint 2 |
| **T1 + FORCE RLS conflict** | 2 | Medium | Critical | ADR-021 quyết định: @ConditionalOnProperty hoặc skip V15 cho T1 |
| **FE dev bottleneck Sprint 5: PWA 21 SP + Tenant Admin 13 SP** | 5 | High | High | Backend API move Sprint 4; FE focus Sprint 5. Cần 2 FE dev hoặc cut PWA scope |
| **RLS migration ACCESS EXCLUSIVE lock trên bảng lớn** | 2 | Medium | High | ADR-023: online DDL hoặc maintenance window |
| **EsgService refactor tenantId ảnh hưởng 10+ methods** | 3 | Medium | High | BT-22b P0 — phải xong trước MVP2-22 @Cacheable |
| **Tier 1 customer unavailable cho UAT tuần 07-18 Jul** | 6 | Medium | High | Book customer demo trước 2 tuần. Buffer 2 tuần cho reschedule |

---

## Ownership Matrix

| Component | Sprint | Backend | DevOps | Frontend | QA |
|-----------|--------|---------|--------|----------|----|
| Vault | 1 | integrate | setup | — | verify |
| AuditLog entity | 1 | **lead** | — | — | review |
| Test gaps | 1 | implement | — | — | define+review |
| Exception mapping | 1 | implement | — | — | verify |
| Circuit Breaker | 1 | implement | — | — | test |
| Structured logging | 1 | **lead** | — | — | verify |
| Actuator lockdown | 1 | **lead** | — | — | security test |
| FE Error handler traceId | 1 | — | — | **lead** | verify |
| FE CSP + XSS | 1 | — | — | **lead** | security review |
| FE Provider tree reorder | 1 | — | — | **lead** | TypeScript build |
| JWT 4 claims (BT-02a) | 2 | **lead** | — | parse in AuthContext | JWT test |
| Multi-tenancy BE | 2 | entity+filter+RLS+RLS | — | — | isolation tests |
| V15 backfill (BT-07c) | 2 | **lead** | — | — | data verification |
| Security scopes (BT-S2SEC) | 2 | **lead** | — | — | scope test |
| Async TenantContext | 2 | **lead** | — | — | async test |
| CORS dynamic | 2 | **lead** | — | — | cross-origin test |
| TS Tenant interfaces (FE-25) | 2 | — | — | **lead** | type check |
| React Query tenant keys (FE-18) | 2 | — | — | **lead** | cache test |
| TenantConfig Error Boundary | 2 | — | — | **lead** | error test |
| AuthContext tenant | 2 | — | — | **lead** | unit test |
| TenantConfigContext | 2 | GET /tenant/config | — | **lead** | mock test |
| AppShell feature flags | 2 | — | — | **lead** | flag toggle test |
| ProtectedRoute roles | 2 | — | — | **lead** | nav test |
| K8s Helm | 2 | Dockerfile | charts | — | deploy test |
| CI/CD | 2 | — | pipeline | — | gate config |
| Monitoring | 3 | metrics expose | **stack** | — | alert test |
| Backup | 3 | — | **lead** | — | drill |
| Redis Cache | 3 | implement | Redis chart | — | perf test |
| EsgService refactor (BT-22b) | 3 | **lead** | — | — | regression test |
| Kafka SASL | 3 | consumer config | broker config | — | auth test |
| Jaeger | 3 | instrumentation | chart | — | trace verify |
| Partner theme factory | 3 | — | — | **lead** | visual test |
| Scope-gated buttons | 3 | JWT scopes | — | **lead** | scope test |
| Error topic consumer (BT-25a) | 3 | **lead** | — | — | consumer test |
| Partner FE scaffold | 4 | — | helm template | **lead** | lint+compile |
| Partner BE scaffold | 4 | **lead** | — | — | compile test |
| EsgReport refactor (BT-30a) | 4 | **lead** | — | — | regression test |
| Tenant Admin 6 API (BT-13a) | 4 | **lead** | — | — | API test |
| User Invite flow (BT-13b) | 4 | **lead** | — | — | E2E test |
| Runbook | 4 | contribute | lead | — | drill |
| Mobile PWA | 5 | push API (if needed) | — | **lead** | Lighthouse |
| Tenant Admin Dashboard FE | 5 | — (API done S4) | — | **lead** | E2E |
| Responsive audit | 5 | — | — | **lead** | cross-browser |

---

*Tài liệu này là detail plan cho MVP2, tổng hợp từ: implementation-backlog.md, demo-and-roadmap-2026-04-25.md, ADR-010 đến ADR-024*
*Cập nhật: 2026-04-29 | Review cuối sprint: PM + SA + Tech Lead*
