# MVP5 PO Synthesis — Briefing 1-trang (pivot: build-50 / test-2-3)

| Field | Value |
|---|---|
| **Author** | PM (tổng hợp từ SA + BA + PM conflict-resolution) |
| **Date** | 2026-06-19 |
| **Audience** | **PO** — chốt scope MVP5 trước 2026-09-14 |
| **Status** | SYNTHESIS — sẵn sàng cho PO Decision D7 |
| **PO decisions đã chốt (2026-06-19)** | (1) **DR OUT** → MVP6+. (2) **K8s DEFER MVP6** → Compose HA làm môi trường test. (3) **BUILD như 50 buildings, TEST chỉ 2-3 buildings** (tài nguyên hạn chế) — giữ modular architecture đầy đủ. (4) **Series A ($100K MRR) OUT khỏi MVP5** → MVP6+. |

> **TL;DR cho PO** — MVP5 = **BUILD modular architecture đầy đủ (sẵn sàng scale 50+ bldg: tenant isolation, NL→BPMN, billing, ESG, LOTUS, EV) NHƯNG TEST chỉ trên 2-3 buildings** vì tài nguyên chưa đủ. DR + K8s + Series A đều → MVP6+. Mục tiêu MVP5 mới = **architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg)**. 3 góc nhìn SA/BA/PM đồng thuận Scenario C. Buffer **~95 SP (37%)** — PM đề xuất **rút ngắn xuống 4-5 sprint**. **7 gate** (G7/G8 đổi criteria). **Top risk R16**: code path scale chưa exercise ở 2-3 bldg → mitigate bằng synthetic multi-tenant test, MVP6 re-verify.

---

## 1. Triết lý MVP5 — Build-50 / Test-2-3

| | BUILD (như 50 bldg) | TEST (chỉ 2-3 bldg) |
|---|---|---|
| **Architecture** | Modular Monolith đầy đủ + tenant isolation (P1) + multi-tenant correctness | Load data 2-3 tenant |
| **Scale code path** | Sẵn sàng 50+ bldg (CH RowPolicy, Flink tenant function, billing metering) | Synthetic multi-tenant test (simulate 50 tenant via test data) |
| **Infra** | Compose HA (K8s defer MVP6) | Test trên Compose HA, ~100-200 RPS |
| **DR** | OUT (MVP6+) | — |
| **Series A** | OUT (MVP6+, khi 10-50 bldg) | pilot-to-paid signal chỉ |

→ **Không nợ kỹ thuật**: build đúng kiến trúc modular, chỉ là test scope nhỏ.

---

## 2. Mục tiêu MVP5 (redefine)

| Objective | KR chính | Gate |
|---|---|---|
| **O1** Build modular multi-tenant architecture (ready for 50+) | 25 bounded-context + tenant isolation (P1) implemented + synthetic 50-tenant test PASS | G1, G2 |
| **O2** NL→BPMN tiếng Việt an toàn | ≥30% workflow qua NL, ≥98% hợp lệ, operator-approve (BR-010) | G3 |
| **O3** Product fit + pilot-to-paid signal (redefine) | **≥2/3 pilot bldg chuyển paid** + ARR projection (không MRR tuyệt đối) | G4, G8 |
| **O4** Compliance enterprise | ISO 37120 + GRI + Decree 13, 0 CVE CVSS≥7 (uptime chỉ signal, không SLO 99.9% bắt buộc) | G6 |
| **O5** Chất lượng | ≥1.900 test 0 fail, 0 phantom DEV-DONE, modular ArchTest PASS | G7 |

> **Đã bỏ**: KR1.2 (3.000 RPS), KR3.1 ($40K MRR), KR4.3 (uptime 99.9%/90 ngày SLO), DR RTO/RPO — tất cả → MVP6+.

---

## 3. Scope MVP5 — KEEP / DEFER / OUT

### ✅ KEEP (MVP5) — ~169 SP committed, ~95 SP buffer (37%)
- **Tenant isolation (P1)**: GAP-1 CH/Flink — build đúng kiến trúc modular, fuzz test cover 2-3 tenant.
- **GAP-2 NL→BPMN residency** Decree 13 (ADR-049) — P0 duy nhất còn lại.
- **Theme A (~20 SP)**: Compose HA (môi trường test) + Vault + schema governance + observability OTel.
- **Theme B (42 SP)**: NL→BPMN (parser + template grounding + operator review UI + simulator, BR-010).
- **Theme C (36 SP)**: Billing GA (metering pipeline) + ISO 37120/GRI + audit log lite.
- **3 BA vertical (31 SP)**: ROI dashboard (M5-3) + LOTUS VN (M5-4) + EV Charging OCPP (M5-5).
- **Mobile v3.1**: offline conflict (LWW + version vector).
- **MVP4 carry-over**: GAP-010 gRPC IT, GAP-039/040/046.
- **Synthetic multi-tenant test** (mitigate R16): simulate 50 tenant via test data.

### ⏸️ DEFER MVP6
- **K8s cutover**, **scale to 10-50 bldg**, **Series A ($100K MRR)**, microservices split, Council portal, Smart Waste, full immutable audit, DR-P2P, SASB, **uptime SLO 99.9%**, **3.000 RPS perf gate**.

### 🚫 OUT (PO decision 2026-06-19)
- **Multi-region DR (ADR-053)** — OUT, MVP6+. Gate G5 xóa.
- **K8s production cutover** — DEFER MVP6.
- **Series A readiness** — OUT khỏi MVP5, MVP6+ khi đủ bldg.

---

## 4. 7 Gate (G5 xóa; G1/G2/G7/G8 đổi criteria)

| Gate | Criterion | Sprint |
|---|---|---|
| **M5-G1** | Compose HA sẵn sàng test 2-3 bldg + GAP-1 tenant isolation (P1) implemented + modular architecture proven | M5-1 |
| **M5-G2** | Tenant isolation fuzz — **cover 2-3 tenant** (đủ prove correctness) | M5-2 |
| **M5-G3** | NL→BPMN UAT (5 operator × 20 workflow, ≥98% hợp lệ) | M5-3 |
| **M5-G4** | Billing metering accuracy 99.5% + 3 invoice auto | M5-4 |
| ~~G5~~ | ~~DR failover~~ — **XÓA** | — |
| **M5-G6** | Compliance: ISO 37120 + GRI + Decree 13, OWASP 0 CVE CVSS≥7 | M5-6 |
| **M5-G7** | **Functional + correctness test trên 2-3 bldg PASS** + synthetic 50-tenant test PASS (không 3.000 RPS) | M5-6 |
| **M5-G8** | **Architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg)** — KHÔNG còn Series A | M5-6 |

---

## 5. Decision PO chốt trước 2026-09-14

| # | Decision | Recommend | Default |
|---|---|---|---|
| **D7** ⭐ | **Scope scenario**: A Hardening-first / B MRR-first / **C Balanced (build-50/test-2-3)** | **C** | **C** |
| **D8** ⭐ (MỚI) | **MVP5 duration**: rút ngắn **4-5 sprint** / giữ 6 sprint + buffer lớn | **Rút ngắn 4-5 sprint** | Giữ 6 sprint |
| ~~D1~~ | ~~K8s distro~~ | **DEFER MVP6** | — |
| D2 | NL→BPMN model | Hybrid (gdpr_mode→on-prem) | Hybrid |
| D4 | Billing unit | Hybrid (base + AI overage) | Hybrid |
| D5 | NL safety policy | Operator-approve-all (BR-010) | Operator-approve-all |
| D6 | Compliance scope | ISO 37120 + GRI + Decree 13 | ISO 37120 + GRI |

(D3 DR — XÓA. D1 K8s — DEFER MVP6.)

---

## 6. Rủi ro nổi bật (post-pivot)

| # | Rủi ro | Sev | Mitigation |
|---|---|---|---|
| **R16** ⭐ (TOP) | **Build-for-50 nhưng test-2-3 → code path scale chưa exercise** (race condition, CH partition hotspot, billing quota, NL routing) có thể break ở 10-50 bldg | **HIGH** | (1) Synthetic multi-tenant test (simulate 50 tenant via test data); (2) ADR-047 CH RowPolicy có synthetic test; (3) **MVP6 bắt buộc re-verify trước mở 10+ bldg** (gate K2). |
| **R11** | BA vertical ship trước GAP-1 hoàn thiện | MED (giảm) | EV dependency trên G2; 2-3 tenant tin cậy nên severity thấp |
| ~~R15~~ | ~~3.000 RPS Compose HA~~ | **GIẢI QUYẾT** | Test 2-3 bldg chỉ cần ~100-200 RPS |
| ~~R1~~ | ~~K8s zero-experience~~ | **GIẢM** | K8s defer |
| ~~R14~~ | ~~Investor đòi enterprise infra~~ | **GIẢM LOW** | MVP5 không còn mục tiêu Series A → pitch là architecture validation |

---

## 7. Timeline (đề xuất rút ngắn — D8)

```
Pilot (Aug 03–31) → Readiness Review (Sep 07) → PO chốt D7+D8 (Sep 14)
  ↓
M5-1 (Sep 21) Compose HA + GAP-1 tenant isolation (P1) + Vault          [G1]
M5-2 (Oct 05) NL POC + GAP-2 residency + billing skeleton                [G2]
M5-3 (Oct 19) NL prod + ROI dashboard                                    [G3]
M5-4 (Nov 02) Billing GA + LOTUS + audit lite                            [G4]
M5-5 (Nov 16) EV Charging + mobile + synthetic 50-tenant test
[M5-6 (Nov 30) — optional nếu giữ 6 sprint: hardening + G6/G7/G8]
  ↓
MVP5 close: Architecture validation + product fit (≥2/3 pilot-to-paid)
  → MVP6: K8s cutover + scale 10-50 bldg + Series A
```

---

## 8. PO — 3 điểm acknowledge + 2 câu hỏi

**Acknowledge:**
1. MVP5 **KHÔNG** deliver: DR, K8s scale, 3.000 RPS, Series A MRR — tất cả MVP6+.
2. MVP5 deliver: **modular architecture proven + 2-3 bldg chạy thật + product fit signal**.
3. **R16**: build đúng nhưng chưa exercise ở scale → synthetic test + MVP6 re-verify bắt buộc.

**Câu hỏi:**
1. **Chốt D7 = C (Balanced, build-50/test-2-3)?**
2. **Chốt D8 — rút ngắn MVP5 xuống 4-5 sprint** (recommend) hay giữ 6 sprint?

Default nếu PO không chốt 2026-09-14 = **D7=C, D8=giữ 6 sprint**.

---

*Synthesis bởi PM, 2026-06-19. Nguồn: `sa-mvp5-conflict-resolution.md`, `ba-mvp5-conflict-resolution.md`, `pm-mvp5-conflict-resolution.md` (pivot build-50/test-2-3, DR out, K8s defer, Series A out).*
