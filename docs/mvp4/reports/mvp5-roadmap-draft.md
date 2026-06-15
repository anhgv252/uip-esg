# MVP5 Roadmap — DRAFT

| Field | Value |
|---|---|
| **Status** | DRAFT — for SA + PO review |
| **Drafted** | 2026-06-15 (PM Task #27) |
| **Target window** | Q1 2027 (after MVP4 pilot stabilizes, ~Dec 2026) |
| **Series A trigger** | $100K MRR (tracked by PM) |

> This roadmap is the PM-authored draft referenced by `mvp4-summary-draft.md` §5. Scope and SP estimates are planning-level; SA must convert to ADRs before Sprint 1 of MVP5.

---

## 1. Strategic drivers

MVP4 leaves UIP at **pilot scale (5 buildings)** with AI cost optimization, correlation, and operator self-service proven in code. MVP5's job is to take UIP from pilot to **commercially viable at scale (50+ buildings)** and to unlock the **Series A revenue trigger** ($100K MRR).

Three forcing functions:
1. **Scale ceiling** — current Docker Compose + single-instance backend hits ~680 RPS ceiling (`feedback_mvp2_demo_and_perf_lessons`). 50+ buildings need horizontal scale.
2. **Operator leverage** — self-service wizard handles 80% of workflows, but Vietnamese-language NL→BPMN removes the last developer bottleneck for non-technical city staff.
3. **Secret/ops maturity** — `.env` files + externalized Kong JWKS are fine for pilot; a paying enterprise customer requires Vault + audited secret rotation.

---

## 2. Themes & epics

### Theme A — Scale infrastructure (~40 SP)

| Epic | SP | Rationale |
|---|---|---|
| **K8s migration** (Helm charts, HPA, Istio) | 21 | Trigger: >20 buildings or Tier-3 customer. Carries over `docker-compose.ha.yml` learnings. Reuse `infrastructure/docker-compose.ha.yml` topology as the Helm source-of-truth. |
| **HashiCorp Vault** secret management | 8 | Replace `.env` + CHANGE_ME pattern (`feedback_mvp4_config_bugs` P0-2 lesson). Required before enterprise contracts. |
| **Multi-region DR** (ClickHouse replication, Kafka MirrorMaker) | 11 | Current CH is 2-node HA (`project_sprint8_decisions`). DR across regions for Tier-3 SLA. |

### Theme B — Vietnamese NL→BPMN workflow generation (~30 SP)

| Epic | SP | Rationale |
|---|---|---|
| **NL intent parser** (Vietnamese LLM, Claude Sonnet tier) | 13 | "Khi ngập lụt ở Quận 1 → gửi SMS + bật sprinkler" → BPMN XML. Builds on M4-AI-02 model routing + M4-SS-02 wizard. |
| **BPMN synthesis** (template grounding + LLM fill) | 12 | Ground generation in the 10 MVP4 templates (`frontend/src/data/workflowTemplates.ts`) to constrain output. |
| **Operator review UI** (diff + approve generated workflow) | 5 | Safety: generated BPMN never auto-deploys; operator approves (mirrors BR-010 BMS pattern). |

### Theme C — Productization & monetization (~20 SP)

| Epic | SP | Rationale |
|---|---|---|
| **Tenant billing** (usage-based: AI tokens, sensor count) | 8 | Tie into `AiCostMetrics` chargeback model already built in MVP4. Enables the $100K MRR path. |
| **Audit log + compliance reporting** (ISO 37120, GRI) | 7 | Extend ESG report API; required for HCMC city-authority renewal. |
| **Mobile v3.1 polish** (offline conflict resolution) | 5 | Resolve the M4 offline-mode descope (`feedback_sprint4_pain_points` R6). |

---

## 3. Deferred from MVP4 (carried in)

From `mvp4-summary-draft.md` §3:
- **GAP-010** gRPC IT vs real analytics-service (Pact suffices for MVP4; real gRPC test in MVP5 when analytics-service scales out)
- **GAP-039/040/046** (CH Keeper dashboard, proto-breaking CI, SSL/TLS termination) — P2, fold into Theme A
- **Pact broker CI** (G4 note in `mvp4-staging-gate-runbook.md`) — required for K8s multi-service contracts

---

## 4. Timeline (planning)

```
Q1 2027
├── Sprint 1-2 (Jan)   — K8s migration + Vault (Theme A core)        ~30 SP
├── Sprint 3-4 (Feb)   — NL→BPMN POC + tenant billing               ~30 SP
├── Sprint 5-6 (Mar)   — NL→BPMN prod + DR + compliance             ~30 SP
└── Gate: 50+ buildings live, Series A readiness review             Apr 2027
```

**Series A trigger checklist** (PM tracks):
- [ ] $100K MRR (billing live, Theme C)
- [ ] 50+ buildings onboarded
- [ ] 99.9% uptime over 90 days (K8s + DR, Theme A)
- [ ] NL→BPMN adoption ≥ 30% of new workflows (Theme B)

---

## 5. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| K8s migration delays (team has no prior K8s) | HIGH | Start Day 1 Sprint 1; bring in 1 K8s contractor; reuse HA topology |
| NL→BPMN hallucinates invalid BPMN | HIGH | Template grounding + operator review gate (BR-010 pattern) |
| Vault adds latency to hot path | MEDIUM | Cache secrets in-memory with 5-min refresh |
| $100K MRR slips → Series A slips | HIGH | PM weekly tracking; descope Theme C compliance if billing at risk |

---

## 6. Open questions for SA + PO

1. **K8s distro** — managed EKS/GKE vs self-hosted? (cost vs ops burden)
2. **NL→BPMN model** — Claude Sonnet vs fine-tuned local model? (cost vs latency vs data residency — HCMC may require on-prem)
3. **DR topology** — active-active vs active-passive across regions?
4. **Billing unit** — per-building flat vs per-AI-token metered? (affects tenant contract template)

*Drafted by PM | SA + PO review before MVP5 Sprint 1 planning.*
