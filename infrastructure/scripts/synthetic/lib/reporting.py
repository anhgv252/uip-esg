"""Structured report writers for the synthetic 50-tenant harness."""
from __future__ import annotations

import json
import os
from typing import Any


def write_json(summary: dict[str, Any], path: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w") as f:
        json.dump(summary, f, indent=2, sort_keys=False)


def write_markdown(summary: dict[str, Any], path: str) -> None:
    """Render a per-tenant results table + invariant verdict as markdown."""
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    cfg = summary.get("config", {})
    inv = summary.get("invariants", {})
    agg = summary.get("aggregate", {})
    lines: list[str] = []
    lines.append("# Synthetic tenant-load report")
    lines.append("")
    lines.append(f"- **Run UTC:** `{summary.get('run_utc')}`")
    lines.append(f"- **Verdict:** **{summary.get('verdict')}**")
    lines.append(f"- **Elapsed:** {summary.get('elapsed_sec')} s")
    lines.append(f"- **Tenants:** {summary.get('tenants_total')}  ·  "
                 f"sensors/tenant: {cfg.get('sensors_per_tenant')}  ·  "
                 f"events/sensor: {cfg.get('events_per_sensor')}")
    lines.append(f"- **Base URL:** `{cfg.get('base_url')}`")
    lines.append("")
    lines.append("## Invariants")
    lines.append("")
    lines.append("| Invariant | Result |")
    lines.append("|---|---|")
    for k, v in inv.items():
        badge = "PASS" if v == "PASS" else "FAIL"
        lines.append(f"| `{k}` | **{badge}** |")
    lines.append("")
    lines.append("## Aggregate")
    lines.append("")
    lines.append(f"- Events sent: **{agg.get('events_sent', 0)}**")
    lines.append(f"- Events failed: **{agg.get('events_failed', 0)}**")
    lines.append(f"- Error rate: **{agg.get('error_rate_pct', 0)}%**")
    lines.append(f"- Cross-tenant leaks: **{agg.get('cross_tenant_leaks_total', 0)}**")
    unreach = agg.get("tenants_unreachable") or []
    lines.append(f"- Tenants unreachable: **{len(unreach)}** "
                 f"({', '.join(unreach) if unreach else 'none'})")
    lines.append("")
    lines.append("## Per-tenant metrics")
    lines.append("")
    lines.append("| Tenant | Sensors | Sent | OK | Fail | Err% | p50 ms | p95 ms | p99 ms | Leaks |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for t in summary.get("per_tenant", []):
        lat = t.get("latency_ms", {})
        lines.append(
            f"| `{t['tenant_id']}` | {t['sensors_owned']} | "
            f"{t['events_sent']} | {t['events_ok']} | {t['events_failed']} | "
            f"{t['error_rate_pct']} | {lat.get('p50')} | {lat.get('p95')} | "
            f"{lat.get('p99')} | {t['cross_tenant_leaks']} |")
    lines.append("")
    lines.append("## Notes")
    lines.append("")
    lines.append("- This report is produced by `infrastructure/scripts/synthetic/lib/runner.py`.")
    lines.append("- Invariants and extension points are documented in "
                 "`docs/mvp5/reports/mvp5-sprint1-synthetic-scaffold.md`.")
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")
