# Sprint 2 Completion Report — UIP ESG POC MVP2

**Ngày:** 2026-05-03
**Sprint:** MVP2 Sprint 2 (Multi-Tenancy & RBAC)
**Tác giả:** PM / Dev Team

---

## Kết luận: GO to Sprint 3

> Sprint 2 đạt **PASS** — multi-tenancy end-to-end hoạt động, 0 bug HIGH/MEDIUM mở. PO demo hoàn thành với backend thực tế.

---

## Exit Criteria Score Card

| Gate | Kết quả | Chi tiết |
|------|---------|---------|
| **SA Spike** | ✅ PASS | Multi-tenancy spike document hoàn chỉnh, 10 implementation tasks, ADR-010/020/021 approved |
| **Backend** | ✅ PASS | 397 unit tests pass, 7 RLS integration tests pass, TenantContext/AOP/filter hoạt động |
| **Frontend** | ✅ PASS | 171 Vitest tests pass, TypeScript strict 0 errors, production build thành công |
| **E2E Tests** | ✅ PASS | 15/15 Playwright tests pass (sprint2-multi-tenancy.spec.ts) |
| **PO Demo** | ✅ PASS | 8 demo scenes chạy thành công với backend thật, 10 acceptance criteria verified |
| **Open Bugs** | ✅ ZERO | BUG-001 + BUG-002 đã fix, 0 bug HIGH/MEDIUM mở |

---

## Metrics

| Chỉ số | Giá trị |
|--------|---------|
| ADRs approved | 5 (ADR-010, ADR-011, ADR-020, ADR-021, ADR-023) |
| Flyway migrations | V16–V19 (4 migrations mới) |
| Backend entities updated | 13 JPA entities thêm tenantId |
| Backend new services | 8 (TenantContext, TenantContextAspect, TenantEntityListener, TenantContextFilter, TenantConfigService, TenantConfigController, TenantContextTaskDecorator, TenantAwareKafkaListener) |
| Backend unit tests mới | ~36 tests (TenantContext, TenantEntityListener, TaskDecorator, KafkaListener, TenantConfigService) |
| Backend integration tests | 7 tests (TenantIsolationIT: RLS cross-tenant, SET LOCAL reset, LTREE, write isolation) |
| Frontend new files | 5 (TenantConfigContext, tenantConfig API, TenantAdminPage, 4 tenant test files) |
| Frontend modified files | 6 (AuthContext, AppShell, ProtectedRoute, routes, client.ts, App.tsx) |
| Frontend unit tests mới | 41 tests (tenantStore, authTenantClaims, TenantConfigContext, navFiltering, ProtectedRoute extended) |
| E2E tests | 15/15 pass, 8.3s execution |
| TypeScript strict | 0 errors |
| Production build | 6.08s, thành công |

---

## Bug Resolution

| ID | Mô tả | Severity | Resolution |
|----|-------|----------|-----------|
| BUG-001 | `/tenant-admin` route chưa đăng ký → trang trắng | Medium | ✅ FIXED — Tạo TenantAdminPage + đăng ký route với ProtectedRoute requiredRoles |
| BUG-002 | `UserRole` enum thiếu ROLE_TENANT_ADMIN → Hibernate crash khi login tadmin | Medium | ✅ FIXED — Thêm ROLE_TENANT_ADMIN vào UserRole.java enum |
| — | @Builder.Default thiếu trên entity tenantId field | Low | ✅ FIXED — Thêm @Builder.Default trên tất cả entities dùng @Builder |
| — | WorkflowConfigPage test mock thiếu useFireWorkflowTrigger | Low | ✅ FIXED — Thêm mock + stub cho hook mới |
| — | AuthUser type thiếu fields mới trong test mocks | Low | ✅ FIXED — Cập nhật mock data trong 3 test files |

---

## Deliverables

### SA Spike Document
- `docs/mvp2/architecture/multi-tenancy-implementation-spike.md`
- 10 implementation tasks chi tiết, architecture diagrams (Mermaid), migration plan
- Security checklist, rollback plan, test strategy

### Backend
- **Tenant infrastructure:** TenantContext (ThreadLocal), TenantContextFilter, TenantContextAspect (AOP SET LOCAL), TenantEntityListener, TenantContextTaskDecorator, TenantAwareKafkaListener
- **API:** `GET /api/v1/tenant/config` — trả tenant config + feature flags
- **Security:** ROLE_TENANT_ADMIN trong UserRole enum, JWT claims mở rộng (tenant_id, tenant_path, scopes, allowed_buildings)
- **Database:** V16 (RLS policies 12 tables), V17 (app_users tenant fields + scopes), V18 (tenants table + seed data), V19 (tadmin user + HCMC feature flag)
- **RLS verified:** Cross-tenant isolation test pass, SET LOCAL resets sau COMMIT, write isolation (INSERT WITH CHECK)

### Frontend
- **AuthContext:** Parse JWT claims mới (tenantId, tenantPath, scopes, allowedBuildings) + sync tenantStore
- **TenantConfigContext:** Fetch config API, isFeatureEnabled() (fail-open), useFeatureFlag() hook, tenant switch invalidation
- **AppShell:** NavItem thêm featureFlag + scope, filter kết hợp role + flag
- **ProtectedRoute:** requiredRoles[] (array), requiredScope (string), backward compatible với requiredRole cũ
- **API client:** tenantStore, X-Tenant-Id header interceptor, clear trên logout
- **TenantAdminPage:** Stub page cho ROLE_TENANT_ADMIN

### Tests
- **Backend:** ~36 unit tests mới, 7 integration tests (Testcontainers)
- **Frontend:** 41 unit tests mới (4 test files trong src/test/tenant/)
- **E2E:** 15 Playwright tests, 100% route-mocked, không cần backend chạy

---

## Retrospective — Lessons Learned

### Thành công

**1. SA Spike trước khi implement — ĐÚNG ĐẦU TƯ**
Sprint 2 multi-tenancy được đánh giá là "rất phức tạp" ở đầu sprint. Việc dành thời gian cho SA spike document trước khi code đã giúp:
- Team hiểu rõ SET LOCAL vs SET SESSION (critical cho HikariCP pool leak)
- AOP approach được chọn sớm thay vì Hibernate interceptor (dễ test hơn)
- Migration numbering plan tránh conflict khi 2 agents chạy song song
- **Lesson:** Với các feature cross-cutting ảnh hưởng toàn hệ thống, spike document là bắt buộc — không code trước khi design xong.

**2. Agent team orchestration hoạt động hiệu quả**
Backend và frontend agents chạy song song, mỗi agent nhận handoff rõ ràng:
- SA spike → Backend implement → Backend verification → Frontend implement
- Context handoff protocol (DECIDED/DONE/NEXT/OPEN) giảm thiểu thông tin bị mất giữa agents
- **Lesson:** Multi-agent workflow phù hợp cho feature có clear boundary giữa BE/FE. Cần DECIDED section rõ ràng để agent tiếp theo không phải guess.

**3. Test-first cho security-sensitive code**
- TenantContext unit tests (7 cases) + TenantIsolationIT (7 cases) được viết cùng lúc với implementation
- RLS integration test bắt được issue SET LOCAL reset sau COMMIT — sớm trước khi push
- **Lesson:** Security/isolation logic PHẢI có integration test (Testcontainers), không chỉ unit test. Unit test không thể verify RLS behavior thật.

**4. Fail-open default cho feature flags**
`isFeatureEnabled()` trả `true` khi flag không tồn tại trong config. Quyết định này giúp:
- Không break existing features khi deploy mới
- Feature flags chỉ ảnh hưởng khi EXPLICITLY set `enabled: false`
- **Lesson:** Fail-open phù hợp cho MVP. Production cần fail-closed cho security-critical flags.

### Thất bại & Cần cải thiện

**1. Migration numbering conflict — 2 agents tạo cùng V16**
Hai backend agents chạy song song đều tạo `V16__*.sql`. Phải rename thủ công thành V17.
- **Nguyên nhân:** Không có centralized migration registry trước khi parallelize work.
- **Lesson:** Trước khi chạy song song agents trên cùng DB layer, phải reserve migration numbers trước. Một agent duy nhất quản lý migration numbering.

**2. @Builder.Default bị quên — 10+ entities bị warning**
Lombok `@Builder` bỏ qua field có giá trị default nếu không có `@Builder.Default`. Phải quay lại fix từng entity.
- **Nguyên nhân:** Sprint scope thay đổi 13 entities cùng lúc, reviewer không catch pattern này ở lần đầu.
- **Lesson:** Khi thêm field mới vào entity dùng `@Builder`, LUÔN check `@Builder.Default`. Tạo checklist: "new field + @Builder → cần @Builder.Default?"

**3. TenantAdminPage tạo sau — BUG-001 (route missing)**
AppShell có nav item "Tenant Admin" nhưng routes/index.tsx chưa đăng ký route → trang trắng.
- **Nguyên nhân:** Nav item và route được implement ở 2 tasks khác nhau (FE-04 vs FE-routing), thiếu integration test end-to-end ban đầu.
- **Lesson:** Mỗi nav item mới PHẢI có route tương ứng. Viết E2E test cho nav click → page render NGAY khi thêm nav item.

**4. ROLE_TENANT_ADMIN thiếu trong Java enum — BUG-002**
V19 migration seed user `tadmin` với role `ROLE_TENANT_ADMIN`, nhưng `UserRole.java` enum không có giá trị này → 500 error khi Hibernate deserialize.
- **Nguyên nhân:** Migration (SQL) và entity code (Java) được implement tách biệt. Không có cross-check giữa Flyway seed data và Java enum values.
- **Lesson:** Khi migration thêm data mới reference enum/constant, PHẢI verify enum đã có giá trị đó. Thêm smoke test: login seeded user → 200.

**5. Test files cũ không cập nhật khi AuthUser thay đổi**
5 test files mock AuthUser thiếu fields mới (tenantId, tenantPath, scopes, allowedBuildings) → TypeScript compile fail.
- **Nguyên nhân:** Interface thay đổi nhưng không grep toàn bộ test files dùng interface đó.
- **Lesson:** Khi thay đổi shared interface (AuthUser, DTO, etc.), chạy `tsc --noEmit` NGAY để bắt tất cả break points trước khi tiếp tục.

**6. WorkflowConfigPage test mock outdated — 16 tests fail**
`useFireWorkflowTrigger` hook được thêm vào page component nhưng test mock không update → tất cả 16 tests fail.
- **Nguyên nhân:** Hook mới được thêm vào component code nhưng developer quên cập nhật test mock.
- **Lesson:** Khi thêm hook mới vào component, LUÔN thêm mock + stub tương ứng trong test file. CI pipeline sẽ bắt lỗi này, nhưng nên fix sớm hơn.

### Quy trình cải tiến cho Sprint 3

| Vấn đề | Cải tiến | Áp dụng từ |
|--------|---------|-----------|
| Migration conflict | Reserve migration numbers trước khi parallelize | Sprint 3 |
| @Builder.Default quên | Checklist: new entity field → check @Builder.Default | Sprint 3 |
| Route missing cho nav item | E2E test: nav click → page render cho MỖI nav item mới | Sprint 3 |
| Enum/constant mismatch | Smoke test: login seeded user → verify 200 | Sprint 3 |
| Shared interface break | `tsc --noEmit` sau MỖI interface change | Sprint 3 |
| Mock outdated | Thêm hook mock cùng lúc với hook implementation | Sprint 3 |

---

## Sprint 3 Readiness

### Sprint 3 Focus Areas (MVP2-3: 26 May – 06 Jun)

Theo `mvp2-detail-plan.md`, Sprint 3 tập trung vào:

| Area | Tasks | ADRs | Est. SP |
|------|-------|------|---------|
| **Redis Caching** | CacheConfig, CacheKeyBuilder, EsgService @Cacheable | ADR-015 | ~20 SP |
| **Telemetry Enrichment** | EsgCleansingJob, TenantIdValidator, error topic | ADR-014 | ~15 SP |
| **Capability Flags** | CapabilityProperties, tier YAML configs | ADR-011 | ~10 SP |
| **Partner Theme** | Partner theme factory, partner-profiles | ADR-019 (partial) | ~13 SP |

### Dependencies từ Sprint 2 → Sprint 3

| Dependency | Status | Impact |
|-----------|--------|--------|
| TenantContext (ThreadLocal) | ✅ Done | Cache keys cần tenant_id prefix |
| RLS policies | ✅ Done | Cache invalidation phải respect tenant |
| JWT tenant claims | ✅ Done | Frontend pass X-Tenant-Id cho cache layer |
| tenant_id trên all entities | ✅ Done | EsgService refactor thêm tenantId param |

### Top 3 Risks Sprint 3

| ID | Risk | Severity | Mitigation |
|----|------|----------|-----------|
| R-301 | Redis cache key design sai → cross-tenant data leak | HIGH | CacheKeyBuilder PHẢI include tenant_id; test với 2 tenants |
| R-302 | Cache warming sau Flink batch write → stale data | MEDIUM | ADR-022 đã plan strategy; implement TTL + event-driven eviction |
| R-303 | ESG Q2 deadline chưa confirm city authority | MEDIUM | Clarify by Sprint 3 kickoff |

### Sprint 3 Kickoff

- **Date:** 2026-05-12 (Thứ Hai)
- **Pre-req:** PO sign-off Sprint 2 completion report
- **First task:** CacheKeyBuilder với tenant_id — P0 blocker cho tất cả cache tasks

---

## Tài liệu tham chiếu

| Tài liệu | Đường dẫn |
|----------|-----------|
| SA Spike Document | [multi-tenancy-implementation-spike.md](../architecture/multi-tenancy-implementation-spike.md) |
| PO Demo Script | [sprint2-po-demo-script.md](sprint2-po-demo-script.md) |
| PO Demo Evidence | [sprint2-demo-evidence-20260503.md](sprint2-demo-evidence-20260503.md) |
| Test Session Report | [sprint2-test-session-report.md](sprint2-test-session-report.md) |
| Sprint 1 Completion | [sprint1-completion-report.md](sprint1-completion-report.md) |
| Detail Plan MVP2 | [../project/mvp2-detail-plan.md](../project/mvp2-detail-plan.md) |

---

**Phiên bản tài liệu:** 1.0
**Người chuẩn bị:** Dev Team / PM
**Reviewer:** Product Owner
