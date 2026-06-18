# SA Code Review — M4-AI-01 Real District Aggregation Flink Job

| Field | Value |
|---|---|
| **Sprint** | MVP4 (post-S6 hardening) |
| **Date** | 2026-06-15 |
| **Reviewer** | Solution Architect |
| **Scope** | Implement real Flink batching job replacing the config-stub previously marked "DEV DONE" |
| **Status** | ✅ APPROVED (Gate G8) |

## Context

M4-AI-01 was previously closed as "DEV DONE" with only `DistrictAggregationConfig` (3 `@ConfigurationProperties` fields, no executable job). Cross-check against `flink-jobs/` confirmed no batching job existed, and `AiInferenceService.analyzeAqiWithMetrics` had **zero callers** in main code — the entire AI cost-optimization stack (batching → routing → caching → budget) was wired but never exercised. G1 (AI cost < $1/day @ 10K sensors) could not pass.

This change delivers a real `DistrictAggregationJob` plus the first backend consumer, so the pipeline runs end-to-end.

## Files added

- `flink-jobs/src/main/java/com/uip/flink/ai/DistrictAggregationJob.java` — job entrypoint
- `flink-jobs/src/main/java/com/uip/flink/ai/DistrictAggregationFunction.java` — AggregateFunction + ProcessWindowFunction.WindowFinalizer
- `flink-jobs/src/main/java/com/uip/flink/ai/DistrictAggregation.java` — DTO
- `flink-jobs/src/main/java/com/uip/flink/ai/DistrictKey.java` — composite key
- `flink-jobs/src/test/java/com/uip/flink/ai/DistrictAggregationJobTest.java` — 11 unit tests
- `backend/src/main/java/com/uip/backend/ai/flink/DistrictAggregationEvent.java` — backend DTO mirror
- `backend/src/main/java/com/uip/backend/ai/flink/DistrictAggregationConsumer.java` — `@RetryableTopic` consumer + DLQ
- `backend/src/test/java/com/uip/backend/ai/flink/DistrictAggregationConsumerTest.java` — 5 unit tests
- `backend/src/test/java/com/uip/backend/ai/AiInferenceServiceBatchTest.java` — 7 unit tests (analyzeBatch + bucket + generic path)

## Files modified

- `backend/src/main/java/com/uip/backend/ai/AiInferenceService.java` — added `analyzeBatch`, `analyzeGenericWithMetrics`, `analyzeGenericConditions`, `callClaudeApiGeneric`, static `bucket()`
- `docs/mvp4/project/task-backend.md` — annotated M4-AI-01 row with real-job note

## SA Review Checklist — Backend (10 items)

| # | Item | Result | Notes |
|---|---|---|---|
| 1 | Unused imports / dead code | ✅ | `extractTupleKey` retained as public helper for future tuple-based callers; flagged in Javadoc |
| 2 | Spring bean registration | ✅ | `DistrictAggregationConsumer` is `@Component`; `@RetryableTopic` reuses existing `kafkaTemplate` bean (same pattern as `CorrelationDlqHandler`) |
| 3 | Null safety | ✅ | `hasDistrictAndValue` guards null meta/district; consumer null-checks event + districtCode before delegating; `analyzeBatch` returns fallback on null |
| 4 | Exception handling | ✅ | Consumer wraps all processing in try/catch, publishes to DLQ (`ai.district.aggregations.dlq`) then re-throws for `@RetryableTopic`; Flink deserializer already swallows malformed bytes (NgsiLdDeserializer:36-41) |
| 5 | JWT claims | N/A | No auth changes |
| 6 | Resource leak | ✅ | KafkaSource/Sink built via builders (Flink manages lifecycle); no streams opened manually |
| 7 | Thread safety | ✅ | `Accumulator` is per-key per-window (Flink serializes, single-threaded per key); `ObjectMapper` reused as static final |
| 8 | Config env vars default | ✅ | All env vars (`AI_DISTRICT_*`, `KAFKA_*`) have `getOrDefault`; backend `@KafkaListener` topic uses `${ai.flink.district.outputTopic:ai.district.aggregations}` |
| 9 | Dependency license | ✅ | Reuses existing Flink 1.19 + Kafka connector deps (Apache 2.0); no new deps |
| 10 | API contract match frontend | N/A | Async pipeline, no REST endpoint |

## Correctness notes

- **Self-injection caveat:** `analyzeGenericConditions` is `@Cacheable` — must be called via the Spring proxy (`analyzeGenericWithMetrics` → `self.analyzeGenericConditions`). Documented inline, matches existing AQI pattern.
- **Window size:** 60 s (not the 300 s default in config) to match the AQI hit-rate math in `AiCacheConfig` Javadoc (poll-every-60s assumption). Configurable via `AI_DISTRICT_WINDOW_SECONDS`.
- **Snapshot cap:** FIFO eviction at `maxSensorsPerDistrict` (default 500) bounds state; `count`/`max`/`avg` remain exact (only the retained snapshot list is capped).
- **CacheManager branches:** AiCacheConfig already covers redis/simple/none — the new `@Cacheable` reuses `ai-responses` cache without adding a bean (no regression to `feedback_mvp4_config_bugs`).

## Test results

| Suite | Tests | Result |
|---|---|---|
| `DistrictAggregationJobTest` (flink-jobs) | 11 | ✅ PASS |
| `DistrictAggregationConsumerTest` (backend) | 5 | ✅ PASS |
| `AiInferenceServiceBatchTest` (backend) | 7 | ✅ PASS |
| **Full backend regression** | **1,726** | ✅ **0 failures, 0 errors, 4 skipped** (was 1,714 — net +12) |

Commands:
```bash
cd flink-jobs && mvn test -Dtest=DistrictAggregationJobTest
cd backend && ./gradlew test --tests "com.uip.backend.ai.flink.DistrictAggregationConsumerTest" --tests "com.uip.backend.ai.AiInferenceServiceBatchTest"
cd backend && ./gradlew test   # full suite: 1,726 tests, 0 failures
```

## Deployment notes

- Flink job deployed via existing shade + Flink REST API flow (same as VibrationAnomalyJob). Env vars `AI_DISTRICT_*` configure window/cap/topics.
- Kafka topics `ai.district.aggregations` + `ai.district.aggregations.dlq` must exist on the broker (consumer uses `autoCreateTopics="false"` — same convention as `CorrelationDlqHandler`).
- **G1 verification still requires staging:** send 10K AQI readings into `ngsi_ld_environment` within 60 s, confirm `ai.district.aggregations` receives ~N_districts records (not 10K), then read `ai-cost-dashboard` Grafana. Code path is now complete; measurement is a staging task (G1 gate).

## Verdict

✅ **APPROVED — Gate G8 PASS.** Real Flink batching job + first AI caller implemented, 23 new tests green, full regression 1,726/0. Ready for DevOps deploy + staging G1 measurement.
