# MVP5 BA Conflict Resolution — Scope: Modular-full vs Test-2-3 (build-50/test-2-3)

| Field | Value |
|---|---|
| **Author** | Business Analyst |
| **Date** | 2026-06-20 |
| **Audience** | PO, SA, PM (chốt scope MVP5 trước PO Decision Log 2026-09-14) |
| **Status** | BA RECOMMENDATION (UPDATED 2026-06-20) — đối thoại trực tiếp với `sa-mvp5-conflict-resolution.md`, `pm-mvp5-conflict-resolution.md`, và `mvp5-po-synthesis.md` |
| **Lens** | Sản phẩm/thị trường — cái gì unlock **product fit + pilot-to-paid signal (≥2/3 bldg)** sớm nhất mà không phá gate kỹ thuật |

> *(updated 2026-06-20 per PO decisions: build-50/test-2-3, Series A out, K8s defer, DR out)*
>
> **TL;DR** — Conflict cốt lõi đã dịch chuyển: không còn "MRR-first vs Hardening-first ở 50 building" mà là **"BUILD modular architecture đầy đủ (như 50 bldg) vs TEST chỉ 2-3 bldg"**. Series A ($100K MRR) đã **OUT khỏi MVP5** → MVP6+; mục tiêu MVP5 mới = **architecture validation + product fit + pilot-to-paid signal (≥2/3 pilot bldg)**. BA giữ **LOTUS VN + Building Owner ROI dashboard + EV Charging + audit-lite (~31 SP)** — 4 vertical này đều fit với mode build-50/test-2-3 (ROI/LOTUS cho building owner, EV nếu 1 trong 2-3 bldg có trạm sạc). BA **nhường**: Council portal / Smart Waste / full-audit / DR-P2P / SASB → MVP6. **Không chạm** GAP-1 (giờ P1, không còn BLOCK — chỉ dependency nhẹ) và GAP-2 (P0 duy nhất, hard gate). Đề xuất BA final = **~31 SP vertical + 5 SP BA-side acceptance/LOTUS checklist**. PO chốt **D7 = C** và **D8 = rút ngắn 4-5 sprint** trước 2026-09-14.

---

## 1. Mục tiêu BA resolve *(updated 2026-06-20 per PO decisions: build-50/test-2-3, Series A out, K8s defer, DR out)*

Conflict không còn là "BA vs SA tranh nhau 258 SP cho MRR vs hardening ở 50-building scale", mà là **thứ tự đầu tư trong envelope rỗng (buffer ~95 SP / 37%) sau khi 4 PO decision giải phóng ~90 SP**:

- **4 PO decisions đã chốt (2026-06-19/20)**:
  1. **DR (ADR-053) OUT khỏi MVP5** → MVP6+. Gate G5 xóa.
  2. **K8s DEFER MVP6** → Compose HA làm môi trường test 2-3 bldg.
  3. **BUILD như 50 buildings nhưng TEST chỉ 2-3 buildings** — modular architecture đầy đủ, tenant isolation **P0 → P1** (vẫn build đúng kiến trúc modular, chỉ là test scope nhỏ).
  4. **Series A ($100K MRR) OUT khỏi MVP5** → MVP6+. MVP5 = architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg). Không còn mục tiêu $100K MRR tuyệt đối trong MVP5.

- **BA axis (đã redefine)**: ~~MRR / pilot-to-paid conversion ở 50 bldg~~ → **product fit + pilot-to-paid signal (≥2/3 pilot bldg chuyển paid) + ARR projection**. Cái gì khiến 2-3 pilot building owner thấy ROI đủ để ký năm 2 + tạo signal cho MVP6 mở 10-50 bldg.
- **SA axis**: build modular architecture sẵn sàng scale 50+ bldg (tenant isolation P1, NL→BPMN residency P0, billing metering) — *test chỉ cần cover 2-3 tenant, không cần 3.000 RPS*.

Cả hai **đều cần cho MVP6 (scale 10-50 bldg + Series A)**, nhưng **không cùng vừa cần trong MVP5** nếu BA chọn sai vertical. BA chọn vertical nào **cao pilot-to-paid signal / thấp-dependency SA nhất**, và **nhường P0 của SA là hard gate** (không mặc cả).

**Nguyên tắc BA adopt (đã cập nhật):**
1. **P0 duy nhất của SA (GAP-2 NL residency) là điều kiện cần** — không có Decree 13 residency thì 2-3 pilot building owner (đặc biệt HCMC government) không ký. BA không trade-away P0. **GAP-1 giờ là P1** — vẫn build đúng kiến trúc modular nhưng không còn "breach-waiting" vì 2-3 tenant tin cậy.
2. **Vertical phải tự chứng minh pilot-to-paid ROI/building** trước khi vào scope — cái nào cần nhiều hardening SA thì defer. Với 2-3 bldg, ROI/LOTUS cho building owner, EV chỉ fit nếu 1 bldg có trạm sạc.
3. **Pilot cohort (2-3 building) là test bed + signal generator** — chấp nhận ship vertical song song với GAP-1 (P1) vì tenant tin cậy, nhưng **PHẢI** re-verify ở MVP6 trước khi mở commercial 10+ bldg (gate K2 — xem §6).
4. **Không còn áp lực $100K MRR tuyệt đối trong MVP5** — Series A là MVP6 story. BA pitch là "product fit + pilot-to-paid signal", không phải "MRR achievement".

---

## 2. Matrix ưu tiên 7 đề xuất BA *(updated 2026-06-20 per PO decisions: build-50/test-2-3, Series A out, K8s defer, DR out)*

Trục đánh giá (đã redefine trục X):
- **Trục X — Pilot-to-paid signal impact** (1–5): ~~MRR impact tuyệt đối~~ → **tác động tới pilot-to-paid conversion (≥2/3 bldg) + ARR projection**. Cái gì khiến 2-3 pilot building owner ký năm 2 + tạo signal khả tín cho MVP6 Series A.
- **Trục Y — SA dependency** (1–5): 5 = BLOCK bởi GAP-1/GAP-2/GAP-3 (cần hardening trước); 1 = độc lập, ship được song song. **Lưu ý post-pivot**: GAP-1 giờ là **P1** (không còn BLOCK — chỉ dependency nhẹ), chỉ GAP-2 còn P0.
- **Verdict**: `KEEP` (MVP5) / `DEFER` (MVP6) / `CUT`.

| # | Đề xuất BA | SP | Pilot-to-paid (X) | SA-dep (Y) | Block bởi | Verdict | Lý do |
|---|---|---|---|---|---|---|---|
| 1 | **LOTUS VN evidence pack** | 6 | 5 | 2 | — | **KEEP** | Killer feature thị trường VN, không đối thủ nào có. Độc lập với CH tenant (chỉ đọc ESG data đã aggregate). Pilot owner (2-3 bldg VN) muốn LOTUS để renew năm 2 → **pilot-to-paid signal trực tiếp**. Fit build-50/test-2-3: LOTUS logic không phụ thuộc số tenant. |
| 2 | **Building Owner ROI dashboard** | 5 | 5 | 2 | — | **KEEP** | Persona trả tiền trực tiếp. Đọc usage + cost data đã có (`AiCostMetrics`). **Với 2-3 bldg**: ROI dashboard càng quan trọng — building owner cần evidence rõ ràng trên cohort nhỏ để justify renewal năm 2. Không cần tenant isolation mới (P1 đủ) nếu chỉ show data của chính tenant. |
| 3 | **EV Charging + Parking (OCPP)** | 10 | 4 | 3 | GAP-4 (schema) nhẹ | **KEEP** | ~~$15-30K ARR/building ở 50 bldg~~ → với 2-3 bldg: **chỉ fit nếu 1 trong 2-3 pilot bldg có trạm sạc** (verify với PO pilot cohort). Phụ thuộc billing metering (Theme C) + OCPP schema — SA cần thêm `billing.events` Kafka topic (ADR-054) → phải đi **sau** billing GA (M5-4). Nếu pilot cohort không có bldg với trạm sạc → DEFER MVP6. |
| 4 | **Council transparency portal** | 4 | 3 | 2 | — | **DEFER MVP6** | Persona ký ngân sách renew — quan trọng nhưng **không phải pilot-to-paid signal trigger** (renew cycle 12 tháng, sau pilot). Signal gián tiếp, không trực tiếp. |
| 5 | **Smart Waste (Citenco/URSVN)** | 6 | 3 | 2 (giảm — GAP-1 giờ P1) | — | **DEFER MVP6** | Trojan horse vào UBND quận — nhưng customer là **quận**, không phải pilot building owner. Pilot-to-paid signal trigger là building owner, không phải district. Defer. |
| 6 | **Compliance audit trail (immutable)** | 4 | 4 | 3 (giảm — GAP-1 giờ P1) | GAP-1 (hash-chain tenant-scoped) nhẹ | **KEEP (lite)** — 2 SP chỉ, defer full 4 SP | Table-stakes cho Tier-3 (MVP6), nhưng **full immutable hash-chain cần CH Append-Only engine** (SA ADR-047 territory). BA giữ: **audit log tenant-scoped PostgreSQL** (2 SP) trong M5-4, full immutable defer MVP6. |
| 7 | **Demand Response / P2P** | 8 | 4 | 4 (giảm — GAP-1 giờ P1, nhưng EVN regulatory vẫn BLOCK) | EVN regulatory | **CUT → MVP6** | BA đã đề xuất cắt từ `ba-mvp5-gap.md` §6. EVN regulatory chưa rõ Q1/2027. Khả thi thấp nhất. |

**Tổng BA scope final**: KEEP #1 (6) + #2 (5) + #3 (10) + #6-lite (2) = **23 SP core**, plus **+5 SP** cho BA-side acceptance test/LOTUS checklist authoring/UAT coordination = **~28–31 SP** (khớp với envelope BA mà SA đã absorb 31 SP trong `sa-mvp5-conflict-resolution.md` và `mvp5-po-synthesis.md`).

**So sánh với BA gap ban đầu**: gap đề xuất +43 SP → **giảm xuống ~31 SP** (nhường 12 SP cho SA hardening + sớm hơn nhờ buffer ~95 SP giải phóng từ DR/K8s/Series A out).

> **Lưu ý trục X**: toàn bộ matrix đã chuyển từ "$100K MRR tuyệt đối" sang "pilot-to-paid signal (≥2/3 bldg) + ARR projection". Không còn vertical nào được đánh giá qua lăng kính Series A MRR trong MVP5.

---

## 3. Đề xuất BA scope MVP5 final *(updated 2026-06-20 per PO decisions: build-50/test-2-3, Series A out, K8s defer, DR out)*

### KEEP (MVP5) — 4 vertical, ~28-31 SP

| Vertical | Sprint đề xuất | Lý do vị trí | Dependency phải chờ |
|---|---|---|---|
| **LOTUS VN evidence pack** | **M5-4** | Sau khi ISO 37120/GRI (Theme C M5-4) xong — LOTUS kế thừa ESG data pipeline. Không block gate kỹ thuật. Fit build-50/test-2-3 (logic không phụ thuộc số tenant). | ESG report API (Theme C) |
| **Building Owner ROI dashboard** | **M5-3** | Đọc `AiCostMetrics` + sensor usage đã có từ MVP4. Frontend-heavy, ship song song với NL→BPMN UAT (G3). **Quan trọng nhất cho pilot-to-paid signal ở 2-3 bldg** — building owner cần evidence rõ ràng trên cohort nhỏ. | AiCostMetrics (MVP4) |
| **EV Charging + Parking (OCPP)** | **M5-5** | Phải đi **sau** billing GA (M5-G4 M5-4) — EV thu phí cần metering pipeline. OCPP schema cần ADR-051 (Schema Registry, SA Sprint 2-3). **Điều kiện fit**: 1 trong 2-3 pilot bldg có trạm sạc — nếu không, DEFER MVP6. Không cần 50-bldg scale (test 1-2 trạm đủ). | Billing GA + Schema Registry + pilot cohort có trạm sạc |
| **Compliance audit trail (lite)** | **M5-4** | Cùng sprint với ISO 37120 audit prep (Theme C). PostgreSQL tenant-scoped append-only — không cần CH Append-Only. GAP-1 giờ P1 (chỉ dependency nhẹ). | GAP-1 RLS (PG đã có V16/V18/V30) |

**Xác nhận 4 vertical KEEP**: LOTUS VN + ROI dashboard + EV Charging + audit-lite — tất cả đều fit mode build-50/test-2-3, không phụ thuộc 50-bldg scale hay $100K MRR.

### DEFER (MVP6) — 4 đề xuất

| Đề xuất | Lý do defer |
|---|---|
| Council transparency portal (4 SP) | Renew cycle 12 tháng; không pilot-to-paid signal trigger. |
| Smart Waste district (6 SP) | Customer = quận, không phải pilot building owner. |
| Compliance audit full immutable hash-chain (2 SP) | Cần CH Append-Only engine (SA ADR-047). |
| SASB real-estate subset | Investor-grade, post-Series A (MVP6+). |

### CUT (sang MVP6+) — 1 đề xuất

| Đề xuất | Lý do cut |
|---|---|
| Demand Response / P2P (8 SP) | EVN regulatory chưa rõ Q1/2027. SA-dep = 4 (BLOCK EVN regulatory + GAP-1 P1 nhẹ). BA đã đề xuất cut trong `ba-mvp5-gap.md`. |

---

## 4. Sprint mapping (đối chiếu PM — post-pivot, có thể rút ngắn 4-5 sprint) *(updated 2026-06-20 per PO decisions: build-50/test-2-3, Series A out, K8s defer, DR out)*

Sprint mapping đồng bộ với `mvp5-po-synthesis.md` §7 timeline (M5-1→M5-5, M5-6 optional nếu giữ 6 sprint). BA vertical không thay đổi vị trí sprint, chỉ dependency chain rõ hơn:

| Sprint | PM theme (post-pivot) | BA vertical thêm vào | Conflict check / dependency |
|---|---|---|---|
| M5-1 (Sep 21) | Compose HA + GAP-1 tenant isolation (P1) + Vault | — | BA không chen — để SA build modular architecture P1 (GAP-1 CH RowPolicy) + Compose HA test env. |
| M5-2 (Oct 05) | NL→BPMN POC + GAP-2 residency + billing skeleton | — | BA-side: chuẩn bị LOTUS checklist (BA acceptance, 0 SP dev). |
| M5-3 (Oct 19) | NL→BPMN prod + schema reg + observability | **ROI dashboard (5 SP)** | Độc lập với NL — frontend ROI đọc cost data có sẵn. **Top pilot-to-paid signal vertical** cho 2-3 bldg. |
| M5-4 (Nov 02) | Billing GA + ISO 37120 + GRI | **LOTUS VN (6 SP) + audit lite (2 SP)** | LOTUS kế thừa ESG pipeline (Theme C cùng sprint). Audit lite gắn ISO audit prep. GAP-1 P1 đủ (không block). |
| M5-5 (Nov 16) | EV Charging + mobile + synthetic 50-tenant test | **EV Charging (10 SP)** | Sau billing GA (M5-G4). Cần Schema Registry (ADR-051) đã xong M5-3. **Không cần 50-bldg scale** — test 1-2 trạm + synthetic billing metering đủ. Điều kiện: pilot cohort có trạm sạc. |
| [M5-6 (Nov 30)] — optional | Hardening + OWASP + regression burn-down | — | Buffer — nếu PO chọn D8 rút ngắn 4-5 sprint thì bỏ M5-6, BA vertical không thêm vào sprint cuối. |

**Tổng BA SP / sprint**: M5-3 (5) + M5-4 (8) + M5-5 (10) = **23 SP**. Phù hợp envelope SA absorb 31 SP (còn dư 8 SP cho BA-side UAT/acceptance spread M5-2→M5-5).

**EV M5-5 — lưu ý post-pivot**: Vẫn cần billing GA (M5-4) nhưng giờ **không cần 50-bldg scale** — test 1-2 trạm sạc + synthetic billing metering (simulate 50 tenant via test data) đủ prove correctness. Code path scale sẽ được MVP6 re-verify (gate K2).

---

## 5. Kịch bản rủi ro — 2 cực PO có thể chọn *(updated 2026-06-20 per PO decisions: build-50/test-2-3, Series A out, K8s defer, DR out)*

Post-pivot, 2 cực đã thay đổi nghĩa: K8s/DR đã out nên Hardening-first không còn nghĩa "focus K8s/DR"; Feature-first không còn trade-away P0 ở 50-building scale.

### 5.1 Rủi ro nếu PO chọn **Hardening-first** (chỉ GAP-2 + bỏ hết vertical)
- **Pilot-to-paid signal yếu**: 2-3 pilot building không thấy ROI (không có ROI dashboard) + không có LOTUS để justify renewal năm 2 → **pilot-to-paid conversion < 2/3** → **O3 fail** (G8 product fit gate).
- **Khách VN mất interest**: building owner VN đã bị vendor quốc tế "lừa" ROI nhiều lần (xem `ba-mvp5-gap.md` §5) — không có outcome-based evidence (ROI dashboard) thì họ không ký năm 2.
- **MVP6 chậm khởi động**: không có pilot-to-paid signal → MVP6 mở 10-50 bldg không có evidence base → Series A trượt tiếp.
- **Verdict BA**: Hardening-first = an toàn kỹ thuật nhưng **MVP5 không deliver product fit signal**. Chỉ chấp nhận nếu PO chấp nhận **MVP5 = pure architecture validation, MVP6 mới bắt đầu product fit**.

### 5.2 Rủi ro nếu PO chọn **Feature-first** (chấp nhận GAP-1 P1 debt nhẹ ở 2-3 tenant)
- **NL residency violation (P0)**: nếu bỏ GAP-2 → text tiếng Việt (chứa địa chỉ building/incident) sang Claude = **vi phạm Decree 13** → HCMC government cancel contract. **Đây là P0 duy nhất không trade-away được**.
- **R16 (build-for-50 chưa exercise ở scale)**: vertical ship trước GAP-1 hoàn thiện correctness ở 2-3 tenant — nhưng vì GAP-1 giờ P1 và 2-3 tenant tin cậy, severity **giảm MED/LOW** (xem `pm-mvp5-conflict-resolution.md` R11).
- **Verdict BA**: Feature-first post-pivot = **chấp nhận GAP-1 P1 debt nhẹ ở 2-3 tenant** (không còn breach-waiting như 50-bldg). BA **vẫn PHẢN ĐỐI** nếu trade-away GAP-2 (P0). Nếu chỉ trade-away GAP-1 (P1) cho pilot cohort tin cậy → BA **chấp nhận có điều kiện** (gate K2 MVP6 re-verify).

### 5.3 **BA recommendation — Balanced (Scenario C)** *(không đổi verdict, chỉ clarify post-pivot)*
- P0 duy nhất của SA (GAP-2 NL residency) = hard gate, không mặc cả. GAP-1 P1 build đúng kiến trúc modular, test cover 2-3 tenant.
- Ship 4 vertical pilot-to-paid-signal cao / thấp-dependency (LOTUS, ROI, EV, audit-lite) theo sprint mapping §4.
- Defer Council/Waste/full-audit/DR-P2P/SASB sang MVP6.
- → **Pilot-to-paid signal ở tốc độ tối đa có thể mà không nợ GAP-2 compliance**. Build modular architecture sẵn sàng scale 50+ bldg cho MVP6.

---

## 6. Gate kỹ thuật ảnh hưởng BA scope *(updated 2026-06-20 per PO decisions: build-50/test-2-3, Series A out, K8s defer, DR out)*

Đồng bộ với `mvp5-po-synthesis.md` §4 (7 gate, G5 xóa, G7/G8 đổi criteria):

| Gate | Ảnh hưởng BA |
|---|---|
| **M5-G1** (Compose HA + tenant isolation P1) | BA vertical không block G1. |
| **M5-G2** (multi-tenant isolation fuzz — cover 2-3 tenant) | BA vertical ROI/LOTUS **KHÔNG** block G2 — chúng chỉ read data của chính tenant (qua JWT `tenant_id`). **EV Charging** (M5-5) đọc billing ledger → cần GAP-1 P1 implemented trước M5-5 (dependency nhẹ, không BLOCK). |
| **M5-G3** (NL→BPMN UAT) | ROI dashboard (M5-3) ship song song — không conflict (frontend khác lane). |
| **M5-G4** (billing metering accuracy) | **Hard prerequisite cho EV Charging** (M5-5). Nếu billing trượt M5-4 → EV trượt M5-6 hoặc MVP6. |
| ~~M5-G5 (DR failover)~~ | **XÓA** — DR out khỏi MVP5. |
| **M5-G6** (compliance/OWASP) | Audit-lite (M5-4) contribute. GAP-2 residency phải xong trước. |
| **M5-G7** (functional correctness 2-3 bldg + synthetic 50-tenant test) | ~~3.000 RPS~~ → đổi criteria. BA vertical PASS qua functional test 2-3 bldg. |
| **M5-G8** (architecture validation + product fit + **pilot-to-paid signal ≥2/3 bldg**) | ~~Series A $40K MRR~~ → đổi criteria. **BA vertical (ROI + LOTUS) là contributor chính cho pilot-to-paid signal**. |
| **K2 (BA-proposed gate, MVP6)** | BA đề xuất thêm gate **K2 "Commercial cohort tenant isolation + product-fit re-verify"** trước khi mở commercial 10+ bldg ở MVP6: re-run G2 fuzz ở quy mô + audit LOTUS/ROI evidence không leak cross-building + re-verify pilot-to-paid signal đã scale. |

---

## 7. Recommendation cho PO *(updated 2026-06-20 per PO decisions: build-50/test-2-3, Series A out, K8s defer, DR out)*

> **PO cần chốt trước 2026-09-14 (Decision D7 + D8)** *(updated 2026-06-20)*:
>
> - **D7 = C (Balanced, build-50/test-2-3)**: Hard gate duy nhất = GAP-2 NL residency (P0); GAP-1 tenant isolation build đúng kiến trúc modular (P1, test cover 2-3 tenant); ship 4 BA vertical pilot-to-paid-signal cao (LOTUS VN M5-4, ROI dashboard M5-3, EV Charging M5-5, audit-lite M5-4) theo dependency chain (billing GA → EV); defer Council portal + Smart Waste + full audit + DR-P2P + SASB sang MVP6. **Series A ($100K MRR) OUT khỏi MVP5** — MVP5 deliver = architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg).
> - **D8 = Rút ngắn MVP5 xuống 4-5 sprint** (BA đồng tình với PM): buffer ~95 SP (37%) quá dư cho 6 sprint khi Series A out + R15 giải quyết + DR/K8s out. Đưa team sang MVP6 sớm.
>
> Nếu PO không chốt D7 trước 2026-09-14, default = **Scenario C**. BA phản đối Scenario B (Feature-first trade-away GAP-2) vì vi phạm Decree 13; Scenario A (Hardening-first bỏ hết vertical) chỉ chấp nhận nếu PO chấp nhận MVP5 = pure architecture validation, MVP6 mới bắt đầu product fit.

---

## 8. Open questions cho SA / PM *(updated 2026-06-20 per PO decisions: build-50/test-2-3, Series A out, K8s defer, DR out)*

| # | Question | Cho ai |
|---|---|---|
| Q1 | LOTUS VN evidence pack có cần CH aggregate mới, hay reuse ESG pipeline hiện có (`EsgService`)? Nếu reuse → BA 6 SP đủ; nếu cần aggregate mới → +2 SP. | SA |
| Q2 | ROI dashboard: `AiCostMetrics` có per-building breakdown không, hay chỉ per-tenant? Với 2-3 pilot bldg, per-building breakdown càng quan trọng (building owner muốn evidence rõ ràng). | SA + Backend |
| Q3 | EV Charging OCPP schema có trigger ADR-051 Schema Registry BACKWARD_TRANSITIVE breaking-change không? Nếu có → EV phải đi sau registry GA (M5-3), không song song. | SA |
| Q4 | **EV Charging fit với 2-3 pilot bldg không?** 1 trong 2-3 pilot building có trạm sạc không? Nếu không → DEFER EV MVP6, BA scope giảm 10 SP (còn ~21 SP). PO/PM xác nhận pilot cohort. | PO + PM |
| Q5 | Scenario C của PM (`pm-mvp5-conflict-resolution.md` + `mvp5-po-synthesis.md`) absorb đúng 31 SP BA vertical không? Nếu PM chỉ absorb 23 SP (cắt EV), BA cần trade-off lại. | PM |
| Q6 | **D8 rút ngắn 4-5 sprint**: BA vertical M5-5 (EV) có bị compress không? Nếu rút ngắn, EV có thể push sang MVP6 nếu pilot cohort chưa sẵn sàng. | PM |

---

*Phân tích bởi BA, 2026-06-20 *(updated 2026-06-20 per PO decisions: build-50/test-2-3, Series A out, K8s defer, DR out)*. Đối thoại với `sa-mvp5-conflict-resolution.md`, `pm-mvp5-conflict-resolution.md`, và `mvp5-po-synthesis.md`. PO chốt D7 + D8 trước 2026-09-14.*
