# SA Architecture Review: UIP ESG Platform — MVP3

**Ngày review:** 2026-06-01  
**Reviewer:** Solution Architect (độc lập)  
**Phiên bản hệ thống:** MVP3 Sprint 6 (end-of-sprint)  
**Tài liệu tham chiếu:** `system-architecture.md`, ADR-010 đến ADR-035, `sprint1-risk-review.md`, `flink-dual-sink-risk-assessment.md`, `sprint6-sa-final-review.md`  
**Kiểm tra code:** `ModuleBoundaryArchTest.java`, `infra/clickhouse/schema/V001__create_analytics_schema.sql`, `scripts/flink-checkpoint-recovery-test.sh`, `EsgDualSinkFlinkE2EIT.java`

> **Lưu ý về quan điểm:** Review này được viết từ góc nhìn của một Solution Architect độc lập — không tham gia implementation, không có lợi ích gắn với stack hiện tại. Mục tiêu là đưa ra đánh giá trung thực để team cải thiện trước production.

---

## Tóm tắt điều hành

UIP ESG Platform là một hệ thống SmartCity phức tạp với IoT ingestion pipeline, stream processing, multi-tenant OLTP/OLAP, AI workflow, và real-time dashboard. Sau 3 MVP, kiến trúc đã có nền tảng kỹ thuật tốt ở nhiều lớp. Tuy nhiên, một số quyết định tích lũy technical debt đáng kể, và một số rủi ro vẫn chưa được giải quyết trước production.

**Kết luận tổng thể: DUYỆT CÓ ĐIỀU KIỆN** — hệ thống có thể đưa vào pilot với điều kiện 2 vấn đề bảo mật được xử lý trong Sprint 7, và roadmap rõ ràng cho 2 vấn đề chiến lược.

---

## 1. Điểm tốt

### 1.1 Event-driven pipeline thiết kế đúng
Luồng `EMQX → Redpanda Connect → Kafka → Flink → TimescaleDB/ClickHouse` phân tách rõ ràng ingestion, normalization, và processing. Việc dùng NGSI-LD làm canonical format là quyết định đúng — chuẩn hóa từ sớm giảm coupling giữa sensor vendor và processing logic.

### 1.2 Multi-tenancy có chiều sâu
Việc kết hợp **Kong JWT validation → TenantContextAspect → PostgreSQL RLS** tạo ra 3 lớp bảo vệ tenant isolation. Đây là approach đúng cho SaaS platform — không chỉ dựa vào application-layer filter mà có database enforcement. ADR-010 tư duy tốt về tier model.

### 1.3 Phân tách OLTP/OLAP hợp lý
TimescaleDB cho real-time + operational queries, ClickHouse cho OLAP analytics là phân tách đúng về mặt kiến trúc. Dual-write từ Flink thay vì replication đơn giản là lựa chọn phù hợp khi cần transform data trước khi vào OLAP store.

### 1.4 AI trong alert path được thiết kế an toàn
Claude API được gọi async, có timeout 10 giây, có fallback về rule-based decision. Alert notification path không bị block bởi AI call. Đây là pattern đúng — AI là enhancement, không phải dependency trong critical path.

### 1.5 ADR culture tốt
25+ ADRs từ MVP2-MVP3 cho thấy team có thói quen ghi lại quyết định kiến trúc. Đây là asset quan trọng cho maintainability dài hạn.

### 1.6 Module boundary được enforce kỹ thuật
ArchUnit tests (`ModuleBoundaryArchTest.java`) enforce ranh giới giữa tất cả domain modules — không phải convention mà là automated test fail khi vi phạm. Exception được document rõ trong code (ví dụ: `forecast` dùng `esg.repository` theo ADR-032). Đây là điểm mạnh hiếm thấy trong modular monolith.

### 1.7 Flink fault tolerance được thiết kế và kiểm tra đầy đủ
Checkpoint lưu MinIO/S3 + kill/restart test script (`scripts/flink-checkpoint-recovery-test.sh`) + E2E IT (`EsgDualSinkFlinkE2EIT`) + `ReplacingMergeTree` cho ClickHouse tạo thành một bộ đảm bảo toàn diện cho fault tolerance của streaming pipeline.

---

## 2. Vấn đề & Phát hiện

### 2.1 ~~Modular Monolith chưa có boundary enforcement~~ — ĐÃ XỬ LÝ ✅

**Kiểm tra code:** `ModuleBoundaryArchTest.java` đã implement đầy đủ bằng ArchUnit. Các rules enforce kỹ:
- `environment`, `esg`, `alert`, `notification`, `traffic` — mỗi module không được import domain/service/repository của module khác
- Repository access bị lock trong cùng module (exception được document rõ: `forecast` được phép dùng `esg.repository` theo ADR-032 D6)
- Controller không được dùng `@Cacheable`

Boundary enforcement là **kỹ thuật thực sự** (ArchUnit test fail nếu vi phạm), không phải convention. Vấn đề này không tồn tại.

---

### 2.2 Camunda 7 embedded sẽ là bottleneck khi scale — CAO

**Vấn đề:** Camunda 7 được nhúng trong Spring Boot process. Camunda 7 đã EOL (End of Life), vendor chỉ hỗ trợ đến 2025 với extended support. Process execution history lưu trong cùng database với application data, không tách biệt.

**Tại sao quan trọng:**
- Khi số lượng BPMN process instances tăng (nhiều tenant, nhiều alert), Camunda DB history sẽ phình to và ảnh hưởng performance của application DB
- Upgrade lên Camunda 8 (Zeebe) là breaking change — không backward compatible với Camunda 7 BPMN
- Embedded mode không scale horizontally — chỉ 1 Spring Boot instance xử lý workflow tại một thời điểm

**Mức độ:** CAO (chưa ảnh hưởng pilot, nhưng phải có roadmap rõ ràng)

**Đề xuất:**
- Sprint 7: Tách Camunda DB history sang schema riêng, set `history.cleanup.strategy=removalTimeBased` với TTL 30 ngày
- Roadmap MVP4: Evaluate Camunda 8 Zeebe (Kubernetes-native) hoặc Temporal.io
- Không thêm BPMN process mới mà không có story tách ra external

---

### 2.3 Kong DB-less mode — fragile trên restart — CAO

**Vấn đề:** Kong chạy ở DB-less mode, config được inject tại runtime qua entrypoint script. Nếu `$KONG_JWT_SECRET` env var thiếu khi container restart → JWT plugin config với secret rỗng → mọi request đều pass auth.

**Bằng chứng từ risk report:** "Gate test: alg=none → 401 PASS, nhưng test chạy sau khi Kong start với config đúng" — test chưa cover restart scenario.

**Mức độ:** CAO (security risk trực tiếp)

**Đề xuất:**
- Sprint 7 (ngay): Thêm startup health check script: gọi API endpoint không có Authorization header → phải nhận 401. Nếu không → container exit(1) + alert
- Sprint 7: Thêm automated CI test: stop Kong → start Kong → verify unauthenticated = 401
- Trước production: Chuyển sang Kong với declarative config mounted readonly, hoặc Kong DB mode

---

### 2.4 ~~ClickHouse không exactly-once~~ — ĐÃ XỬ LÝ ✅

**Kiểm tra code:** `infra/clickhouse/schema/V001__create_analytics_schema.sql` và `infrastructure/clickhouse/init.sql` đều dùng `ENGINE = ReplacingMergeTree(ingested_at)`. Deduplication dựa trên `ingested_at` version column đã được implement. Vấn đề này không tồn tại.

---

### 2.5 `is_aggregator` flag — quyền lực quá lớn, thiếu constraint — TRUNG BÌNH

**Vấn đề:** `is_aggregator=true` cho phép tenant xem TẤT CẢ buildings trong cluster. Hiện tại chỉ check flag, không validate cluster ownership. Admin vô tình set flag sai → full data leak.

**Mức độ:** TRUNG BÌNH

**Đề xuất:** Thêm DB constraint: `CONSTRAINT check_aggregator CHECK (NOT is_aggregator OR cluster_id IS NOT NULL)`. Admin API phải validate aggregator + cluster_id pair. Audit log khi toggle flag.

---

### 2.6 ~~Flink kill/restart chưa có E2E test~~ — ĐÃ XỬ LÝ ✅

**Kiểm tra code:** `scripts/flink-checkpoint-recovery-test.sh` (TD-03) đã implement đầy đủ:
1. Verify Flink JobManager healthy
2. Ghi lại row count trước khi kill (TimescaleDB + ClickHouse)
3. Force kill TaskManager container
4. Restart TaskManager, chờ re-register với JobManager
5. Verify job về trạng thái RUNNING sau recovery
6. So sánh row count trước/sau — delta phải = 0 (no data loss)

Script yêu cầu chạy trên môi trường thực với Flink cluster active. Vấn đề này không tồn tại.

---

### 2.7 Không có circuit breaker giữa Spring Boot và các dependencies — TRUNG BÌNH

**Vấn đề:** Spring Boot gọi trực tiếp TimescaleDB (qua JDBC), Redis, Kafka, ClickHouse, Keycloak, Claude API — không có circuit breaker nào. Nếu TimescaleDB slow (query >30s), JDBC connection pool sẽ bị exhaust, toàn bộ API endpoints trả 503.

**Mức độ:** TRUNG BÌNH

**Đề xuất:** Thêm Resilience4j hoặc cấu hình HikariCP với `connectionTimeout` + `idleTimeout` phù hợp. Ưu tiên protect connection pool với `maximumPoolSize` và timeout ngắn hơn default.

---

### 2.8 `redisTemplate.keys()` trong production code — THẤP (nhưng nguy hiểm khi scale)

**Vấn đề:** `redisTemplate.keys()` là blocking operation, scan toàn bộ keyspace — O(N) với N là tổng số keys. Với nhiều tenant và cache keys, operation này sẽ block Redis và làm chậm toàn bộ system.

**Bằng chứng:** Được flag trong Sprint 6 tech debt (TD-S7-04).

**Mức độ:** THẤP hiện tại, CAO khi scale

**Đề xuất:** Thay bằng `SCAN` với cursor pattern. Ưu tiên Sprint 7 (đã có trong tech debt list).

---

### 2.9 Keycloak RoutingJwtDecoder chưa test với real Keycloak — THẤP (giảm sau Sprint 2)

**Vấn đề:** ADR-027 mô tả dual-issuer JWT (legacy + Keycloak), nhưng theo risk report, test chỉ với legacy token. RoutingJwtDecoder chưa được test đầy đủ với Keycloak-issued tokens trong môi trường thực.

**Mức độ:** THẤP (nếu đã fix trong Sprint 2-3, cần confirm)

**Đề xuất:** Xác nhận test coverage cho dual-issuer scenario, đặc biệt edge case token từ tenant realm không tồn tại.

---

### 2.10 Missing FORCE RLS trên `ai_workflow.workflow_definitions` — CAO (production)

**Vấn đề:** Đã phát hiện trong Sprint 6 SA review. Table `ai_workflow.workflow_definitions` có RLS enabled nhưng thiếu `FORCE ROW LEVEL SECURITY`. Table owner (application user) bypass RLS — có thể đọc workflow definitions của tất cả tenants.

**Mức độ:** CAO cho production, THẤP cho pilot (vì pilot là single tenant)

**Đề xuất:** Thêm V30 migration (`ALTER TABLE ai_workflow.workflow_definitions FORCE ROW LEVEL SECURITY`) — đã có trong Sprint 7 tech debt TD-S7-01, cần đảm bảo thực hiện.

---

## 3. Đề xuất ưu tiên

### Trước pilot (Sprint 7 — phải làm)

| # | Hành động | Lý do | Effort |
|---|---|---|---|
| P1 | Thêm Kong restart health check → fail-fast nếu unauthenticated request pass | Security | 1 ngày |
| P2 | V30 migration: FORCE RLS trên `ai_workflow.workflow_definitions` | Multi-tenant security | 0.5 ngày |
| P3 | `is_aggregator` DB constraint + Admin API validation | Data leak prevention | 1 ngày |

### Trước scale-out (MVP4 roadmap)

| # | Hành động | Lý do |
|---|---|---|
| R1 | Camunda 8 Zeebe evaluation hoặc Temporal.io | Workflow không scale với embedded Camunda 7 |
| R2 | Resilience4j circuit breaker cho DB/Redis connections | Fault isolation |
| R3 | Thay `redisTemplate.keys()` bằng SCAN (TD-S7-04) | Performance khi nhiều tenant |

---

## 4. Câu hỏi còn mở

1. **Pilot scope:** Pilot 2026-08-10 sẽ có bao nhiêu tenant thực sự? Nếu chỉ 1 tenant thì multi-tenant isolation issues có thể defer — nếu nhiều tenant thì cần giải quyết `is_aggregator` + FORCE RLS ngay.

2. **Camunda roadmap:** Team có kế hoạch upgrade lên Camunda 8 không? Nếu không, ít nhất cần có plan cho DB history cleanup để tránh phình database.

3. **Analytics SLA:** Khi nào analytics data được coi là "stale"? Nếu Flink restart và duplicate tạm thời xuất hiện trong ClickHouse, user có nhận được warning không?

4. **DevOps pilot buffer:** Sprint 1 risk report ghi "0-day buffer trước pilot" sau extension 1 tuần. Buffer hiện tại là bao nhiêu? DevOps condition yêu cầu tối thiểu 5 ngày.

---

## 5. Kết luận

### Điểm mạnh nổi bật
- Pipeline IoT → Kafka → Flink → dual-store thiết kế đúng hướng
- Multi-tenant RLS có chiều sâu, được test concurrent
- AI trong alert path an toàn (async + fallback)
- ADR culture tốt, team có tư duy kiến trúc
- Module boundary được enforce kỹ thuật bằng ArchUnit — không phải convention
- Flink fault tolerance được thiết kế và test toàn diện (checkpoint + kill/restart script + ReplacingMergeTree)

### Rủi ro cần xử lý trước pilot
- Kong restart → auth bypass (SECURITY)
- Missing FORCE RLS trên `ai_workflow.workflow_definitions` (MULTI-TENANT SECURITY)

### Rủi ro cần có roadmap rõ ràng
- Camunda 7 EOL + embedded không scale horizontally
- Thiếu circuit breaker giữa Spring Boot và dependencies

---

## Kết luận: DUYỆT CÓ ĐIỀU KIỆN

**Điều kiện bắt buộc trước pilot (Sprint 7):**
1. Kong restart health check → fail-fast nếu auth bypass
2. FORCE RLS trên `ai_workflow.workflow_definitions` (V30 migration — đã có trong TD-S7-01)

**Những điều này không phải là redesign** — ước tính 1–2 ngày. Nền tảng kiến trúc tốt hơn đánh giá ban đầu từ tài liệu: module boundary, ClickHouse deduplication, và Flink fault tolerance đều đã được implement đúng cách trong code.

---

*Review bởi: Solution Architect (độc lập) | 2026-06-01*  
*Tài liệu liên quan: [`system-architecture.md`](./system-architecture.md) · [`sprint1-risk-review.md`](./sprint1-risk-review.md) · [`sprint6-sa-final-review.md`](../reports/sprint6-sa-final-review.md)*
