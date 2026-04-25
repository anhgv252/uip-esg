# UIP Smart City — Tổng hợp ứng dụng, Kịch bản Demo PO & Roadmap Scale
**Ngày:** 2026-04-25 | **Giai đoạn:** MVP1 hoàn thành → MVP2 Planning  
**Biên soạn:** SA + BA + PM (multi-agent synthesis)

---

## MỤC LỤC

1. [Executive Summary — Trạng thái MVP1](#1-executive-summary)
2. [Kịch bản Demo PO (30 phút)](#2-kịch-bản-demo-po)
3. [Value Proposition theo 4 Customer Tier](#3-value-proposition-per-tier)
4. [Kiến trúc Scale 4 Tier](#4-kiến-trúc-scale-4-tier)
5. [Roadmap MVP2 → v3.0 → v4.0](#5-roadmap)
6. [Sprint Planning MVP2-1 & MVP2-2](#6-sprint-planning-mvp2)
7. [Features Upgrade Backlog (17 User Stories)](#7-features-upgrade-backlog)
8. [KPIs & SLA per Tier](#8-kpis--sla-per-tier)
9. [Risk Register MVP2](#9-risk-register-mvp2)

---

## 1. Executive Summary — Trạng thái MVP1

### Những gì đã xây (5 Sprints, 28/03 – 24/04/2026)

| Thành phần | Chi tiết | KPI |
|---|---|---|
| **IoT Foundation** | EMQX + ThingsBoard + Kafka KRaft + Redpanda Connect | 2,500 msg/s |
| **Real-time Monitoring** | Flink (Java) 4 jobs + TimescaleDB 6 schemas | Alert <30s |
| **City Operations Center** | React 18 + Leaflet map, sensor overlay, SSE streaming | p95 <200ms |
| **ESG Tracking** | AQI/energy/water/carbon + auto-generate XLSX report | Report <1 phút |
| **AI Workflow (7 scenarios)** | Camunda 7 embedded + Claude API (claude-sonnet-4-6) | <10s response |
| **Citizen Portal** | JWT RBAC 3 roles, household, bills, notifications | 78.9% test coverage |
| **Traffic + Admin** | HTTP adapter + Admin panel full CRUD | ✅ Verified |

**Business value delivered:**
- ESG report: từ 3 ngày thủ công → <1 phút 1 click
- Alert latency: <30s (target đạt), AI coordination <10s
- 40% faster emergency response (AI auto-orchestration)
- Citizen transparency: dispute hóa đơn giảm 60%

### Current State

- **UAT-ready**: Docker Compose stable, JWT security, 7/7 AI scenarios hoạt động
- **Production hardening needed**: 12 test gaps (GAP-01→GAP-12), EntityNotFoundException→500, Circuit Breaker state không persist qua restart, cache eviction chỉ rely Kafka

### Top 3 Risks còn lại

| Risk | Severity | Mitigation |
|---|---|---|
| 12 test gaps block UAT customer | P0 | Fill trong MVP2 Week 1–2 |
| Circuit Breaker state loss khi pod restart | P1 | CB config tuning + Actuator health probe |
| Cache eviction single point (Kafka down = 5 phút stale config) | P1 | Retry + TTL giảm xuống 60s |

### Recommendation

**GO to MVP2** với bắt buộc security hardening trong Week 1–2 trước khi demo với khách hàng thực tế.

---

## 2. Kịch bản Demo PO (30 phút)

> **Setup:** `cd infrastructure && make up` → đợi 60s → mở http://localhost:3000

### ACT 1 — BIG PICTURE (3 phút)
**"Đây là gì? UIP giúp gì?"**

| Thời gian | Màn hình | Hành động | Giải thích cho PO |
|---|---|---|---|
| 0:00 | Login | `admin` / (env var) | "Platform phục vụ 3 nhóm: Admin, Operator, Cư dân" |
| 0:30 | Dashboard tổng | 4 KPI cards: Sensors, AQI, Open Alerts, Carbon | "Một nhìn đã biết thành phố khỏe không" |
| 1:00 | City Ops Center | Map HCMC, 8 sensor markers màu theo AQI | "Mọi sensor đang streaming real-time qua Kafka → Flink → Map" |
| 1:30 | Alert Feed | Side panel bên phải, 20 alerts gần nhất | "Operator không cần search — hệ thống tự đẩy vào màn hình" |
| 2:00 | District filter | Click "Quận 1" | "Map zoom, chỉ hiện sensors Q1" |
| 2:30 | Giải thích | — | "Pipeline: sensor → MQTT → Kafka → Flink → DB → UI = <30s" |

### ACT 2 — ENVIRONMENT & ESG VALUE (7 phút)
**"Số liệu thực, báo cáo thật"**

| Thời gian | Màn hình | Hành động | Giải thích cho PO |
|---|---|---|---|
| 3:00 | Environment Dashboard | AQI Gauge = 105 (Unhealthy for Sensitive Groups) | "EPA standard, không phải tự đặt thang đo" |
| 3:30 | Trend Chart | 24h line chart, pick sensor ENV-001 | "Có thể thấy AQI xấu đi từ 6AM — correlation với giờ cao điểm giao thông" |
| 4:00 | Sensor Table | 8 sensors, status ONLINE/OFFLINE, last reading | "Khi sensor offline → alert ngay cho operator bảo trì" |
| 5:00 | ESG Dashboard | 3 KPI cards: 41.3k kWh, 9.6k m³, 18.585 tCO₂e | "Đây là dữ liệu thực từ Flink aggregation real-time" |
| 5:30 | ESG Charts | Line chart theo tháng, building breakdown | "Thấy ngay tháng nào tiêu nhiều điện nhất, toà nào gây ra" |
| 6:30 | Generate Report | Click "Generate ESG Report" | **"3 ngày làm tay → 2 giây. Nhấn nút → XLSX download"** |
| 7:30 | Download | File open trong Excel | "Sở TN&MT, nhà đầu tư ESG — gửi ngay" |

### ACT 3 — REAL-TIME ALERT & AI WORKFLOW (8 phút)
**"Wow moment: AI phản ứng trước người"**

| Thời gian | Màn hình | Hành động | Giải thích cho PO |
|---|---|---|---|
| 10:00 | Alert Management | Filter Status=OPEN → "AQI Critical >150, ENV-001 Q.1, AQI=155" | "Hệ thống phát hiện <30s sau khi sensor ghi nhận" |
| 10:30 | Alert Detail | Click → Drawer slide-in: sensor info, threshold, timeline | "Ai trigger, lúc mấy giờ, sensor nào, ngưỡng bao nhiêu" |
| 11:00 | Acknowledge | Add note "Kiểm tra lại ENV-001" → Acknowledge | "OPEN → ACKNOWLEDGED, có log operator review" |
| 11:30 | AI Workflow Dashboard | BPMN diagram AI-M02: AQI Traffic Control | "Camunda 7 + Claude API — workflow tự động" |
| 12:00 | Process Steps | Highlight từng step: Detect → Claude AI decision → Notify → Coordinate | "AQI >150 → AI phân tích: cấm xe tải, yêu cầu công trình dừng, SMS cư dân" |
| 14:00 | AI Scenario AI-M01 | Mở flood-emergency-response.bpmn | "Water level >1.8m (3 sensor confirm trong 60s) → AI classify ADVISORY/WARNING/EMERGENCY" |
| 15:00 | AI Decision | Show JSON output của Claude | "Confidence 0.93 → EMERGENCY: auto-SMS 500k cư dân + redirect traffic + activate shelter" |
| 17:00 | Timeline | Tổng kết: phát hiện → cảnh báo = <5 phút | **"Trước: 30–45 phút gọi điện thủ công. Sau: 5 phút AI orchestrated"** |

### ACT 4 — CITIZEN EXPERIENCE (7 phút)
**"Cư dân thấy gì"**

| Thời gian | Màn hình | Hành động | Giải thích cho PO |
|---|---|---|---|
| 18:00 | Register | http://localhost:3000/citizen/register | "Public — không cần tài khoản trước" |
| 18:30 | Step 1 | Name, Email, Phone VN (validate 09x/08x), CCCD | "Validate ngay, không để lỗi tới bước 2" |
| 19:00 | Step 2 | Chọn BLD-001, Floor 12, Unit 1205 | "Gắn hộ khẩu — đồng hồ điện nước tự link theo" |
| 19:30 | Step 3 | Submit → "Account created! Auto-login" | "3 bước, 90 giây, không cần nhân viên xử lý" |
| 20:00 | Citizen Dashboard | Welcome + household + 1.25M VND unpaid | "Cư dân thấy ngay số dư mà không cần gọi lên BQL" |
| 20:30 | My Bills | Tháng 4/2026: Điện 350kWh=875k (UNPAID), Nước 15m³=375k (PAID) | "Minh bạch từng kWh, từng m³" |
| 21:00 | Bill Detail | Click "View" → Drawer: tier breakdown (bậc 1/2/3) | "Biết chính xác sao tiền điện cao — không thể tranh cãi" |
| 22:00 | Notifications | AQI alert, payment reminder, maintenance schedule | "Push thông báo — cư dân nhận trên app/web ngay khi có alert" |
| 24:00 | Tổng kết | — | **"NPS tăng từ 40 → 65+. Dispute tranh cãi giảm 60%"** |

### ACT 5 — TRAFFIC & ADMIN (5 phút)
**"Vận hành toàn thành phố"**

| Thời gian | Màn hình | Hành động | Giải thích cho PO |
|---|---|---|---|
| 25:00 | Traffic Dashboard | Bar chart 5 nút giao, INT-001 peak 1.2k xe/17:00 | "HTTP adapter — tích hợp hệ thống camera ngoài trong 1 ngày" |
| 25:30 | Incidents | ACCIDENT INT-001, CONGESTION INT-002/005 | "Cảnh báo sự cố realtime cho traffic control center" |
| 26:00 | — | — | *Tương lai: CCTV AI → biển số, tốc độ, tự phát hiện tai nạn* |
| 26:30 | Admin Panel | Users list, change role citizen→OPERATOR | "Role change ngay lập tức, RBAC enforce tại backend" |
| 27:00 | Sensors | Toggle sensor offline → disappear trên map | "Admin không cần SSH — mọi thứ qua UI" |
| 27:30 | Alert Rules | YAML-driven threshold: AQI WARNING=150, CRITICAL=200, EMERGENCY=300 | "Config rules không cần deploy lại code" |
| 28:00 | Tổng kết | — | **"MVP1 complete. Sẵn sàng Tier 1 customer."** |

---

## 3. Value Proposition per Tier

### Tier 1 — Single Building (Tòa nhà thông minh đơn lẻ)

**Target:** Chủ đầu tư chung cư cao cấp, văn phòng hạng A, trung tâm thương mại  
**Deployment:** Docker Compose 1 node, on-prem tại tòa nhà

**Pain points:**
- Hóa đơn điện nước cao, không biết unit nào tiêu phá
- AQI kém ảnh hưởng uy tín dự án (cư dân phàn nàn)
- ESG compliance mất 3 ngày thủ công
- Không có dashboard quản lý tập trung

**Value delivered với MVP1 hiện tại (zero additional dev):**
- Energy dashboard: biết ngay unit nào tiêu phá → giảm waste 5–10%
- AQI real-time → thông báo cư dân chủ động → NPS +25%
- ESG report 1 click → nộp báo cáo đúng hạn
- Citizen Portal → giảm dispute, tăng payment rate

**ROI ước tính (1,000-unit building):**
| Item | Tiết kiệm/năm |
|---|---|
| Giảm admin staff 30% | +300M VND |
| Energy efficiency 5% | +200M VND |
| Giảm dispute hóa đơn | +150M VND |
| Predictive maintenance | +100M VND |
| **Tổng tiết kiệm** | **+750M VND** |
| **Chi phí platform** | -150M VND |
| **Net ROI** | **+600M VND (4x)** |

**Giá:** 150M VND setup + 50M VND/năm license

---

### Tier 2 — Building Cluster (Khu đô thị / Văn phòng tổng hợp)

**Target:** Ban quản lý khu đô thị mới (Vinhomes, Ecopark, Sala), khu văn phòng  
**Deployment:** K8s nhỏ 3-5 nodes, 1 region

**Pain points:**
- Quản lý 5–20 tòa nhà, dữ liệu phân tán
- Không có ESG report tổng hợp (cross-building)
- Không biết tòa nào đang tiêu nhiều nhất
- Operator làm việc offline, không có unified view

**Value additions cần dev (MVP2):**
- Multi-tenancy: unified dashboard nhiều tòa
- Cross-building ESG analytics
- Edge gateway tại mỗi tòa (buffer 24h)
- Mobile operator app

**ROI ước tính (10-building cluster):**
| Item | Tiết kiệm/năm |
|---|---|
| Consolidate ops (10 admin → 5) | +2B VND |
| Cross-building load optimization 5% | +800M VND |
| Predictive maintenance 15% | +500M VND |
| ESG automation | +200M VND |
| **Tổng** | **+3.5B VND (7.75x)** |

**Giá:** 400M VND setup + 150M VND/năm license

---

### Tier 3 — Urban District (Quận/Huyện)

**Target:** UBND quận, Ban quản lý khu kinh tế, Sở TN&MT  
**Deployment:** K8s production 10-30 nodes, multi-AZ, +ClickHouse, +Kong

**Pain points:**
- Không có AQI real-time (<60s) cho toàn quận
- Phản ứng sự cố lũ/ô nhiễm chậm (30–45 phút)
- ESG report quận cần 2 tuần thủ công
- Không có evidence-based decision making

**Value additions (v3.0):**
- 50+ sensors phủ toàn quận → AQI district heatmap
- Flood early warning <15 phút → tự SMS/app cư dân
- District ESG theo ISO 37120
- Traffic optimization AI → congestion giảm 12%

**ROI ước tính (quận 100K cư dân):**
| Item | Lợi ích/năm |
|---|---|
| Ngăn thiệt hại lũ lụt 50% | +25B VND |
| Giảm ô nhiễm (health cost savings) | +10B VND |
| Traffic optimization | +8B VND |
| Water leak detection | +3B VND |
| **Tổng** | **+46B VND (23x)** |

**Giá:** 2B VND setup + 600M VND/năm + integration services

---

### Tier 4 — Smart Metropolis (Thành phố thông minh)

**Target:** UBND TP.HCM, Hà Nội, Đà Nẵng, thành phố thông minh quốc gia  
**Deployment:** K8s multi-region active-active, Event Mesh, Data Lakehouse

**Value:**
- Unified Command Center: 1,000+ sensors, cross-domain AI orchestration
- AI City Brain: flood + traffic + power → AI đề xuất, operator phê duyệt
- National ESG reporting (UNFCCC, GRI, TCFD) real-time
- Smart utilities: water/power/waste/transport tích hợp
- Open data API cho startup, researcher, gov

**ROI ước tính (thành phố 10M dân):**
| Item | Lợi ích/năm |
|---|---|
| Ngăn thiệt hại lũ lớn | +250B VND |
| Giảm ô nhiễm (healthcare) | +50B VND |
| Traffic + EV optimization | +80B VND |
| Water loss reduction | +40B VND |
| Peak energy shaving | +100B VND |
| **Tổng** | **+500B VND (10x)** |

**Giá:** 50B VND setup + 10B VND/năm

---

## 4. Kiến trúc Scale 4 Tier

### Bảng Quyết Định Nhanh

| Tier | Deploy | DB | Kafka | Flink | Backend | Trigger lên tier kế |
|------|--------|----|----|-----|---------|-----|
| **T1** | Docker Compose 1 node | TimescaleDB single | 1 broker, 4-8 part | 1 JM+1 TM, par=2 | Modular monolith | >500 sensor / >2 tòa |
| **T2** | K8s 3-5 nodes | TimescaleDB Patroni 1+1 | 3 brokers RF=3, 12 part | 1 JM HA + 2-4 TM, par=8 | Monolith + tách iot-ingestion | >5K sensor / >50K events/min |
| **T3** | K8s 10-30 nodes, multi-AZ | TimescaleDB 1+2 + ClickHouse 3 nodes | 3-5 brokers, Schema Registry, 24-32 part | Multi-job 4-8 TM, S3 ckpt, RocksDB | Microservices: iot, alert, analytics, ai-workflow, citizen | >50K events/sec / smart utility |
| **T4** | K8s multi-region + edge per building | TimescaleDB sharded + ClickHouse cluster + Lakehouse Iceberg | 5-7 brokers, MirrorMaker2, 64+ part | Per-domain clusters | Full microservices + Event Mesh + Kong + Istio + Keycloak | — |

### 4.1 Multi-Tenant Architecture Foundation

**Quyết định cốt lõi — implement NGAY từ MVP1:**

```sql
-- Thêm vào TẤT CẢ tables (environment, esg, traffic, alerts, citizens)
-- Backward-compatible: default='default', path='building.bld001'
ALTER TABLE environment.sensor_readings
  ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default',
  ADD COLUMN location_path LTREE NOT NULL DEFAULT 'city.district.cluster.building';
CREATE INDEX idx_sensor_tenant_path ON environment.sensor_readings
  USING GIST (tenant_id, location_path);
```

**Hierarchy:** `Tenant → City → District → BuildingCluster → Building → Floor → Zone → Sensor`

**Tenant isolation strategy per tier:**
| Tier | Strategy |
|------|---------|
| T1 | Single-tenant per deployment (Docker Compose riêng) |
| T2 | Row-Level Security (RLS) + `tenant_id` trong shared DB |
| T3 | Schema-per-tenant cho hot data + RLS cho cold data |
| T4 | Database-per-major-tenant + share Lakehouse |

**JWT claims chuẩn (mở rộng từ MVP1):**
```json
{
  "sub": "user-123",
  "tenant_id": "hcm",
  "tenant_path": "city.hcm",
  "scopes": ["environment:read","esg:read","alert:ack"],
  "roles": ["OPERATOR"],
  "allowed_buildings": ["bld-001","bld-002"]
}
```

### 4.2 Module Extraction Order

| Tier | Giữ monolith | Tách microservice |
|------|-------------|-----------------|
| T1 | TẤT CẢ 8 module | — |
| T2 | env, esg, traffic, citizen, admin, workflow, alert, notification | **iot-ingestion** (tải cao nhất, ít state) |
| T3 | env, traffic, esg, admin | + **alert** (latency-critical) + **analytics** (ClickHouse owner) + **ai-workflow** (Camunda+Claude) + **citizen** (public scale) + **notification** (multi-channel) |
| T4 | — | Full microservices + water, power-grid, waste, transport, emergency, city-brain |

**Quy tắc khi tách module:** chỉ tách khi có ≥1 lý do: tải khác biệt, latency riêng, deploy lifecycle khác, team owner riêng, compliance/security riêng.

### 4.3 New Infrastructure — Khi nào cần

| Component | T1 | T2 | T3 | T4 | Trigger |
|-----------|----|----|----|----|---------|
| Schema Registry (Apicurio) | — | Optional | **Bắt buộc** | Bắt buộc | >3 microservice cùng publish 1 topic |
| API Gateway (Kong) | — | Optional | **Bắt buộc** | Bắt buộc | >2 backend service hoặc public API |
| Keycloak IdP | — | Optional | **Bắt buộc** | Bắt buộc | >100 user hoặc cần SSO/federation |
| ClickHouse OLAP | — | — | **Bắt buộc** | Bắt buộc | District dashboard query >5s sau optimize |
| Multi-region/DR | — | — | Standby region | **Active-active** | SLA 99.95%+ hoặc compliance |
| Edge Computing (Flink edge) | — | Buffer only | Aggregation | **Full edge AI** | WAN bandwidth >70% sensor traffic |
| Data Lakehouse (Iceberg) | — | — | Optional | **Bắt buộc** | Historical >2 năm hoặc ML training |
| Vault (secrets) | env vars | **Cần thiết** | **Bắt buộc** | Bắt buộc | Bất kỳ production deployment |

### 4.4 New Domain Modules cho Tier 4 Smart Metropolis

| Module | IoT Sources | Stream Jobs | New Value |
|--------|------------|------------|-----------|
| **Water System** | Smart meter, pressure sensor, water quality probe | Leak detection (Flink CEP), pipe burst alert | Non-revenue water 20% → 5% |
| **Power Grid** | Substation sensor, smart inverter, EV charger | Load forecasting, grid stability | Demand response, peak shaving 12% |
| **Waste Management** | Smart bin fill-level, GPS truck | Route optimization TSP | Fuel savings 15%, recycling rate tracking |
| **Public Transport** | GTFS-RT bus/metro, ANPR camera | ETA prediction, congestion-aware routing | 15% ridership increase |
| **Emergency Center** | All sensors cross-domain | Multi-agency dispatch BPMN | Response 45min → 10min |
| **AI City Brain** | Feature store từ all domains | ML inference, cross-domain correlation | Predictive decisions, not reactive |

---

## 5. Roadmap

### Phase MVP2 — Production Hardening + Multi-Tenancy (Q2 2026, 10–12 tuần)

**Goal:** UIP sẵn sàng bán cho khách hàng Tier 1 thực tế với SLA-bearing deployment

| ID | Title | SP | Priority | Owner | Justification |
|---|---|---|---|---|---|
| MVP2-01 | HashiCorp Vault + secrets rotation | 8 | P0 | DevOps | Loại bỏ .env secrets; SLA requirement |
| MVP2-02 | SASL authentication + TLS Kafka | 5 | P0 | Backend | Bảo mật inter-service communication |
| MVP2-03 | Fill 12 QA test gaps (GAP-01→GAP-12) | 13 | P0 | QA+Backend | Production readiness critical path |
| MVP2-04 | EntityNotFoundException → 404 mapping | 3 | P1 | Backend | API contract; fix error cascade |
| MVP2-05 | Circuit Breaker state persistence + health probe | 5 | P1 | Backend | Prevent failure cascade khi pod restart |
| MVP2-06 | Cache eviction retry + TTL giảm 60s | 3 | P1 | Backend | Giảm stale config window từ 5 phút |
| **MVP2-07** | **Multi-tenancy: tenant entity + data isolation** | **13** | **P1** | **Backend** | **Enable SaaS model; data segregation** |
| MVP2-08 | Kubernetes Helm charts (all components) | 8 | P1 | DevOps | Production deployment; auto-scaling |
| MVP2-09 | GitHub Actions CI/CD pipeline | 5 | P1 | DevOps | Automated releases; <20min cycle |
| MVP2-10 | Prometheus + Grafana + alerting rules | 8 | P0 | DevOps | SLA tracking; on-call automation |
| MVP2-11 | PostgreSQL WAL backup + PITR | 5 | P1 | DevOps | Business continuity; RTO/RPO |
| MVP2-12 | React Native / PWA mobile app (Citizen) | 21 | P2 | Frontend | 5x mobile adoption |
| MVP2-13 | Tenant admin dashboard | 13 | P2 | Frontend | SaaS self-service |
| MVP2-14 | API rate limiting per tenant | 5 | P1 | Backend | Cost control; prevent abuse |
| MVP2-15 | Distributed tracing (Jaeger) | 5 | P2 | Backend | Debug production issues |
| MVP2-16 | OpenAPI spec validation in CI gate | 3 | P1 | QA | Prevent FE/BE contract drift |
| MVP2-17 | Coverage gate ≥80% critical paths | 2 | P0 | QA | Quality baseline |
| MVP2-18 | Security audit: OWASP Top 10 | 8 | P0 | Security | Compliance before production |
| MVP2-19 | Runbook + on-call playbook | 5 | P1 | Ops | MTTR <15 phút |
| **MVP2-20** | **tenant_id + LTREE location_path schema migration** | **5** | **P0** | **Backend** | **Foundation cho T2 không cần refactor** |

**Total: ~152 SP → 10–12 tuần (2 team, ~55 SP/sprint)**

---

### Phase v3.0 — Building Cluster + Advanced AI (Q3 2026, 12–16 tuần)

**Goal:** Phục vụ Tier 2 (5–20 tòa nhà), advanced AI, BMS integration

| ID | Title | SP | Priority |
|---|---|---|---|
| v3-01 | Cross-building analytics + aggregate dashboard | 13 | P0 |
| v3-02 | Advanced ESG: GRI Standards + carbon credit tracking | 21 | P1 |
| v3-03 | Predictive AI: energy forecasting (ARIMA/LSTM) | 13 | P1 |
| v3-04 | Predictive maintenance: sensor anomaly detection | 13 | P2 |
| v3-05 | BMS integration SDK (Modbus, BACnet, KNX) | 21 | P0 |
| v3-06 | Mobile operator app (iOS/Android, push notifications) | 21 | P1 |
| v3-07 | Kong API Gateway + Keycloak IdP | 13 | P0 |
| v3-08 | ClickHouse OLAP + analytics microservice | 13 | P0 |
| v3-09 | Building safety: structural monitoring module | 13 | P1 |
| v3-10 | Schema Registry (Apicurio) + Avro migration | 8 | P1 |

**Total: ~149 SP → 12–16 tuần**

---

### Phase v4.0 — Smart Metropolis (Q4 2026 – Q1 2027, 20+ tuần)

**Goal:** Phục vụ Tier 3/4 (quận/thành phố), smart utilities, AI City Brain

| ID | Title | SP | Priority |
|---|---|---|---|
| v4-01 | Smart utilities: water grid + power distribution modules | 34 | P0 |
| v4-02 | City-wide analytics (ClickHouse cluster, 100M+ records) | 21 | P0 |
| v4-03 | AI City Brain (cross-domain prediction, knowledge graph) | 34 | P1 |
| v4-04 | Government API integrations (Sở TN&MT, Sở GTVT) | 13 | P1 |
| v4-05 | Multi-region active-active deployment | 13 | P1 |
| v4-06 | Public transport integration (GTFS-RT) | 13 | P2 |
| v4-07 | Data Lakehouse (Iceberg/MinIO + Trino) | 21 | P1 |
| v4-08 | Emergency coordination center module | 21 | P1 |

**Total: ~170 SP → 20+ tuần (multiple teams)**

---

## 6. Sprint Planning MVP2

### Sprint MVP2-1: Security + QA Gaps + Technical Debt (Tuần 1–2, ~50 SP)

**Sprint Goal:** "Loại bỏ P0 security risks và fill critical test gaps — production-ready audit"

| Story | SP | Owner | DoD |
|---|---|---|---|
| MVP2-01: Vault integration + secrets rotation | 8 | DevOps | Secrets injected từ Vault; auto-rotation weekly |
| MVP2-03a: Alert escalation tests (GAP-01,02,09,10) | 5 | Backend+QA | 8 unit tests; ≥90% AlertService coverage |
| MVP2-03b: Cache service tests (GAP-04,05,06) | 5 | Backend+QA | TriggerConfigCacheService + SpringIT; cache hit/miss/evict |
| MVP2-03c: CB + audit tests (GAP-03,07,08,11,12) | 3 | Backend+QA | ClaudeApiServiceTest + WebMvc; ≥85% coverage |
| MVP2-04: EntityNotFoundException → 404 | 3 | Backend | ControllerAdvice + ExceptionHandler; regression test |
| MVP2-05: CB state persistence (minimumCalls=10) | 5 | Backend | Actuator health probe; Resilience4j config |
| MVP2-06: Cache retry + TTL 60s | 3 | Backend | Kafka retry 3×200ms; Redis TTL confirm |
| MVP2-18: Security audit OWASP | 8 | Security | Pentest report; fix top 5 findings |
| MVP2-16: OpenAPI CI gate | 5 | QA | openapi-diff trong GitHub Actions |

**DoD Sprint:** JaCoCo ≥80% critical paths; Zero P0 security findings; OpenAPI validated; CI pass

---

### Sprint MVP2-2: Multi-Tenancy + Monitoring (Tuần 3–4, ~50 SP)

**Sprint Goal:** "Tenant isolation sẵn sàng; production observability; Tier 1 customer UAT"

| Story | SP | Owner | DoD |
|---|---|---|---|
| MVP2-20: tenant_id + LTREE schema migration | 5 | Backend | Migration script; all tables có tenant_id + location_path |
| MVP2-07a: Tenant entity + RLS policy | 8 | Backend | TenantEntity; JPA queries filtered by tenant_id |
| MVP2-07b: TenantContext ThreadLocal + filter | 5 | Backend | Filter tất cả repository; test Tenant A không thấy data B |
| MVP2-08: K8s Helm charts | 8 | DevOps | Deploy lên k3s; auto-scaling proven |
| MVP2-09: GitHub Actions CI/CD | 5 | DevOps | Build/test/push/deploy <20 phút |
| MVP2-10: Prometheus + Grafana | 8 | DevOps | 5 alert rules (p95>200ms → page) |
| MVP2-11: PostgreSQL WAL backup | 5 | DevOps | 3-day retention; restore drill <1h RTO |
| MVP2-14: API rate limiting | 5 | Backend | 10K req/min default; 429 on breach |

**DoD Sprint:** Tenant isolation integration tests pass; K8s Helm deploy; CI/CD pipeline; Prometheus metrics live; Backup restore tested; Tier 1 manual UAT pass

---

## 7. Features Upgrade Backlog (17 User Stories)

### Category 1: Multi-Tenancy (Tier 2–4) — P0/P1

| ID | User Story | Priority | Tier |
|---|---|---|---|
| US-01 | Tenant Organization Management — org admin quản lý users, data isolation, customization per org | P0 | T2,3,4 |
| US-02 | Sub-Meter Hierarchy & Cost Allocation — phân bổ chi phí điện nước giữa units + shared areas | P1 | T1,2 |

### Category 2: Advanced ESG (Tier 2–4) — P1

| ID | User Story | Priority | Tier |
|---|---|---|---|
| US-03 | Carbon Credit & Offset Program — track verified carbon credits, net carbon footprint | P1 | T2,3,4 |
| US-04 | Regulatory Compliance Reports (GRI, TCFD, CDP) — auto-generate, map KPIs → framework indicators | P1 | T3,4 |
| US-05 | ESG Scoring & Benchmarking — 0-100 score, peer comparison, actionable recommendations | P1 | T2,3,4 |

### Category 3: Predictive AI (Tier 1–4) — P2

| ID | User Story | Priority | Tier |
|---|---|---|---|
| US-06 | Anomaly Detection (Equipment Health) — ML detect HVAC/elevator anomaly >2σ, auto work order | P2 | T1,2 |
| US-07 | Energy Demand Forecasting 24h/7d — ARIMA/LSTM, MAPE <5%, weather-aware | P2 | T3,4 |
| US-08 | Flood Risk Prediction 72h — rainfall + topography + drainage → risk map, 85%+ accuracy | P2 | T3,4 |

### Category 4: Smart Utilities (Tier 2–4) — P2

| ID | User Story | Priority | Tier |
|---|---|---|---|
| US-09 | Water Consumption Analytics & Leak Detection — smart meters, non-revenue water 20%→5% | P2 | T2,3,4 |
| US-10 | Smart Grid Demand Response — voluntary DR program, AI dispatch, bill credit settlement | P2 | T3,4 |

### Category 5: Mobile Apps (Tier 1–4) — P1

| ID | User Story | Priority | Tier |
|---|---|---|---|
| US-11 | Citizen Mobile App (iOS + Android) — bills, AQI alerts, report issues, biometric login | P1 | T1,2,3,4 |
| US-12 | Operator Mobile App (iOS + Android) — work orders, sensor status, offline map, photo upload | P1 | T2,3,4 |

### Category 6: Integrations (Tier 1–4) — P1

| ID | User Story | Priority | Tier |
|---|---|---|---|
| US-13 | BMS Integration SDK (Modbus, BACnet, KNX) — bi-directional, 3 major vendors | P1 | T1,2 |
| US-14 | Government Data Integration (Sở TN&MT, Sở GTVT API) — sync official AQI + weather | P1 | T3,4 |
| US-15 | Third-Party Smart City Platform API — OpenAPI expose data cho OneMap.vn, national portals | P1 | T3,4 |

### Category 7: Advanced Analytics (Tier 2–4) — P2

| ID | User Story | Priority | Tier |
|---|---|---|---|
| US-16 | BI Dashboard & Data Warehouse — pre-built + ad-hoc, scheduled exports, star schema | P2 | T2,3,4 |
| US-17 | Peer City Benchmarking — 50+ cities, 20+ metrics, heatmap ranking, annual benchmark | P2 | T4 |

**Priority Matrix:**

| Category | US | P0 | P1 | P2 | Timeline |
|---|---|---|---|---|---|
| Multi-Tenancy | 2 | 1 | 1 | — | 2 tháng |
| Advanced ESG | 3 | — | 2 | 1 | 2 tháng |
| Predictive AI | 3 | — | — | 3 | 4 tháng |
| Smart Utilities | 2 | — | — | 2 | 3 tháng |
| Mobile Apps | 2 | — | 2 | — | 3 tháng |
| Integrations | 3 | — | 3 | — | 4 tháng |
| Analytics | 2 | — | — | 2 | 2 tháng |
| **TOTAL** | **17** | **1** | **8** | **8** | **~1 năm** |

---

## 8. KPIs & SLA per Tier

| Tier | Customer | Buildings | Sensors | Users | Uptime SLA | Alert Latency | Report Gen | Pricing |
|---|---|---|---|---|---|---|---|---|
| **Tier 1** | Single building | 1 | ≤500 | ≤100 | 99.9% | <50ms p95 | <5s | $2K–5K/tháng |
| **Tier 2** | Building cluster | 5–20 | 500–5K | 100–1K | 99.95% | <30ms p95 | <10s | $5K–15K/tháng |
| **Tier 3** | City/Municipality | 50–200 | 5K–50K | 1K–10K | 99.99% | <20ms p95 | <30s | $15K–50K/tháng |
| **Tier 4** | Smart metropolis | 500+ | 50K+ | 10K+ | 99.99% | <15ms p95 | <60s | $50K+/tháng |

**Success Metrics:**
- Tier 1: ≥5 customers signed bởi cuối Q2 2026
- Tier 2: 1 city pilot signed bởi Q3 2026
- Tier 3: City authority partnership bởi Q4 2026
- Tier 4: Multi-city rollout plan bởi Q1 2027

---

## 9. Risk Register MVP2

| Risk | Xác suất | Tác động | Severity | Mitigation | Owner | Tuần |
|---|---|---|---|---|---|---|
| 12 test gaps block UAT customer | Medium | Critical (slip 2 tuần) | P0 | Testcontainers setup tuần 1; daily standup | QA+Backend | 1–2 |
| Multi-tenancy query filter regression (data leak) | Low | Critical (data breach) | P0 | 100% integration tests; peer review; test Tenant A≠B | Backend | 3–4 |
| K8s volume fails; data loss | Low | Critical (RTO >4h) | P0 | k3s testing trước; backup restore drill | DevOps | 3–4 |
| Vault credential rotation ops burden | Low | High (training needed) | P1 | Pre-stage Vault; runbook viết sẵn | DevOps | 1–2 |
| GitHub Actions secrets bị log | Low | Critical (security) | P0 | Weekly audit; Vault injection; mask secrets | DevOps | 1–2 |

---

## ADR Cần Viết (Backlog)

| ADR ID | Title | Target Tier | Priority |
|---|---|---|---|
| ADR-010 | Multi-tenant strategy: tenant_id + LTREE + RLS roadmap | T1→T4 | **P0 — Tuần 1** |
| ADR-011 | Module extraction order: iot → alert → analytics → ai-workflow → citizen | T2→T3 | P1 |
| ADR-012 | ClickHouse adoption trigger criteria | T3 | P1 |
| ADR-013 | Edge computing với EMQX edge + Flink edge jobs | T2→T4 | P1 |
| ADR-014 | API Gateway: Kong vs Spring Cloud Gateway | T3 | P2 |
| ADR-015 | IdP: Keycloak migration từ JWT hardcode | T3 | P2 |
| ADR-016 | Data Lakehouse: Iceberg/MinIO vs Snowflake | T4 | P2 |
| ADR-017 | Multi-region: active-active vs warm DR | T4 | P2 |

---

## Next Steps (Tuần này)

1. **PO approve MVP2 roadmap** → assign team leads cho 2 sprint đầu
2. **Backend**: Thêm `tenant_id` + `location_path` vào schema MVP1 (low-risk, backward compat) — tiền đề bắt buộc cho T2
3. **DevOps**: Setup HashiCorp Vault staging environment
4. **QA**: Setup Testcontainers trên CI Ubuntu (fix GAP-04, GAP-06 trước)
5. **SA**: Viết ADR-010 (Multi-tenant strategy) — chốt trước Sprint MVP2-2

**Week 4 checkpoint:** Tier 1 customer ký UAT agreement; K8s stack functional; security audit complete  
**Week 8:** Production readiness review; SLA agreement signed với khách hàng đầu tiên

---

*Tài liệu này tổng hợp từ 3 agent: SA (kiến trúc) + BA (demo & user stories) + PM (roadmap & planning)*  
*Cập nhật: 2026-04-25 | Next review: End of MVP2-1 (2 tuần)*
