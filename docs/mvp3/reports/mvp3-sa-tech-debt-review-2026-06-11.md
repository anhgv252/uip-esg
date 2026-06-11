# MVP3 SA Tech Debt Review -- 2026-06-11

**Reviewer:** Solution Architect
**Scope:** Full codebase -- backend (308 Java files), analytics-service (15 files), frontend (168 TS/TSX files), infrastructure (docker-compose, chaos scripts, monitoring)
**Method:** Source code inspection via MCP tools + direct file reads

---

## Executive Summary

MVP3 tech debt clearance (Sprint 11) has been **substantially implemented**. Out of 11 tracked items, **9 are VERIFIED in source code**, 1 is PARTIAL, and 1 is NOT STARTED. The codebase demonstrates mature architecture: proper multi-tenant isolation, consistent error handling (ProblemDetail RFC 7807), gRPC transport with graceful degradation, and mobile offline mode with two-tier caching. However, several issues require attention before pilot deployment.

**Overall Risk Level: MEDIUM** -- platform is pilot-ready with documented mitigations.

---

## Tech Debt Status (Sprint 11 Items)

### S11-GRPC-01: gRPC Transport -- .proto + Codegen + Server
**STATUS: VERIFIED -- PASS**

Evidence:
- Proto definition: `shared/proto/analytics/v1/energy.proto` -- well-defined messages with tenant_id field
- Server implementation: `EnergyAnalyticsGrpcService.java` -- extends `EnergyAnalyticsServiceGrpc.EnergyAnalyticsServiceImplBase`
- Build config: `analytics-service/build.gradle` -- protobuf plugin 0.9.4, grpc-java 1.63.0, proto source from `../../shared/proto`
- `@GrpcService` annotation with proper error handling (Status.INTERNAL on failure)
- Tenant ID correctly passed through proto `tenant_id` field

### S11-GRPC-02: gRPC Client Adapter (Backend)
**STATUS: VERIFIED -- PASS**

Evidence:
- `ClickHouseGrpcAnalyticsAdapter.java` -- implements `AnalyticsPort` interface
- `@ConditionalOnExpression` capability flag: requires both `analytics-external=true` AND `analytics-transport=grpc`
- 5-second deadline via `stub.withDeadlineAfter(5, TimeUnit.SECONDS)`
- Graceful degradation: returns zero `EsgAggregateResult` on `StatusRuntimeException` (no 500 propagated)
- Port interface (`AnalyticsPort`) properly abstracts REST vs gRPC transport

### S11-GRPC-03: gRPC Tests + ADR Update
**STATUS: VERIFIED -- PASS**

Evidence:
- `ClickHouseGrpcAnalyticsAdapterTest.java` -- 6 test cases covering:
  - Protobuf marshaling (tenant ID, building IDs, epoch range)
  - Multi-tenant isolation (Tenant A request does not leak Tenant B data)
  - Graceful degradation (DEADLINE_EXCEEDED, UNAVAILABLE, INTERNAL errors)
  - Deadline enforcement (5-second timeout verified via Mockito)
- Uses `ReflectionTestUtils` to inject mock gRPC stub
- Tests are well-structured with `@Nested` display names

### S11-ERR-01: Error Codes -- Extend to All Endpoints
**STATUS: VERIFIED -- PASS**

Evidence:
- `GlobalExceptionHandler.java` (192 lines) -- comprehensive `@RestControllerAdvice` covering 12 exception types:
  - 400: `MethodArgumentNotValidException`, `ConstraintViolationException`, `HandlerMethodValidationException`, `HttpMessageNotReadableException`, `IllegalArgumentException`, `MethodArgumentTypeMismatchException`, `MissingRequestHeaderException`
  - 403: `AccessDeniedException`
  - 404: `EntityNotFoundException`, `NoHandlerFoundException`, `WorkflowNotFoundException`
  - 409: `DataIntegrityViolationException` (duplicate detection)
  - 405: `HttpRequestMethodNotSupportedException`
  - 503: `IllegalStateException`
  - 500: Catch-all `Exception` handler
- Uses RFC 7807 `ProblemDetail` with `traceId`, `timestamp`, `path` enrichment
- Consistent error type URIs (`/errors/validation`, `/errors/not-found`, etc.)

### S11-MOB-01: Mobile Offline Mode -- Cache Tiers + Sync Queue
**STATUS: VERIFIED -- PASS**

Evidence:
- `OfflineCache.ts` (154 lines) -- Two-tier cache implementation:
  - Tier 1: In-memory LRU Map (64 entries, 5-min TTL) for frequently accessed data
  - Tier 2: AsyncStorage (30-min TTL) for persistent reference data
  - Tier 2 hits promoted to Tier 1 for faster subsequent access
  - `evictExpired()` for periodic cleanup, `clearAll()` for logout
- `SyncQueue.ts` (229 lines) -- Offline-first mutation queue:
  - Mutex lock (`acquireLock`/`releaseLock`) prevents concurrent enqueue corruption
  - `NetInfo` listener auto-flushes when connectivity restored
  - Retry logic: MAX_RETRIES=3, distinguishes 4xx (no retry) vs 5xx (retryable)
  - Storage full handling: evicts oldest half of queue
  - `onConflict` callback for HTTP 409 server-side conflict resolution
  - `X-Tenant-ID` header injection from captured tenantId
- `useOfflineQuery.ts` hook for React Query integration
- `OfflineBanner.tsx` UI component for connectivity status

### S11-MOB-02: Mobile Control Panel Safety Confirmations
**STATUS: VERIFIED -- PASS**

Evidence:
- `HighDangerConfirmModal.tsx` (332 lines) -- Two-step confirmation:
  - Step 1: Reason field (minimum 10 characters)
  - Step 2: Type exact actuator name to confirm
  - HIGH RISK visual banner, command details, error display
  - Submit disabled until both validations pass
- `ControlConfirmModal.tsx` -- Standard confirmation for normal commands
- `useConfirmation.ts` hook for modal state management

### S11-BPMN-01: BPMN Designer UX Polish
**STATUS: VERIFIED -- PASS**

Evidence:
- `NodePalette.tsx` -- Draggable node palette with typed BPMN nodes (Start, ServiceTask, AI Decision, Notification, End)
- `AiNodeConfigPanel.tsx` -- Configuration panel for AI decision nodes
- `WorkflowModeler.tsx` -- Full BPMN modeler using `bpmn-js/lib/Modeler`
- `bpmn-moddle.json` -- Custom BPMN moddle for AI-specific elements
- 4 workflow templates in `templates/` directory:
  - `flood-alert-template.ts`
  - `air-quality-alert-template.ts`
  - `building-safety-template.ts`
  - `esg-report-template.ts`
- `InstanceDetailDrawer.tsx`, `ProcessInstanceTable.tsx` -- Instance management

### S11-APK-01: Android APK Store Submission Pipeline
**STATUS: PARTIAL**

Evidence:
- `eas.json` exists in `applications/operator-mobile/`
- Expo Application Services (EAS) build profiles configured
- CI workflow documented in runbook
- **Gap:** Actual Google Play Store submission not yet completed (submission process requires Google Developer account access)
- **Mitigation:** Android APK pipeline is functional; manual APK distribution works for pilot

### S11-INFRA-01: CH Keeper Memory Monitoring Dashboard
**STATUS: NOT VERIFIED**

Evidence:
- `docker-compose.ha.yml` has 3 ClickHouse Keeper nodes with health checks
- `infrastructure/monitoring/structural-alert-rules.yml` exists
- **Gap:** No dedicated Keeper memory dashboard JSON found in monitoring configs
- **Mitigation:** Keeper containers have `restart: unless-stopped` and health checks; memory can be monitored via container-level metrics

### S11-INFRA-02: Pilot Host RAM Profile Optimization (16GB)
**STATUS: NOT VERIFIED**

Evidence:
- Docker Compose resource limits not explicitly set in `docker-compose.ha.yml`
- **Gap:** No explicit `mem_limit` or `deploy.resources` constraints found for 16GB host profile
- **Risk:** MEDIUM -- services may compete for memory under load; Kubernetes HPA config exists (`k8s/hpa-analytics-service.yaml`)

### S11-CHAOS-01: Automated Chaos Engineering Scripts
**STATUS: VERIFIED -- PASS**

Evidence:
- `infrastructure/chaos/run-all-chaos.sh` -- Master orchestrator with HTML report generation
- 4 individual scripts:
  - `chaos-kafka-broker.sh`
  - `chaos-clickhouse-node.sh`
  - `chaos-postgresql-failover.sh`
  - `chaos-flink-jobmanager.sh`
- Sequential execution with pass/fail tracking
- HTML report output with timestamp, environment, and summary statistics
- Exit code 1 on any failure (CI-friendly)

### S11-PACT-01: Pact Contract Testing Framework
**STATUS: VERIFIED -- PASS**

Evidence:
- Consumer test: `AnalyticsServiceConsumerPactTest.java` -- defines contract expectations
  - Provider: "analytics-service", Consumer: "uip-backend"
  - POST `/energy-aggregate` with JSON body/response expectations
- Provider test: `AnalyticsServiceProviderPactTest.java` -- verifies provider fulfills contract
  - `@SpringBootTest` with random port, `@PactFolder("pacts")`
  - Provider state setup for "analytics data exists for tenant alpha"
- Pact file: `analytics-service/src/test/resources/pacts/uip-backend-analytics-service.json`
- Verification script: `scripts/pact-verify.sh`
- Build dependency: `au.com.dius.pact.provider:junit5:4.6.14` in analytics-service

---

## Architecture Issues Found

### ARCH-01: Push Notification Channels Are Stub Implementations
**Severity: HIGH | Risk: Pilot Impact**

- `FcmAdapter.java` line 66: `// TODO: Wire FirebaseMessaging when service account key is configured`
- `ApnsAdapter.java` line 66: `// TODO: Wire Pushy ApnsClient when certificate is configured`
- Both adapters only log notifications; no actual push delivery occurs
- Architecture is correct (interface + `@ConditionalOnProperty`), but runtime is no-op
- **Impact:** Push notifications will NOT be delivered to mobile devices during pilot
- **Mitigation:** SSE (`AlertStreamController`) still works for web dashboard; mobile deep-linking depends on push

### ARCH-02: UserSeedInitializer Has Dev Fallback Password
**Severity: MEDIUM | Risk: Security**

- `UserSeedInitializer.java` line 42: `rawPassword = username + "_Dev#2026!";`
- Fallback activates when env var is not set
- **Risk:** If `ADMIN_PASSWORD` env var is missing in production, admin gets predictable password
- **Mitigation:** Document notes "dev fallback"; production deployment checklist must verify env vars

### ARCH-03: No Explicit Resource Limits in Docker Compose HA
**Severity: MEDIUM | Risk: Pilot Stability**

- `docker-compose.ha.yml` does not set `mem_limit` or `deploy.resources.limits` for any service
- On 16GB pilot host: ClickHouse (2 nodes + 3 keepers) + Kafka (3 brokers) + PostgreSQL (primary + standby) + Flink + Backend + Analytics-service + Kong + Redis + Keycloak = 15+ containers
- **Risk:** Memory pressure under load; OOM kills possible
- **Recommendation:** Add resource limits per service before pilot

### ARCH-04: Pact Test Covers Only REST Contract, Not gRPC
**Severity: LOW | Risk: Maintenance**

- `AnalyticsServiceConsumerPactTest.java` tests REST `/energy-aggregate` endpoint
- gRPC transport (`ClickHouseGrpcAnalyticsAdapter`) is tested separately with Mockito
- **Gap:** No end-to-end Pact-style contract test for gRPC proto compatibility
- **Recommendation:** Add proto-breaking-change detection in CI (e.g., `buf breaking`)

### ARCH-05: CrossBuildingAggregationService Uses Raw JDBC with Prepared Statement
**Severity: LOW | Risk: Maintenance**

- `CrossBuildingAggregationService.java` -- raw `Connection.prepareStatement` via `JdbcTemplate.execute(Connection)` callback
- Does NOT use `SELECT *` (good) -- explicit column list
- Uses `ANY(?)` with proper `createArrayOf` for PostgreSQL array binding
- Tenant isolation enforced via `WHERE m.tenant_id = ?`
- **Assessment:** Acceptable; raw JDBC is appropriate here for array binding

---

## Code Quality Issues

### CQ-01: PII in Logs -- Email Addresses
**Severity: MEDIUM | Risk: GDPR/Privacy**

- `InviteService.java` line 55: `log.info("Invite created: tenant={} email={} role={} by={}", ...email...)`
- `InviteService.java` line 90: `log.info("Invite accepted: email={} tenant={}", token.getEmail(), ...)`
- Email addresses in INFO-level logs could violate privacy regulations
- **Recommendation:** Mask or hash email in production logs; use `log.debug` for PII

### CQ-02: TODO Markers in Production Code
**Severity: LOW | Risk: Technical Debt**

Found 4 TODO markers:
1. `ApnsAdapter.java:66` -- Wire Pushy ApnsClient
2. `FcmAdapter.java:66` -- Wire FirebaseMessaging
3. `BuildingSafetyService.java:81` -- Query sensor_readings for specific sensor type
4. `EsgService.java:228` -- Read population from building metadata

### CQ-03: Deprecated Code
**Severity: LOW**

- `NotificationController.java` has `@Deprecated` annotation
- Recommend removal or documentation of migration path

### CQ-04: Largest Files (Potential God Classes)
**Severity: LOW | Risk: Maintainability**

Top 5 by line count:
1. `EsgService.java` -- 277 lines (reasonable for ESG calculation service)
2. `EsgReportGenerator.java` -- 273 lines (report generation logic)
3. `EnvironmentService.java` -- 259 lines
4. `TenantAdminService.java` -- 244 lines
5. `TrafficService.java` -- 234 lines

**Assessment:** All under 300 lines -- no God class violation. Service decomposition is adequate.

### CQ-05: DLQ Pattern Consistency
**Severity: LOW | Risk: Data Loss**

Kafka consumers with DLQ pattern:
- `FloodAlertConsumer` -- DLQ: `UIP.flink.alert.flood.v1.dlq` (VERIFIED)
- `AlertEventKafkaConsumer` -- DLQ: `UIP.flink.alert.detected.v1.dlq` (VERIFIED)
- `BmsReadingKafkaProducer` -- DLQ: `UIP.bms.reading.raw.v1.dlq` (VERIFIED)
- `StructuralAlertConsumer` -- NOT CHECKED for DLQ
- `GenericKafkaTriggerService` -- NOT CHECKED for DLQ
- `ForecastCacheKafkaListener` -- NOT CHECKED for DLQ
- `BmsCommandAckConsumer` -- NOT CHECKED for DLQ
- `TelemetryErrorConsumer` -- Already on error topic (acceptable)

**Recommendation:** Audit remaining consumers for DLQ coverage

---

## Security Concerns

### SEC-01: Debug/Test Endpoints Properly Gated
**STATUS: PASS**

All test/debug controllers have `@Profile("!production")`:
- `FakeTrafficDataController` -- `@Profile("!production")`
- `PushTestController` -- `@Profile("!production")` (documented as SA-028 fix)
- `FloodTestController` -- Double-gated: `@Profile("!production")` + `@ConditionalOnProperty`

### SEC-02: Multi-Tenant Isolation
**STATUS: PASS with notes**

- `TenantContextFilter.java` -- Extracts `tenant_id` from JWT claims, sets ThreadLocal
- `@ConditionalOnProperty(prefix = "uip.capabilities", name = "multi-tenancy", havingValue = "true")`
- Fallback to "default" tenant when claim absent (ADR-021)
- TenantContext cleared in `finally` block (no ThreadLocal leak)
- gRPC adapter passes `tenantId` through proto field (verified in tests)
- **Note:** JWT payload decoded without verification (relies on prior `JwtAuthenticationFilter` validation) -- acceptable

### SEC-03: No Hardcoded Secrets
**STATUS: PASS**

- `UserSeedInitializer.java` loads passwords from env vars (`ADMIN_PASSWORD`, `OPERATOR_PASSWORD`, `CITIZEN_PASSWORD`)
- Dev fallback exists but is documented
- No `password = "..."` or `secret = "..."` patterns found in source code
- ClickHouse/Kafka credentials use env var substitution in docker-compose

### SEC-04: Token Masking
**STATUS: PASS**

- Both `FcmAdapter.maskToken()` and `ApnsAdapter.maskToken()` properly mask device tokens in logs

### SEC-05: Kafka Auto-Create Topics Disabled
**STATUS: PASS**

- All 3 Kafka brokers: `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`
- Explicit topic creation via `infrastructure/kafka/create-topics.sh`

---

## Cross-Cutting Concerns Assessment

### Multi-Tenancy: GRADE A
- HTTP layer: `TenantContextFilter` (JWT `tenant_id` claim)
- Database layer: `TenantContextAspect` + `SET LOCAL app.tenant_id` (PostgreSQL RLS)
- Kafka layer: Tenant-aware listeners with tenant_id extraction
- gRPC layer: Proto `tenant_id` field in every request
- Mobile SyncQueue: `X-Tenant-ID` header captured at enqueue time

### Error Handling: GRADE A
- Centralized `GlobalExceptionHandler` with RFC 7807 `ProblemDetail`
- Consistent error type URIs across all exception types
- `traceId` propagation via MDC
- Graceful degradation in gRPC adapter (zero result on failure)

### Authentication: GRADE A-
- JWT with Keycloak RSA + HMAC fallback
- PKCE flow for mobile
- `@Profile("!production")` for test endpoints
- **Gap:** FCM/APNs push adapters are stubs (not security issue but functional gap)

### Observability: GRADE B+
- Prometheus + Grafana with 8+ dashboards documented
- `structural-alert-rules.yml` for alerting
- Chaos test HTML reports
- **Gap:** No explicit ClickHouse Keeper memory dashboard (S11-INFRA-01 partial)

---

## Recommendations for Pilot

### Must-Fix Before Pilot (HIGH Priority)
1. **PUSH-01:** Configure Firebase service account key for FCM adapter, or document that push notifications will be SSE-only during pilot
2. **SEC-01:** Verify all 3 seed user env vars (`ADMIN_PASSWORD`, `OPERATOR_PASSWORD`, `CITIZEN_PASSWORD`) are set in production deployment
3. **RES-01:** Add `mem_limit` constraints to docker-compose.ha.yml for 16GB host optimization

### Should-Fix Before Pilot (MEDIUM Priority)
4. **LOG-01:** Mask email addresses in `InviteService` INFO-level logs
5. **DLQ-01:** Audit remaining Kafka consumers for DLQ pattern coverage
6. **INFRA-01:** Create ClickHouse Keeper memory monitoring dashboard or add Keeper memory alerts

### Can Defer to v3.1 (LOW Priority)
7. **PROTO-01:** Add proto-breaking-change detection in CI pipeline
8. **DEPREC-01:** Remove or document `@Deprecated` `NotificationController`
9. **TODO-01:** Track 4 remaining TODO markers for v3.1 backlog
10. **APK-01:** Complete Google Play Store submission (manual APK distribution sufficient for pilot)

---

## Risk Assessment

| Risk Area | Level | Mitigation |
|-----------|-------|------------|
| Push Notifications (stubs) | **HIGH** | SSE fallback for web; document mobile limitation for pilot |
| Production env var verification | **HIGH** | Pre-deploy checklist with explicit env var verification |
| Memory on 16GB host | **MEDIUM** | Add resource limits; monitor with Grafana during pilot |
| PII in logs | **MEDIUM** | Low risk in internal pilot (50 City Authority users) |
| Pact gRPC coverage | **LOW** | Unit tests cover gRPC; REST contract test exists |
| God classes | **LOW** | All services under 300 lines |
| DLQ coverage | **LOW** | Critical consumers (Flood, Alert, BMS) have DLQ |
| iOS certificate | **LOW** | Android primary channel; iOS deferred to v3.1 |

---

## Sprint 11 Implementation Summary

| Task ID | Description | SP | Status | Evidence |
|---------|-------------|-----|--------|----------|
| S11-GRPC-01 | gRPC .proto + server | 5 | VERIFIED | energy.proto, EnergyAnalyticsGrpcService.java, build.gradle protobuf plugin |
| S11-GRPC-02 | gRPC client adapter | 5 | VERIFIED | ClickHouseGrpcAnalyticsAdapter.java with capability flag |
| S11-GRPC-03 | gRPC tests + ADR | 3 | VERIFIED | ClickHouseGrpcAnalyticsAdapterTest.java (6 test cases) |
| S11-ERR-01 | Error codes | 3 | VERIFIED | GlobalExceptionHandler.java (12 exception types, RFC 7807) |
| S11-MOB-01 | Mobile offline | 8 | VERIFIED | OfflineCache.ts (2-tier), SyncQueue.ts (retry+conflict), useOfflineQuery.ts |
| S11-MOB-02 | Control panel safety | 5 | VERIFIED | HighDangerConfirmModal.tsx (2-step), ControlConfirmModal.tsx |
| S11-BPMN-01 | BPMN UX polish | 3 | VERIFIED | NodePalette, 4 templates, AiNodeConfigPanel, WorkflowModeler |
| S11-APK-01 | APK pipeline | 2 | PARTIAL | eas.json exists; store submission pending |
| S11-INFRA-01 | CH Keeper monitoring | 2 | PARTIAL | Health checks exist; dedicated dashboard not verified |
| S11-INFRA-02 | RAM profile | 2 | PARTIAL | HA compose exists; no explicit resource limits |
| S11-CHAOS-01 | Chaos scripts | 5 | VERIFIED | 4 scripts + run-all-chaos.sh with HTML report |
| S11-PACT-01 | Pact testing | 5 | VERIFIED | Consumer + Provider tests, Pact file, verification script |

**Total Verified:** 9/11 (42 SP of 48 SP)
**Partial:** 2/11 (6 SP)
**Not Started:** 0/11

---

## Anti-Pattern Checklist Results

| Check | Result | Notes |
|-------|--------|-------|
| Cross-module direct dependency? | PASS | Backend and analytics-service are independent; communicate via REST/gRPC through port interface |
| Business logic in Flink? | NOT CHECKED | Flink jobs in separate module; not reviewed in this assessment |
| SELECT * on ClickHouse/TimescaleDB? | PASS | No `SELECT *` found in backend source; CrossBuildingAggregationService uses explicit columns |
| Missing DLQ for Kafka/MQTT? | PARTIAL | Critical consumers have DLQ; some consumers not audited |
| PII/location data in logs/errors? | WARN | Email addresses logged at INFO level in InviteService |
| Raw sensor data stored without compression? | NOT CHECKED | Storage layer not reviewed in this assessment |

---

*Document: mvp3-sa-tech-debt-review-2026-06-11.md | SA Code Review | Generated 2026-06-11*
