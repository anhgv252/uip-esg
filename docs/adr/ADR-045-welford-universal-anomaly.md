# ADR-045: Welford Universal — Adaptive Anomaly Detection

| Field | Value |
|---|---|
| **ADR Number** | ADR-045 |
| **Title** | Welford Universal — Adaptive Anomaly Detection Across All Sensor Types |
| **Status** | Accepted |
| **Date** | 2026-06-12 |
| **Author** | Solution Architect |
| **Sprint** | MVP4-S3/S6 (Task #15 START, Task #25 COMPLETE — M4-AI-07) |
| **Supersedes** | — |
| **Related ADRs** | ADR-041 (AI Cost), ADR-042 (Correlation Engine), ADR-046 (Feedback Loop) |

---

## Context

MVP3 anomaly detection was **hardcoded per sensor type**: AQI used fixed EPA breakpoints, flood used static water-level thresholds, structural used Welford (but only for vibration). This had three problems at MVP4 scale (10K sensors):

1. **Threshold rot.** A fixed "AQI > 100 = alert" threshold is wrong for a district whose baseline AQI is 80 vs one whose baseline is 30. Static thresholds either over-alert (noisy district) or under-alert (clean district).
2. **New sensor types need code.** Adding smoke or CO detection required a new strategy class, new config, new tests — a developer bottleneck at odds with MVP4's operator-self-service goal (Trụ 3).
3. **No cold-start handling.** A freshly deployed sensor has no history. Hardcoded thresholds fire immediately on the first reading; statistical detectors refuse to fire until they have data. The latter is correct but must be made explicit.

MVP3's structural `VibrationAnomalyJob` already used Welford's online algorithm successfully. The question: **extend Welford to every sensor type, or keep per-type thresholds?**

---

## Decision

**Welford's online algorithm is the universal anomaly detector for all sensor types.** Hardcoded thresholds are removed in favour of statistical deviation from a per-sensor running baseline. The detector is **type-agnostic** — it keys on a string, so adding a sensor type needs no code change.

### Algorithm

Welford's online algorithm computes running mean and M2 (sum of squared deviations) in O(1) per update, numerically stable. For each sensor key:

```
n      ← n + 1
delta  ← value − mean
mean   ← mean + delta / n
delta2 ← value − mean
M2     ← M2 + delta × delta2

stdDev ← sqrt(M2 / (n − 1))        // Bessel-corrected
zScore ← |value − mean| / stdDev
anomaly← zScore > sigmaThreshold   // default 3.0σ
```

### Two-phase model

| Phase | Condition | Behaviour |
|---|---|---|
| **Learning** | `count < learningPhaseCount` (default 100) | `isAnomaly()` always returns `false`. Accumulate statistics only. |
| **Detection** | `count ≥ learningPhaseCount` | Flag readings whose z-score exceeds `sigmaThreshold` (default 3.0σ). |

The learning phase is the cold-start strategy. A sensor must observe 100 readings before it produces anomalies — preventing false alerts on deployment day. `isInLearningPhase(sensorKey)` exposes the state for UI surfacing ("sensor calibrating").

### Design choices

| Choice | Rationale |
|---|---|
| **Generic string key** (`"AQI:building-01"`) | New sensor types need no enum change. The `SensorType` enum is informational, not enforced — `update("FOO:bar", 1.0)` works. |
| **100-reading learning phase** | Balances statistical significance against time-to-first-anomaly. At 1 reading/min, ~100 min to calibrate. Configurable via `ai.welford.learning-phase-count`. |
| **3.0σ default threshold** | 3σ = 99.7% confidence band → 0.3% false-positive rate per reading. Tuned down from 2σ (95.4%) during Sprint 3 — 2σ was too noisy at 10K sensors. Configurable via `ai.welford.sigma-threshold`. |
| **ConcurrentHashMap + immutable state records** | Thread-safe under concurrent sensor updates. Each `update()` produces a new immutable `WelfordState` — no shared mutable counters. |
| **`stdDev == 0` guard** | When all readings are identical, stdDev is 0 and z-score is undefined. The detector substitutes `std = 1` so an outlier against a flat baseline still fires. |

### Sensor type coverage (MVP4-S6 complete)

AQI, WATER_LEVEL, NOISE, HUMIDITY, TEMPERATURE, STRUCTURAL, VIBRATION, SMOKE, PRESSURE, CO_LEVEL — 10 types, all via the same detector instance.

---

## Consequences

### Positive

- **Zero-threshold anomaly detection.** A district with baseline AQI 80 only alerts on deviations from 80, not on a fixed 100. Adaptive by construction.
- **New sensor types are free.** Adding "OCCUPANCY" or "CO2_PPM" needs no code — just feed readings under a new key.
- **Numerically stable.** Welford avoids the catastrophic cancellation of naive variance computation — important for sensors with 10⁶+ lifetime readings.
- **Cold-start is explicit.** The learning phase is a first-class state, surfaced to operators, not a silent period of non-detection.

### Negative

- **Statistics lag reality.** A slow baseline drift (e.g. AQI creeping up over weeks) raises the running mean, so the *next* outlier is measured against a drifted baseline — drift can mask itself. Mitigated by `BaselineDriftDetector` (ADR-042/M4-COR-05), which independently tracks 7-day drift and adjusts thresholds upward.
- **3σ misses slow anomalies.** A reading that is 2.5σ from a rising mean is not flagged, even if it represents a real change. This is the cost of a low false-positive rate. Operators can lower `sigma-threshold` per tenant if they prefer sensitivity.
- **State is in-memory.** A backend restart loses running statistics — every sensor re-enters the learning phase. Acceptable for pilot (single instance); MVP5 (HA) needs persistent state or a warm-up rebuild from TimescaleDB history.

### Risks & mitigations

| Risk | Mitigation |
|---|---|
| R5: pilot data insufficient for stable statistics | Learning phase gates detection; 100 readings is a low bar; synthetic seeding available for staging |
| Restart resets state | Documented limitation; warm-up rebuild is an MVP5 item |
| Sigma too loose → missed anomalies | Per-tenant `sigma-threshold` config; ADR-046 feedback loop surfaces systematic misses |

---

## Compliance

- **Thread safety**: `ConcurrentHashMap` + immutable `WelfordState` records (SA checklist item 7).
- **Config externalisation**: `learning-phase-count` and `sigma-threshold` via `@Value` with defaults (item 8).
- **Test coverage**: `WelfordAnomalyDetectorTest` (12) + `WelfordAnomalyDetectorExtendedTest` (cold-start, new sensor types, smoke, vibration, config) — statistical correctness verified.

---

## Open questions (deferred to MVP5)

1. **Persistent state across restarts.** Rebuild Welford state from TimescaleDB on startup? Needs a backfill job + cost analysis.
2. **Per-sensor sigma tuning.** Today `sigma-threshold` is global. Should noisy sensors auto-tune to 4σ while sensitive ones sit at 2.5σ? Feedback loop (ADR-046) could drive this.
3. **Multivariate detection.** Welford is univariate. A fire (temp + smoke + CO rising together) is better caught by joint distribution. Out of scope — correlation engine (ADR-042) handles the multi-sensor case at a higher layer.

---

## References

- `backend/src/main/java/com/uip/backend/ai/anomaly/WelfordAnomalyDetector.java`
- `backend/src/main/java/com/uip/backend/ai/anomaly/WelfordConfig.java`
- `backend/src/main/java/com/uip/backend/ai/anomaly/BaselineDriftDetector.java`
- `backend/src/test/java/com/uip/backend/ai/anomaly/WelfordAnomalyDetector*Test.java`
- `docs/mvp4/README.md` §2 Trụ 1 (M4-AI-07), §9 ADR-045

---

*Authored 2026-06-12 — MVP4 Sprint 6 close-out.*
