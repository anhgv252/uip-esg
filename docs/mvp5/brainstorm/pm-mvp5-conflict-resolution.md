# MVP5 PM Conflict Resolution — Timeline & SP Budget Reconciliation

| Field | Value |
|---|---|
| **Author** | Project Manager |
| **Date** | 2026-06-19 |
| **Audience** | PO, SA, BA (chốt scope MVP5 trước 2026-09-14) |
| **Status** | PM RECOMMENDATION — đối thoại với `sa-mvp5-conflict-resolution.md` và `ba-mvp5-conflict-resolution.md` |
| **Predecessor** | `pm-mvp5-master-plan.md` (258 SP, 6 sprint) |

> **TL;DR** — **MVP5 pivot (PO decision 2026-06-19)**: **BUILD như 50 buildings** (kiến trúc modular đầy đủ: multi-tenant correctness, tenant isolation, NL→BPMN, billing, ESG, LOTUS) **nhưng TEST chỉ trên 2-3 buildings** vì tài nguyên chưa đủ. 3 PO decision đã chốt: (1) **DR OUT** → MVP6+; (2) **K8s DEFER MVP6** → Compose HA làm môi trường test; (3) **Scale target 50+ bldg → 2-3 bldg (build-ready for 50)**. Hệ quả: **R15 (Compose HA 3.000 RPS) GIẢI QUYẾT** — test chỉ cần ~100-200 RPS; **tenant isolation GAP-1 P0→P1** (2-3 tenant tin cậy nhưng vẫn build đúng kiến trúc); **KR1.2 3.000 RPS BỎ**; **Series A ($100K MRR) OUT khỏi MVP5** → MVP6+ khi 10-50 bldg. MVP5 mục tiêu mới = **architecture validation + product fit + pilot-to-paid signal**. PM đề xuất **Scenario C Balanced**. PO chốt D7 trước 2026-09-14, default = C.

---

## 1. Conflict summary — tại sao 3 plan không vừa nhau

| Source | SP claim | Bản chất |
|---|---|---|
| **PM** (`master-plan`) | **258 SP** (6 sprint × 43) | Envelope tối đa capacity, 17% buffer |
| **SA** (`sa-review` + `sa-conflict-resolution`) | **129 SP** realistic hardening (Theme A 57 + B 44 + C 28) + 22 SP carry-over = **151 SP non-negotiable** | "Draft under-estimated ~43%, 3 P0 là hard gate" |
| **BA** (`ba-gap` gốc) | **+43 SP** vertical VN | "Persona trả tiền + LOTUS VN killer feature" |
| **BA** (`ba-conflict-resolution` revised) | **~31 SP** vertical (nhường 12 SP) | "Giữ LOTUS+ROI+EV+audit-lite, defer Council/Waste/DR-P2P" |

**Toán học (sau pivot 2-3 bldg)**: Build full architecture (NL→BPMN 42 + billing/ESG 36 + tenant isolation P1 + BA vertical 31 + mobile 18 + carry-over 22 + Vault/schema/observability ~20) = **~169 SP committed**. Bỏ perf-at-scale (G7 3.000 RPS ~6 SP) + giảm tenant fuzz (~4 SP) + bỏ K8s cutover (~25 SP) → buffer thực **~95 SP (37%)**.

> **3 PO decision đã chốt (2026-06-19)**:
> 1. **DR (ADR-053) OUT khỏi MVP5** — MVP6+. Gate G5 xóa.
> 2. **K8s DEFER MVP6** — Compose HA làm môi trường test. Theme A giảm 55→**~20 SP** (chỉ Vault + schema + observability + GAP-1, không K8s readiness nặng).
> 3. **Scale 50+ bldg → 2-3 bldg (BUILD-ready for 50)** — build modular architecture đầy đủ, test chỉ 2-3 tenant.
>
> **Hệ quả pivot**:
> - **R15 GIẢI QUYẾT** — test 2-3 bldg chỉ cần ~100-200 RPS, Compose HA dư sức. KR1.2 (3.000 RPS) **BỎ**.
> - **GAP-1 tenant isolation P0→P1** — vẫn implement (build đúng kiến trúc modular) nhưng không còn "breach-waiting" vì 2-3 tenant tin cậy. Fuzz test giảm từ 10K → cover 2-3 tenant (~4 SP tiết kiệm).
> - **Series A ($100K MRR) OUT khỏi MVP5** → MVP6+ khi 10-50 bldg. MVP5 mục tiêu = **architecture validation + product fit + pilot-to-paid signal**. OKR O3 redefine.

---

## 2. Reconciliation approach — 3 scenario PO chọn

### Scenario A — Hardening-first (SA-friendly)
- **Ship**: 2 P0 (GAP-1 CH tenant, GAP-2 NL residency) + Vault + schema governance + observability + billing GA + ISO 37120 + Compose HA hardening.
- **Defer MVP6**: HẾT BA vertical (LOTUS, ROI, EV, Waste, Council, audit, microservices split) + **K8s cutover**.
- **DR / K8s**: DR OUT; K8s defer (chỉ readiness spike).
- **SP**: ~148 (SA min sau K8s defer) + 11 (mobile) + 9 (spike) = **~168 SP committed**, buffer ~90 SP (35%).
- **MRR impact**: pilot-to-paid conversion rủi ro (< 3/5) — không có ROI dashboard/LOTUS → building owner không justify renewal năm 2. **KR3.4 at risk**.
- **Series A**: trượt sang H2/2027.
- **Gate impact**: G2/G6/G8 PASS dễ; **G8 (Series A readiness) FAIL trên MRR**.

### Scenario B — MRR-first (BA-friendly) ⚠️ PM KHÔNG recommend
- **Ship**: LOTUS + ROI + EV + Waste + Council + audit + NL→BPMN + billing + mobile.
- **Accept debt**: GAP-1 (CH tenant) chỉ ở pilot cohort trusted; GAP-2 (residency) dùng Claude với PII-mask weak.
- **DR / K8s**: DR OUT; K8s defer.
- **SP**: ~148 (SA min) + 43 (BA full) = **~191 SP committed**, buffer ~67 SP.
- **Risk**: **breach tenant ở commercial cohort** (GAP-1 chưa hardening) + **Decree 13 violation** (GAP-2) → fail audit M5-G6 → Series A dies on compliance.
- **Gate impact**: G6 (compliance) **FAIL**.

### Scenario C — Balanced (PM RECOMMENDATION) ✅
- **Ship (BUILD như 50 bldg)**: Modular Monolith đầy đủ + **tenant isolation (P1)** + GAP-2 NL residency + NL→BPMN + billing GA + ISO 37120 + **3 BA vertical** (LOTUS M5-4, ROI M5-3, EV M5-5) + audit-lite + mobile v3.1 + Compose HA (môi trường test).
- **Test trên 2-3 bldg**: chỉ load 2-3 tenant, fuzz test tenant cover 2-3, perf test ~100-200 RPS (không 3.000 RPS).
- **Defer MVP6**: K8s cutover, microservices split, Council portal, Smart Waste, full audit, DR-P2P, SASB, **scale to 10-50 bldg, Series A**.
- **OUT**: DR (MVP6+), K8s cutover (MVP6), **Series A / $100K MRR** (MVP6+ khi đủ bldg).
- **SP**: ~169 SP committed, **buffer ~95 SP (37%)**.
- **Mục tiêu**: architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg chuyển paid).
- **Gate impact**: G1 (Compose HA + tenant isolation), G2 (fuzz 2-3 tenant), G3, G4, G6 PASS; **G7 đổi từ "3.000 RPS" → "functional correctness test 2-3 bldg"**; **G8 đổi từ "Series A" → "Architecture validation + product fit"**.

**PM recommend C** vì: (1) build đúng kiến trúc modular (sẵn sàng scale, không nợ kỹ thuật), (2) test thực tế với tài nguyên hiện có (2-3 bldg), (3) DR/K8s/Series A đều premature cho 2-3 bldg → defer hợp lý, (4) buffer 37% rất dư, (5) R15 giải quyết, GAP-1 P1 vẫn build.

---

## 3. Per-sprint re-plan (Scenario C — recommendation)

Capacity 43 SP/sprint. Cột = bucket SP.

| Sprint | Dates (2026) | Theme A (Compose HA + CH + Vault) | Theme B (NL→BPMN) | Theme C (billing/ESG) | BA vertical | Mobile | Tech-debt | Spike/ADR | Buffer | Total |
|---|---|---|---|---|---|---|---|---|---|---|
| **M5-1** | Sep 21–Oct 04 | **14** (GAP-1 CH RowPolicy P1 + Compose HA test env + Vault) | 0 | 0 | 0 | 0 | 2 | 4 (S1 CH PoC, S3 K8s readiness-only, **S7 synthetic 50-tenant test setup**) | 23 | 43 |
| **M5-2** | Oct 05–Oct 18 | 6 (GAP-1 Flink tenant + Vault rotation) | **18** (NL parser POC + GAP-2 residency spike) | 8 (billing skeleton) | 0 (BA prep LOTUS checklist) | 2 | 1 | 2 (S2 LLM bench) | 6 | 43 |
| **M5-3** | Oct 19–Nov 01 | 4 (schema reg ADR-051 + observability OTel) | **18** (BPMN synth + validator + simulator) | 4 | **5 (ROI dashboard)** | 3 | 2 | 1 (K8s Helm skeleton) | 6 | 43 |
| **M5-4** | Nov 02–Nov 15 | 2 | 4 | **18** (billing GA + ISO 37120 + GRI) | **8 (LOTUS 6 + audit-lite 2)** | 4 | 5 | 0 | 2 | 43 |
| **M5-5** | Nov 16–Nov 29 | 2 (Compose HA replica tuning) | 2 | 4 (billing accuracy) | **10 (EV Charging OCPP)** | 6 | 10 | 0 | 9 | 43 |
| **M5-6** | Nov 30–Dec 13 | 2 (Compose HA hardening) | 0 | 2 | 0 | 3 | **15** (OWASP + regression burn-down) | 0 | **21** | 43 |
| **TOTAL** | | **20** | **42** | **36** | **23** (+8 BA UAT spread) = **31** | **18** | **35** | **7** | **69** | **258** |

> **Lưu ý pivot 2-3 bldg + K8s defer + DR out**:
> - Theme A giảm 55→**20 SP** (chỉ Vault + schema + observability + GAP-1 tenant isolation P1; bỏ K8s cutover, bỏ K8s readiness nặng).
> - **Spike S6 (Compose HA scale verify) BỎ** — R15 giải quyết, test 2-3 bldg chỉ cần ~100-200 RPS.
> - Buffer column (69) đã bao gồm DR-freed + K8s-freed + scale-freed. Buffer thực **~95 SP (37%)**.
> - Buffer lớn → PM đề xuất **rút ngắn MVP5 xuống 4-5 sprint** (xem §3a) HOẶC giữ 6 sprint với buffer lớn phòng vertical thêm / GAP-2 phức tạp.

### 3a. PM đề xuất rút ngắn MVP5 (tùy PO)

Buffer 95 SP (37%) quá dư cho 6 sprint. 2 option PO chọn:

| Option | Sprint count | Lý do |
|---|---|---|
| **Rút ngắn 4-5 sprint** (PM recommend) | M5-1→M5-4/5 (~170-215 SP) | Build architecture + 2-3 bldg test không cần 6 sprint. Series A là MVP6 anyway. Đưa team sang MVP6 sớm. |
| Giữ 6 sprint + buffer lớn | 6 sprint (~169 SP committed, 95 SP buffer) | An toàn cho GAP-2 residency phức tạp + NL→BPMN hallucination tuning. Có thể absorb thêm Smart Waste vertical. |

**PM recommend rút ngắn 4-5 sprint** — không lý do kéo 6 sprint khi Series A out và R15 giải quyết.

---

## 4. Gate sequencing impact (Scenario C)

| Gate | PM gốc | Scenario C | Thay đổi |
|---|---|---|---|
| M5-G1 (K8s staging) | M5-1 | **M5-1 — đổi criteria** | ⚠️ Đổi từ "K8s staging-ready" → **"Compose HA sẵn sàng test 2-3 bldg + GAP-1 tenant isolation (P1) implemented + modular architecture proven"**. |
| M5-G2 (tenant isolation fuzz) | M5-2 | **M5-2 — giảm scope** | Fuzz test giảm từ 10K → cover **2-3 tenant** (đủ prove correctness, không cần load). GAP-1 P1. |
| M5-G3 (NL→BPMN UAT) | M5-3 | M5-3 | Không đổi |
| M5-G4 (billing accuracy) | M5-4 | M5-4 | Không đổi — **hard gate cho EV M5-5** |
| ~~M5-G5 (DR failover)~~ | ~~M5-5~~ | **XÓA** | DR OUT khỏi MVP5. |
| M5-G6 (compliance/OWASP) | M5-6 | M5-6 | Không đổi — GAP-2 residency phải xong trước |
| **M5-G7** (perf 3000 RPS) | M5-6 | **M5-6 — đổi criteria** | ⚠️ Đổi từ "3.000 RPS" → **"Functional + correctness test trên 2-3 bldg PASS"** (perf chỉ cần ~100-200 RPS). KR1.2 bỏ. R15 giải quyết. |
| **M5-G8** (Series A readiness) | M5-6 | **M5-6 — đổi criteria** | ⚠️ Đổi từ "Series A ($40K MRR + 50 bldg)" → **"Architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg)"**. Series A → MVP6. |

**MVP5 còn 7 gate**: M5-G1, G2 (giảm scope), G3, G4, G6, G7 (đổi criteria), G8 (đổi criteria).

**PO cần acknowledge**:
- MVP5 **KHÔNG còn** DR RTO, K8s scale proof, 3.000 RPS, hay Series A MRR — tất cả → MVP6+.
- MVP5 deliverable = **modular architecture proven + 2-3 bldg chạy thật + product fit signal (≥2/3 pilot-to-paid)**.

---

## 5. Risk register update — 3 risk mới do reconciliation

| # | Risk mới | Sev | Prob | Owner | Mitigation |
|---|---|---|---|---|---|
| **R1** | ~~K8s migration delays — team zero K8s experience~~ | ~~HIGH~~ | ~~HIGH~~ | **GIẢM MED/MED** | K8s defer MVP6 → R1不再是 critical path. Chỉ còn K8s readiness spike (Helm skeleton) — contractor down-tier. Rủi ro thật sự chuyển sang **R16** (build-for-50 chưa exercise ở scale). |
| **R11** | ~~BA vertical ship trước tenant isolation = breach debt~~ | ~~HIGH~~ | ~~MED~~ | **GIẢM MED/LOW** | Pivot 2-3 bldg: tenant tin cậy + GAP-1 P1 vẫn implement. Breach risk giảm. EV dependency trên G2 vẫn giữ nhưng severity thấp. |
| **R12 (đã gỡ)** | ~~K8s contractor trễ~~ | — | — | **GỠ** | K8s defer, không cutover. |
| **R14** | DR out + K8s defer + Series A out → investor đòi enterprise-grade infra ngay | LOW | MED | PM + PO | Giảm severity: MVP5 không còn mục tiêu Series A → investor pitch là "architecture validation", không phải "Series A readiness". Series A là MVP6 story. |
| **R15 (đã gỡ)** | ~~3.000 RPS trên Compose HA chưa verify~~ | — | — | **GỖI — GIẢI QUYẾT** | Pivot 2-3 bldg: test chỉ cần ~100-200 RPS, Compose HA dư sức. KR1.2/S6 bỏ. |
| **R16** ⭐ (MỚI — top risk post-pivot) | **Build-for-50 nhưng test-2-3 → code path scale chưa exercise** → tenant isolation / billing metering / NL routing chạy đúng ở 2-3 bldg nhưng có thể break ở 10-50 bldg (race condition, partition hotspot, quota) | **HIGH** | **MED** | SA + QA | (1) Giữ tenant fuzz + load test synthetic (simulate 50 tenant via test data, không cần 50 bldg thật); (2) ADR-047 CH RowPolicy phải có synthetic multi-tenant test; (3) MVP6 bắt buộc re-verify trước mở commercial 10+ bldg — gate K2. |

(R2-R10 từ `master-plan` §5 giữ nguyên, trừ R3/R7 giảm (DR out). **R16 là top risk mới** — thay R15: rủi ro "build đúng nhưng chưa exercise ở scale".)

---

## 6. PO Decision Log — D7 mới + framing 6 decision gốc

| # | Decision | Options | PM Recommendation | Default if no PO decision by 2026-09-14 |
|---|---|---|---|---|
| ~~D1~~ | ~~K8s distro~~ | ~~EKS managed / k3s-colo / hybrid~~ | **DEFER MVP6** — PO decision 2026-06-19: K8s không phải ưu tiên MVP5. MVP5 chỉ readiness spike (ADR-050 draft + Helm skeleton). D1 chốt ở Pilot Readiness Review MVP6. | — |
| D2 | NL→BPMN model | Claude / on-prem PhoGPT / **hybrid** | **Hybrid** (gdpr_mode→on-prem) | Hybrid |
| ~~D3~~ | ~~DR topology~~ | ~~Active-active / active-passive~~ | **XÓA** — DR out khỏi MVP5 (PO decision 2026-06-19). Làm MVP6+. | — |
| D4 | Billing unit | Per-building / per-token / **hybrid** | **Hybrid** (base + AI overage) | Hybrid |
| D5 | NL safety policy | Operator-approve-all / auto-template / auto-all | **Operator-approve-all** (BR-010) | Operator-approve-all |
| D6 | Compliance scope | ISO 37120 / +GRI / +Decree 13 / +SOC2 | **ISO 37120 + GRI + Decree 13** | ISO 37120 + GRI |
| **D7** ⭐ **(MỚI)** | **Scope scenario** | A Hardening-first / B MRR-first / **C Balanced** | **C Balanced** (P0 SA hard gate + 3 BA vertical, DR đã out, defer microservices) | **C Balanced** |

**PM framing cho PO**: D7 là decision **chốt scope**, 5 decision kia (D1, D2, D4, D5, D6) là decision **thực thi**. D7 phải chốt **đầu tiên**. D3 đã xóa khỏi scope. (Renumber: nếu PO muốn numbering liền, D4→D3, D5→D4, D6→D5, D7→D6 — PM đề xuất giữ số gốc tránh xáo trộn cross-reference với `master-plan`.)

---

## 7. Hiring dependency (Scenario C)

| Role | PM gốc | Scenario C thay đổi | Lý do |
|---|---|---|---|
| K8s/SRE contractor | M5-1 (1.0 FTE) | **Giảm 1.0 → 0.5 FTE**, down-tier sang "DevOps generalist + Compose HA tuner" | K8s defer → không cutover. Chỉ cần Compose HA hardening + Helm skeleton readiness. **Tiết kiệm ~0.5 FTE**. |
| Data Engineer (billing) | M5-2 (1.0) | **Không đổi** — M5-2 | Billing GA M5-4 hard gate cho EV |
| Sec/compliance contractor | M5-4 (0.5) | **M5-4** — load tăng do GAP-2 residency audit | Scenario C giữ Decree 13 audit (D6) → Sec contractor cần thêm 0.5 sprint M5-3 prep |
| **BA (vertical owner)** | — | **PM đề xuất thêm 0.5 BA từ M5-2** (LOTUS checklist + ROI acceptance + EV OCPP spec) | BA vertical 31 SP cần dedicated ownership |
| **QA — Synthetic multi-tenant test owner** | — | **MỚI — 0.5 FTE M5-1→M5-5** (overlay QA hiện có) | R16 top risk (build-for-50 chưa exercise ở scale) → cần synthetic 50-tenant test (simulate 50 tenant via test data, không cần 50 bldg thật). Có thể overlay lên QA hiện có. |

**Hiring decision mới**: BA vertical owner (0.5 FTE M5-2→M5-5) + QA synthetic-test owner (0.5 FTE M5-1→M5-5). Nếu PO không approve BA owner → BA vertical cắt về 23 SP (bỏ EV). Nếu PO không approve QA synthetic owner → R16 không mitigate → gate K2 (MVP6 re-verify) at-risk.

> **K8s defer + DR out → tiết kiệm hiring**: K8s contractor giảm 0.5 FTE + không cần DR specialist. Pool ứng viên rộng hơn. Net hiring **giảm ~0.5 FTE** vs master-plan gốc.

---

## 8. Recommendation cho PO (1 đoạn)

> **PM recommend Scenario C Balanced**. Lý do 1 câu: **BUILD modular architecture đầy đủ (như 50 bldg: tenant isolation P1, NL→BPMN, billing, ESG, LOTUS, EV) nhưng TEST chỉ trên 2-3 bldg vì tài nguyên hạn chế — DR out + K8s defer + Series A out → MVP6+, giải phóng ~90 SP → buffer ~95 SP (37%) cho R16 (build-for-50 chưa exercise ở scale, mitigate bằng synthetic multi-tenant test)**. PO chốt D7=C trước 2026-09-14. **PM đề xuất rút ngắn MVP5 xuống 4-5 sprint** (Series A out + R15 giải quyết → không cần 6 sprint). Top risk R16 = code path scale chưa exercise — bắt buộc MVP6 re-verify trước mở 10+ bldg.

---

## 9. Open questions cho SA / BA

| # | Question | Cho ai |
|---|---|---|
| Q1 | Scenario C absorb 31 SP BA — SA confirm envelope (151 SA + 31 BA + 11 mobile + 9 spike = 202, buffer ~58 với DR-freed) có đúng không? | SA |
| Q2 | DR out → M5-5 Theme A còn "autoscale drill" (6 SP). SA confirm autoscale drill đủ cho G7 (perf 3000 RPS) mà không cần DR component? | SA + DevOps |
| Q3 | BA vertical owner 0.5 FTE — BA confirm 31 SP cần dedicated owner, hay BA agent hiện đủ cover? | BA |
| Q4 | Nếu PO chọn D7=A (hardening-first), PM cần re-plan: buffer 87 SP dùng cho gì? (microservices split spike + tech-debt sâu + Series A MRR bridge loan?) | PO + PM |

---

*Authored by PM, 2026-06-19. Đối thoại với `sa-mvp5-conflict-resolution.md` và `ba-mvp5-conflict-resolution.md`. PO chốt D7 trước 2026-09-14 (M5-1 week).*
