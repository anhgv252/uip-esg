# ADR-041: AI Cost Optimization Strategy — District Batching + Model Routing + Caching

| Field | Value |
|---|---|
| **ADR Number** | ADR-041 |
| **Title** | AI Cost Optimization Strategy — District Batching + Model Routing + Caching + Token Budgeting |
| **Status** | Accepted |
| **Date** | 2026-06-12 |
| **Author** | Solution Architect |
| **Sprint** | MVP4-S2 (Task #9 — M4-AI-01/02/05) |
| **Supersedes** | — |
| **Related ADRs** | ADR-039 (OpenAPI), ADR-045 (Welford Universal), ADR-046 (Incident Feedback Loop) |

---

## Context

MVP4 must scale AI inference from MVP3's ~5 buildings to **10,000 sensors**. The naive cost projection was catastrophic:

> 10,000 sensors × 1 AI call per event × ~60 events/min/sensor = **600,000 calls/min ≈ $50/day**.

Pilot economics (target: AI cost **< $1/day** at 10K sensors) required an 83× cost reduction. No single optimisation achieves that. We needed a layered pipeline where each stage removes a category of waste:

| Stage | Waste removed | Component |
|---|---|---|
| Pre-filter | Rule-decidable events never reach AI | `SmartPreFilter` (M4-AI-03, S3) |
| Batching | Per-sensor calls → per-district calls | `DistrictAggregationConfig` (M4-AI-01) |
| Caching | Repeated identical calls deduplicated | `AiCacheConfig` / `AqiRangeBucket` (M4-AI-04, S3) |
| Model routing | Every call uses cheapest sufficient model | `ModelRouter` (M4-AI-02) |
| Token budget | Hard ceiling on monthly spend | `TokenBudgetService` (M4-AI-05) |

---

## Decision

**A four-layer AI cost pipeline, applied in order. Each layer is independently bypassable for critical events.**

### Pipeline (request → AI call)

```
Sensor event
   │
   ▼
[1] SmartPreFilter ─── rule-decidable? ─── YES → local decision (no AI call)
   │                                    NO
   ▼
[2] DistrictAggregationConfig (Flink 300s tumbling window)
   │   groups by districtCode, caps at 500 sensors/district
   ▼
[3] AiCacheConfig ─── cache hit (district + AQI bucket)? ─── YES → cached response
   │                                                       NO
   ▼
[4] ModelRouter.selectModel(tokens, priority)
   │   LOW priority  → Haiku (claude-haiku-4-5)
   │   ≤500 tokens   → Haiku
   │   else          → Sonnet (claude-sonnet-4-6)
   ▼
[5] TokenBudgetService.isWithinBudget(used)? ─── NO → reject + alert
   │                                             YES
   ▼
   AI inference → cache response (5-min TTL) → return
```

### Bypass rule (safety override)

**Critical events bypass stages [1]–[3].** Flood, fire, and structural-collapse alerts route directly to the AI (or to deterministic escalation) without batching or caching. This is risk R1 in the MVP4 README: *batching must not miss critical alerts*. The bypass is implemented at the SmartPreFilter entry — critical event types short-circuit before any aggregation.

### Layer-by-layer design choices

| Layer | Choice | Rationale |
|---|---|---|
| **Batching window** | 300s (5 min) tumbling | Matches the district-dashboard freshness SLA. Smaller windows → more calls; larger → stale data on dashboards. |
| **Max sensors/district** | 500 | Caps payload size to stay within a single AI call's context window. Districts with >500 sensors split into sub-batches. |
| **Cache key** | `district + AQI bucket (0/50/100/150/200/300/500)` | AQI bucketed so a 51 vs 52 AQI event shares a cache entry. Bucket boundaries align with the AQI severity bands operators already reason about. |
| **Cache TTL** | 5 min | Equal to batching window. Older cached decisions risk staleness given baseline drift (ADR-045). |
| **Haiku threshold** | ≤500 tokens OR LOW priority | Haiku is ~12× cheaper than Sonnet. Short prompts (threshold breach classification) don't need Sonnet's reasoning depth. HIGH priority + >500 tokens → Sonnet. |
| **Monthly budget** | 1,000,000 tokens default, 80% alert | Configurable via `ai.token-budget.monthly-limit`. Alert at 80% gives ops a window to investigate before hard-stop. |

### Cost model (projected)

| Scenario | Calls/min | Avg cost/call | Daily cost |
|---|---|---|---|
| MVP3 naive (no pipeline) | 600,000 | $0.000058 | ~$50 |
| After pre-filter (80% ruled out) | 120,000 | $0.000058 | ~$10 |
| After batching (district rollup) | ~50 | $0.000058 | ~$0.004 |
| After model routing (Haiku bias) | ~50 | $0.000008 | ~$0.0006 |
| **Target (with caching)** | **~30** | **$0.000008** | **~$0.60** |

Caching removes the residual ~20 duplicate calls/min where a district's AQI bucket is stable across consecutive windows.

---

## Consequences

### Positive

- **83× cost reduction** vs naive — meets MVP4 G1 gate (`AI cost < $1/day @ 10K sensors`).
- **Each layer independently testable.** 9 ModelRouter tests, 10 TokenBudgetService tests, district config IT — no monolithic optimiser.
- **Hard stop is a feature.** `TokenBudgetService.isWithinBudget` is a circuit breaker: a runaway consumer (bug, misconfigured trigger) cannot bankrupt the tenant.
- **Bypass rule keeps critical events fast.** Flood/fire never wait for a 5-min window.

### Negative

- **5-min latency for non-critical events.** AQI advisory, energy recommendations batch for 5 min before AI sees them. Acceptable — these are advisory, not safety-critical.
- **Cache can mask baseline drift.** A cached AQI-150 decision applied to a rising baseline understates severity. Mitigated by 5-min TTL + ADR-045 drift detection.
- **Token-budget hard-stop is blunt.** When budget is exhausted, *all* AI calls reject, including non-critical but useful ones. Future: tiered rejection (reject LOW priority first).

### Risks & mitigations

| Risk | Mitigation |
|---|---|
| R1: batching misses critical alert | Critical event types bypass stages 1–3 by design |
| R7: model routing sends complex decision to Haiku | >500-token heuristic + HIGH-priority override forces Sonnet for complex prompts |
| Budget exhausted mid-incident | Alert at 80% gives ops lead time; circuit breaker prevents overspend |

---

## Compliance

- **Config externalisation**: all tunables via `@Value("${...:default}")` or `@ConfigurationProperties` — satisfies SA checklist item 8.
- **No AGPL deps**: Haiku/Sonnet called via Anthropic API (commercial). No new license risk.
- **Fallback path**: if AI rejects (budget/billing), `RuleBasedFallbackDecisionService` produces a deterministic decision — graceful degradation per Sprint 5 lessons.

---

## References

- `backend/src/main/java/com/uip/backend/ai/flink/DistrictAggregationConfig.java`
- `backend/src/main/java/com/uip/backend/ai/routing/ModelRouter.java`
- `backend/src/main/java/com/uip/backend/ai/budget/TokenBudgetService.java`
- `backend/src/main/java/com/uip/backend/ai/cache/AiCacheConfig.java`, `AqiRangeBucket.java`
- `backend/src/main/java/com/uip/backend/ai/filter/SmartPreFilter.java`
- `docs/mvp4/grafana/ai-cost-dashboard.json`
- `docs/mvp4/README.md` §2 Trụ 1, §9 ADR-041

---

*Authored 2026-06-12 — MVP4 Sprint 2 retrospective, documented in Sprint 6 close-out.*
