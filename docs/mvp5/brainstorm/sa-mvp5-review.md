# SA Review — MVP5 Roadmap Draft

> ⚠️ **ADDENDUM 2026-06-20 — Tài liệu này là LỊCH SỬ (review gốc trên PM draft 2026-06-15).** Nội dung bên dưới phản ánh quan điểm SA **trước** 4 PO decisions. Đã được supersede bởi `sa-mvp5-conflict-resolution.md` (updated) + `mvp5-po-synthesis.md`.
>
> **4 PO decisions đã chốt (2026-06-19/20) thay đổi kết luận của review này:**
> 1. **DR (ADR-053) OUT khỏi MVP5** → MVP6+.
> 2. **K8s DEFER MVP6** → Compose HA môi trường test. Conflict K8s (R1) không còn critical.
> 3. **BUILD như 50 buildings nhưng TEST chỉ 2-3 buildings** → **GAP-1 (CH/Flink tenant isolation) P0→P1** (vẫn build đúng kiến trúc modular, nhưng 2-3 tenant tin cậy nên không breach-critical). Fuzz test giảm scope.
> 4. **Series A ($100K MRR) OUT khỏi MVP5** → MVP6+. MVP5 = architecture validation + product fit.
>
> **Mapping findings gốc → trạng thái hiện tại:**
> - GAP-1 (CH/Flink tenant): **P0→P1** — vẫn implement (ADR-047), fuzz giảm scope. Xem `sa-mvp5-conflict-resolution.md` §Conflict #1.
> - GAP-2 (NL residency): **P0 duy nhất còn lại** — hard gate. ADR-049.
> - GAP-3 (microservices split): **DEFER MVP6** (ADR-048, S4 spike defer).
> - GAP-4 (schema governance): KEEP — ADR-051.
> - GAP-5 (observability): KEEP — ADR-052.
> - GAP-6 (K8s cost): **DEFER MVP6** (K8s out) — ADR-050 readiness-only.
> - ADR-053 (DR): **OUT khỏi ADR candidates MVP5**.
> - R15 (3.000 RPS Compose HA): **GIẢI QUYẾT** (test 2-3 bldg chỉ cần ~100-200 RPS). R16 (build-for-50 chưa exercise) là top risk mới.
>
> **Đọc `sa-mvp5-conflict-resolution.md` cho trạng thái hiện tại.** Nội dung dưới đây chỉ còn giá trị tham khảo/lịch sử.

---

| Field | Value |
|---|---|
| **Reviewer** | Solution Architect |
| **Input** | `docs/mvp4/reports/mvp5-roadmap-draft.md` (PM Task #27, DRAFT 2026-06-15) |
| **Date** | 2026-06-18 *(historical — superseded by addendum above + sa-mvp5-conflict-resolution.md)* |
| **Status** | SA REVIEW — 6 gaps identified, 7 ADR candidates, 3 epics proposed *(historical)* |
| **Verdict** | Draft is **directionally sound** but **under-scoped on data-plane hardening** (multi-tenant isolation outside PostgreSQL, schema governance, contract testing at K8s scale). PM's 90 SP plan should grow to ~110–120 SP or stretch Theme C. *(historical — pre-PO-decisions)* |

---

## 0. Architecture baseline verified (today, MVP4)

- **Modular Monolith** — 25 bounded-context packages under `com.uip.backend.*` (ai, aiworkflow, alert, bms, building, citizen, common, correlation, dashboard, environment, esg, forecast, kafka, monitoring, notification, partner, safety, scheduler, tenant, traffic, workflow, ...). Module boundaries enforced via ArchTest (with one carve-out for `ai.feedback`).
- **Multi-tenant isolation today** — **PostgreSQL RLS only** (migrations V16, V18, V30). **ClickHouse and Flink have NO tenant enforcement** — `tenant_id` is filtered at the application layer (`ClickHouseRestAnalyticsAdapter`, `ClickHouseGrpcAnalyticsAdapter`, `TimescaleDbAnalyticsAdapter`, `EsgService`). This is a **P0 latent risk** the draft does not mention.
- **ADRs in place** through **ADR-046** (incident feedback loop). Next candidate numbers: **ADR-047+**.
- **Docker Compose HA** is current prod topology; single-instance backend ceiling ~680 RPS.

---

## 1. Architecture feasibility review — per theme/epic

### Theme A — Scale infrastructure (~40 SP) → FEASIBLE but **under-estimated by ~50%**

| Epic | SP draft | SA assessment | Hidden cost / danger |
|---|---|---|---|
| **K8s migration** (Helm, HPA, Istio) | 21 | **Under-estimated. Realistic 30–35 SP.** Migrating a stateful Modular Monolith + Kafka 3-broker KRaft + Flink JobManager/TaskManager + CH 2-node + EMQX + Keycloak + Kong to K8s is not "lift-and-shift". | (a) Flink checkpoint migration off local MinIO → PVC/MinIO-on-K8s; (b) EMQX mnesia cluster on K8s is fragile; (c) Istio mTLS double-encrypts Kafka inter-broker — disable auto-mTLS for Kafka mesh; (d) StatefulSet anti-affinity rules for CH/Kafka need AZ topology; (e) `docker-compose.ha.yml` is **NOT** 1:1 mappable to Helm — Kong DB-less mode, init containers, readiness probes all differ. |
| **HashiCorp Vault** | 8 | **Feasible at 8 SP for integration**, +4 SP for **secret rotation + dynamic DB creds** which PM omitted. | Vault in hot path: draft says "cache 5-min" — **wrong** for JWT signing keys. Use **Spring Cloud Vault + refresh scope**, lease-driven rotation, NOT poll. Also: Vault HA itself needs 3-node Raft cluster → new stateful workload on K8s. |
| **Multi-region DR** | 11 | **Feasible but premature in Sprint 5–6.** Trigger (`project_sprint3_po_decisions` deferred CH HA 3 times) shows team under-estimates distributed-state work. | (a) ClickHouse ReplicatedMergeTree + Keeper cross-region is latency-sensitive (Keeper quorum needs <5ms RTT); (b) Kafka MirrorMaker2 vs Cluster Linking — prefer **Cluster Linking** (Kafka 3.7+) for offset preservation; (c) Active-active requires **conflict resolution policy for tenant billing + audit log** — not specified. |

**Verdict Theme A**: 40 SP → realistically **48–52 SP**. Recommend folding `GAP-039/040/046` (CH Keeper dashboard, proto-breaking CI, TLS termination) here, +1 SP each, NOT into a generic bucket.

### Theme B — Vietnamese NL→BPMN (~30 SP) → FEASIBLE but **missing the residency + cost ceiling story**

| Epic | SP draft | SA assessment | Hidden cost / danger |
|---|---|---|---|
| **NL intent parser** (Claude Sonnet) | 13 | **Feasible.** MVP4 already has model routing (M4-AI-02). | (a) **Data residency — DRAFT DOES NOT ADDRESS.** Sending Vietnamese city-authority workflow descriptions (potentially containing operator PII, building addresses, incident details) to Anthropic API = data leaves Vietnam. HCMC government contract likely prohibits this (Decree 13/2023 ND-CP on personal data). **This is a P0 blocker.** (b) Token cost ceiling: 30% adoption × 50 buildings × N operators/day — must extend `AiCostMetrics` with per-tenant NL quota or MRR math inverts. |
| **BPMN synthesis** (template grounding) | 12 | **Feasible — strongest epic.** Grounding in 10 templates is the right safety rail. | (a) BPMN XML schema validation must run **server-side before operator review** (camunda bpmn-validator), not in UI; (b) LLM may emit service tasks referencing **non-existent sensors/actuators** → need a building/sensor registry resolver as a hard constraint, not a prompt hint; (c) Output token cap (~4K) may truncate complex workflows — chunking strategy needed. |
| **Operator review UI** (diff + approve) | 5 | **Under-estimated. 8 SP.** Diff visualization of BPMN XML with semantic equivalence is non-trivial. | Need: side-by-side bpmn-js render, semantic diff (not text diff), **dry-run simulator** ("execute this BPMN against last 7 days of events, show what would have fired"). The simulator alone is 3 SP. |

**Verdict Theme B**: 30 SP → realistically **34–38 SP** + **must add a residency epic (see §2)**.

### Theme C — Productization (~20 SP) → FEASIBLE but **billing epic hides a metering pipeline**

| Epic | SP draft | SA assessment | Hidden cost / danger |
|---|---|---|---|
| **Tenant billing** (usage-based) | 8 | **Under-estimated. 14 SP.** "Tie into `AiCostMetrics`" is a one-liner that hides a real metering pipeline. | Need: (a) metering event stream (`billing.events` Kafka topic, per-call emission from AI layer), (b) aggregation job (Flink tumbling window → `tenant_usage_daily` table), (c) idempotent ingestion (dedup key), (d) invoice generation + PDF, (e) **dispute workflow** (operator challenges token count), (f) currency/locale for VND. The draft scopes only (a)+(b). |
| **Audit log + compliance** (ISO 37120, GRI) | 7 | **Feasible.** Extends ESG report API cleanly. | (a) Audit log MUST be **append-only, tenant-scoped, immutable** — tamper-evident via hash chain or CH Append-Only engine; (b) ISO 37120 city indicators need a **separate indicator registry** (CSV/JSON → DB seed) — currently no such entity. |
| **Mobile v3.1 polish** (offline conflict) | 5 | **Feasible.** Resolves M4 descope cleanly. | Last-write-wins vs CRDT — pick **LWW with version vector** for sensor readings, **manual merge prompt** for workflow edits. Document the choice as an ADR. |

**Verdict Theme C**: 20 SP → realistically **26–30 SP**.

---

## 2. Architectural gaps the draft does NOT mention

These are the **most important findings** of this review. Each is a missing epic or ADR.

### GAP-1 (P0) — Multi-tenant isolation in ClickHouse & Flink **does not exist today**

- **Today**: PostgreSQL has RLS (V16/V18/V30). ClickHouse and Flink rely on **application-layer `tenant_id` filtering** — a single bug in any analytics adapter (`ClickHouseRestAnalyticsAdapter`, `ClickHouseGrpcAnalyticsAdapter`, `TimescaleDbAnalyticsAdapter`) leaks cross-tenant sensor data.
- **At 5 buildings** (pilot) this is tolerable — operators are trusted. **At 50+ buildings with paying customers, it is a breach-waiting-to-happen** and a blocker for ISO 27001 / SOC 2 (which Tier-3 enterprise customers will demand).
- **Proposed epic** (Theme A, **8 SP**): "ClickHouse row-level tenant enforcement"
  - Add `tenant_id` as CH partition key + enforced filter via **CH RowPolicy** (`CREATE ROW POLICY tenant_isolation ON sensor_readings FOR SELECT USING tenant_id = currentTenant()`) driven by a per-session context.
  - Flink: enforce `tenant_id` in every keyed window via a **TenantKeyedProcessFunction** base class + ArchUnit rule banning raw `KeyedProcessFunction`.
  - Add a **tenant-leak fuzz test**: inject queries without tenant filter, assert 0 rows returned.

### GAP-2 (P0) — Data residency for NL→BPMN (Vietnamese government data leaving jurisdiction)

- Decree 13/2023/ND-CP (Personal Data Protection) + likely HCMC contract clause: **operator-entered Vietnamese text + building/incident context cannot leave Vietnam jurisdiction** without explicit cross-border transfer approval.
- Claude API = data transits to AWS US/EU. **Draft's Open Question #2 names this but defers it** — SA position: **resolve BEFORE Sprint 3**, not during.
- **Options** (need ADR-049):
  - (A) **On-prem LLM** — vLLM/LM Studio hosting **PhoGPT-4B** or **Qwen2.5-7B-Vietnamese** on a GPU node in HCMC DC. Latency +60%, cost fixed (capex), compliance clean. **Recommended.**
  - (B) **Claude via AWS Bedrock in `ap-southeast-1` (Singapore)** — still cross-border but within ASEAN, lower regulatory friction. Need DPA + sub-processor agreement.
  - (C) **Hybrid** — PII-redaction layer (Vietnamese NER) → stripped prompt to Claude. Risky: redaction leakage.
- **Proposed epic** (Theme B, **6 SP**): "On-prem Vietnamese LLM tier for NL→BPMN" — wire as a second route in M4-AI-02 model router, default for `tenant.gdpr_mode = true`.

### GAP-3 (P1) — Modular Monolith → microservices split criteria **undefined**

- Draft's Theme A assumes K8s = horizontal scale, but a **stateless deployment of a 25-module monolith** only buys RPS, not team independence. Series A customers will ask "can you isolate my data/workload?".
- **Missing**: an ADR defining **when** to split (trigger thresholds) and **along which seams**.
- **Proposed split candidates** (by load + team ownership, NOT by arbitrary package):
  - **`ingestion-service`** (Kafka MQTT→CH raw) — highest RPS, stateless, split first.
  - **`ai-gateway-service`** (NL→BPMN, model router, cost metering) — GPU-bound, isolated scaling.
  - **`analytics-service`** (ESG, ClickHouse adapters) — already a port (`AnalyticsPort`), natural seam.
  - **Core monolith** (workflow, alert, tenant, billing) — stays modular monolith.
- **Trigger thresholds** (for ADR-048): >40 buildings OR >3 concurrent tenant onboarding OR AI cost >5% of revenue OR p95 latency regression >2x on ingestion. Do **NOT** split prematurely — Modular Monolith is the right shape through MVP5.

### GAP-4 (P1) — Schema evolution & contract governance at 50+ buildings

- Today: Flyway migrations on PostgreSQL (V31+). **No ClickHouse migration tooling** (schema applied manually / via init SQL). **No Avro schema registry for Kafka** (MVP4 Sprint 7 added Avro but registry governance is ad-hoc).
- At 50 buildings with N consumer services, **a breaking Kafka schema change = silent data loss**.
- **Proposed epic** (Theme A, **5 SP**):
  - Confluent **Schema Registry** with **BACKWARD_TRANSITIVE** compatibility.
  - **ClickHouse migrations**: adopt `migrate` (golang-migrate) or `sqitch` — V1 baseline from current schema.
  - **Protobuf/Avro breaking-change CI** (folds `GAP-040` from MVP4 carry-over).
  - **Pact broker** for gRPC + REST contracts (folds MVP4 `GAP-010`, Pact CI note).

### GAP-5 (P1) — Observability maturity (distributed tracing, SLO/SLI)

- MVP4 has Prometheus + Grafana + metrics, but **no distributed tracing** (OpenTelemetry) and **no formal SLO/SLI definitions**. "99.9% uptime over 90 days" (PM's Series A checklist) is **unmeasurable** without error-budget tracking.
- **Proposed epic** (Theme A, **6 SP**):
  - OpenTelemetry instrumentation (Java agent + Spring Boot starter) → Tempo or Jaeger.
  - Define 3 SLOs: **ingestion freshness** (p95 < 2s sensor→dashboard), **API availability** (99.9%), **AI workflow success rate** (99%).
  - Error budget burn alerts in PagerDuty/Alertmanager.

### GAP-6 (P2) — K8s cost model absent from draft

- Open Question #1 asks "managed EKS/GKE vs self-hosted?" without numbers. SA's position (rough order-of-magnitude, to be refined in spike):
  - **Docker Compose on 3 bare-metal hosts** (current-ish): ~$2.5K/mo capex-equivalent at pilot.
  - **EKS/GKE managed** (3-node m5.xlarge + managed Kafka MSK + managed CH via Altinity.Cloud + S3): **~$8–12K/mo at 50 buildings**. 4–5x cost jump.
  - **Self-hosted K8s (k3s/rke2) on HCMC colo**: ~$4–6K/mo but ops burden on team (PM flagged "no prior K8s" risk).
  - **Recommendation**: **k3s on colo** for HCMC deployment (data residency + cost), **EKS** for international/DR region. Needs ADR-050.

---

## 3. ADR candidates for MVP5 (continuing from ADR-046)

| ADR | Title | Driver | Sprint |
|---|---|---|---|
| **ADR-047** | ClickHouse row-level tenant isolation | GAP-1 | Sprint 1–2 |
| **ADR-048** | Modular Monolith → microservices split criteria & seams | GAP-3 | Sprint 1 (spike) |
| **ADR-049** | Vietnamese NL→BPMN LLM tier: on-prem vs Bedrock vs API | GAP-2 | Sprint 2 (before NL parser) |
| **ADR-050** | K8s distro & deployment topology: managed EKS/GKE vs self-hosted k3s | GAP-6, draft OQ#1 | Sprint 1 (spike) |
| **ADR-051** | Schema governance: Schema Registry + CH migrations + Pact | GAP-4 | Sprint 2–3 |
| **ADR-052** | Observability: OpenTelemetry + SLO/SLI definitions | GAP-5 | Sprint 2–3 |
| **ADR-053** | Multi-region DR topology: active-active vs active-passive | draft OQ#3 | Sprint 5–6 |
| **ADR-054** | Billing metering pipeline architecture | Theme C hidden cost | Sprint 3 |
| **ADR-055** | Tenant billing unit & pricing model (per-building vs per-token) | draft OQ#4 | Sprint 3 (PM-owned, SA co-sign) |

---

## 4. Technical risks — TOP 5 (ranked)

| # | Risk | Sev | Likelihood | Architectural mitigation |
|---|---|---|---|---|
| **R1** | **Cross-tenant data leak via CH/Flink** (no RLS) — surfaces as a breach at 50 buildings | CRITICAL | HIGH | GAP-1 epic (ADR-047) in Sprint 1. Block Series A readiness gate on a **tenant-leak fuzz test passing in CI**. |
| **R2** | **NL→BPMN data residency violation** — Vietnamese authority data egresses to Anthropic | CRITICAL | HIGH (if Claude API used as-is) | ADR-049 decided **before Sprint 3**. Default route = on-prem PhoGPT/Qwen for `gdpr_mode` tenants. |
| **R3** | **K8s migration doubles in cost/schedule** — team has zero prior K8s, stateful workloads (Flink, Kafka KRaft, CH Keeper, EMQX mnesia) are non-trivial | HIGH | HIGH | (a) Bring K8s contractor Day 1 Sprint 1 (draft already says this — endorse); (b) **sequence**: stateless services first (Sprint 1), Kafka/Flink stateful in Sprint 2 with checkpoint migration rehearsal, CH last; (c) Keep Docker Compose HA as **fallback prod** for 2 sprints after K8s cutover. |
| **R4** | **Schema drift breaks consumers at 50 buildings** — no Schema Registry, CH migrations manual | HIGH | MEDIUM | GAP-4 epic (ADR-051). BACKWARD_TRANSITIVE compat enforced in CI before any Kafka topic deploy. |
| **R5** | **Billing metering under-built** — draft's 8 SP delivers invoicing UI but no metering pipeline; PM's $100K MRR trigger becomes unmeasurable | HIGH | MEDIUM | ADR-054 (Sprint 3). Build metering **before** invoice — Kafka `billing.events` topic + Flink daily rollup as the source of truth; invoice is a view. |

*(R6/R7, lower rank: Vault hot-path latency — use Spring Cloud Vault lease refresh not 5-min poll; offline mobile CRDT — LWW + version vector, ADR in Theme C.)*

---

## 5. Open questions needing SA spike before Sprint 1

| # | Spike | Output | Why before Sprint 1 |
|---|---|---|---|
| **S1** | **ClickHouse RowPolicy PoC** — does CH RowPolicy + per-session `currentTenant()` work with the existing gRPC adapter? | ADR-047 draft + working branch | Sets Theme A scope; if RowPolicy is insufficient, falls back to view-per-tenant (costlier) |
| **S2** | **On-prem Vietnamese LLM benchmark** — PhoGPT-4B vs Qwen2.5-7B-VI on 50 representative workflow prompts; measure BLEU/BERTScore vs Claude baseline + GPU TCO | ADR-049 decision matrix | Blocks Theme B kickoff; if on-prem quality <80% of Claude, need Bedrock fallback in plan |
| **S3** | **K8s cost & topology spike** — concrete quotes for EKS/GKE vs k3s-on-colo at 50-building load; stateful workload placement; Istio vs Linkerd vs Cilium | ADR-050 + Helm skeleton | Gates Theme A sizing; draft's 21 SP is unverified |
| **S4** | **Modular Monolith split triggers** — instrument current cross-module call graph (use `codegraph` + `java-graph get_resolved_callees`) to find the 2–3 seam candidates with lowest coupling | ADR-048 + coupling report | Prevents premature split; identifies which module extracts first |
| **S5** | **Multi-region DR feasibility** — Keeper cross-region latency test, Kafka Cluster Linking vs MM2 offset preservation | ADR-053 draft | May push DR out of MVP5 entirely if latency/consistency unworkable |

---

## 6. Revised SP summary (SA recommendation)

| Theme | Draft SP | SA realistic SP | Delta |
|---|---|---|---|
| A — Scale | 40 | **57** | +17 (K8s realism, Vault rotation, GAP-1 CH tenant, GAP-4 schema, GAP-5 observability, GAP-6 partial) |
| B — NL→BPMN | 30 | **44** | +14 (residency epic GAP-2, BPMN validator + simulator, ops review UI) |
| C — Productization | 20 | **28** | +8 (metering pipeline in billing) |
| **Total** | **90** | **~129** | **+39 SP (~+43%)** |

**Recommendation to PM/PO**:
- Either **stretch MVP5 to 5–6 sprints** at ~120 SP (current team velocity ~24 SP/sprint × 5 = 120), OR
- **Descope DR (ADR-053, 11 SP)** to MVP6 — DR is premature until K8s stabilises; ship K8s + Vault + tenant isolation + observability first.
- **Make GAP-1 (CH tenant) and ADR-049 (residency) hard gates** — no Series A readiness sign-off without both.

---

## 7. Anti-pattern checklist (run on draft)

| Check | Draft | SA flag |
|---|---|---|
| Cross-module direct dependency proposed? | No new ones proposed | OK |
| Business logic in Flink? | Billing aggregation proposed but not specified | **WARN** — keep Flink to windowed rollup, put invoice logic in monolith |
| `SELECT *` on CH/TimescaleDB? | Not addressed | **WARN** — add to ADR-047: CH queries must project explicit columns |
| Missing DLQ for Kafka/MQTT? | NL→BPMN topics have no DLQ mentioned | **WARN** — `nl.bpmn.requests.dlq` + `billing.events.dlq` mandatory |
| PII/location data in logs/errors? | NL prompts may contain addresses | **WARN** — redaction filter before any LLM call log |
| Raw sensor data stored without compression? | Not addressed | OK at pilot; revisit at 50 buildings in ADR-050 |

---

*Authored by SA. For PM/PO review before MVP5 Sprint 1 planning. No code changed.*
