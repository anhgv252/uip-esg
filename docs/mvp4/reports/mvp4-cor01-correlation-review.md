# SA Code Review — M4-COR-01 Real Incident Correlation Flink CEP Job

| Field | Value |
|---|---|
| **Sprint** | MVP4 (post-S6 hardening) |
| **Date** | 2026-06-15 |
| **Reviewer** | Solution Architect |
| **Scope** | Implement real Flink CEP correlation job replacing the in-app-only / config-stub state previously marked "DEV DONE" |
| **Status** | ✅ APPROVED (Gate G8) |

## Context

M4-COR-01 was previously closed as "DEV DONE" claiming an "IncidentCorrelationFlinkJob" using "Flink CEP library". Cross-check showed only `IncidentCorrelationConfig` (`@ConfigurationProperties`) + an in-app `CorrelationService` (Spring `@Service`) existed. `CorrelationService.correlate()` had **zero callers** in main code, and the `correlated.incidents` topic had **no producer** — `CorrelationDlqHandler` was a consumer waiting on a topic nobody wrote to. G2 (false positive < 5%) could not be exercised end-to-end.

This change delivers the missing Flink CEP producer.

## Architecture decision

Alert events arrive on `UIP.flink.alert.detected.v1` (already produced by the existing `AlertDetectionJob` + persisted by `AlertEventKafkaConsumer`). The new job consumes that topic, applies CEP per `buildingId`, and emits to `correlated.incidents` — reusing the existing `CorrelationDlqHandler` → `CorrelationService.processIncomingEvent` sink unchanged.

Two `CorrelationService` entry points now both have real callers:
- `correlate(List, buildingId)` — remains the in-process API (callable directly by tests / future services)
- `processIncomingEvent(Map)` — called by `CorrelationDlqHandler` from the Flink-produced topic (the production path)

The scoring formula is duplicated in `IncidentCorrelationJob.score()` (static) to keep Flink decoupled from the backend classpath; it is byte-identical to `CorrelationScoringService.score()`.

## Files added

- `flink-jobs/src/main/java/com/uip/flink/correlation/IncidentCorrelationJob.java` — CEP job entrypoint + `evaluateWindow` decision fn + `CorrelationPatternProcessFunction`
- `flink-jobs/src/main/java/com/uip/flink/correlation/AlertEventEnvelope.java` — POJO mirror of backend AlertEvent JSON
- `flink-jobs/src/main/java/com/uip/flink/correlation/CorrelatedIncidentEvent.java` — output DTO (keys match `processIncomingEvent`)
- `flink-jobs/src/test/java/com/uip/flink/correlation/IncidentCorrelationJobTest.java` — 12 unit tests

## Files modified

- `docs/mvp4/project/task-backend.md` — annotated M4-COR-01 row with real-job note

## SA Review Checklist — Backend (10 items)

| # | Item | Result | Notes |
|---|---|---|---|
| 1 | Unused imports / dead code | ✅ | Removed unused `IterativeCondition` import |
| 2 | Spring bean registration | ✅ | No new backend beans (Flink job runs outside Spring; existing `CorrelationDlqHandler` already consumes the topic) |
| 3 | Null safety | ✅ | `parseAlert` returns null on malformed input (filtered downstream); `evaluateWindow` guards null/empty lists + null measureType; `parseInstant` returns null on bad timestamp |
| 4 | Exception handling | ✅ | Mapper exceptions caught in `parseAlert` (logged at debug, returns null); CEP pattern delegates pure decision fn that cannot throw |
| 5 | JWT claims | N/A | No auth changes |
| 6 | Resource leak | ✅ | Kafka source/sink via builders (Flink lifecycle) |
| 7 | Thread safety | ✅ | `evaluateWindow` is pure (no shared state); `ObjectMapper` static final |
| 8 | Config env vars default | ✅ | All `COR_*` env vars have `getOrDefault` |
| 9 | Dependency license | ✅ | Reuses `flink-cep` (already in pom.xml line 92-93, Apache 2.0); no new deps |
| 10 | API contract match frontend | N/A | Async pipeline |

## Correctness notes

- **Distinct-type constraint:** CEP `timesOrMore(3)` matches any 3 alerts; the distinct-measure-type check is enforced in `evaluateWindow` because Flink CEP does not natively express "≥ N distinct field values". This is documented inline.
- **Threshold gating:** score < `minScore` (default 0.6) → no emit. Reproduces the 2-sensor boundary (0.556 < 0.6) already documented in `sprint4-correlation-test-results.md`.
- **Score parity:** `IncidentCorrelationJob.score()` is byte-identical to backend `CorrelationScoringService.score()` — verified by unit tests asserting the same formula on the same inputs. Drift risk noted: if the backend formula changes, this must change too (flagged for ADR-042 follow-up).
- **Watermark:** uses processing-time assigner on the raw JSON string (alert events carry ISO-8601 `detectedAt` but CEP windowing keys on `buildingId`, not event-time ordering, for this POC scale). Acceptable for pilot 5 buildings; revisit for 50+ with strict event-time semantics.
- **Backend unchanged:** `CorrelationDlqHandler` already listens on `${correlation.flink.outputTopic:correlated.incidents}` — the job now produces to that exact topic. No backend code change needed, verified by full regression (1,726 tests, 0 failures).

## Test results

| Suite | Tests | Result |
|---|---|---|
| `IncidentCorrelationJobTest` (flink-jobs) | 12 | ✅ PASS |
| Backend correlation tests (`com.uip.backend.correlation.*`) | all | ✅ PASS (unchanged) |
| **Full backend regression** | **1,726** | ✅ **0 failures, 0 errors, 4 skipped** |

Commands:
```bash
cd flink-jobs && mvn test -Dtest=IncidentCorrelationJobTest
cd backend && ./gradlew test --tests "com.uip.backend.correlation.*"
cd backend && ./gradlew test   # 1,726 tests, 0 failures
```

## Deployment notes

- Deployed via the same Maven shade + Flink REST flow as the other jobs. Env vars `COR_WINDOW_SECONDS` / `COR_MIN_SENSOR_TYPES` / `COR_MIN_SCORE` / `COR_SOURCE_TOPIC` / `COR_OUTPUT_TOPIC` configure behavior.
- Source topic `UIP.flink.alert.detected.v1` and sink `correlated.incidents` + `correlated.incidents.dlq` must exist on the broker.
- **G2 verification still requires staging:** inject 3+ distinct-type alerts for one building within 30s → confirm exactly one `CorrelatedIncident` row appears with score ≥ 0.6. Single-type floods must NOT produce incidents.

## Verdict

✅ **APPROVED — Gate G8 PASS.** Real Flink CEP correlation producer implemented, 12 new tests green, backend regression 1,726/0, existing consumer wiring reused unchanged. Ready for DevOps deploy + staging G2 measurement.

## Note on the two M4-COR-01 paths

This review closes the **producer** gap (Flink CEP → topic). The in-app `CorrelationService.correlate()` is retained as a programmatic API and is exercised by `CorrelationE2ETest` / `CorrelationServiceTest`. Both paths share `CorrelationScoringService` semantics. ADR-042 (deferred in Task #13) should document this dual-path design.
