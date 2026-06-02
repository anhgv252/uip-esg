# UIP Smart City Platform — Hướng dẫn tích hợp & vận hành
**Dành cho nhà đầu tư & đối tác triển khai**
_Version 1.0 — Sprint 6 — 2026-06-01_

---

## Tổng quan

UIP Smart City Platform là nền tảng đa-tenant, sử dụng kiến trúc event-driven cho phép nhiều khu đô thị, nhiều hệ thống, nhiều loại thiết bị **cùng tồn tại và phối hợp** với nhau trong một platform thống nhất. AI là lớp trí tuệ trung tâm biến các sự kiện rời rạc thành quy trình vận hành thông minh.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    UIP Platform — Lớp tích hợp                       │
│                                                                       │
│  [Khu đô thị A]  [Khu đô thị B]  [Khu đô thị C]                    │
│       │                │                │                             │
│       └────────────────┴────────────────┘                             │
│                         │                                             │
│              ┌──────────▼──────────┐                                 │
│              │  Kong API Gateway   │  (JWT + tenant routing)          │
│              └──────────┬──────────┘                                 │
│                         │                                             │
│    ┌────────────────────┼────────────────────┐                       │
│    │                    │                    │                        │
│  [EMQX MQTT]      [Kafka Topics]       [REST API]                   │
│  IoT/Sensor        Event Bus           External                      │
│    │                    │              Systems                        │
│    └────────────────────┼──────────────────┘                        │
│                         │                                             │
│              ┌──────────▼──────────┐                                 │
│              │  AI Workflow Engine  │  (Camunda + Claude AI)          │
│              │  DecisionRouter      │  (Confidence Routing)           │
│              └──────────┬──────────┘                                 │
│                         │                                             │
│         ┌───────────────┼───────────────┐                            │
│         │               │               │                             │
│    [TimescaleDB]  [ClickHouse]    [Notifications]                    │
│    Time-series    Analytics       Operators/Citizens                  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Câu hỏi 1: Khu đô thị mới join vào hệ thống như thế nào?

### Khái niệm Tenant

Mỗi khu đô thị là một **Tenant** độc lập. Mọi dữ liệu (sensor readings, ESG reports, alerts, BMS devices) đều có `tenant_id` — đảm bảo **data isolation hoàn toàn** giữa các khu đô thị.

```
Tier T1 → Khu đô thị nhỏ      (< 100 devices)
Tier T2 → Khu đô thị vừa     (100–1000 devices)
Tier T3 → Khu đô thị lớn     (> 1000 devices, multi-zone)
```

### Quy trình onboarding (5 bước)

```
Bước 1: Đăng ký Tenant
┌────────────────────────────────────────────────────┐
│ POST /api/v1/admin/tenants                          │
│ {                                                   │
│   "tenantId":    "vinhomes-grand-park",            │
│   "tenantName":  "Vinhomes Grand Park Q9",         │
│   "tier":        "T2",                              │
│   "locationPath": "hcmc.q9.vinhomes-grand-park"    │
│ }                                                   │
└────────────────────────────────────────────────────┘

Bước 2: Platform tự động provision
  ├─ Keycloak: tạo realm + roles (admin, operator, viewer)
  ├─ PostgreSQL: schema isolation (tenant_id filter)
  ├─ Kafka: namespace topics UIP.{tenantId}.*
  └─ EMQX: ACL rules cho MQTT namespace {tenantId}/#

Bước 3: Cấu hình thông tin khu đô thị
  POST /api/v1/admin/tenants/{tenantId}/config
  Ví dụ: timezone, ngôn ngữ, AQI thresholds, alert emails

Bước 4: Thêm admin user cho khu đô thị
  Keycloak: tạo user + assign role "district-admin"
  → Người vận hành khu đô thị đăng nhập và tự quản lý

Bước 5: Kiểm tra
  GET /api/v1/admin/tenants/{tenantId}
  → status: ACTIVE, tier: T2, configEntries: {...}
```

### locationPath — định vị phân cấp địa lý

Hệ thống dùng **PostgreSQL ltree** để biểu diễn địa lý dạng cây:

```
hcmc                          ← Thành phố
hcmc.q1                       ← Quận
hcmc.q1.ben-nghe              ← Phường
hcmc.q9.vinhomes-grand-park   ← Khu đô thị
hcmc.q9.vinhomes-grand-park.toa-s1  ← Tòa nhà
```

**Lợi ích**: Truy vấn "tất cả sensor trong Quận 9" chỉ cần `locationPath ~ 'hcmc.q9.*'` — không cần JOIN phức tạp.

---

## Câu hỏi 2: Cấu hình một hệ thống bên ngoài join vào UIP như thế nào?

### UIP hỗ trợ 3 phương thức tích hợp

```
┌─────────────────┬──────────────────────────────────────────────┐
│ Phương thức     │ Dùng cho                                      │
├─────────────────┼──────────────────────────────────────────────┤
│ MQTT (EMQX)     │ IoT sensors, camera events, BMS telemetry    │
│ Kafka Bridge    │ Hệ thống ERP, SCADA, third-party platforms   │
│ REST API        │ Web services, mobile apps, partner APIs       │
└─────────────────┴──────────────────────────────────────────────┘
```

### Phương thức 1: MQTT — dành cho hệ thống IoT/BMS

```yaml
# Cấu hình device kết nối EMQX
MQTT Broker:   emqx.uip-smartcity.vn:1883
Client ID:     {tenantId}-{systemType}-{deviceId}
Username:      {tenantId}-device
Password:      [JWT token hoặc API key từ Admin Portal]

# Topic convention
Publish:   {tenantId}/sensors/{sensorType}/{deviceId}/telemetry
Subscribe: {tenantId}/commands/{deviceId}/#

# Ví dụ: Camera IP tại Vinhomes Grand Park
Client ID:  vinhomes-grand-park-camera-cam001
Topic:      vinhomes-grand-park/sensors/camera/cam001/telemetry
Payload:    {"event": "MOTION_DETECTED", "zone": "gate-A", "timestamp": "..."}
```

### Phương thức 2: Kafka Bridge — dành cho hệ thống lớn

```yaml
# Hệ thống BMS của Schneider Electric / Siemens kết nối qua Kafka Bridge
Kafka Bootstrap: kafka.uip-smartcity.vn:9092
Topic Pattern:   UIP.{tenantId}.bms.events.v1

# Cấu hình AI Workflow để xử lý event từ Kafka
POST /api/v1/admin/workflow-configs
{
  "configName":   "schneider-bms-elevator-alert",
  "triggerType":  "KAFKA",
  "kafkaTopic":   "UIP.vinhomes-grand-park.bms.events.v1",
  "scenarioKey":  "aiM03_utilityIncidentCoordination",
  "enabled":      true
}
```

### Phương thức 3: REST Webhook — dành cho web services

```yaml
# Hệ thống quản lý tòa nhà (IBMS) gọi webhook khi có sự cố
POST /api/v1/workflow/trigger/rest/{configName}
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "tenantId": "vinhomes-grand-park",
  "event":    "ELEVATOR_FAULT",
  "deviceId": "LIFT-B2-03",
  "severity": "HIGH",
  "details":  "Cabin dừng giữa tầng, chuông SOS kích hoạt"
}
```

### Trigger Config Engine — trái tim của tích hợp

Tất cả integrations đều đi qua **Trigger Config** — một bảng cấu hình data-driven (không cần deploy lại code):

```
┌──────────────────────────────────────────────────────────────────┐
│                     Trigger Config Engine                         │
│                                                                    │
│  configName     triggerType  kafkaTopic / schedule   scenarioKey  │
│  ─────────────  ───────────  ─────────────────────   ──────────── │
│  aqi-alert      KAFKA        UIP.*.env.aqi.v1        aiC01_aqi    │
│  flood-sensor   KAFKA        UIP.*.flood.events.v1   aiM01_flood  │
│  esg-daily      SCHEDULED    cron: 0 8 * * *         aiM04_esg    │
│  citizen-web    REST         –                       aiC02_citizen│
│  elevator-bms   KAFKA        UIP.*.bms.events.v1     aiM03_util   │
│  cctv-motion    MQTT-bridge  UIP.*.camera.events.v1  [custom]     │
└──────────────────────────────────────────────────────────────────┘
```

**Thêm integration mới = chỉ cần thêm 1 row vào bảng config.** Không deploy, không restart.

---

## Câu hỏi 3: Thiết bị mới join hệ thống như thế nào?

### Loại 1: IoT Sensor (môi trường, AQI, flood)

```
Bước 1: Đăng ký sensor trong Admin Portal
POST /api/v1/admin/sensors
{
  "sensorId":     "ENV-Q9-001",
  "sensorName":   "Cảm biến AQI Cổng A Vinhomes",
  "sensorType":   "AQI",
  "districtCode": "D09",
  "locationPath": "hcmc.q9.vinhomes-grand-park.gate-a",
  "latitude":     10.8412,
  "longitude":    106.8157,
  "tenantId":     "vinhomes-grand-park"
}

Bước 2: Lập trình firmware thiết bị
  → MQTT broker: emqx.uip-smartcity.vn:1883
  → Client ID:   vinhomes-grand-park-ENV-Q9-001
  → Topic:       vinhomes-grand-park/sensors/AQI/ENV-Q9-001/telemetry
  → Payload format:
    {
      "sensorId":    "ENV-Q9-001",
      "pm25":        45.2,
      "pm10":        82.1,
      "aqi":         128,
      "temperature": 31.5,
      "humidity":    75,
      "timestamp":   "2026-06-01T14:00:00Z"
    }

Bước 3: Verify
  → Platform tự động nhận data qua EMQX → Redpanda Connect → TimescaleDB
  → Dashboard hiện sensor ONLINE sau 30 giây
```

### Loại 2: BMS Device (thang máy, điện, HVAC, PCCC)

UIP hỗ trợ **BACnet/IP auto-discovery** — không cần cấu hình thủ công từng thiết bị:

```
Bước 1: Cấu hình BACnet Discovery
  PUT /api/v1/admin/tenants/vinhomes-grand-park/config
  {
    "bms.discovery.enabled":       "true",
    "bms.discovery.broadcast":     "192.168.100.255",
    "bms.discovery.interval-ms":   "300000"
  }

Bước 2: Platform tự scan BACnet Who-Is trên LAN mỗi 5 phút
  BmsDiscoveryService → broadcast Who-Is → thiết bị I-Am response
  → Tự động register vào bảng bms_devices với:
     deviceName, protocol=BACNET, host, port=47808, deviceId, tenantId

Bước 3: Với thiết bị Modbus (thang máy cũ)
POST /api/v1/bms/devices
{
  "deviceName": "Thang máy B2 số 3",
  "protocol":   "MODBUS_TCP",
  "host":       "192.168.1.45",
  "port":       502,
  "unitId":     3,
  "pollInterval": 5000,
  "tenantId":   "vinhomes-grand-park"
}
```

### Loại 3: Camera CCTV

```
Bước 1: Cấu hình Camera gửi event về EMQX
  (ONVIF event, webhook, hoặc NVR SDK bridge)
  Topic: {tenantId}/sensors/camera/{cameraId}/events
  Payload:
  {
    "cameraId":  "CAM-LOBBY-01",
    "eventType": "INTRUSION_DETECTED",  // hoặc FIRE_DETECTED, MOTION
    "zone":      "lobby",
    "confidence": 0.92,
    "snaphotUrl": "https://storage.uip/cam001/2026-06-01T14:00:00.jpg",
    "timestamp":  "2026-06-01T14:00:00Z"
  }

Bước 2: Mapping event camera → Kafka topic
  EMQX Rule Engine → forward to Kafka topic:
  UIP.{tenantId}.camera.events.v1

Bước 3: Tạo Trigger Config
  {
    "triggerType": "KAFKA",
    "kafkaTopic":  "UIP.vinhomes-grand-park.camera.events.v1",
    "scenarioKey": "aiSecurity_intrusionWithCctv"
  }
```

---

## Câu hỏi 4: Các hệ thống phối hợp nhau như thế nào?

### Kịch bản A: Cháy + CCTV — Phối hợp sơ tán thông minh

```
                    EVENT SOURCES
         ┌──────────────┬───────────────┐
         │              │               │
   [PCCC Sensor]  [CCTV Camera]  [BMS Smoke]
   FIRE_DETECTED  FIRE_VISUAL    SMOKE_ALARM
         │              │               │
         └──────────────┴───────────────┘
                         │
                  Kafka: UIP.*.fire.events.v1
                         │
              ┌──────────▼──────────┐
              │  AI Workflow Engine  │
              │  scenarioKey:        │
              │  aiSecurity_fire     │
              └──────────┬──────────┘
                         │
              ┌──────────▼──────────┐
              │   Claude AI phân tích│
              │   Đầu vào (context): │
              │   - Vị trí PCCC      │
              │   - CCTV frame URL   │
              │   - Mức khói BMS     │
              │   - Số người (BMS)   │
              └──────────┬──────────┘
                         │
              Confidence: 0.94 → AUTO_EXECUTE
                         │
         ┌───────────────┼──────────────────────┐
         │               │                      │
    [Hành động 1]  [Hành động 2]         [Hành động 3]
   Mở cửa thoát   CCTV chuyển sang      Gửi SMS/App
   hiểm tự động   chế độ tracking       cư dân + PCCC
   (BMS command)  tầng đang cháy        (vị trí chính xác)
```

**API kích hoạt phối hợp CCTV:**
```json
// BMS command gửi đến camera sau khi AI quyết định
POST /api/v1/bms/devices/{cctv-device-id}/command
{
  "command": "SWITCH_TO_TRACKING_MODE",
  "params": {
    "zone":       "floor-12",
    "targetType": "FIRE_SOURCE",
    "duration":   3600
  }
}
```

---

### Kịch bản B: Cảm biến trèo rào + CCTV an ninh

```
                    MULTI-SOURCE DETECTION
    ┌───────────────────┬──────────────────────┐
    │                   │                      │
[Sensor rào rung]  [Motion Sensor]      [Access Control]
PERIMETER_BREACH   MOTION_ZONE_3        UNAUTHORIZED_BADGE
    │                   │                      │
    └───────────────────┴──────────────────────┘
                         │
              Kafka: UIP.*.security.events.v1
                         │
              ┌──────────▼──────────┐
              │  AI Aggregation      │
              │  Trong 30 giây:      │
              │  - 3 signals từ      │
              │    cùng 1 zone       │
              │  → CONFIRMED breach  │
              └──────────┬──────────┘
                         │
              Claude AI quyết định:
              - Không cần leo rào giả (maintenance)
              - Có tên trong blacklist?
              - Giờ đêm → severity HIGH
                         │
              Confidence: 0.91 → AUTO_EXECUTE
                         │
    ┌──────────────────────┬─────────────────────────┐
    │                      │                         │
[CCTV zoom & record]  [Lock zone doors]        [Alert guard]
 Camera tracking       BMS access control      Push notification
 kẻ xâm phạm          khóa cửa khu vực         → Security app
```

**Cách CCTV nhận lệnh từ AI Workflow:**
```yaml
# Sau khi AI quyết định AUTO_EXECUTE, BmsDeviceCommandService gửi lệnh:
Device:  camera-security-zone3
Command: PTZ_TRACK_TARGET
Params:
  target_zone:  "perimeter-north"
  record_mode:  "HIGH_RES_CONTINUOUS"
  snapshot_interval: 5  # seconds
  upload_to:    "uip-storage/security-events/{incidentId}/"
```

---

### Kịch bản C: Sự cố thang máy — Phối hợp đa hệ thống

Đây là ví dụ phức tạp nhất, phối hợp **5 hệ thống đồng thời**:

```
┌─────────────────────────────────────────────────────────────────┐
│                    ELEVATOR INCIDENT RESPONSE                    │
│                                                                   │
│  EVENT TRIGGER: Thang máy B2-03 dừng giữa tầng                  │
│  Source: BMS Modbus → Kafka UIP.*.bms.events.v1                 │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              AI CONTEXT AGGREGATION (10s)                │    │
│  │                                                           │    │
│  │  BMS Data:         Cabin ở tầng 7.5, doors jammed       │    │
│  │  CCTV Data:        Camera tầng 7 → 3 người trong cabin  │    │
│  │  Building Data:    Tòa B2, 25 tầng, 3 thang máy         │    │
│  │  Access Control:   Ai đang trong tòa nhà                 │    │
│  │  External API:     Số ĐT Otis Service Vietnam            │    │
│  └─────────────────────────────────────────────────────────┘    │
│                         │                                         │
│              Confidence: 0.89 → AUTO_EXECUTE                     │
│                         │                                         │
│  ┌──────────┬───────────┼──────────────┬────────────────────┐   │
│  │          │           │              │                    │   │
│ [BMS]    [CCTV]    [Notification]  [Access]          [External] │
│  Mở khóa  Bật đèn   SMS người      Mở cửa tầng 7    Gọi API    │
│  cabin    tầng 7+8  trong cabin    từ bên ngoài      Otis Tech  │
│  thủ công                          (dùng badge)      Service    │
│  override                                             hotline    │
└─────────────────────────────────────────────────────────────────┘
```

**BPMN Workflow cho Elevator Incident:**
```
[Elevator Fault Event]
        │
[AI Analysis Task]      ← scenarioKey: aiM03_utilityIncidentCoordination
   aiDecision: MULTI_SYSTEM_COORDINATE
   aiSeverity: HIGH
   aiConfidence: 0.89
        │
[Parallel Gateway]
   ├─ [BMS Command Task]         → cabin override open
   ├─ [CCTV Command Task]        → floor 7+8 lighting + recording
   ├─ [Notification Task]        → SMS trapped persons + security
   └─ [External Service Task]    → call elevator company API
        │
[Join Gateway]
        │
[Incident Resolved Check]
        │
[Close Incident + ESG Log]
```

**Lý do AI làm tốt hơn logic cứng:**
- Cabin ở **tầng 7.5** → AI biết cần mở cửa tầng 7 (không phải 8)
- **3 người** trong cabin → ưu tiên EMERGENCY thay vì ROUTINE
- **Giờ hành chính** → có thể gọi hotline; đêm → chỉ SMS on-call
- **Lịch sử thang máy** → thang này bị lỗi 3 lần tuần trước → severity CRITICAL

---

## Câu hỏi 5: AI thống nhất các sự kiện rời rạc thành quy trình hoàn chỉnh như thế nào?

### Vấn đề của hệ thống truyền thống

```
Truyền thống (rule-based):
  IF smoke_sensor == 1 THEN send_alert()         ← 1 rule
  IF camera_motion == 1 THEN record_video()      ← 1 rule
  IF bms_elevator_fault == 1 THEN notify()       ← 1 rule

  → 3 sự kiện rời rạc, 3 phản ứng độc lập
  → Không biết chúng liên quan nhau
  → Không có context → quyết định sai hoặc thiếu
```

### UIP AI Unification Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    AI Unification Layer                           │
│                                                                   │
│  Lớp 1: EVENT COLLECTION (30s time window)                       │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  smoke_sensor: TRIGGERED    (hcmc.q9.vinhomes.toa-b2)  │    │
│  │  camera_b2:    MOTION       (tầng 12, zone lobby)       │    │
│  │  bms_hvac:     FAULT        (Tòa B2 HVAC dừng)         │    │
│  │  access_b2:    FORCED       (cửa thoát hiểm tầng 12)   │    │
│  └─────────────────────────────────────────────────────────┘    │
│                         │                                         │
│  Lớp 2: AI CONTEXT SYNTHESIS                                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Claude nhận prompt:                                     │    │
│  │  "4 events trong 30s tại Tòa B2:                        │    │
│  │   - Smoke detected floor 12                             │    │
│  │   - Camera motion floor 12 lobby                        │    │
│  │   - HVAC fault (có thể làm khói lan)                    │    │
│  │   - Emergency door forced open                          │    │
│  │   Building: 25 tầng, 200 cư dân giờ này...             │    │
│  │   Phân tích và đề xuất hành động"                       │    │
│  └─────────────────────────────────────────────────────────┘    │
│                         │                                         │
│  Lớp 3: UNIFIED DECISION                                         │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  {                                                        │    │
│  │    "decision":   "FIRE_EMERGENCY_FULL_EVACUATION",       │    │
│  │    "confidence": 0.96,                                   │    │
│  │    "severity":   "CRITICAL",                             │    │
│  │    "reasoning":  "4 correlated signals trong 30s        │    │
│  │                   tại cùng zone. HVAC fault có thể       │    │
│  │                   khiến khói lan nhanh. Cần sơ tán       │    │
│  │                   toàn tòa.",                            │    │
│  │    "actions": [                                          │    │
│  │      "Kích hoạt báo cháy toàn tòa",                     │    │
│  │      "Mở tất cả cửa thoát hiểm",                        │    │
│  │      "Gọi 114 tự động",                                  │    │
│  │      "CCTV tracking zone tầng 12",                      │    │
│  │      "Gửi SMS sơ tán 200 cư dân"                        │    │
│  │    ]                                                     │    │
│  │  }                                                        │    │
│  └─────────────────────────────────────────────────────────┘    │
│                         │                                         │
│  Lớp 4: CONFIDENCE-BASED ROUTING                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  confidence > 0.85 → AUTO_EXECUTE tất cả actions        │    │
│  │  confidence 0.6–0.85 → OPERATOR phê duyệt trước        │    │
│  │  confidence < 0.6   → ESCALATE lên chỉ huy trực tiếp   │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### Cách mở rộng — thêm scenario AI mới

Toàn bộ quá trình **không cần code**, chỉ cần:

```
Bước 1: Viết prompt template
  backend/src/main/resources/prompts/aiS01_elevatorFire.txt
  
  Nội dung: mô tả scenario, output JSON format, edge cases

Bước 2: Thêm BPMN process
  Designer → Kéo thả Start → AI Decision Node → 
  Service Tasks (BMS/CCTV/Notify) → End
  → Save → Deploy (1 click)

Bước 3: Thêm Trigger Config
  POST /api/v1/admin/workflow-configs
  {
    "triggerType": "KAFKA",
    "kafkaTopic":  "UIP.*.fire.events.v1",
    "scenarioKey": "aiS01_elevatorFire"
  }

Bước 4: Kết quả
  → Platform tự pick up config mới (không restart)
  → Event đến → AI phân tích → Action tự động
```

### Decision Cache — hiệu suất ở scale lớn

```
Vấn đề: 1000 sensor events/giây × Claude API = chi phí cao + latency

Giải pháp: Redis TTL Cache trong DecisionRouter
  Cache key: {tenantId}:{scenarioKey}:{SHA256(context)}
  TTL: 15 phút

  Ví dụ: AQI Quận 9 = 145 (UNHEALTHY) → quyết định CẢNH BÁO
  → Cache kết quả AI 15 phút
  → 1000 sensor readings cùng zone trong 15 phút → gọi AI 1 lần
  → 999 requests được serve từ cache: <1ms latency
```

---

## Roadmap mở rộng

| Giai đoạn | Tính năng | Timeline |
|---|---|---|
| **MVP3 (hiện tại)** | 7 AI scenarios, MQTT/Kafka/REST triggers, BACnet discovery | ✅ Done |
| **Phase 2** | CCTV AI Vision (nhận diện đám đông, phát hiện cháy từ hình ảnh) | Q3 2026 |
| **Phase 2** | Multi-tenant AI workflows (mỗi khu đô thị có prompt riêng) | Q3 2026 |
| **Phase 3** | Federated Learning — AI học từ dữ liệu nhiều khu đô thị | Q4 2026 |
| **Phase 3** | Digital Twin integration — mô phỏng trước khi thực thi | Q1 2027 |

---

## Tóm tắt cho nhà đầu tư

| Câu hỏi | Câu trả lời kỹ thuật |
|---|---|
| Khu đô thị mới join? | **5 bước, < 1 ngày** — Tenant registration, Keycloak provisioning, data isolation tự động |
| Hệ thống bên ngoài join? | **3 cách** — MQTT (IoT), Kafka Bridge (Enterprise), REST Webhook — Trigger Config Engine không cần deploy |
| Thiết bị join? | **BACnet auto-discovery** (scan tự động) hoặc **manual API** — < 30 giây online |
| Cross-system phối hợp? | **BPMN Workflow + AI** — fire+CCTV, intrusion+security, elevator+5 hệ thống chạy song song |
| AI unification? | **4-layer architecture** — Event collection → Context synthesis → Unified decision → Confidence routing |

**Key differentiator**: Các hệ thống smart city hiện tại dùng rule-based IF/THEN — chỉ phản ứng với 1 sự kiện. UIP dùng AI để **hiểu correlation** giữa nhiều sự kiện, **suy luận context**, và đưa ra **quyết định thống nhất** với độ tin cậy đo lường được.

---

_Tài liệu này được tạo từ source code thực tế của UIP ESG POC (Sprint 6).  
Các API endpoint, topic names, và class names đều verified từ codebase._
