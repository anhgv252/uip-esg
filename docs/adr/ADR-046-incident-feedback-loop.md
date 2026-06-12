# ADR-046: Incident Feedback Loop — Self-Improving AI Decisions

| Field | Value |
|---|---|
| **ADR Number** | ADR-046 |
| **Title** | Incident Feedback Loop — Self-Improving AI Decisions |
| **Status** | Accepted |
| **Date** | 2026-06-12 |
| **Author** | Solution Architect |
| **Sprint** | MVP4-S6 (Task #25 — M4-COR-07) |
| **Supersedes** | — |
| **Related ADRs** | ADR-041 (AI Cost Optimization), ADR-042 (Correlation Engine), ADR-043 (BMS Safety Protocol), ADR-045 (Welford Universal) |

---

## Context

MVP4 targets a **false-positive rate < 5%** (down from ~20% at MVP3). Static thresholds cannot reach that target alone: sensor baselines drift, new urban patterns emerge, and AI model behaviour shifts. A closed feedback loop — where every operator decision becomes training signal — is required to keep accuracy high over the 30-day pilot and beyond.

Three data points motivated this design:

1. **Feedback already captured.** MVP3/Sprint 3 added `POST /api/v1/alerts/{id}/feedback` (`AlertFeedbackController`) and a `feedback_correct` column on `alert_events`. Operators answer "Was this AI decision correct?" on every alert detail page. Until MVP4-S6 this data was **write-only** — nothing consumed it.

2. **BMS closed-loop exists.** MVP4-S5 (Task #21) implemented the BMS command feedback state machine (`COMMAND_SENT → COMMAND_ACKNOWLEDGED → ACTION_TAKEN → FEEDBACK_VERIFIED` in `BmsFeedbackService`). The BMS loop confirms *actuation* success; it does not confirm *decision* quality. We needed a parallel loop for decision quality.

3. **Trigger rules are static.** `alert_rules` thresholds are hand-tuned by developers. With 50+ buildings and per-district AQI baselines, manual tuning does not scale. The loop must surface **concrete, threshold-level suggestions** to an operator, not raw analytics.

---

## Decision

**Implement a 4-stage feedback loop: capture → aggregate → analyze → suggest.** The loop runs on demand (admin-triggered via REST) and is intentionally **advisory** — it never auto-applies changes. A human operator reviews every suggestion.

### Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  STAGE 1 — CAPTURE (MVP3, already shipped)                          │
│  Operator clicks "Was this correct?" on AlertDetail                 │
│  POST /api/v1/alerts/{id}/feedback {correct, comment}               │
│  → alert_events.feedback_correct = true/false                        │
└──────────────────────────────────┬───────────────────────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│  STAGE 2 — AGGREGATE  (IncidentFeedbackAggregator)                  │
│  collectRecentFeedback(): SELECT * FROM alert_events                │
│    WHERE feedback_correct IS NOT NULL                               │
│    AND detected_at >= now() - 30 days                               │
│  Module-boundary-safe: inline JPA Specification, no cross-schema JOIN│
└──────────────────────────────────┬───────────────────────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│  STAGE 3 — ANALYZE  (FeedbackPatternAnalyzer)                       │
│  Group by triggerKey = MODULE:MEASURE_TYPE                          │
│  Per group: accuracyRate = correct / total                          │
│  Flag HIGH_FP when accuracyRate < 0.70                               │
└──────────────────────────────────┬───────────────────────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│  STAGE 4 — SUGGEST  (TriggerSuggestionGenerator)                    │
│  For each HIGH_FP trigger:                                          │
│    look up current AlertRule.threshold                              │
│    suggestedThreshold = current × (1 + fpRate × 0.20)  // ≤ +20%    │
│  Pad to ≥3 with cooldown-reduction / general-review suggestions     │
│  → GET /api/v1/ai/trigger-suggestions  (ADMIN role only)            │
└──────────────────────────────────────────────────────────────────────┘
```

### Key design choices

| Choice | Rationale |
|---|---|
| **30-day rolling window** | Balances recency (urban patterns shift) against sample size. `<100` records → loop returns empty (statistical noise). |
| **Trigger key = `MODULE:MEASURE_TYPE`** | Granular enough to act on (one threshold per key), coarse enough to reach 100+ samples within 30 days. |
| **≥3 suggestions always** | Operators lose trust in a tool that says "nothing to report". Padding with cooldown/review suggestions keeps the channel warm even on healthy triggers. |
| **Confidence tiers (0.55 / 0.70 / 0.85)** | Sample-size-driven. A 200-record suggestion gets 0.85; a 100-record one gets 0.70. Surfaces statistical uncertainty to the reviewer. |
| **Advisory, not autonomous** | Hard rule (R7, MVP4 README): AI suggests, operator approves. Auto-applying threshold changes would violate the operator-trust principle and BR-010 safety constraints. |
| **ADMIN-only endpoint** | Trigger tuning affects every building under a tenant. Restricted via `@PreAuthorize("hasRole('ADMIN')")`. |

### Minimum-data guard

`TriggerSuggestionGenerator.generate()` returns `List.of()` when fewer than `MIN_FEEDBACK_RECORDS = 100` records exist. This prevents the loop from acting on statistical noise during cold-start (first weeks of pilot). The pilot must accumulate 30 days of feedback before the loop produces output.

---

## Consequences

### Positive

- **False-positive reduction is measurable.** The same `feedback_correct` data that feeds the loop also feeds the G2 gate metric (`false positive < 5%`). One source of truth.
- **No new infrastructure.** Reuses `alert_events` (PostgreSQL), no new topic, no new service. Cost ≈ 0.
- **Operator trust builds over time.** Because suggestions are advisory and explainable (`reason` field cites the FP rate and sample size), operators learn *why* a threshold changes.
- **A/B-testable.** Suggestions carry `confidence`; an operator can apply a high-confidence suggestion to one district first, observe, then roll out.

### Negative

- **30-day cold-start.** No suggestions during pilot weeks 1–4. Mitigation: synthetic feedback seeding for staging demos; expectation set with PO.
- **Pad-to-3 can feel noisy.** When all triggers are healthy, the loop still emits general-review suggestions. Acceptable trade-off vs. a silent channel; the `reason` text distinguishes real suggestions from padding.
- **Single-dimension tuning.** The loop adjusts `threshold` only — not cooldown, not severity mapping. Cooldown suggestions are advisory prose, not actionable fields. Future work (MVP5): multi-dimension suggestions.

### Risks & mitigations

| Risk | Mitigation |
|---|---|
| Operator ignores suggestions → loop has no effect | R7 (MVP4 README): gamification + default prompt in alert detail. Track suggestion-apply rate as a KPI in MVP4 gate review. |
| Bias: only operators who care click feedback → skewed sample | Documented limitation. MVP5 can add random sampling prompts to reduce selection bias. |
| Large tenant overwhelms aggregator (10K+ feedback rows) | Aggregator is `@Transactional(readOnly=true)` + Specification; pagination available if needed. 30-day window bounds the scan. |

---

## Compliance

- **SA Review Checklist**: passes all 10 backend items (bean registration via `@Component`/`@RestController`, null-safe via `Optional` in generator, exception-handling consistent, no resource leaks, `@PreAuthorize` on endpoint).
- **Module boundary**: `IncidentFeedbackAggregator` queries `alert_events` via `AlertEventRepository` only — no cross-module JOIN, no direct schema access from the `ai` module.
- **Security**: endpoint is ADMIN-gated; feedback rows are tenant-scoped by the existing `TenantContextFilter`.

---

## Open questions (deferred to MVP5)

1. **Auto-apply high-confidence suggestions?** After 90 days of pilot data, should `confidence ≥ 0.90` suggestions auto-apply with a rollback window? Deferred — requires a separate safety ADR.
2. **NL→BPMN integration.** MVP5 may let an operator type "reduce flood alerts in District 7" and have the loop pre-fill the wizard. Out of scope here.
3. **Multi-dimension suggestions.** Today only `threshold` is tuned. Cooldown, severity, and routing are manual.

---

## References

- `backend/src/main/java/com/uip/backend/ai/feedback/` — all four components
- `docs/mvp4/README.md` §2 (Trụ 2: Correlation Engine), §9 (ADR-046)
- `docs/mvp4/project/task-backend.md` Task #25 — M4-COR-07
- `docs/mvp4/project/task-qa.md` Task #26 — G2 gate (false-positive < 5%)

---

*Authored 2026-06-12 — MVP4 Sprint 6 close-out.*
