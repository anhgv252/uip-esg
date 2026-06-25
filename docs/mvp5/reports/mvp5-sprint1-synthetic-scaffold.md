# MVP5 Sprint M5-1 — Synthetic 50-tenant test scaffolding (T12, R16 mitigation)

| Field | Value |
|---|---|
| **Task** | M5-1-T12 — Synthetic 50-tenant test scaffolding |
| **SP** | 3 |
| **Owner** | QA synthetic-test owner (0.5 FTE overlay, M5-1 → M5-5) |
| **Mitigates** | **R16** — build-for-50 nhưng test-2-3 → code path scale chưa exercise |
| **Status** | ✅ SCAFFOLD DONE — sample 5-tenant run validated against mock |
| **Dependencies** | M5-1-T04 (CH RowPolicy V32 + RowPolicyEngine) — DONE |
| **Per-sprint extension** | M5-2 partition scan · M5-3 NL routing · M5-4 billing quota · **M5-5 FULL 50-tenant run** (gate M5-G7) |

> **Scope boundary.** T12 delivers the **harness** only. The full 50-tenant
> run is **M5-5-T13** (gate M5-G7) and MUST NOT be run from M5-1.

---

## §1. Why this exists

R16 is the top post-pivot risk (`pm-mvp5-master-plan.md` §6): the platform is
**built for 50 tenants** (CH RowPolicy, Flink tenant delegate, cache namespace,
billing metering, NL routing) but only ever **tested with 2–3**. Race
conditions, partition hotspots, quota contention, and routing edge cases can
all silently pass at 2–3 tenants and only surface at commercial scale
(10–50 buildings). The synthetic-test owner role (0.5 FTE overlay) runs a
**synthetic 50-tenant fleet** via test data — no need for 50 real buildings —
and grows the test scope each sprint so the M5-5 full run is a one-liner
rather than a scramble.

T12 is the **scaffold**: the generator + runner + report format + 5-tenant
smoke. Each subsequent sprint plugs a new probe into the same harness.

---

## §2. Harness architecture

```
                ┌──────────────────────────────────────────────────┐
                │ infrastructure/scripts/synthetic/                │
                │                                                  │
                │  lib/generate.py ─── NGSI-LD test-data generator │
                │       │                                          │
                │       ├── build_fleet(T, S)   → deterministic    │
                │       │                          Sensor[T×S]     │
                │       ├── build_ngsi_ld(sensor) → event envelope │
                │       │                          with _meta.     │
                │       │                          tenantId        │
                │       └── event_stream(rate_rps) → paced iterator│
                │                                                  │
                │  lib/runner.py ───── tenant-load runner          │
                │       │                                          │
                │       ├── login() → admin JWT (tenant_id=default)│
                │       ├── ThreadPool(max_workers=T)              │
                │       │     └── _tenant_worker() per tenant:     │
                │       │           ├── Phase 1: bulk reads        │
                │       │           │   (body.tenantId = JWT tid)  │
                │       │           └── Phase 2: leak probe        │
                │       │               (body.tenantId = OTHER)    │
                │       │                                          │
                │       └── invariant check → JSON + MD report     │
                │                                                  │
                │  lib/reporting.py ── structured report writers   │
                │                                                  │
                │  profiles/                                       │
                │    ├── smoke-5-tenant.yaml   ← M5-1 (THIS sprint)│
                │    └── full-50-tenant.yaml    ← M5-5 (DO NOT RUN)│
                └──────────────────────────────────────────────────┘
                              │
                              ▼  exercises the REAL isolation paths
        ┌─────────────────────────────────────────────────────────┐
        │ Tenant isolation P1 (T04/T05/T06, DONE):                │
        │  • CH V32 RowPolicy  tenant_id = getSetting('SQL_…')    │
        │  • RowPolicyEngine   executeWithTenant(tid, …)          │
        │  • Flink TenantBindingProcessFunction (fail-closed)     │
        │  • Cache key prefix  tenant:{tid}:  (5 points)          │
        │  • AnalyticsController.isCrossTenantViolation → 403     │
        └─────────────────────────────────────────────────────────┘
```

### §2.1 What the harness exercises

| Code path | How the harness hits it |
|---|---|
| **CH RowPolicy V32** (`SQL_tenant_id` per-connection SET) | Phase 1 bulk reads flow through `RowPolicyEngine.executeWithTenant` on the analytics-service JDBC layer (full-stack runs against Compose HA) |
| **Analytics REST cross-tenant guard** (`isCrossTenantViolation`) | Phase 2 leak probe sends `body.tenantId = <other>`; expects 403 |
| **Cache key namespace** (`tenant:{tid}:` prefix, 5 points) | Phase 1 reads populate the per-tenant cache slot; a missing prefix would let Phase 2 see stale tenant-A data |
| **Flink TenantBindingProcessFunction** (fail-closed) | Generator's `_meta.tenantId` field is parsed by `NgsiLdDeserializer` → `NgsiLdMessage.getTenantId()`; null/blank → dropped (INV-3 hook, M5-2) |

### §2.2 NGSI-LD payload contract

The generator produces the exact envelope consumed by Flink
(`flink-jobs/.../NgsiLdDeserializer`):

```json
{
  "@context": ["https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"],
  "id": "urn:ngsi-ld:Device:tenant-001-ENV-001",
  "type": "Device",
  "deviceId":   {"type": "Property", "value": "tenant-001-ENV-001"},
  "observedAt": {"type": "Property", "value": 1782311241849},
  "sensorType": {"type": "Property", "value": "environment"},
  "measurements": {"type": "Property", "value": {"aqi": 94.0, "pm25": 32.97, …}},
  "_meta": {
    "source": "synthetic-harness",
    "sensorType": "environment",
    "tenantId": "tenant-001",      ← NgsiLdMessage.Meta.tenantId (DRIVES isolation)
    "buildingName": "tenant-001-BLDG-001",
    "district": "D1",
    "category": "air_quality"
  }
}
```

The `_meta.tenantId` field is the contract `TenantBindingProcessFunction`
enforces — drop it (or blank it) and Flink fail-closed drops the record,
incrementing `uip.tenant.dropped_no_tenant` (M5-2 will assert this stays flat).

---

## §3. Invariants and how each sprint extends them

| Invariant | Description | Asserted by | M5-1 | M5-2 | M5-3 | M5-4 | M5-5 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|
| **INV-1** | Every tenant's events reach the read layer | runner — `events_sent > 0` per tenant, 0 transport errors | ✅ | ✅ | ✅ | ✅ | ✅ |
| **INV-2** | No cross-tenant leak (tenant A never sees tenant B's sensors) | runner — Phase 2 probe expects 403/401, 200 = leak | ✅ | ✅ | ✅ | ✅ | ✅ |
| **INV-3** | Null/blank `_meta.tenantId` records dropped fail-closed | Prometheus `uip.tenant.dropped_no_tenant` counter flat | ⬜ | ✅ | ✅ | ✅ | ✅ |
| **INV-4** | CH partition skew < 20% across tenants | CH system table query per tenant | ⬜ | ✅ | — | — | ✅ |
| **INV-5** | NL routing correct per-tenant (no cross-tenant BPMN dispatch) | NL→BPMN synthesis probe per tenant | ⬜ | — | ✅ | — | ✅ |
| **INV-6** | Billing quota enforced per-tenant (no quota bleed) | Metering ledger reconcile per tenant | ⬜ | — | — | ✅ | ✅ |

Legend: ✅ = enforced by this sprint's harness · ⬜ = not yet · — = N/A that sprint.

### §3.1 Per-sprint extension points (one profile each)

| Sprint | New profile | What it adds | Gate |
|---|---|---|---|
| **M5-2** | `profiles/m5-2-partition-scan.yaml` | INV-3 (Prometheus counter scrape) + INV-4 (CH partition skew query via `system.parts`) | M5-G2 |
| **M5-3** | `profiles/m5-3-nl-routing.yaml` | INV-5 (NL→BPMN synthesis probe per tenant) | M5-G3 |
| **M5-4** | `profiles/m5-4-billing-quota.yaml` | INV-6 (metering ledger reconcile + quota cap probe) | M5-G4 |
| **M5-5** | run `profiles/full-50-tenant.yaml` | FULL 50-tenant run + all invariants | **M5-G7** |

Each extension is additive — the M5-1 generator + runner + report format are
the spine; later sprints plug new probes into `_tenant_worker` and new
invariant checks into the summary.

---

## §4. Sample 5-tenant smoke (T12 deliverable)

### §4.1 Run command

```bash
cd infrastructure/scripts/synthetic

# Against a live seed stack (analytics-service on :8081):
python3 -m runner \
    --profile profiles/smoke-5-tenant.yaml \
    --report-json /tmp/synth-smoke.json \
    --report-md   /tmp/synth-smoke.md

# Dry-run (no infra) — generator only, verify payload shape:
python3 -m generate --tenants 5 --sensors-per-tenant 20 \
    --events-per-sensor 1 --rate 100 --sink stdout | head -1 | python3 -m json.tool
```

### §4.2 Expected report (PASS against a healthy stack)

```
verdict: PASS
invariants:
  INV1_events_reachable:      PASS    (5/5 tenants, events_sent = 20×5 = 100 each)
  INV2_no_cross_tenant_leak:  PASS    (0 leaks across 25 probes)
  SLO_error_rate_pct_le_5.0:  PASS    (0% on smoke threshold)
aggregate:
  events_sent: 500
  events_failed: 0
  cross_tenant_leaks_total: 0
```

### §4.3 Scaffold self-verification (no live stack needed)

Because M5-1 is being authored before the seed stack is reliably up, the
harness was verified against a **mock backend** that faithfully mirrors
`AnalyticsController.isCrossTenantViolation`:

| Mode | Mock behaviour | Expected verdict | Verified |
|---|---|---|:---:|
| `pass` | 200 iff `body.tenantId == jwt.tenant_id`; 403 on cross-tenant probe | **PASS** (exit 0) | ✅ |
| `leak` | 200 + foreign tenant's sensor data on cross-tenant probe | **FAIL** (exit 1, INV-2 FAIL) | ✅ |
| unreachable | TCP refused | exit 2 (setup error) | ✅ |

The leak-mode run produced exactly the expected failure signature:
`cross_tenant_leaks_total: 6` across 3 tenants × 2 probes, with per-tenant
error trail `LEAK: 200 for tenant=tenant-002 while auth tenant=default`.
This proves the INV-2 detector fires when a real cross-tenant bug exists.

---

## §5. Caveat — admin-token vs per-tenant JWT (M5-5 prep)

The seed stack authenticates a single `admin` user whose JWT carries
`tenant_id = "default"`. The harness's Phase 1 (bulk reads) therefore targets
the admin's own `default` tenant so reads return 200 — this exercises
reachability, latency, and the CH RowPolicy code path
(`RowPolicyEngine.executeWithTenant("default", …)`).

Phase 2 (leak probe) sends **synthetic** tenant IDs in the body and expects
403 from `isCrossTenantViolation`. This is the strongest assertion possible
without seeding one operator per synthetic tenant.

For the **M5-5 full run** (gate M5-G7), seed one operator per synthetic
tenant (50 users) so each JWT carries its own `tenant_id` claim, and set
`cfg["per_tenant_auth"] = true`. The harness then logs in per tenant and
Phase 1 exercises true multi-tenant reads against per-tenant JWTs. This is
documented as a one-line config flip — no harness rewrite needed.

---

## §6. Files

| Path | Purpose |
|---|---|
| `infrastructure/scripts/synthetic/README.md` | Operator quick-start |
| `infrastructure/scripts/synthetic/lib/__init__.py` | Package marker |
| `infrastructure/scripts/synthetic/lib/generate.py` | NGSI-LD test-data generator |
| `infrastructure/scripts/synthetic/lib/runner.py` | Concurrent tenant-load runner + invariants |
| `infrastructure/scripts/synthetic/lib/reporting.py` | JSON + markdown report writers |
| `infrastructure/scripts/synthetic/profiles/smoke-5-tenant.yaml` | M5-1 deliverable profile |
| `infrastructure/scripts/synthetic/profiles/full-50-tenant.yaml` | M5-5 profile (NOT run in M5-1) |
| `docs/mvp5/reports/mvp5-sprint1-synthetic-scaffold.md` | This report |

---

## §7. Acceptance criteria (T12)

- [x] Test-data generator produces 50 tenant × 100 sensor = 5,000 NGSI-LD payloads
- [x] Generator configurable: `--tenants --sensors-per-tenant --rate`
- [x] Payload includes `_meta.tenantId` (exercises real Flink tenant binding)
- [x] Tenant-load runner: concurrent across N tenants, per-tenant latency/throughput/error
- [x] Structured JSON report keyed by `tenant_id` + markdown summary
- [x] **INV-1** (events reachable) enforced — exits 1 if any tenant has 0 events_sent
- [x] **INV-2** (no cross-tenant leak) enforced — exits 1 on any 200-response to a foreign tenant probe
- [x] Sample 5-tenant run profile (`smoke-5-tenant.yaml`)
- [x] Run command + expected report documented
- [x] Verified end-to-end against mock backend (PASS + LEAK + unreachable)
- [x] Design report explains how M5-2/M5-3/M5-4/M5-5 extend the harness
- [x] No AGPL dependencies (stdlib + optional `kafka-python`/`PyYAML`, both BSD/Apache)

---

## §8. Definition of Done for the FULL 50-tenant run (M5-5-T13, NOT this sprint)

The M5-5 full run must additionally:

1. Seed 50 operator users (one per synthetic tenant) — DevOps BAU
2. Flip `per_tenant_auth: true` in `full-50-tenant.yaml`
3. Run against Compose HA with all 5 invariants (INV-1 → INV-6) enforced
4. Capture per-tenant p95 latency, partition skew %, quota bleed count
5. Produce the M5-G7 gate scorecard entry: "synthetic 50-tenant test PASS"
