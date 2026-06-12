# ADR-042: Incident Correlation Engine — Flink CEP Multi-Device Pattern

| Field | Value |
|---|---|
| **ADR Number** | ADR-042 |
| **Title** | Incident Correlation Engine — Flink CEP Multi-Device Pattern |
| **Status** | Accepted |
| **Date** | 2026-06-12 |
| **Author** | Solution Architect |
| **Sprint** | MVP4-S3/S4 (Task #13/#17 — M4-COR-01/02/05) |
| **Supersedes** | — |
| **Related ADRs** | ADR-041 (AI Cost), ADR-045 (Welford), ADR-046 (Feedback Loop) |

---

## Context

MVP3 raised alerts **per sensor**. At 10K sensors this produces **alert fatigue**: a single HVAC failure that trips temperature, smoke, and pressure sensors in one building yields three separate alerts within 30 seconds. Operators see three incidents; there is really one. The measured false-positive impact of per-sensor alerting was ~20%.

The MVP4 Trụ 2 goal: **correlate N concurrent sensor events in one building into a single incident** so that (a) operators see one actionable signal and (b) the AI receives unified context (not three fragmented prompts). Target: false-positive **< 5%** on 30-day pilot data (G2 gate).

Three constraints shaped the design:

1. **Latency.** Correlated incidents must surface within **60 s** (MVP4 SLA). A 30 s CEP window + ≤30 s downstream pipeline fits.
2. **Module boundary.** The `ai` and `safety` modules may not JOIN each other's tables. Correlation must work off the Kafka event stream, not cross-schema SQL.
3. **Reuse.** MVP3 already shipped `VibrationAnomalyJob` (a Flink CEP job for structural sensors). The correlation job reuses that pattern rather than inventing a new streaming primitive.

---

## Decision

**A Flink CEP job (`IncidentCorrelationFlinkJob`) with a 30 s per-building tumbling window, fed by a Spring-side `CorrelationService` that scores and merges correlated sensor events into a unified incident.** The job detects candidates; the service decides correlation.

### Architecture

```
Kafka: sensor.events
   │
   ▼
[Flink CEP: IncidentCorrelationFlinkJob]
   │  Pattern: 3+ distinct sensor_type within 30s, same buildingId
   │  Keyed by buildingId
   ▼
Kafka: correlation.candidates
   │
   ▼
[Backend: CorrelationService.correlate()]
   │  1. Score each candidate via CorrelationScoringService
   │     - score = f(sensor_type_diversity, temporal_overlap, value_severity)
   │     - score ≥ 0.6 → correlated incident
   │  2. Merge N events → CorrelatedPayload (CorrelatedPayloadBuilder)
   │     {buildingId, sensors:[{type,value,ts}], correlationScore}
   │  3. Publish unified incident → 1 alert (not N)
   │  4. Failed correlations → DLQ (Micrometer: correlation_dlq_total)
   ▼
Alert pipeline (single incident) → AI (unified payload) → operator
```

### Key design choices

| Choice | Rationale |
|---|---|
| **3+ sensor types, not 3+ sensors** | Two temperature sensors firing is one faulty reading, not an incident. Three *different* types (temp + smoke + pressure) is a real event. This single rule is the largest false-positive reducer. |
| **30 s window** | Matches the 60 s end-to-end SLA with 30 s headroom for scoring + AI. Shorter windows miss slow-building events; longer delays the alert. |
| **Keyed by `buildingId`** | Correlation is a building-level phenomenon. District-level correlation (MVP5) would merge unrelated buildings. |
| **Score ≥ 0.6 threshold** | Empirically validated in Sprint 4 UAT (`sprint4-correlation-test-results.md`): 2-sensor events score 0.556 (< 0.6 → no false correlation); 3+ sensor events score ≥ 0.72. The boundary is the single most-tuned constant in the engine. |
| **DLQ + Micrometer, not silent drop** | Failed correlations go to a DLQ topic + `correlation_dlq_total` counter. Sprint 5 lesson: never silently drop streaming events — ops needs a metric to alert on. |
| **CorrelatedPayloadBuilder separates merge from score** | Scoring is testable in isolation (9 unit tests). Payload building is testable in isolation (9 unit tests). One service per responsibility. |

### Baseline drift (M4-COR-05)

Adjacent to the CEP job, `BaselineDriftDetector` tracks a 7-day rolling AQI baseline per district. When the baseline rises >10%, it auto-adjusts anomaly thresholds upward (feeds ADR-045). This prevents drift from inflating correlation scores over time — a rising baseline would otherwise make every event look severe.

---

## Consequences

### Positive

- **N → 1 alert reduction.** Sprint 4 E2E (`CorrelationE2ETest`, 8 tests): 3+ sensor events produce exactly 1 incident; single/2-sensor events produce 0 incidents (no false correlation).
- **Unified AI context.** The AI sees `{buildingId, sensors:[…], correlationScore}` — one rich prompt instead of N thin ones. Also feeds ADR-041 batching (one call, not N).
- **False-positive boundary is explicit.** The 0.6 threshold is a single tunable, validated against pilot-style data, not buried in business logic.
- **Production-hardened.** DLQ + Micrometer metrics + Flink checkpoint config — all present (Sprint 4 close-out).

### Negative

- **30 s minimum latency.** Even an obvious 5-sensor incident waits for the window to close. Acceptable per SLA; unacceptable if a future customer wants sub-second.
- **Building-scoped only.** Cross-building correlation (e.g. a district-wide power event) is out of scope. MVP5.
- **CEP pattern rigidity.** "3+ distinct sensor types" is hardcoded. A "2 types but very high severity" pattern needs a code change. Mitigated by score-based merge — the CEP is a candidate generator, the service is the decider.

### Risks & mitigations

| Risk | Mitigation |
|---|---|
| R4: complexity > estimate | Reused VibrationAnomalyJob CEP pattern (Sprint 7); POC Sprint 3, full Sprint 4 — staged |
| Pattern misses novel incidents | Operator feedback (ADR-046) surfaces missed correlations; pattern tunable per trigger type |
| Flink job restart loses in-flight windows | Checkpoint config (Sprint 4) + idempotent merge downstream |

---

## Compliance

- **Module boundary**: job consumes Kafka only; `CorrelationService` lives in `ai` module, publishes to alert pipeline via topic — no cross-schema JOIN.
- **SA checklist**: Micrometer metrics, DLQ, null-safe scoring, `@Transactional` on persist path.
- **E2E verified**: `CorrelationE2ETest` 8 PASS, false-positive boundary documented in `sprint4-correlation-test-results.md`.

---

## References

- `backend/src/main/java/com/uip/backend/ai/` — correlation, payload, drift components
- `flink-jobs/src/main/java/com/uip/flink/` — CEP job source
- `docs/mvp4/uat/sprint4-correlation-test-results.md`
- `docs/mvp4/README.md` §2 Trụ 2, §9 ADR-042

---

*Authored 2026-06-12 — MVP4 Sprint 4 retrospective, documented in Sprint 6 close-out.*
