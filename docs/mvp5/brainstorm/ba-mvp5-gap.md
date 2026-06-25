# MVP5 BA Gap Analysis — Product & Market View

> ⚠️ **ADDENDUM 2026-06-20 — Tài liệu này là LỊCH SỬ (gap analysis gốc trên PM draft 2026-06-15).** Nội dung bên dưới phản ánh quan điểm BA **trước** 4 PO decisions. Đã được supersede bởi `ba-mvp5-conflict-resolution.md` (updated) + `mvp5-po-synthesis.md`.
>
> **4 PO decisions đã chốt (2026-06-19/20) thay đổi kết luận của gap analysis này:**
> 1. **DR (ADR-053) OUT khỏi MVP5** → MVP6+.
> 2. **K8s DEFER MVP6** → Compose HA môi trường test.
> 3. **BUILD như 50 buildings nhưng TEST chỉ 2-3 buildings** → GAP-1 P0→P1, không còn BLOCK vertical.
> 4. **Series A ($100K MRR) OUT khỏi MVP5** → MVP6+. MVP5 = architecture validation + product fit + pilot-to-paid signal (≥2/3 bldg). **Field "Series A trigger: $100K MRR @ 50+ buildings" bên dưới KHÔNG còn mục tiêu MVP5.**
>
> **Mapping đề xuất BA gốc → trạng thái hiện tại** (xem `ba-mvp5-conflict-resolution.md` §2 matrix):
> - LOTUS VN, ROI dashboard, EV Charging, audit-lite: **KEEP MVP5** (~31 SP).
> - Council portal, Smart Waste, full audit: **DEFER MVP6**.
> - DR/P2P, SASB: **CUT/DEFER MVP6**.
> - Vertical giờ đánh giá qua **pilot-to-paid signal (≥2/3 bldg)** thay vì $100K MRR tuyệt đối.
> - Persona analysis (Building Owner/FM, Council, Compliance, Field Operator) **vẫn valid** cho MVP6 roadmap.
> - ESG expansion (LOTUS VN = killer feature) **vẫn valid** — LOTUS vào MVP5.
>
> **Đọc `ba-mvp5-conflict-resolution.md` cho trạng thái hiện tại.** Nội dung dưới đây chỉ còn giá trị tham khảo/lịch sử cho MVP6 planning.

---

| Field | Value |
|---|---|
| **Author** | Business Analyst (Sanity check trên PM draft `mvp5-roadmap-draft.md`) |
| **Date** | 2026-06-18 *(historical — superseded by addendum above + ba-mvp5-conflict-resolution.md)* |
| **Audience** | PO, SA, PM (review trước khi lock MVP5 scope) |
| **Series A trigger** | ~~$100K MRR @ 50+ buildings~~ *(historical — OUT khỏi MVP5 per PO decision; MVP5 = product fit + pilot-to-paid ≥2/3 bldg)* |
| **Lens** | Sản phẩm & thị trường (KHÔNG phải kỹ thuật) |

> **TL;DR** — PM draft mạnh về scale kỹ thuật (K8s/Vault/DR) và operator leverage (NL→BPMN), nhưng **bỏ qua 4 persona trả tiền/quyết định**: building owner/FM (ROI + LEED/LOTUS), city council/executive (transparency), compliance officer (audit), field operator (mobile work order). Draft cũng **thiếu 3 vertical ROI cao cho HCMC**: EV charging + parking, demand response/P2P energy, smart waste. ESG scope hẹp (chỉ ISO 37120 + GRI) — thiếu TCFD/SBTi/LOTUS VN/EDGE là chuẩn mà building owner và investor VN thực sự hỏi.

---

## 1. User Persona Gap Analysis

Draft hiện phủ: **operator** (NL→BPMN wizard), **DevOps** (K8s/Vault), **PM/billing admin** (usage-based billing). Thiếu nhóm persona bên dưới.

| Persona | Bị bỏ sót vì sao | Feature thiếu (cụ thể) | Priority | Business impact nếu thiếu |
|---|---|---|---|---|
| **Building Owner / Facility Manager (FM)** | Draft coi operator = kỹ thuật tòa nhà, không phải **người ra quyết định mua/đầu tư**. 5 pilot buildings = 5 owner cần justify ROI | (a) **ROI dashboard** — tiết kiệm điện/nước/chi phí O&M quy đổi VND; (b) **LEED/LOTUS VN evidence pack** — export data gắn với checklist hạng mục (energy, water, IEQ); (c) **EDGE (IFC) certification tracker** cho building class; (d) **Capital expenditure planner** — recommend retrofit dựa trên anomaly pattern | **P0** | Owner không thấy ROI → không ký hợp đồng năm 2 → **Series A MRR sụp**. Đây là persona trả tiền trực tiếp (building pays license, không phải city) |
| **City Council / Executive (Cấp Ủy, Hội Đồng Nhân Dân)** | Draft gộp chung "city authority" nhưng HCMC tách 2 role: **operator technical** (Sở KH&ĐT, Sở TN&MT) vs **council chính trị** (UBND, HDND). Council không xem dashboard kỹ thuật | (a) **Executive briefing pack** (PDF/presentation) — top-line ESG score, ranking vscities, chính sách rõ ràng bằng tiếng Việt; (b) **Council transparency portal** — open data subset cho công dân/báo chí; (c) **Voter-facing KPI page** (NPS, complaint resolution, AQI improvement) | **P0** | Council ký ngân sách gia hạn hợp đồng. Không có reporting cấp chính trị → renew bị block sau pilot 12 tháng |
| **Compliance / Audit Officer** | Draft có "audit log + ISO 37120" ở Theme C nhưng đó là **output report**, không phải **workflow của compliance officer**. Thiếu persona thật | (a) **Immutable audit trail** (write-once, hash-chained) cho mọi alert decision + AI recommendation; (b) **Evidence export** cho 2 bộ chuẩn quốc tế (TCFD, GRI) + 2 VN (LOTUS VN, thông tư MONRE); (c) **Data lineage view** — trace 1 con số trên report về sensor raw; (d) **SOX-style segregation of duties** (người approve ≠ người deploy workflow) | **P1** | Bị fail audit trong pilot review →tin tưởng giảm → khó upsell sang 50+ buildings. Đây là table-stakes cho Tier-3 enterprise customer |
| **Field Operator / Maintenance Technician** | MVP4 có mobile v3.1 "polish" nhưng là **công cụ cho operator ngồi văn phòng**. Field tech ra hiện trường sửa sensor/đọc đồng hồ bị quên | (a) **Mobile work order** — nhận lệnh, check-in GPS, chụp ảnh evidence, close ticket offline; (b) **AR/QR sensor lookup** — quét QR trên sensor → manual override/calibration log; (c) **Route optimization** cho multi-building rounds; (d) **Voice-to-text incident report** tiếng Việt | **P1** | Không có field tech → sensor downtime tăng → data coverage <95% → ESG report bị audit reject. Đây là mắt xích duy nhất giữ data quality ở 50+ buildings |
| **Citizen (Resident app engagement)** | Draft coi citizen = nhận notification. Nhưng engagement loop (report issue, vote, reward) mới tạo **network effect + pressure lên council** | (a) **2-way citizen reporting** (photo + GPS + category); (b) **Gamification** (green points, leaderboard khu phố); (c) **Service request tracking** với ETA; (d) **Community voting** cho prioritization | **P2** | Citizen app rỗng → Council không thấy "voice of people" → ít justification chính trị. ROI gián tiếp nhưng mạnh cho renewal |

**Tóm tắt persona**: TOP 3 phải vào scope MVP5 = **Building Owner/FM** (P0, trả tiền), **City Council** (P0, ký ngân sách), **Compliance Officer** (P1, table-stakes enterprise).

---

## 2. Vertical Use-Case Gap (TOP 3 ROI cho HCMC @ 50+ buildings)

Draft MVP4 có 7 scenarios (3 citizen + 4 management). Để unlock 50+ buildings + $100K MRR, cần thêm scenario ROI cao. Đánh giá 5 ứng viên:

| Ứng viên | HCMC fit | Revenue potential | Chi phí triển khai | Verdict |
|---|---|---|---|---|
| EV charging + smart parking | Rất cao — EV sắp bùng nổ VN (chính sách 2030), HCMC thiếu hạ tầng | Cao — monetize trực tiếp per-charge | Trung bình — cần OCPP integration | **Chọn #1** |
| Demand response / P2P energy trading | Cao — EVN đang thí điểm DPP, building commercial load lớn | Rất cao — % cut giao dịch | Cao — regulatory + grid integration | **Chọn #2** |
| Smart waste + smart bin | Cao — HCMC đang externalize cho Citenco/URSVN, công nghệ còn thủ công | Trung bình — per-pickup hoặc per-bin | Thấp — sensor đơn giản | **Chọn #3** |
| Public safety / CCTV AI | Trung bình — đã có vendor (Viettel, FPT), chính trị nhạy cảm (giám sát dân) | Cao nhưng rủi ro uy tín | Cao — GPU + privacy compliance | Bỏ — rủi ro > ROI |
| Health / pandemic airborne | Thấp hậu COVID — ngân sách đã cắt, CO2/PM sensor khó justify riêng | Thấp | Thấp | Bỏ — ghép vào AQI hiện có |

### TOP 1 — EV Charging + Smart Parking

**User Story**:
> *As a building owner ở Quận 1, I want UPC platform tích hợp trạm sạc EV và bãi đỗ thông minh, so that tôi thu phí sạc + tối ưu occupancy + chứng minh giảm CO2 cho ESG report.*

**Acceptance Criteria**:
- AC1: Hệ thống đọc được trạng thái trạm sạc qua **OCPP 1.6/2.0.1** (real-time: available/charging/fault) từ ≥3 vendor (ABB, VinFast, ChargePoint).
- AC2: Citizen/resident đặt chỗ bãi đỗ + sạc qua mobile app, thanh toán VNPay/MoMo, nhận QR code; hệ thống tự động release slot sau 15 phút no-show.
- AC3: CO2 avoided (kWh sạc × grid emission factor) được tính tự động và đẩy vào **ESG report scope 2** của building.

**Business Impact (1 dòng)**: Đốt **$15-30K ARR/building** từ phí giao dịch + mở tệp khách hàng mới (vinhomes, Novaland, Capitaland commercial) chưa có smart building vendor.

---

### TOP 2 — Demand Response + P2P Energy Trading (EVN thí điểm)

**User Story**:
> *As a commercial building owner, I want tham gia chương trình Demand Response của EVN và trao đổi điện dư với building lân cận, so that giảm bill điện 8-15% và có doanh thu từ capacity payment.*

**Acceptance Criteria**:
- AC1: Khi EVN phát DR signal (peak load), hệ thống auto-suggest curtailment action (HVAC setpoint +2°C, tắt lighting non-essential) với estimated $ savings; operator approve 1-click.
- AC2: P2P ledger ghi nhận kWh giao dịch giữa 2 building (blockchain hoặc hashed append-only log) cho đối chiếu với EVN.
- AC3: Dashboard "Demand Response performance" cho building owner xem % reduction vs target + $ capacity payment received.

**Business Impact (1 dòng)**: Tạo **outcome-based pricing** (UIP take 5-10% energy savings) — đây là hook duy nhất giúp UIP thoát khỏi "tool cost" thành "revenue partner", tăng stickiness 3-5x so với SaaS flat.

---

### TOP 3 — Smart Waste + Smart Bin (Citenco/URSVN partnership)

**User Story**:
> *As a district environmental officer (Sở TN&MT Quận), I want sensor fill-level trên thùng rác công cộng + tối ưu tuyến thu gom, so that giảm 20-30% chi phí xe rác và giảm overflow complaint từ dân.*

**Acceptance Criteria**:
- AC1: Ultrasonic/IR fill-level sensor report mỗi 1h;预警 khi >85% → auto-create pickup work order gán cho route gần nhất.
- AC2: Route optimization (OR-tools / GraphHopper) tái tính tuyến hàng ngày dựa trên actual fill pattern, giảm km chạy ≥15% vs fixed route.
- AC3: Citizen complaint về overflow rác giảm được tracked trên council transparency portal (link tới persona #2).

**Business Impact (1 dòng)**: Hook vào **Citenco/URSVN contract** ở cấp quận (50+ thùng/quận × 24 quận HCMC) — market entry nhanh hơn building-by-building, và là trojan horse để upsell UIP vào UBND quận.

---

## 3. ESG Compliance Expansion (Roadmap MVP5)

Draft chỉ nhắc ISO 37120 + GRI. Với tệp khách **building owner VN + investor quốc tế**, cần mở rộng:

| Framework | Tại sao cần cho MVP5 | Ai yêu cầu | Đề xuất scope |
|---|---|---|---|
| **TCFD** (Task Force Climate-related Financial Disclosures) | Investor quốc tế (BlackRock, GIC) đang ép portfolio company disclose climate risk. Building owner muốn hấp dẫn REIT/fund nước ngoài | Building owner + REIT investor | **MVP5 Sprint 5** — climate scenario analysis (sea-level rise HCMC, heat stress) + transition risk; ghép với flood AI scenario hiện có |
| **SASB** (Sustainability Accounting Standards Board) | Chuẩn ngành-specific cho real estate (IF-RE); nhiều fund yêu cầu song song GRI | Building owner + institutional investor | **MVP5 Sprint 6** — subset metric cho real estate sector (energy intensity, water, tenant engagement) |
| **SBTi** (Science-Based Targets initiative) |Đã trở thành norm cho Fortune 500 commitment; building owner muốn claim "1.5°C aligned" | Building owner + corporate tenant | **MVP5 defer → MVP6** — cần baseline 12 tháng + target validation; P2 |
| **LOTUS VN** (Vietnam Green Building Council) | **Quan trọng nhất cho VN** — building muốn giấy chứng nhận LOTUS All-Vietnamese phải có data evidence; đối thủ trực tiếp của LEED nhưng cheaper + local | Building owner (chính!) + Sở Xây dựng | **MVP5 Sprint 4** — evidence pack full (energy, water, materials, IEQ, waste) theo LOTUS checklist; đây là **killer feature cho thị trường VN** |
| **EDGE** (IFC Excellence in Design for Greater Efficiency) | IFC tài trợ, được ngân hàng VN (Vietcombank, Techcombank) chấp nhận cho green loan | Building owner cần green financing | **MVP5 Sprint 5** — 3 metric cốt lõi (energy/water/material % savings vs baseline); output EDGE certificate-ready file |

**Đề xuất roadmap ESG MVP5** (đặt vào Theme C, mở rộng từ 7 SP lên ~15 SP):

```
Sprint 4 (Feb): LOTUS VN evidence pack (P0 — VN market entry)
Sprint 5 (Mar): TCFD climate risk + EDGE green loan pack (P1)
Sprint 6 (Mar): SASB real-estate subset (P1)
Defer MVP6: SBTi validation, GRI sector supplement
```

**Why LOTUS VN là P0**: Đây là **điểm khác biệt duy nhất** UIP có thể claim trong 6-12 tháng tới mà Siemens/Honeywell không có. Building owner VN sẽ trả premium cho công cụ giúp họ lấy chứng nhận LOTUS nhanh hơn 50%.

---

## 4. Monetization / Pricing Model

Draft có "tenant billing (usage-based: AI tokens, sensor count)" nhưng để ngỏ câu hỏi #4. Đánh giá 4 mô hình:

| Model | Pros | Cons | Fit thị trường VN |
|---|---|---|---|
| **Per-building flat** (vd $2K/building/month) | Đơn giản, dễ forecast revenue, dễ sale | Không capture value khi building scale up sensor/AI usage; "rẻ" với big building, "đắt" với small | **Thấp** — VN building owner ghét fixed cost lớn; cần entry barrier thấp |
| **Per-sensor / per-meter** (vd $5/sensor/month) | Linear với usage, dễ giải thích | Khuyến khích customer **giảm sensor** (anti-pattern); phạt pilot nhỏ; khó audit | **Trung bình** — tốt cho entry nhưng ceiling thấp |
| **Per-AI-token metered** (vd $0.001/inference) | Capture AI value, scale với intelligence | Khó forecast cho customer; "bill shock"; cảm giác bị charge cho "AI mình không cần" | **Thấp** — thị trường VN chưa mature, customer sợ black-box metering |
| **Outcome-based / gain-share** (vd 8% energy savings, 5% DR revenue) | Cao nhất stickiness; UIP thành partner không phải vendor; bỏ qua được "cost" objection | Phức tạp pháp lý (VN contract law), cần baseline trust, khó thu tiền ngắn hạn | **Cao cho upsell, thấp cho entry** |

**Recommendation cho HCMC market — Hybrid 3-tier**:

```
Tier 1 — Entry (per-sensor, $3-5/sensor/mo)
  ├─ Mục tiêu: 5-20 buildings nhỏ, phá rào cản
  ├─ Bao gồm: monitoring + alert + basic ESG (ISO 37120)
  └─ Cap: $1500/building/mo để tránh bill shock

Tier 2 — Growth (per-building flat $1500-3000/mo)
  ├─ Mục tiêu: 20-50 buildings commercial
  ├─ Bao gồm: + AI workflow + NL→BPMN + LOTUS VN pack
  └─ Best fit cho đa số HCMC commercial building

Tier 3 — Enterprise (flat + gain-share 5-8%)
  ├─ Mục tiêu: Tier-3 customer, REIT, portfoliocổ đông
  ├─ Bao gồm: + TCFD + DR/P2P + audit/SOX
  └─ This is where $100K MRR lives: 10 Tier-3 × $10K MRR
```

**Lý do**: Thị trường VN có **hai-speed** — building owner cần entry rẻ (per-sensor) để thử, nhưng investor-grade customer (REIT, fund) sẵn sàng trả premium cho outcome. Hybrid bắt được cả hai, và gain-share ở Tier-3 tạo hook khó rời.

---

## 5. Competitive Positioning

| Đối thủ | Điểm mạnh | Điểm yếu cho thị trường VN | UIP nên positioning thế nào ở MVP5 |
|---|---|---|---|
| **Siemens MindSphere / Building X** | Brand toàn cầu, hardware tích hợp sâu, Navision/middleware mature | **Đắt** ($50K+ entry), closed ecosystem, không native Vietnamese, data residency phải ra EU/SG | **Cost 1/3-1/5** + Vietnamese-first + on-prem deploy được |
| **Honeywell Forge** | Enterprise HxGN, very strong ở HVAC/BMS tích hợp | Cùng vấn đề cost + vendor lock-in; yếu về ESG VN framework | Nhấn **modular + no hardware lock-in** — UIP chạy trên BMS có sẵn |
| **Schneider EcoStruxure** | Rất mạnh energy + power monitoring, EVN relationship | Không có citizen/council layer, không có AI workflow | Khác biệt: **UIP = full-stack (citizen + building + council)**, Schneider chỉ building |
| **FPT / Viettel Smart City (local VN)** | Vietnamese, quan hệ chính phủ mạnh, giá cạnh tranh | **Stack rời rạc**, không có ESG framework, không có AI workflow tự động | Khác biệt: **UIP có workflow AI + ESG reporting** — đối thủ VN chỉ có dashboard tĩnh |
| **CiticSys / VNG Corp (local)** | Vietnamese, pricing rất thấp | Non-enterprise grade, không có multi-tenant, không có compliance | Nhấn **enterprise-grade + audit trail + multi-tenant** |

### USP MVP5 — nên nhấn 3 điểm:

1. **Vietnamese-first (độc quyền)**: NL→BPMN bằng tiếng Việt, LOTUS VN native, reporting tiếng Việt cho council. **Không đối thủ nào (kể cả FPT) có NL→BPMN tiếng Việt**. Đây là moat 18-24 tháng.

2. **On-prem / data residency**: Khách VN (đặc biệt chính phủ + bank) cấm data ra ngoài country. UIP deploy được on-prem trên OpenStack/vSphere (K8s migration Theme A là enabler). Siemens/Honeywell bắt buộc cloud EU/US.

3. **Outcome-based pricing (gain-share)**: Đối thủ đều charge fixed license. UIP duy nhất willing to take performance risk cùng customer — đây là **trust signal** mạnh cho building owner VN đã bị vendor quốc tế "lừa" ROI nhiều lần.

**Không nên nhấn**: Modular (đối thủ cũng claim), cost đơn thuần (local VN thắng về giá), AI chung chung (ai cũng có).

**Message MVP5 cho PO**:
> *"UIP là nền tảng ESG thông minh duy nhất **xây cho Việt Nam** — Việt ngữ native, chứng nhận LOTUS VN sẵn sàng, deploy on-prem tại chỗ, và chia sẻ rủi ro cùng building owner qua outcome-based pricing."*

---

## 6. Recommendation tóm tắt cho PO/PM

**Lock vào MVP5 scope (thêm vào PM draft)**:

| Đề xuất | Theme | SP ước tính | Lý do |
|---|---|---|---|
| LOTUS VN evidence pack | C (mở rộng) | +6 SP | Killer feature thị trường VN, không ai có |
| Building Owner ROI dashboard | C (mới) | +5 SP | Persona trả tiền, P0 cho MRR |
| Council transparency portal | C (mới) | +4 SP | Persona ký ngân sách renew |
| EV Charging + Parking (OCPP) | Vertical mới | +10 SP | Hook new customer segment (commercial building) |
| Demand Response / P2P | Vertical mới | +8 SP | Outcome-based pricing enabler |
| Smart Waste (district hook) | Vertical mới | +6 SP | Trojan horse vào UBND quận |
| Compliance audit trail (immutable) | C (mở rộng) | +4 SP | Table-stakes Tier-3 enterprise |

**Tổng thêm**: ~43 SP. **PM draft hiện**: ~90 SP. **Tổng MVP5 real scope**: ~133 SP → **vượt capacity**, cần PO **cắt một trong**:
- Nếu ưu tiên MRR ngắn: cắt DR/P2P (phức tạp pháp lý), giữ EV+Waste+LOTUS.
- Nếu ưu tiên enterprise grade: cắt EV/Parking (outsource integration), giữ DR/P2P+Compliance+LOTUS.

**My BA recommendation**: Cắt DR/P2P ra MVP6, giữ MVP5 = **LOTUS + ROI dashboard + Council portal + EV + Waste + Compliance**. Lý do: 4 cái đầu có market-ready customer (building owner), DR/P2P cần EVN regulatory clear (chưa sẵn Q1 2027).

---

*Phân tích bởi BA | Cần PO review + prioritization workshop trước Sprint 1 MVP5.*
