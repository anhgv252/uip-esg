# MVP3 QA Assessment -- 2026-06-11

**Assessor:** QA Engineer (automated audit)
**Methodology:** Direct code inspection of test files, JaCoCo XML, build results XML, and script review. No reliance on summary reports.

---

## 1. Test Suite Statistics (actual vs claimed)

| Metric | Claimed | Actual (verified) | Delta |
|--------|---------|-------------------|-------|
| Total test files | -- | **200** (156 backend + 20 frontend + 20 E2E + 2 mobile + 2 analytics) | -- |
| Backend test methods | 1,191 | **1,237** (from build XML, last run 2026-06-11) | +46 |
| Frontend unit tests | -- | **~172** across 20 files | -- |
| E2E scenarios (Playwright) | -- | **~179** across 20 spec files | -- |
| Mobile tests | -- | **~35** across 2 files | -- |
| Analytics service tests | -- | **8** (build XML) | -- |
| IoT ingestion tests | -- | **3** (build XML) | -- |
| **Grand total** | **1,191** | **~1,634** | **+443** |
| Failures | 0 | **0** (verified from XML) | Match |
| Errors | 0 | **0** (verified from XML) | Match |
| Skipped | -- | **3** (BmsIntegrationExtendedTest, OpenApiSpecGeneratorTest) | -- |

### Test pyramid distribution

| Layer | Count | Percentage | Target | Status |
|-------|-------|-----------|--------|--------|
| Unit (backend *Test.java) | 137 files / ~900 methods | 57% | 60% | Close |
| Integration (*IT.java + *IT tests) | 19 files / ~330 methods | 21% | 30% | Below |
| E2E (Playwright) | 20 files / ~179 scenarios | 12% | 10% | OK |
| Contract (Pact) | 2 files / 3 interactions | <1% | -- | Weak |
| Architecture (ArchUnit) | 1 file | <1% | -- | Present |

---

## 2. Test Quality Assessment

### Strengths

**S1: Boundary-value testing is strong.**
`AqiCalculatorTest` parameterizes all AQI breakpoints (0, 50, 51, 100, 101, 150, 151, 200, 201, 300, 301, 500) with +-1 tolerance. `BuildingSafetyServiceTest` parameterizes structural threshold checks. This matches the QA skill requirement for parameterized threshold tests.

**S2: Alert deduplication is well-tested.**
`AlertEngineTest` covers Redis dedup (setIfAbsent), cooldown key format, mismatched measureType skip, empty rules list. `FloodAlertConsumerTest` covers DLQ routing, severity mapping (P0/P1/P2/null/unknown), tenant validation, and retry logic. These are safety-critical paths.

**S3: Testcontainers pattern is mature.**
`AbstractIT` provides a shared PostgreSQL container with Ryuk cleanup, Flyway integration, and Kafka/Redis stubs. 15 IT classes extend it. TenantIsolationIT intentionally uses its own container for RLS testing -- documented rationale is sound.

**S4: @DisplayName is consistently used.**
1,210 @DisplayName annotations across 156 test files = near-universal adoption. Tests are self-documenting.

**S5: Security test is properly scoped.**
`ProductionProfileSecurityTest` uses @WebMvcTest slice (not @SpringBootTest) to verify debug endpoints return 404 in production profile. Clean, fast, no external dependencies.

**S6: Mobile offline tests are thorough.**
`SyncQueue.test.ts` covers concurrent flush (SQ-01), concurrent enqueue (SQ-02), enqueue-during-flush (SQ-03), JWT not in AsyncStorage (SQ-04), MAX_RETRIES (SQ-05), 4xx vs 5xx retry behavior. 35 test cases across 2 files.

**S7: SSE lifecycle tested.**
`useNotificationSSE.test.ts` tests connection creation, credential handling, malformed data, reconnect with 5s delay, unmount cleanup, and unmount-during-reconnect race condition.

### Weaknesses

**W1: Parameterized tests are underused.**
Only 5 files use @ParameterizedTest across 156 test files. For a smart city platform with many threshold-based rules (AQI, flood, noise, PM2.5), more boundary-value parameterization is needed.

**W2: Thread.sleep() found in 12 test locations.**
Per QA skill rules: "No Thread.sleep() -- Awaitility for async sensor event processing." Three files use Awaitility correctly, but 12 locations still use Thread.sleep.

**W3: DTO tests include trivial getter/setter checks.**
`WorkflowDtoTest` tests getter/setter roundtrips on DTOs. This inflates test count without meaningful coverage. ~20 tests of this pattern found.

**W4: No REST Assured API contract tests found.**
Zero files import io.restassured. Controller testing relies on @WebMvcTest + MockMvc (13 files), which tests Spring MVC routing but not HTTP-level contract (status codes, response headers, JSON schema). The QA skill pattern requires REST Assured for contract testing.

**W5: Pact contract coverage is minimal.**
Only 1 consumer contract (backend -> analytics-service energy-aggregate) and 1 provider verification. Missing contracts for: IoT ingestion, Kafka event schemas, weather API, GIS integration.

---

## 3. Coverage Analysis

### Backend monolith (JaCoCo, last run 2026-06-11)

| Metric | Claimed | Actual | Delta |
|--------|---------|--------|-------|
| Line coverage | 86% | **76.3%** (3,675/4,815) | **-9.7%** |
| Branch coverage | 71% | **61.4%** (891/1,451) | **-9.6%** |
| Instruction coverage | -- | 78.4% (18,903/24,110) | -- |
| Method coverage | -- | 75.5% (778/1,031) | -- |
| Class coverage | -- | 87.5% (133/152) | -- |

**Verdict: Line coverage is 76.3%, NOT 86% as claimed. Branch coverage is 61.4%, NOT 71%. Both metrics are approximately 10 percentage points below claimed values.**

### Lowest coverage packages (critical risk)

| Package | Line | Branch | Lines | Risk |
|---------|------|--------|-------|------|
| com.uip.backend.bms.mqtt | 21% | 6% | 48 | HIGH -- BACnet/MQTT protocol handling |
| com.uip.backend.kafka.producer | 22% | 12% | 45 | HIGH -- Kafka producer error paths |
| com.uip.backend.bms.adapter | 47% | 33% | 223 | MEDIUM -- Modbus/BACnet adapters |
| com.uip.backend.tenant.service | 60% | 33% | 247 | MEDIUM -- Multi-tenant data isolation |
| com.uip.backend.alert.flood | 66% | 71% | 116 | MEDIUM -- Flood alert consumer |
| com.uip.backend.notification.channel | 71% | 58% | 68 | MEDIUM -- Push notification delivery |
| com.uip.backend.aiworkflow.gateway | 89% | 24% | 66 | LOW -- AI decision branching |

### Analytics service (JaCoCo)

| Metric | Actual | Verdict |
|--------|--------|---------|
| Line | 2.3% (22/968) | **CRITICAL** |
| Branch | 1.4% (4/285) | **CRITICAL** |
| Test files | 2 (1 IT + 1 Pact) | Insufficient |

15 source files, 968 lines of code, only 22 lines covered. This is a significant gap for a service that produces ESG energy analytics.

### IoT Ingestion service (JaCoCo)

| Metric | Actual | Verdict |
|--------|--------|---------|
| Line | 21.4% (6/28) | LOW |
| Branch | N/A | -- |
| Test files | 2 (1 smoke + 1 config) | Insufficient |

---

## 4. Performance Test Review

### Available tools

| Tool | Location | Status |
|------|----------|--------|
| Python Kafka benchmark | `scripts/perf_benchmark.py` | Present -- multi-mode (kafka-only, flink, db-only) |
| MQTT load test | `tests/mvp1/performance/run_full_perf.sh` | Present -- 10-minute sustained at 2000 msg/s |
| Full perf test | `tests/mvp1/performance/run_perf.sh` | Present |
| Large data seeder | `scripts/perf-seed-large.sql` | Present |
| Docker compose perf env | `perf/docker-compose.perf.yml` | Present |

### Assessment

- MQTT sustained load test targets 2000 msg/s for 10 minutes (= 1.2M messages). This is reasonable for MVP.
- Python benchmark supports configurable rate, duration, and worker threads. Measures p50/p99 latency.
- **Gap: No 1000 VU (Virtual User) JMeter/Gatling scenario found.** The MVP2 target was 1000 VU. The existing scripts test MQTT throughput, not concurrent HTTP API users.
- **Gap: No automated p95 < 200ms API latency gate.** Performance results are manual reports, not CI gates.
- **Gap: No sensor-to-alert end-to-end latency measurement (< 30s target).**

---

## 5. Security Test Review

### ProductionProfileSecurityTest
- Uses @WebMvcTest + @ActiveProfiles("production") -- correct approach
- Verifies 3 debug/test endpoints return 404 (inject-reading, inject-flood-alert, fake-traffic)
- Has inline TestSecurityConfig that permits all requests -- verifies 404 is from missing controller, not security
- **Well structured** but only covers 3 endpoints. Missing: all other test/debug endpoints.

### OWASP Dependency Check
- Configured: `org.owasp.dependencycheck` v12.1.0 in build.gradle
- Policy: `failBuildOnCVSS = 7.0` -- build fails on HIGH/CRITICAL CVEs
- Last scan: 2026-05-06
- **16 CRITICAL CVEs: all fixed** (Tomcat 10.1.19 -> 10.1.41, Netty, Log4j, PostgreSQL, Kafka)
- **6 HIGH CVEs open**: 2 with no fix available on Maven Central, 4 below policy threshold
- Suppression file: `backend/config/dependency-check-suppressions.xml` exists
- **171 MEDIUM/LOW CVEs open** -- typical for Spring Boot, not blocking

### Security gaps found
- **No REST Assured tests for auth header validation** (expired JWT, missing JWT, tampered JWT)
- **No rate limiter integration test** (100 req/min public, 10K req/min IoT)
- **No SQL injection test** (parameterized query params: sensor ID, district, date range)
- **No audit log verification** for P0 alert creation/acknowledgment
- **Only 1 security-specific test file** (ProductionProfileSecurityTest)

---

## 6. E2E Test Review (Playwright)

### Configuration
- Multi-browser: Chromium + Firefox + Mobile Chrome + Mobile Safari
- CI-ready: `forbidOnly` in CI, retries=2, parallel workers
- Trace/screenshot/video on failure
- Timeout: 45s default, 120s slow-mo

### Test coverage

| Spec File | Focus | Lines | Priority |
|-----------|-------|-------|----------|
| sprint1-demo.spec.ts | Demo walkthrough | 18K | P0 |
| sprint5-po-demo.spec.ts | PO demo | 23K | P0 |
| sprint2-multi-tenancy.spec.ts | Multi-tenant isolation | 19K | P0 |
| sprint6-uat.spec.ts | UAT scenarios | 21K | P0 |
| pwa-mobile.spec.ts | PWA + mobile | 11K | P1 |
| alert-pipeline.spec.ts | Alert end-to-end | 6K | P0 |
| ai-workflow.spec.ts | AI workflow | 3K | P1 |
| esg-reports.spec.ts | ESG report gen | 2K | P0 |
| citizen-register.spec.ts | Citizen registration | 6K | P1 |
| tenant-admin-crud.spec.ts | Tenant admin | 9K | P1 |
| Other 10 specs | Various features | ~60K | P1-P2 |

### Assessment
- **Strong coverage** for critical user journeys: flood alert, ESG report, multi-tenancy, PWA
- **Total: ~3,800 lines of E2E test code** across 20 spec files
- **Gap: No automated smoke test after deployment** (production gate)
- **Gap: No cross-browser results matrix** (config exists but no CI execution evidence)

---

## 7. Test Gaps

### Critical gaps (must fix before pilot)

| Gap | Module | Risk | Impact |
|-----|--------|------|--------|
| Analytics service 2.3% coverage | analytics-service | CRITICAL | ESG energy data may be wrong -- no test verification |
| No 1000 VU perf scenario | infrastructure | HIGH | Cannot verify production load capacity |
| No sensor-to-alert E2E latency test | alert pipeline | HIGH | Cannot verify < 30s alert SLA |
| No REST Assured API contract tests | all modules | HIGH | API contract drift not caught automatically |
| bms.mqtt 21% coverage | BMS module | HIGH | BACnet/MQTT error paths untested |

### High gaps (should fix before pilot)

| Gap | Module | Risk |
|-----|--------|------|
| EnvironmentController has no test | environment | Controller endpoints untested |
| TrafficController has no test | traffic | Controller endpoints untested |
| kafka.producer 22% coverage | Kafka | Producer error paths untested |
| No JWT validation IT test | auth | Expired/tampered JWT not tested |
| Pact contract only 1 consumer | integration | API drift with analytics-service not guarded |
| tenant.service 60% line / 33% branch | tenant | Multi-tenant data isolation gaps |
| 12 Thread.sleep() in tests | test quality | Flaky tests possible |
| notification.channel 71% coverage | notification | Push delivery failure paths untested |

### Medium gaps (fix during pilot)

| Gap | Module | Risk |
|-----|--------|------|
| Only 5 files use @ParameterizedTest | test quality | Threshold boundary gaps |
| DTO getter/setter tests inflate count | test quality | False coverage confidence |
| analytics-service 15 source files, 2 test files | analytics | Service essentially untested |
| iot-ingestion-service 21% line coverage | IoT | Sensor ingestion untested |
| No rate limiter integration test | security | Rate limiting not verified |
| No SQL injection test | security | Input validation gap |
| 60+ packages have 0 test files | various | Mostly DTOs/domain/repos, some API controllers |

---

## 8. Chaos Engineering Review (Sprint 11)

### Available scripts

| Script | Target | Method |
|--------|--------|--------|
| chaos-kafka-broker.sh | Kafka 3-broker cluster | Kill 1 broker, verify remaining, restart |
| chaos-clickhouse-node.sh | ClickHouse cluster | Node kill and recovery |
| chaos-postgresql-failover.sh | PostgreSQL HA | Failover simulation |
| chaos-flink-jobmanager.sh | Flink cluster | JobManager kill and recovery |
| run-all-chaos.sh | All above | Sequential execution with HTML report |

### Assessment
- **Good coverage** of infrastructure failure scenarios: Kafka, ClickHouse, PostgreSQL, Flink
- Each script follows: verify initial state -> kill -> verify degraded -> restart -> verify recovery
- HTML report generation with pass/fail results
- **Dual location** (infrastructure/chaos/ and tests/chaos/) suggests some duplication

### Gaps
- **No Redis chaos test** -- cache failure scenario not covered
- **No network partition test content verified** -- chaos-network-partition.sh exists but needs review
- **No application-level chaos** (e.g., slow Kafka consumer, malformed messages, schema evolution)
- **No automated gate** -- chaos tests are manual scripts, not CI-integrated

---

## 9. Risk Assessment for Pilot

### Risk matrix

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Analytics service produces wrong ESG data | HIGH (2.3% tested) | HIGH | Add IT tests before pilot |
| Flood alert misses critical threshold | LOW (66% alert.flood coverage) | CRITICAL | Add boundary tests |
| Performance degradation under load | MEDIUM (no 1000 VU test) | HIGH | Run perf benchmark before go-live |
| Kafka producer loses messages | MEDIUM (22% coverage) | HIGH | Add producer error path tests |
| Multi-tenant data leak | LOW (TenantIsolationIT exists) | CRITICAL | Extend tenant.service coverage |
| Push notification delivery failure | MEDIUM (71% coverage) | MEDIUM | Add FCM/APNs error tests |
| API contract drift between services | HIGH (1 Pact test) | MEDIUM | Add Pact tests for all inter-service calls |
| BMS MQTT protocol error unhandled | MEDIUM (21% coverage) | MEDIUM | Add MQTT adapter tests |

### Top 5 pilot blockers

1. **Analytics service test coverage** (2.3%) -- ESG energy data is core business value
2. **Performance gate missing** -- No 1000 VU scenario, no p95 < 200ms CI gate
3. **Coverage claim mismatch** -- 86% claimed vs 76.3% actual. Must correct before investor demo
4. **API contract testing gap** -- Only 1 Pact contract for 6+ inter-service integrations
5. **Environment/Traffic controller untested** -- Two core modules have zero controller tests

---

## 10. Recommendations

### Immediate (before pilot go-live)

1. **Correct coverage claims** -- Actual is 76.3% line / 61.4% branch, not 86%/71%
2. **Add analytics-service tests** -- Target at least 50% line coverage. Priority: EnergyAggregateService, AqiTrendService, ClickHouseEnergyRepository
3. **Run performance benchmark** -- Execute `perf_benchmark.py --mode flink --rate 10000 --duration 60` and `run_full_perf.sh` on staging
4. **Add EnvironmentController and TrafficController tests** -- At minimum @WebMvcTest for all endpoints

### Short-term (during pilot)

5. **Add Pact contracts** for IoT ingestion, Kafka event schemas
6. **Replace Thread.sleep() with Awaitility** in the 12 locations identified
7. **Add REST Assured API contract tests** for all P0 endpoints
8. **Add sensor-to-alert E2E latency test** -- measure time from Kafka publish to alert DB record
9. **Add JWT validation integration test** -- expired, tampered, missing token scenarios
10. **Add rate limiter integration test** -- verify 100 req/min for public endpoints

### Medium-term (post-pilot)

11. **Add Redis chaos test** to chaos engineering suite
12. **Increase parameterized tests** -- especially for AQI thresholds, flood levels, noise levels
13. **Remove DTO getter/setter tests** that inflate count without value
14. **CI-integrate chaos tests** as weekly scheduled job
15. **Add 1000 VU JMeter scenario** with HTML report generation

---

## Appendix A: Test file inventory by module

### Backend monolith (156 files)

| Package | Files | Notable tests |
|---------|-------|---------------|
| forecast | 8 | ForecastServiceTest, ForecastCacheKafkaListenerTest |
| notification/service | 7 | NotificationServiceTest, SSE lifecycle tests |
| workflow/config | 7 | TriggerConfig cache + validation tests |
| esg/service | 5 | EsgServiceTest, EsgReportGeneratorTest, EsgReportApiIT |
| notification/push | 5 | PushNotificationServiceTest, NotificationFlowIT |
| bms | 5 | BmsIntegrationTest, BmsSimulatorIT (hardware sim) |
| alert/service | 4 | AlertEngineTest (dedup), AlertEscalationTest |
| building/service | 4 | CrossBuildingAggregationServiceIT, CrossBuildingConcurrentRLSIT |
| regression | 4 | Sprint 2/3/5/11 API regression suites |
| auth/service | 3 | AuthServiceTest, JwtTokenProviderTest, JwtTokenValidationTest |
| Other packages | 104+ | Various service, controller, adapter tests |

### Microservices

| Service | Source files | Test files | Line coverage |
|---------|-------------|------------|---------------|
| analytics-service | 15 | 2 | 2.3% |
| iot-ingestion-service | 7 | 2 | 21.4% |

### Frontend

| Layer | Files | Test methods |
|-------|-------|-------------|
| Unit tests (Vitest) | 20 | ~172 |
| E2E (Playwright) | 20 | ~179 |

### Mobile

| Layer | Files | Test methods |
|-------|-------|-------------|
| Unit tests (Jest) | 2 | ~35 |

## Appendix B: Build verification

- Last build: 2026-06-11 11:25 (same day as assessment)
- Test results XML: 250 files in `build/test-results/test/`
- JaCoCo XML: `build/reports/jacoco/test/jacocoTestReport.xml`
- Failures: 0, Errors: 0, Skipped: 3
- Skipped tests: BmsIntegrationExtendedTest (1), OpenApiSpecGeneratorTest (2)
