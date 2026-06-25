# Synthetic 50-tenant test harness (R16 mitigation, T12 scaffold)

This is the **synthetic multi-tenant test harness** that mitigates R16
("build-for-50 nhưng test-2-3 → code path scale chưa exercise"). It is the
overlay QA tool that runs across M5-1 → M5-5, growing in scope each sprint.

**Owner:** QA synthetic-test owner (0.5 FTE overlay, M5-1 → M5-5).
**Source of truth for the design:**
[`docs/mvp5/reports/mvp5-sprint1-synthetic-scaffold.md`](../../../docs/mvp5/reports/mvp5-sprint1-synthetic-scaffold.md).

## Layout

```
synthetic/
├── README.md                  ← this file
├── lib/
│   ├── __init__.py
│   ├── generate.py            ← NGSI-LD test-data generator (50×100 sensors)
│   ├── runner.py              ← concurrent tenant-load runner + invariants
│   └── reporting.py           ← JSON + markdown report writers
└── profiles/
    ├── smoke-5-tenant.yaml    ← M5-1 T12 deliverable (this sprint)
    └── full-50-tenant.yaml    ← M5-5 T13 (NOT run in M5-1)
```

## Quick start — 5-tenant smoke (M5-1 T12 deliverable)

```bash
# From the repo root. Requires analytics-service on :8081 with seed admin.
cd infrastructure/scripts/synthetic

python3 -m runner \
    --profile profiles/smoke-5-tenant.yaml \
    --report-json /tmp/synth-smoke.json \
    --report-md   /tmp/synth-smoke.md
```

**Expected:** verdict `PASS`, 0 cross-tenant leaks, 5 tenants each with
`events_sent = sensors_per_tenant × events_per_sensor = 20 × 5 = 100` queries.

If the backend is not up, the runner exits with code 2 (setup error) —
no false negatives.

## Dry-run the generator (no infra needed)

```bash
# Emit 3 tenants × 5 sensors × 1 event to stdout — verify NGSI-LD shape
python3 -m generate --tenants 3 --sensors-per-tenant 5 --events-per-sensor 1 \
    --rate 100 --sink stdout | head -1 | python3 -m json.tool
```

The `_meta.tenantId` field is what `NgsiLdDeserializer` parses into
`NgsiLdMessage.getTenantId()` — Flink's `TenantBindingProcessFunction`
fail-closed drops records where this is null/blank (counter
`uip.tenant.dropped_no_tenant`).

## Invariants enforced

| ID | Invariant | Where checked |
|---|---|---|
| INV-1 | Every tenant's events reach the read layer | runner — `events_sent > 0` per tenant |
| INV-2 | No cross-tenant leak (tenant A never sees tenant B's sensors) | runner — 403/401 expected when probing foreign `tenantId` |
| INV-3 | Null/blank `_meta.tenantId` records dropped fail-closed | M5-2 hook (Prometheus `dropped_no_tenant` counter) |

## How M5-2 / M5-3 / M5-4 / M5-5 extend this

See the design report
[`docs/mvp5/reports/mvp5-sprint1-synthetic-scaffold.md`](../../../docs/mvp5/reports/mvp5-sprint1-synthetic-scaffold.md)
§3 for the full extension map. Short version:

| Sprint | Extension | Profile change |
|---|---|---|
| **M5-2** | CH partition hotspot scan (skew < 20%) + INV-3 Prometheus hook | new profile `m5-2-partition-scan.yaml` |
| **M5-3** | NL routing load (NL→BPMN synthesis across tenants) | new profile `m5-3-nl-routing.yaml` |
| **M5-4** | Billing quota enforcement (metering ledger assertions) | new profile `m5-4-billing-quota.yaml` |
| **M5-5** | FULL 50-tenant run (gate M5-G7) | run `full-50-tenant.yaml` |

## Dependencies

- Python 3.10+ (uses `match`-free dataclasses, `from __future__ import annotations`)
- stdlib only for the default `transport: api` runner path
- `kafka-python` for `transport: kafka` (M5-2+)
- `PyYAML` for profile loading (optional — CLI args work without it)
