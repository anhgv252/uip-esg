# UIP MVP5 — MASTER PLAN (PM-authored)

| Field | Value |
|---|---|
| **Status** | DRAFT (revised) — for PO + SA review before Sprint 1 planning |
| **Authored** | 2026-06-18 (PM) — promotes `mvp5-roadmap-draft.md` to first MASTER PLAN |
| **Revised** | **2026-06-20 (PM) — sync với 4 PO decisions 2026-06-19/20 (pivot build-50 / test-2-3)** |
| **Window** | Sprint M5-1 → M5-6 (6 sprints × 2 weeks, **2026-09-21 → 2026-12-13**) — *default 6 sprint; D8 option rút ngắn 4-5 sprint* |
| **Total SP budget** | **258 SP** envelope (6 sprints × 43 SP/sprint, MVP4 velocity) — **committed ~169 SP, buffer ~95 SP (37%)** sau pivot |
| **Strategic goal (revised)** | **Architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg)** — Series A / $100K MRR → MVP6+ |
| **Predecessor** | MVP4 7/10 gates PASS, 3 gates (G2/G6/G10) pending 30-day pilot (Aug 2026) |
| **Gate framework** | **7 gates** M5-G1 → M5-G8 (G5 xóa — DR out) |

> **PO decisions 2026-06-19/20 (đã chốt, không bàn cãi):**
> 1. **DR (multi-region, ADR-053) OUT khỏi MVP5** → MVP6+. Gate **M5-G5 XÓA**.
> 2. **K8s DEFER MVP6** → MVP5 giữ **Docker Compose HA** làm môi trường test, KHÔNG cutover K8s. Decision **D1 DEFER MVP6**.
> 3. **BUILD như 50 buildings nhưng TEST chỉ 2-3 buildings** (tài nguyên hạn chế) — giữ modular architecture đầy đủ; tenant isolation **P0 → P1** (vẫn implement cho architecture correctness, nhưng 2-3 tenant tin cậy nên không còn breach-critical).
> 4. **Series A ($100K MRR) OUT khỏi MVP5** → MVP6+ khi 10-50 bldg. MVP5 mục tiêu mới = **architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg)**.

> This document replaces `mvp5-roadmap-draft.md` (90 SP, vague Q1 2027) with a reconciled plan, fixed timeline, decision log, risk register, OKRs and **7 quality gates**. SA must convert each theme's epic into ADRs before M5-1. **(updated 2026-06-20 per PO decisions)** — bám `pm-mvp5-conflict-resolution.md` và `mvp5-po-synthesis.md` làm source of truth chi tiết.

---

## 1. Timeline — Pilot → MVP5 kickoff dependency  *(updated 2026-06-20 per PO decisions)*

### 1.1 Why not Q1 2027 (the draft's vague date)

The draft says "Q1 2027" and treats MVP5 as a clean start after pilot. That is **wrong on two counts**:

1. **Pilot produces gating data, not a clean break.** Three MVP4 gates remain open and run *during* the Aug 2026 pilot: **G2** (false-positive rate on 30-day live data, threshold <5%), **G6** (App Store / Play Store approval), **G10** (pilot uptime ≥99.5% over 30 days). MVP5 cần G10 data để xác nhận single-instance ceiling — nhưng sau PO decision 2026-06-19 (K8s defer MVP6), G10 chỉ còn drives **Compose HA tuning** thay vì DR topology. Waiting until Q1 2027 wastes the Sep–Dec 2026 window.

2. **~~Series A doesn't wait.~~ → Series A OUT khỏi MVP5.** PO decision 2026-06-19: **Series A ($100K MRR) defer MVP6+** khi đủ 10-50 bldg. MVP5 mục tiêu mới = **architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg)** — không còn revenue trigger trong MVP5. Lý do giữ Sep 2026 kickoff giờ là **rút ngắn time-to-MVP6** (để mở commercial 10-50 bldg sớm) chứ không còn là giữ Series A window.

### 1.2 Pilot → MVP5 dependency map

```
MVP4 PILOT (Aug 3 – Aug 31, 2026, 5 buildings, 30 days)
  │
  ├─ G2  false-positive data    ──┐
  ├─ G10 uptime data (99.5%)     ──┼──► Pilot Readiness Review (2026-09-07)
  ├─ G6  app store approval      ──┤      feeds M5-1 Compose HA tuning + GAP-1 tenant isolation sizing
  └─ Pilot retro (operator UX)   ──┘      (KHÔNG còn DR topology decision — DR OUT MVP6+)
                                  │
                                  ▼
                  MVP5 KICKOFF — Sprint M5-1 (2026-09-21)
```

**Hard dependencies (M5-1 cannot start without):**
- ~~G10 uptime data → DR topology decision~~ → **G10 data → Compose HA replica tuning** (KHÔNG còn DR topology — DR out MVP6+)
- G2 false-positive data → NL→BPMN template grounding (know which false positives operators hit)
- G6 app store approval → Mobile v3.1 polish scope (if rejected, scope grows)

**Soft dependencies (M5-2/M5-3 absorb slippage):**
- Pilot retro operator-UX findings → NL→BPMN review UI
- Pilot billing data (if any tenant pays during pilot) → billing metering unit choice

### 1.3 Sprint breakdown (6 sprints × 2 weeks, 2026-09-21 → 2026-12-13) — *default 6 sprint (D8 = giữ)*

Pilot Readiness Review: **2026-09-07** (1 week after pilot ends + 1 week analysis).
PO Decision Log (§6) must be closed by **2026-09-14** (start of M5-1 week) — đặc biệt **D7 (scope scenario)** và **D8 (sprint count)**.

| Sprint | Dates (2026) | Theme / Goal (build-50 / test-2-3) | SP | Gate |
|---|---|---|---|---|
| **Pilot** | Aug 03 – Aug 31 | MVP4 pilot (5 bldg, 30 days) — G2/G6/G10 data | — | G2/G6/G10 |
| **Review** | Sep 07 | Pilot Readiness Review → PO decisions (D7, D8) | — | — |
| **M5-1** | Sep 21 – Oct 04 | **Compose HA hardening + GAP-1 tenant isolation (P1) + Vault + carry-over closeout.** `docker-compose.ha.yml` làm môi trường test chính; CH RowPolicy tenant; Vault secret injection. Close GAP-039/040/046 + Pact broker CI. | 43 | M5-G1 (Compose HA + tenant isolation P1) |
| **M5-2** | Oct 05 – Oct 18 | **NL→BPMN POC + GAP-2 residency + billing skeleton.** NL intent parser (Vietnamese), template grounding, operator review UI wireframe. GAP-2 Decree 13 residency spike (D2 model). `AiCostMetrics` → tenant metering. | 43 | M5-G2 (tenant isolation fuzz — 2-3 tenant) |
| **M5-3** | Oct 19 – Nov 01 | **NL→BPMN prod hardening + ROI dashboard.** BPMN synthesis + validation gate (BR-010 pattern). ~~DR standup~~ (DR OUT). BA vertical: ROI dashboard. | 43 | M5-G3 (NL→BPMN UAT) |
| **M5-4** | Nov 02 – Nov 15 | **Billing GA + LOTUS VN + compliance prep.** Per-tenant invoicing, ISO 37120 + GRI, audit log lite. GAP-010 gRPC IT. BA vertical: LOTUS VN. | 43 | M5-G4 (billing metering accuracy) |
| **M5-5** | Nov 16 – Nov 29 | **EV Charging + mobile + synthetic multi-tenant test.** OCPP integration (BA vertical). Mobile v3.1 offline conflict (LWW + version vector). **Synthetic 50-tenant test** (mitigate R16, không cần 50 bldg thật). | 43 | — |
| **M5-6** | Nov 30 – Dec 13 | **Hardening + architecture validation + product fit signal.** Bug burn-down, OWASP, perf regression, ~~investor demo~~ → **pilot-to-paid signal review (≥2/3 bldg)**. Risk buffer consumed here. | 43 | M5-G6 (compliance) / G7 (functional 2-3 bldg) / G8 (architecture validation + pilot-to-paid) |
| ~~Series A observation~~ | ~~Dec 14, 2026 → ~Mar 2027~~ | ~~90-day MRR observation~~ — **OUT khỏi MVP5, MVP6+** | — | — |

**Total: 258 SP envelope (6 sprints × 43 SP) — committed ~169 SP, buffer ~95 SP (37%).** Code-complete target: **2026-12-13** (M5-6 close). ~~Series A readiness review 2026-12-18~~ → **MVP5 close review: architecture validation + product fit (≥2/3 pilot-to-paid), 2026-12-18.**

> **D8 option (PM recommend): rút ngắn MVP5 xuống 4-5 sprint.** Buffer 95 SP (37%) quá dư cho 6 sprint khi Series A out + R15 giải quyết. Nếu PO chốt D8 = rút ngắn → MVP5 close ~**2026-11-29** (M5-5 close, 5 sprint) và G6/G7/G8 dồn vào cuối M5-5. Default nếu PO không chốt 2026-09-14 = **giữ 6 sprint**. Chi tiết scenario xem `pm-mvp5-conflict-resolution.md` §3a.

---

## 2. SP budget allocation — reconciling 90 SP draft → 258 SP envelope → 169 SP committed  *(updated 2026-06-20 per PO decisions)*

The draft allocated only ~90 SP across 3 themes. At MVP4 velocity (43 SP/sprint × 6 sprints) the real envelope is **258 SP**. **Sau 4 PO decisions (2026-06-19/20)**: K8s defer + DR out + perf-at-scale bỏ + giảm tenant fuzz → committed chỉ **~169 SP**, buffer tăng lên **~95 SP (37%)**. Allocation below reflects Scenario C (build-50 / test-2-3).

| Bucket | SP | % (of 258) | Notes |
|---|---|---|---|
| **Theme A — Compose HA + Vault + GAP-1 tenant isolation (P1)** | ~~55~~ → **20** | 8% | **K8s cutover OUT (MVP6)** — chỉ còn Compose HA hardening (môi trường test), Vault secret injection, schema governance (ADR-051), observability OTel, GAP-1 CH RowPolicy + Flink tenant function (P1). |
| **Theme B — Vietnamese NL→BPMN** | 42 | 16% | Không đổi — NL parser, template grounding, BPMN synthesis + validation (BR-010), operator review UI, simulator. |
| **Theme C — Productization (billing + compliance + mobile)** | 36 | 14% | Không đổi — billing GA accuracy, ISO 37120 + GRI, audit log lite, mobile v3.1 offline conflict (LWW + version vector). |
| **BA vertical (3 vertical VN)** | **31** (mới tách) | 12% | ROI dashboard (M5-3) + LOTUS VN (M5-4) + EV Charging OCPP (M5-5) — vertical killer-feature cho pilot-to-paid signal. |
| **MVP4 carry-over (explicit)** | 22 | 9% | GAP-010 gRPC IT (8), GAP-039/040/046 (10), Pact broker CI (4). |
| **v3.1 mobile polish** | (gộp Theme C) | — | Đã gộp vào Theme C. |
| **Tech debt + hardening** | 18 | 7% | Giảm 30→18: bỏ CH/Kafka RF DR drills (DR out); giữ Spring config bugs class, OWASP cadence, test gate per microservice. |
| **Discovery / spike (SA-led ADRs)** | ~~11~~ → **7** | 3% | Bỏ K8s distro spike (D1 defer) + DR topology spike (D3 xóa). Giữ: NL model residency (D2), billing unit (D4), Compose HA scale verify (S6 — *optional, R15 giải quyết*). |
| **Risk buffer** | ~~44~~ → **~95** | **37%** | Buffer tăng mạnh do DR-freed + K8s-freed + scale-freed (R15 giải quyết). Industry standard 15–20% — 37% rất dư → PM đề xuất D8 rút ngắn sprint (§1.3 note). Buffer cover R16 (synthetic multi-tenant test), GAP-2 residency phức tạp, NL→BPMN hallucination tuning. |
| **TOTAL (envelope)** | **258** | **100%** | 6 sprints × 43 SP — committed ~169, buffer ~95. |

### Per-sprint allocation (planning values, Scenario C — bám `pm-mvp5-conflict-resolution.md` §3)

| Sprint | Theme A (Compose HA + CH + Vault) | Theme B (NL→BPMN) | Theme C (billing/ESG/mobile) | BA vertical | Carry-over | Tech-debt | Spike/ADR | Buffer | Total |
|---|---|---|---|---|---|---|---|---|---|
| M5-1 | **14** (GAP-1 CH RowPolicy P1 + Compose HA + Vault) | 0 | 0 | 0 | 10 | 2 | 4 | 13 | 43 |
| M5-2 | 6 (Vault rotation) | **18** (NL parser POC + GAP-2 residency) | 8 (billing skeleton) | 0 | 6 | 1 | 2 | 2 | 43 |
| M5-3 | 4 (schema reg ADR-051 + observability OTel) | **18** (BPMN synth + validator + simulator) | 4 | **5 (ROI dashboard)** | 3 | 2 | 1 | 6 | 43 |
| M5-4 | 2 | 4 | **18** (billing GA + ISO 37120 + GRI) | **8 (LOTUS 6 + audit-lite 2)** | 3 | 5 | 0 | 3 | 43 |
| M5-5 | 2 (Compose HA replica tuning) | 2 | 4 (billing accuracy) | **10 (EV Charging OCPP)** | 0 | 10 | 1 | 14 | 43 |
| M5-6 | 2 (Compose HA hardening) | 0 | 2 | 0 (BA UAT spread ở trên) | 0 | 10 | 0 | **29** | 43 |
| **TOTAL** | **30** (~20 core + 10 carry-over/HA) | **42** | **36** | **23** (+8 UAT spread = **31**) | **22** | **30** | **8** | **67** | **258** |

> **Note pivot 2-3 bldg**: Theme A giảm 55→**~20 SP** (K8s out). Tenant isolation fuzz giảm từ 10K attempts → **cover 2-3 tenant** (~4 SP tiết kiệm). KR1.2 3.000 RPS **BỎ** → không cần perf-at-scale spike. Buffer ~95 SP (37%) có thể absorb thêm Smart Waste vertical nếu PO muốn, hoặc rút ngắn sprint (D8).

---

## 3. Team capacity & hiring

### 3.1 MVP4 baseline (9 agents, retained)

SA, Backend (2 FTE-equiv via agent), Frontend (2 FTE-equiv), DevOps, BA, PM, UX, QA, Tester.

### 3.2 MVP5 additions  *(updated 2026-06-20 per PO decisions)*

| New role | Sprint onboarding | Justification | FTE |
|---|---|---|---|
| ~~**K8s / SRE contractor**~~ → **DevOps generalist + Compose HA tuner** | M5-1 (Sep 21) | **K8s DEFER MVP6** → giảm 1.0→**0.5 FTE**, down-tier. Owns Compose HA hardening (môi trường test) + Vault + Helm skeleton readiness spike (ADR-050 draft only). Không cutover, không DR failover drills. **Tiết kiệm ~0.5 FTE + pool ứng viên rộng hơn.** | **0.5** (M5-1 → M5-4) |
| **Data Engineer (billing metering)** | M5-2 (Oct 05) | Per-tenant usage metering (AI tokens, sensor count) needs a dedicated stream — `AiCostMetrics` → tenant ledger → invoice. Backend agent cannot own this alongside feature work. | 1.0 (M5-2 → M5-4) |
| **Security/compliance contractor** | M5-4 (Nov 02) | ISO 37120 + GRI cert prep, Decree 13 (VN) data residency audit (GAP-2), M5-G6 gate owner. **Load tăng do GAP-2 residency audit** — cần 0.5 sprint prep M5-3. | 0.5 (M5-3 prep → M5-6) |
| **BA vertical owner** ⭐ (MỚI) | M5-2 (Oct 05) | **PM đề xuất thêm 0.5 FTE** — BA vertical 31 SP (LOTUS checklist + ROI acceptance + EV OCPP spec) cần dedicated ownership. Nếu PO không approve → BA vertical cắt về 23 SP (bỏ EV). | 0.5 (M5-2 → M5-5) |
| **QA/performance (synthetic multi-tenant test)** ⭐ (MỚI — mitigate R16) | M5-1 (Sep 21) | **R16 top risk**: build-for-50 nhưng test-2-3 → cần **synthetic multi-tenant test** (simulate 50 tenant via test data) prove code path scale correctness. Overlay lên QA hiện có. (R15 đã giải quyết — không cần perf-at-scale 3.000 RPS.) | 0.5 (M5-1 → M5-5) |

> **Hiring net delta vs master-plan gốc**: K8s contractor giảm 0.5 FTE + không cần DR specialist → **net giảm ~0.5 FTE**. BA owner + QA synthetic test là 2 role mới (cộng +1.0 FTE) nhưng có thể overlay lên BA/QA hiện có. **Net hiring ~+0.5 FTE** vs gốc.

### 3.3 Team composition per sprint

| Sprint | SA | Backend | Frontend | DevOps | K8s/SRE | Data Eng | Sec | BA | PM | UX | QA | Tester |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| M5-1 | 1 | 2 | 1 | 1 | **1** | 0 | 0 | 0.5 | 1 | 0 | 1 | 0.5 |
| M5-2 | 1 | 2 | 2 | 1 | 1 | **1** | 0 | 0.5 | 1 | 1 | 1 | 0.5 |
| M5-3 | 1 | 2 | 2 | 1 | 1 | 1 | 0 | 0.5 | 1 | 1 | 1 | 0.5 |
| M5-4 | 1 | 2 | 1 | 1 | 1 | 1 | **0.5** | 0.5 | 1 | 0.5 | 1 | 0.5 |
| M5-5 | 1 | 2 | 2 | 1 | 1 | 0.5 | 0.5 | 0.5 | 1 | 1 | 1.5 | 1 |
| M5-6 | 1 | 2 | 1 | 1 | 1 | 0 | 0.5 | 0 | 1 | 0.5 | 1.5 | 1.5 |

**Hiring decisions needed before 2026-09-07 Pilot Readiness Review:** K8s contractor (PO budget approval), Data Engineer JD posted by Aug 15.

---

## 4. OKR / KPI — MVP5  *(updated 2026-06-20 per PO decisions — redefine theo `mvp5-po-synthesis.md` §2)*

Five Objectives. **MVP5 không còn "Series A trigger clause"** — mục tiêu mới = **architecture validation + product fit + pilot-to-paid signal**. KR targets are measured at M5-6 gate (2026-12-13) unless noted.

### O1 — Build modular multi-tenant architecture, READY for 50+ buildings (test trên 2-3 bldg) — *redefine*
| KR | Target | Measurement |
|---|---|---|
| KR1.1 ~~Buildings onboarded on K8s~~ → **Modular architecture proven**: 25 bounded-context + tenant isolation implemented + Compose HA sẵn sàng test 2-3 bldg | Modular Monolith PASS ArchTest, M5-G1 | SA architecture review, M5-1 |
| ~~KR1.2 Peak sustained throughput ≥ 3,000 RPS~~ | ~~**BỎ**~~ — perf-at-scale → MVP6+ (R15 giải quyết) | — |
| KR1.2' **Synthetic 50-tenant test PASS** (mitigate R16) | 50 tenant simulated via test data, 0 race/quota/isolation bug ở scale code path | Synthetic test suite, M5-G7 |
| ~~KR1.3 Autoscale response time~~ | **BỎ** — K8s HPA → MVP6+ | — |
| KR1.3' **Compose HA scale headroom** | 2-3 bldg test chỉ ~100-200 RPS, Compose HA ceiling ≥ 500 RPS (dư sức) | JMeter 3-bldg scenario, M5-G7 |

### O2 — Ship Vietnamese NL→BPMN with safety gate (giữ nguyên — operator leverage)
| KR | Target | Measurement |
|---|---|---|
| KR2.1 NL→BPMN adoption | ≥ 30% of new workflows authored via NL | Audit log, M5-6 |
| KR2.2 Generated BPMN validity (no hallucinated nodes) | ≥ 98% pass template validation | M5-G3 UAT, M5-3 |
| KR2.3 Operator-approve cycle time | ≤ 90s per workflow | Review UI telemetry |
| KR2.4 NL latency p95 | ≤ 4s (Claude) / ≤ 8s (local model fallback) | APM |

### O3 — Product fit + pilot-to-paid signal — *redefine (bỏ $100K MRR tuyệt đối)* — **PRIMARY**
| KR | Target | Measurement |
|---|---|---|
| ~~KR3.1 MRR at M5-6 close ≥ $40K~~ | ~~**BỎ** — $100K MRR → MVP6+ khi 10-50 bldg~~ | — |
| KR3.1' **Pilot-to-paid conversion** | **≥ 2/3 pilot bldg chuyển paid plan** (signal, không revenue tuyệt đối) | Sales pipeline, M5-G8 |
| KR3.2 Billing accuracy (metered vs actual) | ≥ 99.5% reconciliation | M5-G4, M5-4 |
| KR3.3 Tenant invoicing automation | 100% auto-generated, 0 manual | Stripe/billing export |
| KR3.4' **ARR projection** (thay $40K MRR) | Pilot-to-paid signal đủ mạnh để project ARR cho MVP6/Series A pitch | PM ARR model, M5-6 |

### O4 — Achieve compliance for enterprise contracts — *redefine (bỏ DR, relax uptime SLO)*
| KR | Target | Measurement |
|---|---|---|
| ~~KR4.1 DR failover RTO~~ | ~~**BỎ** — DR (ADR-053) OUT MVP6+~~ | — |
| ~~KR4.2 DR RPO~~ | ~~**BỎ** — DR OUT~~ | — |
| ~~KR4.3 90-day production uptime ≥ 99.9%~~ | ~~**BỎ** — uptime SLO 99.9% → MVP6+~~ | — |
| KR4.3' **Uptime signal** (không SLO bắt buộc) | 2-3 bldg pilot uptime tracked, **signal-only** (target ≥ 99.5% như MVP4 pilot, không gate-hard) | SLO dashboard, rolling |
| KR4.4 Compliance audit pass | ISO 37120 + GRI + Decree 13 (VN) data residency (GAP-2) | M5-G6, M5-6 |

### O5 — Sustain delivery quality (giữ nguyên — operational maturity)
| KR | Target | Measurement |
|---|---|---|
| KR5.1 Sprint velocity sustain | ≥ 40 SP/sprint avg | Sprint burn-down |
| KR5.2 Regression test gate | ≥ 1,900 tests, 0 failures per release | M5-G7, CI |
| KR5.3 OWASP CVE (CVSS ≥ 7) | 0 in production artifacts | M5-G6, quarterly |
| KR5.4 Doc-vs-code audit | 0 phantom "DEV DONE" (executable artifact required) | SA review each task |
| KR5.5' **Modular ArchTest PASS** (mới) | 25 bounded-context boundary test PASS, 0 cross-module leak | SA ArchTest suite, M5-G7 |

> **Đã BỎ khỏi MVP5 OKR**: KR1.2 (3.000 RPS), KR3.1 ($40K MRR), KR4.1/KR4.2 (DR RTO/RPO), KR4.3 (uptime 99.9% SLO) — tất cả → MVP6+. MVP5 deliver **architecture validation + product fit signal**, KHÔNG deliver Series A readiness.

---

## 5. Risk register  *(updated 2026-06-20 per PO decisions)*

Post-pivot risk landscape. **R15 GIẢI QUYẾT, R1/R3/R7/R11/R14 giảm severity, R16 là top risk mới** (build-for-50 nhưng test-2-3). R2/R4/R5/R6/R8/R9/R10 giữ nguyên.

| # | Risk | Sev | Prob | Owner | Mitigation |
|---|---|---|---|---|---|
| ~~**R1**~~ ⬇️ | ~~**K8s migration delays — team zero K8s experience**~~ → **GIẢM MED/MED** | ~~HIGH~~ → **MED** | ~~HIGH~~ → **MED** | DevOps + Compose HA tuner | **K8s DEFER MVP6** → R1不再是 critical path. Chỉ còn K8s readiness spike (ADR-050 draft + Helm skeleton). Contractor down-tier 1.0→0.5 FTE. Rủi ro thật sự chuyển sang **R16** + R15 (đã giải quyết). |
| **R2** ⚠️ | **NL→BPMN hallucinates invalid/unsafe BPMN (wrong sprinkler trigger, false flood alert)** | HIGH | HIGH | SA + Backend | (1) Template grounding — generation constrained to 10 MVP4 templates; (2) operator review gate, never auto-deploy (BR-010 pattern); (3) BPMN schema validator before review UI; (4) M5-G3 UAT with 5 city operators before GA. |
| ~~**R3**~~ ⬇️ | ~~**$100K MRR slips → Series A slips (revenue, not tech)**~~ → **GIẢM LOW/MED** | ~~HIGH~~ → **LOW** | ~~HIGH~~ → **MED** | PM + PO | **Series A OUT MVP5** → R3不再是 critical path. MVP5 không còn revenue trigger. PM tracker chuyển từ weekly MRR → **monthly pilot-to-paid signal** (≥2/3 bldg). Series A là MVP6 story. |
| **R4** | **Multi-tenant data leak (GAP-1)** — RLS bypass, cache key collision — *P0 → **P1***  | ~~HIGH~~ → **MED** | MED | Backend + SA | **Tenant isolation P0 → P1**: vẫn implement (build đúng kiến trúc modular) nhưng 2-3 tenant tin cậy nên không còn breach-critical. (1) M5-G2 fuzz test **cover 2-3 tenant** (đủ prove correctness, giảm từ 10K attempts); (2) cache key namespacing audit; (3) `feedback_sprint3_readiness` cache isolation rules enforced. |
| **R5** ⚠️ | **Data residency (GAP-2, Decree 13 VN)** — NL calls leave country — *P0 duy nhất còn lại* | HIGH | MED | Sec contractor + SA | (1) GAP-2 NL model residency spike M5-2 (D2 hybrid: gdpr_mode→on-prem); (2) data classification + DPIA before M5-4; (3) CH replication giữ trong-region (DR out nên không cross-region). **P0 duy nhất còn lại sau DR out.** |
| R6 | Vault adds latency to hot path (secret fetch per request) | MED | MED | DevOps | (1) Cache secrets in-memory, 5-min TTL refresh; (2) Vault sidecar via service account; (3) bench secret-read latency in M5-G1. |
| ~~**R7**~~ ⬇️ | ~~**CH Keeper / Kafka quorum loss under DR failover (GAP-039)**~~ → **GIẢM LOW/LOW** | ~~MED~~ → **LOW** | ~~MED~~ → **LOW** | DevOps | **DR OUT** → không còn DR failover scenario. Chỉ còn Keeper dashboard (GAP-039) cho Compose HA single-region health. RF=3 runbook drills giữ cho Compose HA, không quarterly DR. |
| R8 | Pilot data invalidates MVP4 assumptions (G2 false-positive > 5%, G10 uptime < 99.5%) | MED | MED | PM + QA | (1) Pilot Readiness Review 2026-09-07 with explicit go/no-go per gate; (2) if G2 fails, +1 sprint on correlation tuning (from buffer); (3) ~~if G10 fails, DR topology shifts to active-active~~ → **if G10 fails, Compose HA replica tuning +1 sprint** (DR out). |
| R9 | App store rejection (G6) blocks mobile v3.1 scope | MED | LOW | Frontend + PM | (1) Resolve G6 before M5-1; (2) mobile v3.1 scoped late (M5-5) so rejection fixes land in M5-6 buffer. |
| ~~R10~~ | ~~K8s contractor attrition / knowledge silo~~ | ~~MED~~ → **LOW** | LOW | PM | K8s defer → contractor down-tier → attrition risk giảm. Runbook + ADR-050 draft vẫn authored by M5-3. |
| ~~**R11**~~ ⬇️ (từ conflict-resolution) | ~~BA vertical ship trước tenant isolation hoàn thiện = breach debt~~ → **GIẢM MED/LOW** | ~~HIGH~~ → **MED** | ~~MED~~ → **LOW** | BA + Backend | Pivot 2-3 bldg: tenant tin cậy + GAP-1 P1 vẫn implement. Breach risk giảm. EV dependency trên G2 vẫn giữ nhưng severity thấp. |
| ~~**R14**~~ ⬇️ (từ conflict-resolution) | ~~DR out + K8s defer + Series A out → investor đòi enterprise-grade infra ngay~~ → **GIẢM LOW/MED** | ~~MED~~ → **LOW** | MED | PM + PO | MVP5 không còn mục tiêu Series A → investor pitch là **"architecture validation"**, không phải "Series A readiness". Series A là MVP6 story khi đủ bldg. |
| ~~**R15**~~ ✅ (từ conflict-resolution) | ~~**3.000 RPS trên Compose HA chưa verify**~~ → **GIẢI QUYẾT** | — | — | — | **Pivot 2-3 bldg**: test chỉ cần ~100-200 RPS, Compose HA dư sức. KR1.2 (3.000 RPS) **BỎ**. Spike S6 (Compose HA scale verify) bỏ. |
| **R16** ⭐ **(MỚI — TOP RISK post-pivot)** | **Build-for-50 nhưng test-2-3 → code path scale chưa exercise** (race condition, CH partition hotspot, billing quota, NL routing) có thể break ở 10-50 bldg | **HIGH** | **MED** | SA + QA | (1) **Synthetic multi-tenant test** (simulate 50 tenant via test data, không cần 50 bldg thật) — QA role mới 0.5 FTE M5-1→M5-5; (2) ADR-047 CH RowPolicy phải có synthetic multi-tenant test; (3) **MVP6 bắt buộc re-verify trước mở commercial 10+ bldg** (gate K2) — flag rõ trong MVP6 hand-off. |

> **Top 3 risk post-pivot**: **R16** (build-for-50 chưa exercise scale — HIGH/MED), **R2** (NL hallucination — HIGH/HIGH), **R5** (GAP-2 Decree 13 residency — P0 duy nhất còn lại, HIGH/MED). R1/R3/R7/R11/R14/R15 đều đã giảm hoặc giải quyết.

---

## 6. Decision Log — PO must close by 2026-09-14 (M5-1 week)  *(updated 2026-06-20 per PO decisions)*

7 decisions (D1 defer, D3 xóa, D7/D8 mới). **D7 phải chốt đầu tiên** (chốt scope), 5 decision kia (D2/D4/D5/D6) là decision thực thi. Default nếu PO không chốt 2026-09-14.

| # | Decision | Options | Recommendation | Spike owner | Default if no PO decision by 2026-09-14 |
|---|---|---|---|---|---|
| **D7** ⭐ **(MỚI — chốt ĐẦU TIÊN)** | **Scope scenario** | (A) Hardening-first (SA-friendly, defer hết BA vertical); (B) MRR-first (BA-friendly, accept tenant debt); **(C) Balanced — build-50/test-2-3** | **(C) Balanced** — build modular architecture đầy đủ (tenant isolation P1 + NL→BPMN + billing + ESG + 3 BA vertical: ROI/LOTUS/EV), test chỉ 2-3 bldg, DR/K8s/Series A → MVP6+. Buffer 95 SP (37%). | PM (Synthesis `mvp5-po-synthesis.md`) | **(C) Balanced** |
| **D8** ⭐ **(MỚI)** | **MVP5 duration** | Rút ngắn **4-5 sprint** (Series A out + R15 giải quyết) / Giữ 6 sprint + buffer lớn | **Rút ngắn 4-5 sprint** — không lý do kéo 6 sprint khi Series A out và R15 giải quyết. MVP5 close ~2026-11-29 (M5-5 close). Đưa team sang MVP6 sớm. | PM | **Giữ 6 sprint** (an toàn cho GAP-2 phức tạp + NL hallucination tuning) |
| ~~**D1**~~ | ~~**K8s distribution**~~ | ~~(a) Managed EKS/GKE; (b) Self-hosted kubeadm; (c) Hybrid~~ | **DEFER MVP6** — **PO decision 2026-06-19: K8s không phải ưu tiên MVP5.** MVP5 chỉ readiness spike (ADR-050 draft + Helm skeleton). D1 chốt ở Pilot Readiness Review MVP6. | DevOps (M5-1 readiness spike only) | — (defer MVP6) |
| **D2** | **NL→BPMN model** | (a) Claude Sonnet (cloud, low latency, data leaves VN); (b) Local PhoGPT/Qwen 14B (on-prem, data residency OK, higher latency, GPU cost); (c) Hybrid routing (Claude default, local for PII-heavy) | **(c) Hybrid** — Decree 13 residency (GAP-2) cho PII, Claude cho quality; routes via existing M4-AI-02 ModelRouter (`gdpr_mode→on-prem`) | SA + Backend (M5-2 spike) | Default (a) Claude with PII-mask pre-step |
| ~~**D3**~~ | ~~**DR topology**~~ | ~~(a) Active-active; (b) Active-passive~~ | **XÓA** — **PO decision 2026-06-19: DR (ADR-053) OUT khỏi MVP5.** Làm MVP6+. Gate M5-G5 xóa. | — | — (xóa) |
| **D4** | **Billing unit** | (a) Per-building flat ($X/building/month); (b) Per-AI-token metered; (c) Per-sensor-count tiered; (d) Hybrid (base + overage) | **(d) Hybrid** — predictable base (per-building) + metered AI overage; aligns với `AiCostMetrics` và pilot-to-paid signal (≥2/3 bldg) | PM + Data Eng (M5-2 spike) | Default (a) per-building flat cho 2-3 pilot bldg |
| **D5** | **NL→BPMN safety policy** | (a) Operator approves every generated workflow; (b) Auto-deploy for template-matched, operator for novel; (c) Auto-deploy all with post-hoc audit | **(a) Operator approves every** — mirrors BR-010 BMS safety pattern; MVP5 risk too high for auto-deploy | SA (no spike, policy) | Default (a) |
| **D6** | **Compliance scope** | ISO 37120 only / + GRI / + Decree 13 (VN) / + SOC 2 | **ISO 37120 + GRI + Decree 13** — HCMC renewal + enterprise VN customers; **Decree 13 là P0 (GAP-2 residency)**; SOC 2 deferred post-Series A (MVP6+) | Sec contractor (M5-4) | Default ISO 37120 + GRI only (Decree 13 buộc phải có nếu PO chốt D7=C) |

> **Renumber note**: PM giữ số gốc (D1–D6) để tránh xáo trộn cross-reference với `pm-mvp5-conflict-resolution.md` và `mvp5-po-synthesis.md`. D7 (scope) + D8 (duration) là 2 decision mới. D3 (DR) đánh dấu XÓA, D1 (K8s) đánh dấu DEFER MVP6.

---

## 7. Gate definitions — MVP5 (**7 gates**, M5-G1 → M5-G8, **G5 XÓA**)  *(updated 2026-06-20 per PO decisions)*

Mirrors MVP4 G1–G10 pattern nhưng **G5 xóa (DR out)**. **G1/G2/G7/G8 đổi criteria** theo pivot build-50/test-2-3. Each gate has explicit criteria, owner, sprint, and PASS/FAIL artifact.

| Gate | Criterion | Owner | Sprint | PASS artifact |
|---|---|---|---|---|
| **M5-G1** *(đổi criteria)* | ~~K8s staging-ready~~ → **Compose HA sẵn sàng test 2-3 bldg + GAP-1 tenant isolation (P1) implemented + modular architecture proven** (25 bounded-context ArchTest PASS). Vault injecting all secrets (no `.env` in pods). Modular Monolith boundary test PASS. | DevOps + Compose HA tuner + SA | M5-1 | Compose HA deploy recording + ArchTest report + tenant isolation P1 code review + Vault secret injection audit |
| **M5-G2** *(đổi criteria — giảm scope)* | ~~Multi-tenant isolation fuzz 10,000 attempts~~ → **Tenant isolation fuzz — cover 2-3 tenant** (đủ prove correctness, không cần load). Tenant A → tenant B read attempts (API + cache + DB), 0 cross-tenant leaks; RLS verified at DB layer; CH RowPolicy verified. GAP-1 P1. | QA + Backend | M5-2 | Fuzz test report (2-3 tenant cover, 0 leak) + cache-key namespace audit + CH RowPolicy synthetic multi-tenant test (mitigate R16) |
| **M5-G3** *(không đổi)* | NL→BPMN UAT: 5 city operators author 20 workflows each via Vietnamese NL; ≥ 98% pass template validation; ≥ 80% operator-approve on first generation (BR-010) | Tester + UX | M5-3 | UAT sign-off sheet + 100-workflow audit log |
| **M5-G4** *(không đổi — hard gate cho EV M5-5)* | Billing metering accuracy: per-tenant metered usage (AI tokens, sensor count) reconciles to actual within 99.5% over 7-day shadow run; invoice auto-generated for 2-3 tenants | Data Engineer + QA | M5-4 | Reconciliation report + 2-3 sample invoices |
| ~~**M5-G5**~~ | ~~DR failover drill (RTO/RPO)~~ — **XÓA** (PO decision 2026-06-19: DR OUT MVP6+) | — | — | — |
| **M5-G6** *(không đổi)* | Compliance audit: ISO 37120 + GRI report generated end-to-end from live data; **Decree 13 (GAP-2) data residency attested (no PII leaves VN)**; OWASP 0 CVE CVSS ≥ 7 | Sec contractor + QA | M5-6 | Audit report + DPIA + OWASP scan clean |
| **M5-G7** *(đổi criteria)* | ~~Performance at scale 50-building 3,000 RPS~~ → **Functional + correctness test trên 2-3 bldg PASS** + **synthetic 50-tenant test PASS** (mitigate R16). Perf chỉ cần ~100-200 RPS trên Compose HA (R15 giải quyết). p95 ≤ 500ms, error ≤ 0.01%. Modular ArchTest PASS (KR5.5'). | QA + SA | M5-6 | JMeter 2-3 bldg report + synthetic 50-tenant test report + ArchTest boundary report |
| **M5-G8** *(đổi criteria)* | ~~**Series A readiness review** ($40K MRR + 50 bldg + 99.9% uptime)~~ → **Architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg chuyển paid)**. Gates M5-G1–G7 PASS. **KHÔNG còn Series A MRR, không 50 bldg, không 99.9% SLO.** Investor pitch = architecture validation story. | PM + PO + SA | M5-6 (2026-12-18) | Gate scorecard + pilot-to-paid conversion report (≥2/3 bldg) + ARR projection model + architecture validation deck |

**Declare-MVP5-DONE trigger (revised):** M5-G1–G4 + G6 + G7 all PASS → M5-G8 review (**architecture validation + product fit + pilot-to-paid ≥2/3**). ~~If M5-G8 FAILS on MRR, enter 90-day observation window~~ → If M5-G8 FAILS on pilot-to-paid (< 2/3 bldg), retros + iterate product fit trong buffer sprint hoặc hand-off MVP6 với pilot-to-paid gap analysis. **Series A readiness → MVP6+ khi đủ 10-50 bldg.**

---

## 8. Cross-references  *(updated 2026-06-20 per PO decisions)*

- Draft this plan replaces: `docs/mvp4/reports/mvp5-roadmap-draft.md`
- **Source of truth (PO decisions 2026-06-19/20) — bám các file này cho chi tiết:**
  - `docs/mvp5/brainstorm/mvp5-po-synthesis.md` — **briefing 1-trang cho PO** (redefine OKR, 7 gate, R16 top risk, D7+D8)
  - `docs/mvp5/brainstorm/pm-mvp5-conflict-resolution.md` — **PM reconciliation 3-scenario + per-sprint re-plan + R16**
  - `docs/mvp5/brainstorm/sa-mvp5-conflict-resolution.md` — SA góc nhìn hardening (151 SP non-negotiable)
  - `docs/mvp5/brainstorm/ba-mvp5-conflict-resolution.md` — BA góc nhìn vertical VN (~31 SP revised)
- MVP4 close-out: `docs/mvp4/reports/mvp4-summary-draft.md` (7/10 gates, 258 SP, code-complete 2026-06-12)
- Pilot carry-over: GAP-010 (gRPC IT), GAP-039/040/046 (CH Keeper, proto CI, SSL), Pact broker CI
- **GAP-2 (Decree 13 NL residency)** — P0 duy nhất còn lại sau DR out → spike D2 (M5-2), audit M5-G6
- **GAP-1 (tenant isolation)** — P0 → **P1** post-pivot (2-3 tenant tin cậy) → ADR-047 CH RowPolicy + synthetic multi-tenant test (R16 mitigation)
- Memory anchors: `feedback_mvp4_config_bugs` (Spring config test rule), `feedback_mvp2_demo_and_perf_lessons` (680 RPS ceiling — *R15 giải quyết ở 100-200 RPS test scope*), `feedback_sprint3_readiness` (cache isolation), `feedback_doc_vs_code_gap` (executable-artifact gate rule), `feedback_mvp4_kafka_reset_runbook` (Compose HA runbook)
- **MVP6 hand-off flags**: K8s cutover (D1), DR (ADR-053), Series A ($100K MRR), scale to 10-50 bldg, uptime SLO 99.9%, 3.000 RPS perf gate, microservices split, Council portal, Smart Waste, full immutable audit, DR-P2P, SASB — tất cả defer MVP6+. **R16 bắt buộc re-verify trước mở 10+ bldg (gate K2).**

---

*Authored by PM, 2026-06-18. **Revised 2026-06-20 per 4 PO decisions (2026-06-19/20): DR OUT, K8s DEFER MVP6, build-50/test-2-3, Series A OUT.** SA + PO review before M5-1 planning (2026-09-14). Each theme's epics must become ADRs (ADR-047+). Bám `mvp5-po-synthesis.md` + `pm-mvp5-conflict-resolution.md` cho detail.*
