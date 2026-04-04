# Thách Thức Kiến Trúc — Dành Cho Phase 2+

**Ngày tạo**: 2026-04-03  
**Tác giả**: UIP Solution Architect  
**Trạng thái**: Backlog — Chưa cần xử lý ở Phase 1

> Tài liệu này lưu các vấn đề kiến trúc đã được phân tích nhưng **chưa áp dụng ở MVP1**. Khi onboard city thứ 2 hoặc partner thật, đây là điểm khởi đầu để thiết kế — không phải thiết kế từ đầu.

---

## Mục Lục

1. [Thách Thức A: Partner Reliability & Data Freshness Contract](#thách-thức-a-partner-reliability--data-freshness-contract)
2. [Thách Thức B: Multi-City Deployment Governance](#thách-thức-b-multi-city-deployment-governance)
3. [Thách Thức C: Reporting Real-Time → Data Lake (Phase 2+)](#thách-thức-c-reporting-real-time--data-lake-phase-2)

---

## Thách Thức A: Partner Reliability & Data Freshness Contract

### Bối Cảnh Khi Nào Cần

Khi onboard partner thật (Singapore IoT Platform, Korean Env System, ...) — tức là data source nằm ngoài tầm kiểm soát của UIP. Internal sensor mất kết nối đã được xử lý ở Phase 1 bằng Stale Data Detection đơn giản.

### Vấn Đề Đặc Thù — "Silence is Ambiguous"

Trong event-driven architecture, **"không có event" và "không có sự kiện xảy ra"** trông giống hệt nhau từ góc nhìn downstream.

```
Tình huống: Singapore IoT Platform bị down 4 tiếng
  → ACL Adapter không nhận được data
  → Kafka topic UIP.iot.sensor.reading không có event từ SG sensors
  → environment-module không nhận AQI update từ SG districts
  → Dashboard hiển thị stale data mà không có cảnh báo rõ ràng
  → City operator không biết data đang bị thiếu
  → ESG report tháng này sẽ thiếu 4 tiếng dữ liệu mà không ai hay
```

### Tại Sao Circuit Breaker Thông Thường Không Đủ

Circuit Breaker (Resilience4j) được thiết kế cho synchronous calls:
```
Service A → [Circuit Breaker] → Service B
Nếu B timeout/lỗi nhiều → Circuit "mở" → A fallback
```

Với partner integration qua ACL adapter:
- Adapter **chủ động nhận** data từ partner (inbound), không phải gọi ra
- Nếu partner dùng Webhook → Circuit Breaker có ý nghĩa (partner gọi vào adapter)
- Nếu adapter polling partner → Circuit Breaker áp dụng được cho polling loop
- Nếu partner dùng MQTT → connection drop, không phải circuit concept

### Framework Đề Xuất — 3 Lớp Bảo Vệ

**Lớp 1 — Heartbeat & Liveness Check (tại ACL Adapter)**
```java
// Mỗi partner adapter phải emit heartbeat metric
@Scheduled(fixedDelay = 30_000)
public void emitHeartbeat() {
    meterRegistry.gauge("adapter.last_received_event_age_seconds",
        ChronoUnit.SECONDS.between(lastReceivedAt, Instant.now()));
}
// Alert nếu không nhận event trong > 5 phút (configurable per partner SLA)
```

**Lớp 2 — Stale Data Detection tại Consumer**
```java
// environment-module biết sensor nào thuộc partner nào
@Scheduled(fixedDelay = 60_000)
public void detectDataGaps() {
    List<Sensor> staleSensors = sensorRepo.findSensorsWithNoReadingSince(
        Instant.now().minus(sensorStalenessThreshold)
    );
    staleSensors.forEach(sensor ->
        eventBus.publish(SensorDataGapDetectedEvent.of(sensor))
    );
}
```

**Lớp 3 — Graceful Degradation Strategy**

```
Khi partner data gap được phát hiện:

Option A — Last Known Good Value (LKGV)
  → Hiển thị giá trị cuối cùng + timestamp + cảnh báo "Data from HH:MM"
  → Phù hợp: AQI snapshot (thay đổi chậm)
  → Không phù hợp: Traffic real-time counts

Option B — Data Gap Marker
  → Dashboard hiển thị "No Data" với màu khác, không fake giá trị
  → Phù hợp: Khi data gap ảnh hưởng safety decision
  → ESG report đánh dấu khoảng thời gian thiếu, không interpolate

Option C — Fallback đến Internal Sensor (nếu có)
  → UIP cũng có sensor vật lý ở khu vực đó → dùng tạm
  → Phù hợp: Critical safety areas có redundant sensors
```

### ESG Integrity Khi Có Data Gap

```
ESG Report Generation phải:
1. Check data completeness trước khi generate
   → "District SG-Central: 4.2 giờ thiếu data trong tháng"
2. Highlight khoảng thiếu data + lý do (partner outage) trong report
   → Không tự điền giá trị tưởng tượng
3. Partner SLA tracking tự động
   → "Singapore IoT Platform: 99.1% uptime tháng 3/2026"
   → Cơ sở để đàm phán SLA penalty nếu < 99.5%
```

### Câu Hỏi Phải Trả Lời Trước Khi Onboard Partner

> Nếu partner xuống 1 ngày, ai chịu trách nhiệm về data gap trong ESG report gửi thành phố — UIP hay partner?

Đây là **quyết định business**, không phải kỹ thuật. Phải có trong SLA contract trước khi design adapter.

---

## Thách Thức B: Multi-City Deployment Governance

### Bối Cảnh Khi Nào Cần

Khi onboard city thứ 2. Một city + một team = không có vấn đề. Vấn đề bắt đầu khi:
- Mỗi city muốn enable/disable module khác nhau
- Mỗi city có partner riêng với compliance riêng
- Security patch cần propagate đến tất cả cities đồng thời

### Vấn Đề — Version Matrix Chaos

```
             HCM      DaNang   Singapore   Korea
iot-module    v2.1     v2.0     v1.8 (*)    v2.1
environment   v3.2     v3.2     -           v2.9 (*)
esg-module    v1.5     v1.4 (*) v1.5        v1.5

(*) = yêu cầu đặc biệt, bị giữ lại version cũ
```

Khi có 5 cities × 8 modules × N versions → security patch không propagate kịp, bug fix diverge, knowledge silo.

### Tại Sao "Dedicated Team Per City" Không Scale

| Hệ Quả | Mô Tả |
|--------|-------|
| **Security patch lag** | CVE trong iot-module → phải patch 5 city riêng → DaNang team bận → delay 2 tuần |
| **Bug fix divergence** | Fix bug cho HCM → DaNang không merge → 6 tháng sau vẫn có bug đó |
| **Feature flag chaos** | HCM enable X, DaNang disable → bug report "tính năng không hoạt động" nhưng đúng thiết kế |
| **Knowledge silos** | Chỉ DaNang team biết tại sao esg-module bị giữ v1.4 → người nghỉ → không ai biết |
| **Tăng headcount tuyến tính** | 5 city → 5 teams → 10 city → 10 teams → không sustainable |

### Tại Sao "Enforce Đồng Nhất Tuyệt Đối" Cũng Không Đủ

```
Platform team: "Mọi city dùng chung version, không có exception"
Singapore: "Chúng tôi có compliance requirement, module A phải ở version cũ"
Korea: "Partner chỉ hỗ trợ API v1.8, không upgrade được"
```

City deployments luôn có legitimate reasons để khác nhau. Enforce tuyệt đối = mất business.

### Framework — Release Train Model

```
Release Train Q2/2026:
  Ngày 1:    Platform team release version mới (tất cả modules)
  Ngày 1-14: City teams test trên staging environment
  Ngày 15:   Tất cả cities bắt buộc upgrade (trừ exception đã approved)
  Ngày 16-30: Support window post-upgrade

Exception process:
  City cần giữ version cũ → submit exception request
  Platform review: security risk, technical reason, upgrade timeline
  Approved: maximum 1 release train (~2-3 tháng)
  Không được approved: security patches không có exception bao giờ
```

### Operational Tooling Cần Khi Scale

```
1. GitOps per city (ArgoCD)
   → Mỗi city có 1 Git repo chứa helm values
   → Thay đổi config = PR → review → merge → auto-deploy
   → Audit trail: ai thay đổi gì, khi nào

2. Deployment Dashboard (tập trung)
   → Xem tất cả cities: version nào đang chạy ở đâu
   → Alert khi city chạy version quá cũ (> 2 trains behind)

3. Security patch automation
   → 1 PR propagate tới tất cả city repos tự động
   → City team chỉ cần review và merge
```

### Câu Hỏi Phải Trả Lời Trước Khi Onboard City Thứ 2

> Khi một city có compliance requirement về data residency (dữ liệu phải ở trong lãnh thổ), module nào cần thay đổi? Ai quyết định — platform team hay city team?

Ranh giới governance này phải được định nghĩa trước, không phải sau khi có incident.

---

## Thách Thức C: Reporting Real-Time → Data Lake (Phase 2+)

> Xem chi tiết đầy đủ tại [realtime-reporting-architecture.md](./realtime-reporting-architecture.md)

### Bối Cảnh Khi Nào Cần

Khi module bắt đầu tách ra **database riêng biệt** — SQL JOIN cross-module không còn khả thi. Ở Phase 1, tất cả schemas nằm trong cùng TimescaleDB instance nên cross-schema JOIN vẫn hoạt động.

### Tóm Tắt Giải Pháp

**Kappa Architecture — Flink Unified Streaming:**
- Một Flink job duy nhất fork hai path từ cùng Kafka stream
- **Hot Path** (30s window): threshold detection → alert → Redis → SSE
- **Cold Path** (10min window): cross-module join → ClickHouse denormalized read model

**Điều kiện tiên quyết:** Module đã tách DB riêng, Kafka retention ≥ 90 ngày, Schema Registry, RocksDB state sizing đúng.

---

*Các thách thức này có thực nhưng xuất hiện ở Phase 2+. Không cần giải quyết sớm hơn mức cần thiết.*
