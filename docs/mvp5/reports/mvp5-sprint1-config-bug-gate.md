# M5-1 Task T14 — Spring Config Bug-Class Hardening Gate

**Status:** DONE — test gate implemented, PASS on dev machine (Docker available).
**Memo driver:** `feedback_mvp4_config_bugs` (4 Spring config bugs surfaced by full `@SpringBootTest`: 93→6 failures).
**Date:** 2026-06-24

## Golden Rule

> **Every time a new `CacheManager` or `KafkaTemplate` bean is added, the
> `ApplicationContextLoadsIT` test must still pass.** A failure there is a config
> bug class (bean override / missing `@Primary` / non-lazy cache), not a flaky test.

This rule is encoded as a Javadoc comment in
`backend/src/test/java/com/uip/backend/config/ApplicationContextLoadsIT.java` so it
travels with the test itself — a developer reading the failure sees the rule without
having to find this doc.

## What the gate catches

| Config bug class | How the gate surfaces it |
|---|---|
| **Bean override collision** (two same-named `@Bean` methods firing together) | `BeanDefinitionOverrideException` at `contextLoads()` |
| **Missing `@Primary`** (multiple `CacheManager` beans, no primary) | `NoUniqueBeanDefinitionException` at `cacheManagerPrimaryResolves()` |
| **Non-lazy cache** (eager Redis connect at startup) | Context startup failure when Redis is mocked |
| **`@RetryableTopic` / KafkaTemplate wiring** (named KafkaTemplate missing) | `NoSuchBeanDefinitionException` at `kafkaTemplatesResolvable()` |
| **Circular dependency** introduced by new config | Startup failure at `contextLoads()` |

## Test files

Three test classes, all in `backend/src/test/java/com/uip/backend/config/`:

### 1. `ApplicationContextLoadsIT.java` — integrated full-context load
- `@SpringBootTest(webEnvironment = NONE)` + `@ActiveProfiles("test")`
- Postgres via Testcontainers (`postgres:15-alpine`), Redis/Kafka mocked
- `@Testcontainers(disabledWithoutDocker = true)` → CI/dev with Docker runs it;
  environments without Docker skip it gracefully (matches every other backend IT)
- **4 test methods:**
  - `contextLoads()` — full Spring context starts without collision
  - `allCacheManagerBeansResolvable()` — `aiResponseCacheManager` present (no override)
  - `kafkaTemplatesResolvable()` — default JSON `kafkaTemplate` bean present
  - `cacheManagerPrimaryResolves()` — unqualified `CacheManager` injection resolves

### 2. `AiCacheConfigMutualExclusionTest.java` — Docker-free condition guard
- Pure `ApplicationContextRunner` slice — no Docker, runs in default `test` build
- Pins down the mutual-exclusion of the three `aiResponseCacheManager` bean methods
  (`spring.cache.type` ∈ {`redis`, `simple`, `none`, absent})
- 7 test methods across 4 nested classes + 1 bug-class sweep

### 3. `AvroProducerConditionalBeanTest.java` — Docker-free Avro chain guard
- Pure `ApplicationContextRunner` slice — no Docker
- Pins down the `apicurio.registry.enabled` ⇄ `@ConditionalOnBean` chain:
  - Flag absent → `avroKafkaTemplate` + `DualPublishKafkaProducer` both absent
  - Flag `true` → both present
  - Flag `false` → both absent
- 6 test methods across 3 nested classes

## Test profile approach

`application.yml` (main resources) already declares a `test` profile multi-document
section that sets `spring.cache.type=simple`. The `ApplicationContextLoadsIT` activates
this via `@ActiveProfiles("test")` — exercising exactly the code path that triggered
the MVP4 bean-override collision (the `simple` variant of `AiCacheConfig`).

Redis/Kafka/Reactive-Redis beans are mocked (`@MockBean`) using the same proven mock
set as `EsgServiceIT` and `WorkflowStartupTest`. Camunda REST + Redis health
auto-configs are excluded via `spring.autoconfigure.exclude` (matches
`TenantContextFilterConditionalTest`).

## Test result summary (2026-06-24, dev machine, Docker available)

| Test | Tests | Failures | Errors | Skipped | Status |
|---|---|---|---|---|---|
| `AiCacheConfigMutualExclusionTest` (slice, no Docker) | 7 | 0 | 0 | 0 | PASS |
| `AvroProducerConditionalBeanTest` (slice, no Docker) | 6 | 0 | 0 | 0 | PASS |
| `ApplicationContextLoadsIT` (full context, Postgres container) | 4 | 0 | 0 | 0 | PASS |
| **Total T14 gate** | **17** | **0** | **0** | **0** | **PASS** |

Full `./gradlew test` (backend default): 1810 tests, **1 failure unrelated to T14**
(`SensorToAlertLatencyTest.duplicateReadingWithinCooldown_onlyOneAlertPersisted` —
pre-existing Mockito state-leak bug in the performance package; fails in isolation too;
documented in `feedback_it_test_debug_patterns`). The T14 gate contributes 0 failures.

## How this gate catches future config bugs

1. **Developer adds a new `CacheManager` bean** → `contextLoads()` and
   `cacheManagerPrimaryResolves()` will fail if the new bean lacks `@Primary` or
   collides with an existing name.
2. **Developer collapses `AiCacheConfig`'s `@ConditionalOnProperty` conditions** →
   `AiCacheConfigMutualExclusionTest` fails immediately (sub-second, no Docker).
3. **Developer flips `apicurio.registry.enabled` `matchIfMissing` to `true`** →
   `AvroProducerConditionalBeanTest.FlagAbsent` fails — the dual-publish chain would
   load unexpectedly.
4. **Developer introduces a Kafka listener that depends on a named `KafkaTemplate`** →
   `kafkaTemplatesResolvable()` fails at context startup.

The gate is intentionally layered: the two `ApplicationContextRunner` slice tests run
on **every** `./gradlew test` invocation (Docker-free, sub-second), giving fast feedback
on the most common bug class. The full `ApplicationContextLoadsIT` runs when Docker is
available, catching integrated wiring bugs the slices cannot see.

## Blockers / follow-ups

- **None** for T14 itself.
- Pre-existing: `SensorToAlertLatencyTest` flakiness should be tracked separately
  (Mockito `@BeforeEach` re-stub needed — see `feedback_it_test_debug_patterns` rule 1).
