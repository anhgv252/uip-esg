"""UIP synthetic 50-tenant test harness — shared library (R16 mitigation).

Modules:
    generate  — NGSI-LD sensor-event payload generator (per-tenant sensor fleets)
    runner    — concurrent tenant-load runner + per-tenant metrics + isolation invariants
    reporting — structured JSON + markdown report writers
"""

__all__ = ["generate", "runner", "reporting"]
