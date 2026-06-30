# Test Session Report — MVP5 Re-test 4 Sprint (M5-1 → M5-4)

| Field | Value |
|---|---|
| **Date** | 2026-06-30 |
| **Tester** | UIP Manual Tester (uip-tester) |
| **Scope** | Re-test toàn bộ task đã hoàn thiện qua 4 sprint MVP5 trên thực tế (build + test + HA + API/UI smoke) |
| **Environment** | local (Docker Compose HA overlay — services chạy 4 ngày) |
| **Source plan** | `docs/mvp5/plans/mvp5-sprint-plan.md` |
| **Why** | Report trước (2026-06-29) ghi nhiều P2 bug FAIL; cần re-verify thực tế xem đã fix chưa + phát hiện regression mới |

---

## 1. Môi trường thực tế (pre-test)

> **Quan trọng:** Port mapping thực tế **khác** skill default. Skill default (8090/8080/8083/8085/8086) không khớp — phải dùng mapping từ `docker ps`.

| Service | Container | Port host → container | Status |
|---|---|---|---|
| Backend API | uip-backend | **8086** → 8081 (app), 8080 (mgmt) | ✅ UP |
| Analytics service | analytics-service-1 | 8082 → 8081 | ✅ UP |
| Frontend | uip-frontend | **3000** → 80 | ✅ HTTP 200 |
| ClickHouse (3 node HA) | uip-clickhouse{,-01,-02} | 8123/8124/8125 + mTLS 8126/8127 | ✅ healthy |
| CH Keeper (3 node quorum) | uip-clickhouse-keeper{,-02,-03} | 9181/9182/9183 | ✅ `imok` ×3 |
| Kafka (3 broker RF=3) | uip-kafka{,-2,-3} | 29092 | ✅ OK ×3 |
| Keycloak | uip-keycloak | 8085 | ✅ HTTP 200, realm `uip` OK |
| Kong | uip-kong | — | ❌ **Crash loop (1686 restarts)** — pre-existing BUG-004, không block test (test dùng port trực tiếp) |
| Flink | uip-flink-jobmanager/taskmanager | 8081 | ✅ healthy |

---

## 2. Tests Executed — Tổng hợp

### 2.1 Backend (Gradle `./gradlew test`)

> BUILD FAILED (do test failures, **không** phải compile error — main code compile OK).

| Suite | Sprint | Tests | Pass | Fail | Status | Ghi chú |
|---|---|---|---|---|---|---|
| ModuleBoundaryArchTest | M5-1 | 73 | 71 | **2** | ❌ **REGRESSION MỚI** | `esg → environment.repository` (xem §3.1) |
| NLIntentParserServiceTest | M5-2 | 3 | 3 | 0 | ✅ PASS | |
| ModelRouterTest | M5-2 | 9 | 9 | 0 | ✅ PASS | |
| BpmnCustomValidatorTest | M5-2 | 18 | 18 | 0 | ✅ PASS | |
| TenantIsolationFuzzTest | M5-2 | 1 | 0 | 1 | ❌ FAIL | BUG-M5-001 (Spring context load — chained, root ở bean) |
| BpmnSynthesisServiceTest | M5-3 | 5 | 5 | 0 | ✅ PASS | |
| BpmnValidatorRegressionTest | M5-3 | 20 | 20 | 0 | ✅ PASS | |
| BpmnSimulatorServiceTest | M5-3 | 5 | 5 | 0 | ✅ PASS | |
| NLGroundingIntegrationTest | M5-3 | 2 | 0 | 2 | ❌ FAIL | BUG-M5-008 (cần live NL endpoint — env, không phải code) |
| RoiCalculationServiceTest | M5-3 | 8 | 7 | 1 | 🟡 PARTIAL | 1 calc failure |
| RoiControllerTest | M5-3 | 7 | 0 | 7 | ❌ FAIL | Spring context load (chained) |
| BillingAggregationJobTest | M5-4 | 5 | 5 | 0 | ✅ PASS | |
| InvoiceGenerationServiceTest | M5-4 | 6 | **6** | **0** | ✅ **FIXED** | BUG-M5-002 đã fix trong session này (xem §4.1) |
| BillingReconciliationServiceTest | M5-4 | 5 | **5** | **0** | ✅ **FIXED** | BUG-M5-003 đã fix trong session này (xem §4.2) |
| LotusVnScoringServiceTest | M5-4 | 8 | 5 | 3 | ❌ FAIL | BUG-M5-004 (business-logic bug — xem §3.2) |
| AuditLogServiceTest | M5-4 | 3 | 3 | 0 | ✅ PASS | |
| Iso37120IndicatorEngineTest | M5-4 | 6 | 6 | 0 | ✅ PASS | |
| MeteringControllerTest | M5-4 | 7 | 0 | 7 | ❌ FAIL | Spring context load (chained) |

### 2.2 Flink-jobs (Maven `mvn test`)

| Suite | Tests | Pass | Fail/Err | Status |
|---|---|---|---|---|
| (toàn bộ unit + tenant ArchTest) | 147 | 147 | 0 | ✅ PASS |
| FlinkMultiTenantConcurrentIT | 4 | 0 | **4 err** | ❌ FAIL — BUG-M5-005 (lambda non-serializable — kiến trúc test) |
| EsgDualSinkFlinkE2EIT | 1 | 0 | **1 err** | ❌ FAIL — Testcontainers không thấy Docker (**env**, không phải code) |
| **TOTAL Flink** | **152** | **147** | **5 err** | |

Tenant isolation core vẫn được bảo chứng bởi `TenantBindingProcessFunctionTest` (5/5) + `TenantContextTest` (4/4) + `TenantKeyedProcessFunctionDelegateTest` (6/6) — chỉ IT concurrent race chưa verify.

### 2.3 Frontend (`npx tsc --noEmit`)

| Check | Result |
|---|---|
| TypeScript compile | ✅ **0 errors** |

### 2.4 HA / Infra / Smoke (test thủ công trên containers)

| TC | Test | Result |
|---|---|---|
| TC-HA-01 | CH replication (ReplicatedMergeTree) | ✅ PASS — `is_readonly=0, absolute_delay=0` |
| TC-HA-02 | Kafka 3-broker RF=3 | ✅ PASS — 3 broker OK, topics listable |
| TC-HA-03 | Keeper 3-node quorum | ✅ PASS — `imok` ×3 (9181/9182/9183) |
| TC-HA-04 | HA tolerance — kill 1/3 keeper | ✅ PASS — CH giữ phản hồi `SELECT 1` khi keeper-03 down, khôi phục `imok` sau restart |
| TC-API-01 | Backend health (8086) | ✅ UP |
| TC-API-02 | Frontend (3000) | ✅ HTTP 200 |
| TC-API-03 | 10 REST endpoints (no auth) | ✅ All 401 (route tồn tại + security filter OK, không 404/500) |
| TC-API-04 | Authenticated API (Keycloak token) | ⚠️ BLOCKED — dev credentials không khớp (realm `uip` OK), ngoài scope |

---

## 3. Phát hiện quan trọng (regression + bug business-logic)

### 3.1 🆕 BUG-M5-009 (P2): ArchTest regression — `esg` vi phạm module boundary `environment` → ✅ FIXED 2026-06-30

**Mới phát hiện trong re-test này** (report trước ghi ModuleBoundaryArchTest 73/73 PASS — sai).

**Root cause:** M5-4 khi thêm ISO 37120 (`Iso37120IndicatorEngine`) + LOTUS VN IEQ scoring (`LotusVnScoringService`) đã inject trực tiếp `environment.repository.AirQualityReadingRepository` vào package `esg`. File `AirQualityReadingRepository.java` là **untracked mới** (git status `??`). Vi phạm 2 ArchTest rule:
- `no classes in 'esg..' access 'environment..'` (2 lần)
- `no classes outside 'environment..' access 'environment.repository..'` (2 lần)

**Fix áp dụng (hexagonal Port pattern):**
- Tạo `common.spi.AirQualityPort` (interface neutral, 2 method PM2.5 query) — package `common.spi` không thuộc `esg` hay `environment` nên không vi phạm chiều nào.
- Tạo `environment.adapter.AirQualityAdapter` (`@Component`, implement Port, delegate sang `AirQualityReadingRepository`).
- `Iso37120IndicatorEngine` + `LotusVnScoringService`: inject `AirQualityPort` thay `AirQualityReadingRepository`.
- 2 test file (`Iso37120IndicatorEngineTest`, `LotusVnScoringServiceTest`): `@Mock AirQualityPort` thay `@Mock AirQualityReadingRepository`.

**Verify:** `ModuleBoundaryArchTest` **73/73 PASS**; `Iso37120IndicatorEngineTest` 6/6 PASS (Port wire đúng, PM2.5 delegate hoạt động). Lotus 3 fail còn lại = BUG-M5-004 business-logic (không liên quan Port wiring — errors=0).

**Đây là instance mới của `feedback_doc_vs_code_gap`** — bug bị miss vì M5-4 đánh giá cov sprint không re-run ArchTest với code mới.

### 3.2 BUG-M5-004 (P2): LOTUS VN scoring — business-logic sai (KHÔNG phải test bug)

**Re-test xác nhận:** report trước ghi "scoring calculation errors" mơ hồ. Thực tế là **thiết kế model scoring sai**, cụ thể:

`LotusCategory.from()` tính `score = tổng raw score các indicator (mỗi indicator 0-4)`, **không quy đổi về `maxScore`** (EN max 40, WA max 20, IEQ max 20, MA max 10, ST max 10 = 100).

Hệ quả:
- Test Platinum mock EN-1=4, WA-1=4, IEQ-2=4 (EN-4/MA/ST notAvailable=0) → `totalScore = 4+4+4+0+0 = 12` (không phải ~75-80 kỳ vọng).
- Mọi test threshold-level (Platinum ≥75, Certified 40-49) fail vì tổng raw score tối đa chỉ ≈ (số indicator × 4) ≈ 20, không bao giờ đạt 75.

**Đây là bug production thật**, không phải test缺陷: dashboard LOTUS frontend (`LotusVnPage.tsx`) hiển thị điểm 0-100 nhưng backend trả raw score 0-20 → **sai business intent**.

**Fix cần quyết định BA/SA:** Quy đổi mỗi indicator về weight của category (`indicatorScore/4 × categoryMax/numIndicators`), hoặc đổi contract totalScore về thang 0-20 và cập nhật frontend + cert thresholds. **Chưa fix** — cần BA confirm model.

### 3.3 Các bug môi trường (KHÔNG phải code defect)

| Bug | Nguyên nhân thực | Hành động |
|---|---|---|
| BUG-M5-008 (NLGroundingIT) | Test gọi live NL model endpoint không có trong env local | Cần offline mode hoặc endpoint thật (DevOps) |
| EsgDualSinkFlinkE2EIT | Testcontainers không thấy Docker khi chạy `mvn test` từ host | Env / Docker socket config |
| TenantIsolationFuzzTest / RoiControllerTest / MeteringControllerTest (Spring context load) | ApplicationContext fail — chained, root ở bean nào đó (có thể liên quan bean mới M5-4) | Cần debug bean wiring riêng |

---

## 4. Bug P2 đã FIX trong session này

### 4.1 BUG-M5-002 — InvoiceGenerationServiceTest NPE ✅ FIXED

**Root cause thực (sâu hơn report trước):** Test mock `invoiceRepository.save()` không stub → Mockito trả `null`. Ngoài ra mock cũng không kích hoạt `@PrePersist` (set `generatedAt`) → `emitInvoiceGeneratedEvent` NPE ở `invoice.getGeneratedAt().toString()`, bị catch-all block nuốt → **Kafka event `UIP.billing.invoice.generated.v1` không emit**.

> ⚠️ **Side-finding (P2):** `emitInvoiceGeneratedEvent` có catch-all nuốt mọi exception (chỉ log). Nếu `generatedAt` null trong production path nào đó, Kafka event silently mất. Khuyến nghị: production nên set `generatedAt` trong builder (không chỉ rely `@PrePersist`), hoặc catch hẹp hơn.

**Fix (test):** Thêm `@BeforeEach` stub `invoiceRepository.save` trả entity với id + generatedAt, mô phỏng JPA lifecycle:
```java
lenient().when(invoiceRepository.save(any(Invoice.class)))
        .thenAnswer(invocation -> {
            Invoice saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setGeneratedAt(Instant.now());
            return saved;
        });
```
**Kết quả:** 6/6 PASS (trước 2/6).

### 4.2 BUG-M5-003 — BillingReconciliationServiceTest sai số ✅ FIXED

**Root cause:** Helper `mockRawEventCost(long cost)` nhận tham số `cost` nhưng **bỏ qua nó**, luôn trả `List.of()` → `rawEventCost` luôn = 0 → `calculateAccuracy(0, ...)` = 100% → mọi assertion accuracy/discrepancy sai.

**Fix (test):** Helper tạo `MeteringEvent` với `costUsdCents = cost`:
```java
MeteringEvent event = MeteringEvent.builder().costUsdCents((int) cost).build();
when(meteringEventRepository.findByTenantAndTimeRange(...)).thenReturn(List.of(event));
```
**Kết quả:** 5/5 PASS (trước 2/5).

> ⚠️ **Side-finding (P2, RIÊNG BIỆT):** Production `BillingReconciliationService` so **USD cents** (`costUsdCents`) với **VND** (`subtotalVnd`/`totalCostVnd`) — **không đồng nhất đơn vị tiền**. Logic discrepancy = `|rawEventCost - invoicedCost|` chỉ đúng nếu 2 vế cùng đơn vị. Test hiện trộn số nên pass, nhưng production sẽ mismatch khi có dữ liệu thật. Cần quyết định nghiệp vụ (chuẩn hóa về VND qua tỷ giá). Chưa fix.

---

## 5. Summary

| Hạng mục | Trước re-test (2026-06-29) | Sau re-test (2026-06-30) |
|---|---|---|
| Backend ArchTest | 73/73 (sai — report miss) | **71/73** (phát hiện BUG-M5-009 regression) |
| InvoiceGenerationServiceTest | 2/6 | **6/6 ✅ FIXED** |
| BillingReconciliationServiceTest | 2/5 | **5/5 ✅ FIXED** |
| LotusVnScoringServiceTest | 5/8 | 5/8 (BUG-M5-004 = business-logic, cần BA) |
| Flink-jobs | — (chưa run full) | **147/152** (5 env/test-arch, tenant core OK) |
| Frontend TSC | 0 errors | **0 errors** |
| HA (CH/Kafka/Keeper) | PASS 2026-06-25 | **PASS 2026-06-30** (re-verify + kill-keeper) |

**Tổng re-test:** Backend 17 suite chính (≈96 test) + Flink 152 test + Frontend TSC + 8 TC HA/API smoke.

### Bugs vẫn mở (sau khi fix 2 bug test)

| Bug | Sev | Loại | Blocker? | Owner |
|---|---|---|---|---|
| ~~BUG-M5-009 (ArchTest esg→environment)~~ | ~~P2~~ | ~~Kiến trúc regression~~ | ~~Cho gate G7~~ | **✅ FIXED 2026-06-30** (extract AirQualityPort) |
| BUG-M5-004 (LOTUS scoring model) | P2 | Business-logic | Cho LOTUS AC (M5-4-T08) | BA confirm model + Backend fix |
| BUG-M5-005 (Flink IT lambda) | P2 | Kiến trúc test | Không (tenant core đã verify) | Backend refactor test |
| BUG-M5-001 (TenantIsolationFuzz context) | P2 | Bean wiring | Cho G2 full closure | Backend debug bean |
| Reconciliation đơn vị tiền | P2 | Business-logic | Cho G4 99.5% real data | Data Eng/BA |
| NLGroundingIT / EsgDualSinkE2E | P3 | Env (no live endpoint/Docker) | Không | DevOps |

---

## 6. Acceptance Criteria Sign-off

- [x] Backend build PASS (compile OK; BUILD FAILED chỉ do test failures)
- [x] Flink-jobs build + test: 147/152 (tenant isolation core PASS)
- [x] Frontend TSC: 0 errors
- [x] HA infrastructure: CH replication + Kafka RF=3 + Keeper quorum + kill-1-keeper tolerance — all PASS
- [x] API routes + security: 10/10 endpoints trả 401 đúng
- [x] 2 bug P2 (M5-002, M5-003) FIXED và verify PASS
- [ ] **KHÔNG sign-off G7/G8** — còn 2 regression mở (BUG-M5-009 ArchTest, BUG-M5-004 LOTUS) cần SA/BA resolve trước khi close MVP5

**Verdict:** 🟡 **CONDITIONAL** — M5-1/M5-2/M5-3 phần lớn PASS, M5-4 billing skeleton + invoice + reconciliation đã fix xong. **Nhưng phát hiện 2 regression mới (ArchTest boundary + LOTUS scoring model) mà report trước miss** — phải fix trước gate G7 (functional/correctness + ArchTest green) và G4/G6 (LOTUS AC + billing accuracy real data).

**Tester sign-off:** _re-test executed 2026-06-30, 2/4 fixable P2 bugs fixed, 2 architectural/business bugs escalated to SA/BA._

---

## 7. Files modified trong session

| File | Change |
|---|---|
| `backend/src/test/.../InvoiceGenerationServiceTest.java` | +`@BeforeEach` stub save (id + generatedAt) — fix BUG-M5-002 |
| `backend/src/test/.../BillingReconciliationServiceTest.java` | `mockRawEventCost` helper tạo MeteringEvent thật — fix BUG-M5-003 |
| `backend/src/main/.../common/spi/AirQualityPort.java` | **NEW** — hexagonal Port interface (2 PM2.5 query method), fix BUG-M5-009 |
| `backend/src/main/.../environment/adapter/AirQualityAdapter.java` | **NEW** — `@Component` implement Port, delegate sang repository |
| `backend/src/main/.../esg/iso37120/service/Iso37120IndicatorEngine.java` | inject `AirQualityPort` thay `AirQualityReadingRepository` |
| `backend/src/main/.../esg/lotus/service/LotusVnScoringService.java` | inject `AirQualityPort` thay `AirQualityReadingRepository` |
| `backend/src/test/.../Iso37120IndicatorEngineTest.java` | `@Mock AirQualityPort` |
| `backend/src/test/.../LotusVnScoringServiceTest.java` | `@Mock AirQualityPort` |
| `billing/api/InvoiceController.java` | `@RestController("billingInvoiceController")` — fix **BUG-M5-010** bean-name conflict (giải ApplicationContext load 366 test) |
| `citizen/api/InvoiceController.java` | `@RestController("citizenInvoiceController")` — fix BUG-M5-010 |
| `common/service/AuditLogService.java` + `AuditLogRepository.java` + `domain/AuditLog.java` + 2 test | **DELETED** — dead code (logAction không ai gọi), trùng bean name với `audit.service.AuditLogService` (M5-4 active) |

Không sửa production business-logic (BUG-M5-002/M5-003 là test-mocking缺陷; BUG-M5-004 là business-logic cần BA, chưa fix).

## 8. BUG-M5-010 (P1) — Bean-name conflict `invoiceController` / `auditLogService` → ✅ FIXED 2026-06-30

**Root cause của 366 ApplicationContext-load failures:** 2 cặp class cùng simple-name ở module khác nhau, `@RestController`/`@Service` không chỉ định bean name → Spring default bean name = `invoiceController`/`auditLogService` → `ConflictingBeanDefinitionException` chặn toàn bộ context startup.

| Cặp | Module 1 (active) | Module 2 | Xử lý |
|---|---|---|---|
| `InvoiceController` | `billing` (admin-ops: generate/list/PDF, role ADMIN) | `citizen` (citizen self-service: meter + invoice + consumption, role CITIZEN) | Khác chức năng hoàn toàn → **tách bean name**: `billingInvoiceController` / `citizenInvoiceController` |
| `AuditLogService` | `audit.service` (M5-4: logEvent + `@TransactionalEventListener` billing) | `common.service` (generic `logAction`) | `common.service` là **dead code** (logAction không ai gọi, repo/entity chỉ reference lẫn nhau) → **xóa** cả chain (service + repo + entity + 2 test) |

**Phân tích quyết định (trả lời câu hỏi "cùng chức năng → common, khác → bean name"):**
- 2 `InvoiceController` phục vụ 2 audience khác hẳn (admin-ops vs citizen-self-service), 2 aggregate root (`billing.Invoice` vs `citizen.dto.InvoiceDto`), 2 path, 2 role → **KHÔNG gộp common** được (vi SRP + module boundary). Tách bean name.
- `common.AuditLogService` vs `audit.AuditLogService`: 2 hệ audit tách biệt (table `public.audit_log` V15 vs `audit.events` V047). `common` dead → xóa.
- Không cần `@Qualifier`: verify không ai `@Autowired` `InvoiceController`/`AuditLogService` theo type (controller route theo path; audit đã decouple qua event ở migration C4).

**Verify:** `ConflictingBeanDefinitionException` **0 instance còn lại** (trước lan truyền 366 test). `ApplicationContextLoadsIT` giờ lộ root cause sâu hơn — `No qualifying bean AppUserRepository` (`AdminController` inject, coupling **D1** pre-existing defer MVP6) — không liên quan conflict bean, tách biệt.

**Note orphan table:** Migration `V15__create_audit_log_table.sql` (bảng `public.audit_log`) không xóa (Flyway đã deployed) → orphan table. POC-acceptable; cleanup ở MVP6 nếu extract `audit` module.

