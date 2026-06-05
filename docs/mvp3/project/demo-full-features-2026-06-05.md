# UIP Smart City Platform — Full Feature Demo
## Investor & PO Presentation — Tính năng mới Sprint 3 & Toàn bộ MVP3

**Ngày:** 2026-06-05  
**Phiên bản:** MVP3 Final (Pilot-Ready)  
**Thời lượng:** 30–45 phút (bản đầy đủ) | 10 phút (executive)  
**Đối tượng:** Investor, PO, City Authority (HCMC)  
**Người trình bày:** PM + Backend Lead + SA

---

## Tóm tắt điều hành (2 phút đọc)

> **UIP (Urban Intelligence Platform)** là nền tảng quản lý tòa nhà thông minh đầu tiên tại Việt Nam tích hợp IoT, AI và ESG — được thiết kế cho các thành phố lớn như TP.HCM. Sau 3 giai đoạn phát triển liên tục (MVP1 → MVP2 → MVP3), hệ thống đã sẵn sàng cho Pilot Phase với thành phố bắt đầu từ 04/08/2026.

| Chỉ số | Giá trị |
|--------|---------|
| Tổng tính năng | 15 module chính |
| API endpoints | 107 được document |
| Test coverage | 1,191 tests, 0 failure |
| Kiến trúc HA | Kafka 3-node + ClickHouse 2-node |
| Bảo mật | OWASP 0 CVE blocking, Keycloak RSA |
| Pilot launch | **2026-08-04** |

---

## Cấu trúc Demo (30 phút)

| Act | Chủ đề | Thời lượng | Điểm nhấn |
|-----|--------|-----------|-----------|
| **Act 0** | Setup & Tổng quan | 2 phút | Platform overview |
| **Act 1** | Từ MVP1: Nền tảng IoT | 5 phút | Sensor, Map, Alert cơ bản |
| **Act 2** | Từ MVP2: Thành phố thông minh | 8 phút | Multi-tenant, ESG, AI |
| **Act 3** | **🆕 MVP3 Sprint 3+: Tính năng mới** | 12 phút | BMS, BPMN AI, HA, Mobile |
| **Act 4** | ROI & Roadmap | 3 phút | Business value |

---

## Setup trước Demo (30 phút trước)

```bash
# 1. Khởi động HA stack
cd infrastructure && make up-ha

# 2. Kiểm tra tất cả services healthy
docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "healthy|Up"

# 3. Seed dữ liệu demo
./scripts/demo-setup.sh

# 4. Kiểm tra backend health
curl http://localhost:8080/actuator/health | python3 -m json.tool

# 5. Mở trình duyệt (Chrome, incognito, 1920×1080)
# Frontend: http://localhost:3000
# Kafka UI: http://localhost:8090
# Flink UI: http://localhost:8081
```

**Tài khoản demo:**
- Admin: `admin@hcm-uip.vn` / (env: DEMO_ADMIN_PASS)
- Operator: `operator@hcm-uip.vn`
- Citizen: `citizen@hcm-uip.vn`

---

## Act 0 — Platform Overview (2 phút)

### Narration
> *"UIP là nền tảng thành phố thông minh tích hợp IoT, AI và báo cáo ESG — một cái nhìn duy nhất để quản lý toàn bộ hệ thống tòa nhà, môi trường và năng lượng của thành phố. Hôm nay tôi sẽ cho các bạn thấy toàn bộ hành trình từ MVP1 đến MVP3 — từ sensor dữ liệu đến quyết định AI đến báo cáo ESG cho chính quyền thành phố."*

**Màn hình:** Architecture diagram (hoặc mô tả bằng ngôn ngữ)
```
IoT Sensors → EMQX MQTT → Kafka → Apache Flink → TimescaleDB/ClickHouse
                                                         ↓
Mobile App ← REST API ← Spring Boot Backend ← Redis Cache
    ↓
City Dashboard (React) + ESG Reports + AI Workflow (Camunda)
```

---

## Act 1 — Nền tảng IoT (MVP1 — đã hoàn thành 04/2026) (5 phút)

> *"Đây là MVP1 — nền tảng mà chúng tôi đã xây trong 5 sprint đầu. Pipeline IoT end-to-end."*

### 1.1 City Operations Center — Bản đồ thời gian thực

**Hành động:**
1. Login → City Operations Center
2. Hiển thị bản đồ HCMC với 8 sensor markers màu theo AQI
3. Click vào một sensor → xem readings real-time

**Điểm nhấn cho investor:**
- Pipeline: **Sensor → MQTT → Kafka → Flink → Dashboard < 30 giây**
- Color coding: 🟢 Tốt | 🟡 Trung bình | 🔴 Xấu | 🟤 Nguy hiểm
- Filter theo quận: Click "Quận 1" → map zoom, chỉ hiện Q1

**Metrics:**
- 2,500 messages/giây throughput
- p95 latency < 200ms cho UI
- 4 Flink jobs chạy song song

### 1.2 Alert System cơ bản

**Hành động:**
1. Alert Feed → xem danh sách 20 alerts gần nhất
2. Trigger demo alert: `POST /api/v1/simulate/iot-sensor` với AQI > 150
3. Alert xuất hiện trên bản đồ (icon nhấp nháy đỏ) trong vòng 30s

**Điểm nhấn:**
> *"Không cần operator nhìn màn hình — hệ thống tự phát hiện, tự cảnh báo."*

### 1.3 ESG Dashboard cơ bản

**Hành động:**
1. ESG Tab → xem AQI, Energy, Carbon metrics
2. Nút "Generate Report" → XLSX xuất trong < 1 phút

**Business value:**
- Báo cáo ESG: từ **3 ngày thủ công → < 1 phút 1 click**
- Format chuẩn GRI cho submission lên cơ quan nhà nước

---

## Act 2 — Thành phố thông minh (MVP2 — hoàn thành 05/2026) (8 phút)

> *"MVP2 nâng cấp từ single-tenant lên multi-tenant — một platform phục vụ được nhiều tòa nhà, nhiều khu công nghiệp cùng lúc với dữ liệu cách ly hoàn toàn."*

### 2.1 Multi-Tenant Architecture

**Demo:**
1. Đăng nhập với `admin@quanly-kcn-binh-duong.vn` (tenant: "binh-duong")
2. Chỉ thấy dữ liệu KCN Bình Dương — không thấy dữ liệu HCMC
3. Switch sang tenant HCMC → dữ liệu khác hoàn toàn

**Kỹ thuật đằng sau:**
- JWT claim `tenant_id` tự động inject vào mọi query
- PostgreSQL Row-Level Security ngăn data leak hoàn toàn
- Kafka streams được tag theo tenant — không bao giờ cross-contaminate

**Investor pitch:**
> *"Mỗi tenant trả subscription riêng — scalable đến hàng trăm tòa nhà."*

### 2.2 AI Workflow Engine (7 kịch bản)

**Demo — Kịch bản Flood Alert:**
1. AI Workflow → chọn "Flood Alert Automation"
2. Hiển thị BPMN diagram: Sensor phát hiện → AI phân tích → Notification → Operator confirm
3. Trigger test: `POST /api/v1/test/inject-flood-alert`
4. Xem workflow chạy từng step trong Camunda Cockpit

**Điểm nhấn:**
- 7 kịch bản AI: Flood, AQI, Energy spike, Structural, Equipment failure, Emergency response, ESG report
- AI decision < 10 giây
- Human-in-the-loop: AI đề xuất → Operator xác nhận → Execute

### 2.3 ESG Advanced (GRI 302 + GRI 305)

**Demo:**
1. ESG Reports → chọn Q1 2026
2. "Generate GRI 302-1 Report" → Energy consumption by building
3. "Generate GRI 305-4 Report" → CO2 emissions intensity
4. **🆕 NEW:** Nút "Export PDF" → PDF chuẩn GRI với bảng số liệu

**Business value:**
> *"City authority có thể submit báo cáo ESG này trực tiếp lên Bộ Tài nguyên & Môi trường — không cần xử lý thêm."*

### 2.4 Performance: Cache 11x

**Demo ngắn:**
- Trước cache: ESG query ~850ms
- Sau cache (Redis + TimescaleDB continuous aggregates): ~75ms
- **11x improvement** — k6 benchmark đã verify với 1,000 concurrent users

---

## Act 3 — Tính năng mới MVP3 (Sprint 3+, 2026-05 → 2026-07) (12 phút)

> *"Và đây là những gì chúng tôi đã thêm vào MVP3 — 8 sprint, 10 tuần phát triển — để biến đây thành một platform sẵn sàng cho pilot thực tế."*

### 3.1 🆕 Building Management System (BMS) — Quản lý thiết bị tòa nhà

**Đây là gì:**
> *"Không chỉ monitor — chúng ta có thể điều khiển thiết bị trực tiếp qua platform."*

**Demo:**
1. BMS tab → Buildings → "Tòa nhà Bitexco F88"
2. Xem danh sách thiết bị: HVAC, Thang máy, Hệ thống PCCC, Chiếu sáng
3. Trạng thái real-time: temperature, power consumption
4. "Device Discovery" → tự động scan BACnet/Modbus trên network
5. Gửi lệnh: HVAC → "Giảm nhiệt độ 2°C" → xác nhận

**Protocols được hỗ trợ:**
- **Modbus TCP** — thiết bị công nghiệp phổ biến tại VN
- **BACnet/IP** — chuẩn quốc tế cho tòa nhà thương mại
- **MQTT v5** — IoT sensors (qua EMQX)

**Security:**
- Mọi lệnh điều khiển: JWT + RBAC + audit log
- Không bao giờ expose trực tiếp thiết bị ra internet

### 3.2 🆕 Building Safety — Giám sát kết cấu

> *"Sau trận động đất ở Thổ Nhĩ Kỳ, tòa nhà cao tầng Việt Nam cần monitoring rung động liên tục."*

**Demo:**
1. Building Safety tab → "Tòa nhà A, District 1"
2. Vibration gauge (0 – 5 mm/s): đang ở 0.8 mm/s (ngưỡng alert: 2.5 mm/s)
3. Trend chart 24h: bình thường vs spike khi xe tải nặng đi qua
4. Alert rule: "Nếu vibration > 2.5 mm/s liên tục 5 phút → Notify structural engineer"

**Standards compliance:**
- TCVN 9386:2012 (Việt Nam seismic design)
- ISO 4866 (vibration measurement)
- Welford algorithm cho anomaly detection real-time

### 3.3 🆕 AI Workflow Designer — Thiết kế quy trình không cần code

> *"City authority muốn tùy chỉnh quy trình phản ứng — không cần gọi developer."*

**Demo:**
1. AI Workflow → "New Workflow"
2. Drag & drop: Start Event → AQI Check (AI node) → Branch → Notify Citizen | Alert Operator
3. Configure AI node: "If AQI > 150 AND duration > 30min → execute"
4. Deploy workflow → Active trong 30 giây

**Công nghệ:**
- bpmn-js visual designer
- Camunda 7 engine
- AI decision nodes kết nối Claude API

**ROI:**
> *"Operator có thể tạo quy trình mới trong 10 phút — không phải 2 tuần chờ IT."*

### 3.4 🆕 Predictive Analytics (ARIMA Forecasting)

> *"Biết trước hôm mai dùng bao nhiêu điện — để mua điện giá thấp, không mua giá cao."*

**Demo:**
1. ESG → Energy Forecast tab
2. Biểu đồ 7 ngày tới: đường dự đoán + confidence interval (màu xanh nhạt)
3. Anomaly markers: "Thứ 3 tuần tới: tiêu thụ tăng 23% — dự báo hội nghị lớn"
4. Baseline so sánh: Actual vs Predicted (MAPE 3.54% — độ chính xác cao)

**Technical:**
- ARIMA model được train trên 90 ngày historical data
- Python FastAPI forecast-service
- Fallback to Naive model nếu ARIMA fails

### 3.5 🆕 Mobile App — Operator trên di động

> *"Operator không ngồi văn phòng 24/7 — họ cần nhận alert ngay trên điện thoại."*

**Demo (sử dụng Expo Go app trên điện thoại thật):**
1. Mở mobile app → Login với Keycloak PKCE
2. Dashboard: 4 KPI cards (AQI, Energy, Active Alerts, Buildings)
3. Trigger alert → Push notification đến điện thoại trong < 10 giây
4. Tap notification → App mở thẳng vào trang alert cụ thể
5. Acknowledge alert ngay trên điện thoại

**Tính năng:**
- React Native + Expo (iOS + Android cùng codebase)
- Push notification: FCM (Android) + APNs (iOS)
- Offline: cached data hiển thị khi mất mạng
- Keycloak SSO — không cần tạo tài khoản riêng

### 3.6 🆕 High-Availability Infrastructure

> *"Đây là điểm khác biệt lớn nhất so với competitors — không downtime kể cả khi node chết."*

**Live HA Demo (1 phút, ấn tượng nhất):**
1. Mở Kafka UI: http://localhost:8090 → thấy 3 brokers đang sync
2. Mở terminal: `docker stop uip-kafka-2` → Kill một broker
3. Refresh dashboard: **Mọi thứ vẫn hoạt động bình thường**
4. `docker start uip-kafka-2` → broker tự rejoin cluster
5. "Zero downtime. Zero data loss."

**HA Architecture:**
```
Kafka: 3 brokers (KRaft mode, no Zookeeper)
ClickHouse: 2 nodes + 3 Keeper (quorum-based replication)
TimescaleDB: Primary + Standby (streaming replication)
```

**SLA đạt được:** 99.9% uptime (tested với failure injection tests)

### 3.7 🆕 API Contract Completion (107/110 endpoints)

> *"Cho developers và đối tác tích hợp — toàn bộ API được document chuẩn OpenAPI 3.0."*

**Demo nhanh:**
1. Mở Swagger UI: http://localhost:8080/swagger-ui.html
2. "Chúng tôi có 107 endpoints được document đầy đủ với request/response schemas"
3. Thử live: `GET /api/v1/environment/sensors` → response với dữ liệu thật
4. Error codes: 401, 403, 404, 400 — tất cả được handle đúng

### 3.8 🆕 ESG PDF Export (GRI chuẩn)

**Demo:**
1. ESG Reports → Q1 2026
2. "Export PDF" → Loading ~3 giây
3. PDF mở ra: header UIP + GRI 302-1 table + GRI 305-4 table + charts
4. "Sẵn sàng nộp cho cơ quan chức năng."

---

## Act 4 — ROI & Business Value (3 phút)

### Tác động kinh doanh đã đo được

| Vấn đề trước | Giải pháp UIP | Kết quả |
|---|---|---|
| Báo cáo ESG thủ công 3 ngày | 1-click PDF export | **−97% thời gian** |
| Alert response thủ công 10–15 phút | AI workflow tự động | **< 30 giây** |
| Kiểm tra thiết bị on-site | BMS remote control | **−60% chi phí hiện trường** |
| Dự báo năng lượng thủ công | ARIMA forecasting | **MAPE 3.54%** |
| Data leak giữa các tenant | RLS + JWT isolation | **0 security incidents** |
| Downtime khi upgrade | HA + Blue-green deploy | **Zero downtime** |

### 4 Customer Tiers & Revenue Model

| Tier | Customer | Features | Pricing |
|------|----------|----------|---------|
| **T1 — Starter** | Tòa nhà đơn | Monitor + ESG basic | $500/tháng |
| **T2 — Business** | Khu công nghiệp | + BMS + AI Workflow + HA | $2,000/tháng |
| **T3 — Enterprise** | Quận/Huyện | + Forecasting + Custom modules | $8,000/tháng |
| **T4 — City** | Toàn thành phố | Full platform + SLA 99.9% | Contract-based |

### Pilot Phase Roadmap

```
2026-08-04  Soft launch — HCMC District 1 (5 buildings, 50 sensors)
2026-08-10  Signed delivery — Tier 2 Pilot contract
2026-09-01  Expand to 3 districts, 20 buildings
2026-12-01  City-wide rollout v3.1 (iOS app, mobile control panel)
```

---

## Q&A Guide

### Câu hỏi thường gặp từ Investor

**Q: Scale lên 10,000 sensors được không?**
> A: Kafka 3-broker hiện tải được 2,500 msg/s. Scale lên bằng cách thêm broker — horizontal scaling không cần thay code. ClickHouse có thể handle billions of rows.

**Q: Bảo mật dữ liệu tòa nhà như thế nào?**
> A: 3 lớp: (1) JWT + Keycloak RSA auth, (2) PostgreSQL Row-Level Security ngăn cross-tenant access, (3) Production profile gating debug endpoints. OWASP scan 0 critical CVE.

**Q: Tích hợp với hệ thống cũ (legacy BMS) được không?**
> A: Có. Hỗ trợ Modbus TCP và BACnet/IP — 2 protocol phổ biến nhất tại VN. Adapter pattern cho phép thêm protocol mới mà không sửa core.

**Q: Đối thủ cạnh tranh?**
> A: Schneider EcoStruxure (không có AI workflow, không đa ngôn ngữ VI), Siemens Desigo (giá rất cao, khó tùy chỉnh). UIP: AI-native, built for VN market, multi-tenant từ đầu.

### Câu hỏi từ PO / City Authority

**Q: Operator mới học dùng mất bao lâu?**
> A: Dashboard trực quan, không cần training kỹ thuật. Estimate 2 giờ onboarding. Mobile app = quen với mọi smartphone.

**Q: Khi hệ thống down thì dữ liệu có mất không?**
> A: Không. Kafka persist messages 7 ngày. HA cluster tự failover < 30 giây. TimescaleDB có standby replica.

**Q: ESG report format có được cơ quan nhà nước chấp nhận không?**
> A: GRI 302 + GRI 305 là chuẩn quốc tế. Bộ TNMT VN đã adopt GRI framework. PDF export sẵn sàng submit.

---

## Phụ lục: Stack công nghệ

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend | Java / Spring Boot | 3.2.4 |
| Stream processing | Apache Flink | 1.19 |
| Message broker | Apache Kafka (KRaft) | 3.9 |
| OLTP Database | TimescaleDB (PostgreSQL 15) | 2.13 |
| Analytics DB | ClickHouse | 23.8 |
| Cache | Redis | 7.2 |
| MQTT Broker | EMQX | 5.3 |
| Frontend | React 18 + TypeScript | Vite 5 |
| Mobile | React Native + Expo | 51 |
| AI Workflow | Camunda 7 + Claude API | 7.22 |
| API Gateway | Kong | 3.6 |
| Auth | Keycloak | 23.0 |
| Object Storage | MinIO | 2024 |
| Forecasting | Python FastAPI + ARIMA | - |

---

## Checklist Demo Day

- [ ] HA stack up (`make up-ha` → tất cả healthy)
- [ ] Demo data seeded (`./scripts/demo-setup.sh`)  
- [ ] Backup recording sẵn sàng (OBS/Zoom local)
- [ ] 3 browser tabs mở sẵn: Frontend, Kafka UI, Swagger
- [ ] Mobile device: Expo Go installed, logged in
- [ ] Internet connection stable (LAN preferred)
- [ ] Backup slide deck nếu demo crash

---

*Tài liệu: Demo Full Features MVP1+2+3 | Version 1.0 | 2026-06-05*  
*Xem chi tiết: [docs/mvp3/MVP3-SUMMARY.md](MVP3-SUMMARY.md)*
