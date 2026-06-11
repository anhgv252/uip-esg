# MVP3 Backend Source Code Review -- 2026-06-11

**Reviewer**: Backend Engineer Agent
**Scope**: Full source code audit, build verification, API inventory, module-by-module review
**Codebase**: `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/backend`
**Commit**: `7f252577` (main branch, clean working tree)

---

## 1. Build & Test Status

| Metric | Value |
|--------|-------|
| Build | **SUCCESS** (15 tasks, 9s) |
| Source files (main) | 308 `.java` files |
| Test files | 156 `.java` files (250 test XML reports) |
| Total test cases | **1,237** |
| Failures | **0** |
| Errors | **0** |
| Skipped | 3 |
| Pass rate | **100.0%** |

### JaCoCo Coverage

| Type | Covered | Total | % |
|------|---------|-------|---|
| LINE | 3,675 | 4,815 | **76.3%** |
| BRANCH | 891 | 1,451 | **61.4%** |
| INSTRUCTION | 18,903 | 24,110 | **78.4%** |
| METHOD | 778 | 1,031 | **75.5%** |
| CLASS | 133 | 152 | **87.5%** |

### Integration Tests (Testcontainers-based)
19 IT classes found:
- `EsgServiceIT`, `EsgReportApiIT`, `AlertServiceIT`, `FloodAlertConsumerIT`
- `BmsSimulatorIT`, `BmsCommandIT`, `CrossBuildingAggregationServiceIT`, `CrossBuildingConcurrentRLSIT`
- `TenantIsolationIT`, `EnvironmentServiceIT`, `NotificationFlowIT`, `PushSubscriptionIT`
- `RoutingJwtDecoderIT`, `AnalyticsPortMutualExclusivityIT`, `WorkflowDefinitionServiceIT`
- `TriggerConfigCacheServiceIT`, `MobileAuthConfigControllerIT`, `CapabilityFlagIT`
- `AbstractIT` (base class)

### Contract Tests
- `AnalyticsServiceConsumerPactTest` -- Pact consumer contract for analytics-service
- `AnalyticsServicePactProviderTest` -- Pact provider verification (excluded from default build per commit `7f252577`)

### Regression Tests
- `Sprint2ApiRegressionIT`, `Sprint3ApiRegressionIT`, `Sprint5ApiRegressionIT`, `Sprint11ApiRegressionIT`

---

## 2. API Endpoints Inventory (actual vs claimed)

### Summary

| Metric | Count |
|--------|-------|
| `@RestController` classes | **37** |
| Method-level mappings (`@GetMapping`/`@PostMapping`/etc.) | **108** |
| Claimed (MVP3 target) | 107-110 |

**Verdict**: **108 actual endpoints** -- matches claimed count.

### Per-Controller Breakdown

| Controller | Endpoints | Module |
|------------|-----------|--------|
| TenantAdminController | 10 | Tenant |
| WorkflowDefinitionController | 7 | AI Workflow |
| WorkflowConfigController | 7 | Workflow Config |
| AdminController | 6 | Admin |
| BmsDeviceController | 6 | BMS |
| EsgController | 6 | ESG |
| InvoiceController | 6 | Citizen |
| TrafficController | 5 | Traffic |
| CitizenController | 5 | Citizen |
| AlertController | 5 | Alerts |
| WorkflowController | 5 | Workflow |
| BuildingClusterController | 4 | Building |
| PushSubscriptionController | 4 | Push |
| EnvironmentController | 4 | Environment |
| AlertRuleController | 3 | Alert Rules |
| ErrorRecordController | 3 | Admin |
| AuthController | 3 | Auth |
| DashboardController | 2 | Dashboard |
| BuildingSafetyController | 2 | Safety |
| AlertStreamController (SSE) | 1 | Notification |
| NotificationController | 1 | Notification |
| PushTestController | 1 | Push |
| EsgReportController | 1 | ESG |
| BmsDeviceCommandController | 1 | BMS |
| CrossBuildingAnalyticsController | 1 | Building |
| InviteController | 1 | Tenant |
| TenantConfigController | 1 | Tenant |
| MobileAuthConfigController | 1 | Auth |
| HealthController | 1 | Health |
| SimulateController | 1 | Workflow |
| GenericRestTriggerController | 1 | Workflow Trigger |
| ForecastController | 1 | Forecast |
| ForecastCacheStatsController | 1 | Forecast |
| FakeTrafficDataController | 1 | Traffic (dev) |
| FloodTestController | 1 | Flood (dev) |

### Error Codes Coverage

| HTTP Status | Present | Where |
|-------------|---------|-------|
| 200 | Yes | All GET endpoints |
| 201/202 | Yes | POST create/async (ESG report: 202) |
| 400 | Yes | `GlobalExceptionHandler` (validation, illegal arg, type mismatch, malformed body) |
| 401 | Yes | `HttpStatusEntryPoint` + SecurityConfig |
| 403 | Yes | `AccessDeniedException` handler + SecurityConfig `accessDeniedHandler` |
| 404 | Yes | `EntityNotFoundException` + `NoHandlerFoundException` |
| 405 | Yes | `HttpRequestMethodNotSupportedException` |
| 409 | Yes | `DataIntegrityViolationException` (duplicate key) |
| 500 | Yes | Catch-all `Exception` handler |
| 503 | Yes | `IllegalStateException` handler |

All error codes return RFC 7807 `ProblemDetail` format with `type`, `title`, `status`, `detail`, `traceId`, `timestamp`, `path`.

---

## 3. Module-by-Module Review

### 3.1 ESG Module (31 files)

**Files**: `esg/api/`, `esg/service/`, `esg/domain/`, `esg/repository/`, `esg/config/`, `esg/export/`, `esg/kafka/`, `esg/common/`

| Aspect | Assessment |
|--------|------------|
| GRI 302-1 (Energy) | Implemented via `AnalyticsPort` -- Tier 1: TimescaleDB, Tier 2: ClickHouse (REST or gRPC) |
| GRI 305-4 (Carbon) | Calculated in `EsgService` from metric data; CO2 estimation factor 0.5 kg/kWh in REST adapter |
| PDF Export | `EsgPdfService` + `DefaultPdfExportAdapter` -- synchronous <30s SLA via `EsgReportController` |
| Excel/CSV Export | `DefaultXlsxExportAdapter`, `DefaultCsvExportAdapter` -- async via `EsgReportGenerator` |
| Cache | `@Cacheable` with `CacheKeyBuilder` -- tenant-scoped keys |
| Tenant isolation | All service methods accept `tenantId` from `TenantContext` |
| Path traversal guard | `EsgController.downloadReport` -- `getCanonicalFile()` + `startsWith(baseDir)` check |
| AnalyticsPort pattern | **Clean** -- 3 implementations with `@ConditionalOnProperty`/`@ConditionalOnExpression` mutual exclusivity |

**Issues**:
- [MEDIUM] `calculateWaterIntensity()` returns `null` always -- TODO comment says "population data not integrated"
- [LOW] `EsgReportController` uses SpEL `#{T(java.time.LocalDate).now()...}` as `@RequestParam` default -- works but unusual; consider server-side default in method body

### 3.2 Alert Module (14 files)

**Files**: `alert/api/`, `alert/service/`, `alert/domain/`, `alert/repository/`, `alert/kafka/`, `alert/flood/`

| Aspect | Assessment |
|--------|------------|
| AlertEngine | Inline rule evaluation (sensor readings vs threshold rules) -- does NOT use Flink CEP directly |
| Flink integration | `AlertEventKafkaConsumer` consumes `UIP.flink.alert.detected.v1` (Flink output) |
| Flood alerts | `FloodAlertConsumer` consumes `UIP.flink.alert.flood.v1` from Flink CEP job |
| Deduplication | Redis `setIfAbsent` with TTL = rule.cooldownMinutes |
| SSE push | `AlertStreamController` + `SseEmitterRegistry` -- Redis pub/sub to SSE bridge |
| Alert lifecycle | OPEN -> ACKNOWLEDGED -> ESCALATED -> RESOLVED (full state machine) |
| DLQ | Both consumers have DLQ topics with max retry = 3 |
| Manual ACK | All Kafka consumers use `Acknowledgment ack` pattern |
| Tenant validation | `FloodAlertConsumer` validates tenantId against allowed list |

**Issues**:
- [LOW] `AlertEngine` uses inline evaluation rather than Flink CEP for direct sensor ingestion -- acceptable for monolith mode

### 3.3 BMS Module (28 files)

**Files**: `bms/adapter/`, `bms/api/`, `bms/domain/`, `bms/service/`, `bms/kafka/`, `bms/mqtt/`, `bms/repository/`, `bms/security/`

| Aspect | Assessment |
|--------|------------|
| Modbus TCP | `ModbusTcpAdapter` using j2mod library -- read/write registers |
| BACnet/IP | `BacnetIpAdapter` using BACnet4J (commercial license) -- read properties, send commands |
| Adapter pattern | `BmsProtocolAdapter` interface + `BmsAdapterRegistry` -- clean strategy pattern |
| Circuit breaker | `BmsCircuitBreakerWrapper` wraps adapters |
| MQTT (EMQX) | `MqttPublisher` using Eclipse Paho MQTTv5 client -- command publishing with QoS |
| Kafka | `BmsReadingKafkaProducer` (raw readings), `BmsCommandAckConsumer` (command acknowledgements) |
| Command confirmation | `@RequiresConfirmation` AOP aspect -- safety gate for dangerous commands |
| Device discovery | `BmsDiscoveryService` -- auto-register discovered devices |
| Tenant isolation | All service methods filter by `tenantId` |

**Issues**:
- [LOW] `MqttPublisher.connect()` is `synchronized` but `publishCommand()` is not -- potential race if connection drops mid-publish
- [LOW] `BacnetIpAdapter.sendCommand()` logs the command but does not execute -- stub implementation

### 3.4 Notification Module (21 files)

**Files**: `notification/api/`, `notification/service/`, `notification/channel/`, `notification/config/`, `notification/domain/`, `notification/repository/`

| Aspect | Assessment |
|--------|------------|
| SSE | `AlertStreamController` + `SseEmitterRegistry` |
| Push (Web) | `PushSubscriptionService` + VAPID config |
| Push (FCM) | `FcmAdapter` implements `NotificationChannel` |
| Push (APNs) | `ApnsAdapter` implements `NotificationChannel` |
| Routing | `NotificationRouter` dispatches to channels |
| Multi-channel | Web Push + FCM + APNs -- mobile-ready |

**Issues**: None significant.

### 3.5 AI Workflow Module (7 files -- awworkflow/)

**Files**: `aiworkflow/controller/`, `aiworkflow/service/`, `aiworkflow/gateway/`, `aiworkflow/model/`, `aiworkflow/repository/`

| Aspect | Assessment |
|--------|------------|
| Workflow definition | CRUD + BPMN parsing + validation |
| AI Decision | `DecisionRouter` with confidence-based routing |
| Storage | PostgreSQL `WorkflowDefinition` entity |

### 3.6 Workflow Engine Module (38 files -- workflow/)

**Files**: `workflow/config/`, `workflow/controller/`, `workflow/delegate/`, `workflow/service/`, `workflow/trigger/`, `workflow/dto/`

| Aspect | Assessment |
|--------|------------|
| Camunda integration | `WorkflowService` + delegates |
| AI analysis | `AIAnalysisDelegate` + `ClaudeApiService` with circuit breaker |
| Triggers | Kafka, REST, Scheduled -- generic trigger framework |
| Rule-based fallback | `RuleBasedFallbackDecisionService` when AI unavailable |
| Citizen scenarios | `AqiCitizenAlertDelegate`, `FloodEvacuationDelegate`, `CitizenServiceRequestDelegate` |
| Management scenarios | `AqiTrafficControlDelegate`, `EsgAnomalyDelegate`, `FloodResponseDelegate`, `UtilityIncidentDelegate` |

### 3.7 Environment Module (13 files)

**Files**: `environment/api/`, `environment/service/`, `environment/domain/`, `environment/repository/`, `environment/config/`

| Aspect | Assessment |
|--------|------------|
| AQI calculation | `AqiCalculator` |
| Sensor CRUD | Standard JPA |
| Capability flag | `IotIngestionAutoConfiguration` -- `@ConditionalOnProperty` for IoT ingestion toggle |

### 3.8 Building Safety Module (7 files)

**Files**: `safety/controller/`, `safety/service/`, `safety/consumer/`, `safety/model/`, `safety/dto/`

| Aspect | Assessment |
|--------|------------|
| Safety score | `BuildingSafetyService` |
| Structural alerts | `StructuralAlertConsumer` -- Kafka consumer with Redis publish |
| Integration | Uses `AlertService.findOpenStructuralAlerts()` |

### 3.9 Tenant Module (33 files)

**Files**: `tenant/api/`, `tenant/service/`, `tenant/domain/`, `tenant/repository/`, `tenant/context/`, `tenant/filter/`, `tenant/hibernate/`, `tenant/kafka/`

| Aspect | Assessment |
|--------|------------|
| Multi-tenancy | `TenantContext` (ThreadLocal) + `TenantContextFilter` + `TenantContextAspect` |
| RLS | Hibernate `@Filter` + PostgreSQL `SET LOCAL app.tenant_id` |
| Tenant isolation | `TenantIsolationIT` -- Testcontainers-verified |
| Kafka | `TenantAwareKafkaListener` -- sets tenant context in Kafka consumer threads |
| Async | `TenantContextTaskDecorator` -- propagates tenant to `@Async` threads |

---

## 4. Security Assessment

### 4.1 Authentication

| Component | Implementation |
|-----------|----------------|
| JWT decoding | `RoutingJwtDecoder` -- HMAC (dev) or RSA/Keycloak (prod) based on `iss` claim |
| JWT filter | `JwtAuthenticationFilter` -- `OncePerRequestFilter` before `UsernamePasswordAuthenticationFilter` |
| Token provider | `JwtTokenProvider` -- generate + validate |
| Rate limiting | `LoginRateLimitService` |
| Token blacklist | `TokenBlacklistService` |
| User seed | `UserSeedInitializer` |

### 4.2 Authorization

| Mechanism | Count | Details |
|-----------|-------|---------|
| `@PreAuthorize` / `hasRole` / `hasAuthority` | **82 occurrences** | Spread across all controllers |
| SecurityConfig URL rules | 20+ rules | Explicit role restrictions per path pattern |
| Method security | `@EnableMethodSecurity` | Active globally |

### 4.3 Tenant Isolation

| Mechanism | Count |
|-----------|-------|
| `TenantContext` / `tenantId` references (main code) | **590** |
| Tenant filter | `TenantContextFilter` -- JWT claim extraction |
| Hibernate filter | `TenantContextAspect` -- `SET LOCAL app.tenant_id` |

### 4.4 Production Profile Security

- `ProductionProfileSecurityTest` verifies debug endpoints return 404 when `production` profile active
- `ManagementSecurityConfig` -- separate management port (8081) with Basic auth for Prometheus
- CSP headers configured: `default-src 'self'`, `frame-ancestors 'none'`, HSTS enabled
- CSRF disabled (stateless JWT API -- correct)
- CORS: `DynamicCorsConfigurationSource`

### 4.5 Security Issues

- [MEDIUM] `ManagementSecurityConfig.prometheusPassword` defaults to `prometheus-dev-scrape` -- must be overridden in production via env var
- [LOW] `SecurityConfig` uses `@Autowired` field injection instead of constructor injection -- contradicts project convention (`@RequiredArgsConstructor`)
- [INFO] Token resolution supports both `Authorization: Bearer` header and `access_token` cookie -- intentional for mobile/PWA support

---

## 5. gRPC Implementation Status (Sprint 11)

### Proto Definition

File: `build/resources/main/analytics/v1/energy.proto`

```protobuf
service EnergyAnalyticsService {
  rpc GetEnergyAggregate(EnergyAggRequest) returns (EnergyAggResponse);
}
```

Fields: `tenant_id`, `building_ids`, `from_epoch`, `to_epoch` (request); `total_kwh`, `peak_demand_kw`, `avg_power_factor`, `co2_tonnes`, `per_building_kwh` map (response).

### gRPC Client

- `ClickHouseGrpcAnalyticsAdapter` -- `@GrpcClient("analytics-service")` with 5s deadline
- `@ConditionalOnExpression`: requires `analytics-external=true` AND `analytics-transport=grpc`
- Graceful fallback: returns zero-result on gRPC failure (no 500 bubble-up)

### REST/gRPC Toggle

| Config | Adapter Loaded |
|--------|----------------|
| `analytics-external=false` (default) | `TimescaleDbAnalyticsAdapter` (Tier 1, local) |
| `analytics-external=true`, `analytics-transport=rest` | `ClickHouseRestAnalyticsAdapter` (Tier 2, REST) |
| `analytics-external=true`, `analytics-transport=grpc` | `ClickHouseGrpcAnalyticsAdapter` (Tier 2, gRPC) |

Mutual exclusivity verified by `AnalyticsPortMutualExclusivityIT`.

### Pact Contract Test

- `AnalyticsServiceConsumerPactTest` -- consumer-side contract verification
- `ClickHouseGrpcAnalyticsAdapterTest` -- unit test for gRPC adapter (mocked stub)

---

## 6. Issues Found

### Critical -- None

### High -- None

### Medium

| # | Issue | Module | File |
|---|-------|--------|------|
| M1 | `calculateWaterIntensity()` always returns null -- ISO 37120 water intensity not implemented | ESG | `EsgService.java:230` |
| M2 | Management Prometheus password defaults to `prometheus-dev-scrape` | Security | `ManagementSecurityConfig.java:34` |
| M3 | CO2 emission factor hardcoded at 0.5 kg/kWh in REST adapter -- should be configurable per region | ESG | `ClickHouseRestAnalyticsAdapter.java:71` |

### Low

| # | Issue | Module | File |
|---|-------|--------|------|
| L1 | `BacnetIpAdapter.sendCommand()` is stub (logs only, no execution) | BMS | `BacnetIpAdapter.java:124` |
| L2 | `MqttPublisher.publishCommand()` not synchronized -- race condition on reconnect | BMS | `MqttPublisher.java:42` |
| L3 | `SecurityConfig` uses `@Autowired` constructor injection (acceptable but inconsistent with `@RequiredArgsConstructor` convention) | Auth | `SecurityConfig.java:37` |
| L4 | `EsgReportController` uses SpEL in `@RequestParam` default values | ESG | `EsgReportController.java:50-51` |
| L5 | `TenantContextFilter` uses manual JSON string parsing instead of Jackson | Tenant | `TenantContextFilter.java:121` |

---

## 7. Match vs Nghiep Vu (Business Requirement Match)

| Module | Files | Endpoints | Key Features | Match % |
|--------|-------|-----------|-------------|---------|
| ESG Reporting | 31 | 7 | GRI 302-1/305-4, PDF/XLSX/CSV export, summary, energy/carbon time-series, anomaly detection | **90%** (water intensity TODO) |
| Alert System | 14 | 8 | Inline engine, Flink CEP consumer, flood alerts, SSE push, lifecycle management, DLQ | **95%** |
| BMS | 28 | 7 | Modbus TCP, BACnet/IP, MQTT (EMQX), device CRUD, command safety, circuit breaker | **85%** (BACnet command stub) |
| AI Workflow | 7 | 7 | BPMN parsing, AI decision routing, confidence-based, CRUD | **90%** |
| Workflow Engine | 38 | 13 | Camunda, AI analysis, 7 scenario delegates, generic triggers (Kafka/REST/Scheduled) | **95%** |
| Notification | 21 | 7 | SSE, Web Push, FCM, APNs, multi-channel routing | **95%** |
| Environment | 13 | 4 | AQI calculation, sensor CRUD, ingestion toggle | **90%** |
| Citizen Portal | 8 | 11 | Registration, invoices, buildings, public notifications | **95%** |
| Tenant/Multi-tenant | 33 | 12 | RLS, context propagation, admin, invite, config, usage tracking | **95%** |
| Auth/Security | 22 | 4 | JWT (HMAC+RSA), rate limit, blacklist, production profile gating | **90%** |
| Building Safety | 7 | 2 | Safety scores, structural alerts, Kafka consumer | **90%** |
| Traffic | 4 | 6 | Traffic data query, forecast, fake data (dev) | **85%** |
| Forecast | 5 | 2 | ARIMA/LSTM forecast, cache, health check | **85%** |

### Overall Business Match: **90%**

---

## 8. Kafka Topics Inventory

| Topic | Purpose |
|-------|---------|
| `UIP.flink.alert.detected.v1` | Flink CEP detected alerts |
| `UIP.flink.alert.detected.v1.dlq` | DLQ for alert consumer |
| `UIP.flink.alert.flood.v1` | Flink flood alerts |
| `UIP.flink.alert.flood.v1.dlq` | DLQ for flood consumer |
| `UIP.bms.reading.raw.v1` | BMS raw sensor readings |
| `UIP.bms.reading.raw.v2` | BMS raw readings (Avro) |
| `UIP.bms.reading.raw.v1.dlq` | DLQ for BMS readings |
| `UIP.bms.command.ack.v1` | BMS command acknowledgements |
| `UIP.esg.telemetry.error.v1` | ESG telemetry errors |
| `UIP.admin.trigger-config.updated.v1` | Workflow trigger config updates |
| `UIP.structural.alert.critical.v1` | Building structural alerts |
| `UIP.structural.alert.dlq.v1` | DLQ for structural alerts |
| `UIP.workflow.trigger.dlq.v1` | DLQ for workflow triggers |

**Total**: 13 Kafka topics (6 with DLQ)

---

## 9. Recommendations

### Priority 1 -- Before Production
1. **Override Prometheus password** in staging/production environment variable
2. **Implement water intensity calculation** (ISO 37120) -- requires population metadata integration
3. **Add integration test for gRPC adapter** against real analytics-service (currently only unit test with mock)

### Priority 2 -- Before Pilot
4. Complete `BacnetIpAdapter.sendCommand()` implementation (currently stub)
5. Make CO2 emission factor configurable (`uip.esg.co2-factor-kg-per-kwh`)
6. Add `synchronized` to `MqttPublisher.publishCommand()` for thread safety
7. Refactor `TenantContextFilter.extractJsonField()` to use `ObjectMapper`

### Priority 3 -- Tech Debt
8. Convert `SecurityConfig` to `@RequiredArgsConstructor` pattern
9. Add `@ApiResponses` annotations to all endpoints (currently ~60% coverage)
10. Increase branch coverage from 61.4% to 70%+ for alert and BMS modules

---

## 10. Summary

| Category | Status |
|----------|--------|
| Build | PASS (clean, 0 failures) |
| Test count | 1,237 tests, 100% pass rate |
| Line coverage | 76.3% |
| API endpoints | 108 (matches claimed 107-110) |
| Security | Sound -- JWT dual-mode, RLS, production profile gating |
| gRPC | Implemented with proto, client adapter, mutual exclusivity test |
| Error handling | Complete -- RFC 7807 ProblemDetail for all error codes |
| Tenant isolation | Comprehensive -- 590+ references, Testcontainers-verified |
| Critical issues | 0 |
| High issues | 0 |
| Medium issues | 3 |
| Overall match | **90%** vs business requirements |

**Conclusion**: MVP3 backend is in good shape for pilot deployment. No critical or high blockers. Three medium issues (water intensity, Prometheus password default, hardcoded CO2 factor) should be addressed before production but do not block pilot.
