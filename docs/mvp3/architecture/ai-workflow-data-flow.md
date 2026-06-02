# AI Workflow — Kiến Trúc & Data Flow

**Phiên bản:** 1.0  
**Ngày:** 2026-06-02  
**Đối tượng:** Investors, Product Owner, Technical Stakeholders

---

## 1. Tổng Quan

UIP Smart City Platform tích hợp **AI-driven Workflow Engine** — hệ thống tự động tiếp nhận dữ liệu thời gian thực từ hàng nghìn cảm biến IoT, phân tích bằng AI, và ra quyết định hành động mà không cần can thiệp thủ công.

**Giá trị cốt lõi:**
- **Tự động hóa 85%+** các sự kiện đô thị thường gặp (cảnh báo AQI, khiếu nại, lũ lụt)
- **Phản hồi trong vòng 2–5 giây** từ khi phát hiện sự kiện đến khi thông báo
- **AI confidence-based routing** — chỉ escalate lên con người khi AI không đủ chắc chắn
- **Audit trail đầy đủ** — mọi quyết định AI đều được ghi log với reasoning

---

## 2. Kiến Trúc Tổng Thể

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        UIP AI WORKFLOW ENGINE                               │
│                                                                             │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────────────────┐     │
│  │  IoT Layer   │────▶│  Stream      │────▶│    Trigger Engine        │     │
│  │              │     │  Processing  │     │  (GenericKafkaTrigger)   │     │
│  │ 500+ sensors │     │  (Flink)     │     │                          │     │
│  │ AQI / Flood  │     │              │     │  • Filter Evaluation     │     │
│  │ Traffic / etc│     │  Anomaly     │     │  • Variable Mapping      │     │
│  └──────────────┘     │  Detection   │     │  • Deduplication         │     │
│                       └──────────────┘     └────────────┬─────────────┘     │
│                                                         │                   │
│                              Kafka                      │ startProcess()    │
│                    UIP.flink.alert.detected.v1          │                   │
│                       ◀────────────────────             │                   │
│                                                         ▼                   │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                   CAMUNDA BPM ENGINE                                │    │
│  │                                                                     │    │
│  │  ┌──────────┐   ┌───────────────┐   ┌──────────┐   ┌───────────┐    │    │
│  │  │  Start   │──▶│  AI Analysis  │──▶│ Gateway  │──▶│  Notify / │    │    │
│  │  │  Event   │   │  (Claude AI)  │   │ Routing  │   │  Assign / │    │    │
│  │  │          │   │               │   │          │   │  Escalate │    │    │
│  │  └──────────┘   └───────────────┘   └──────────┘   └───────────┘    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  ┌───────────────────┐     ┌────────────────────┐     ┌───────────────┐     │
│  │  Trigger Config   │     │  Process Defs      │     │  Decision     │     │
│  │  (Admin CRUD)     │     │  (BPMN deployed)   │     │  Router       │     │
│  │                   │     │                    │     │  (Redis cache)│     │
│  └───────────────────┘     └────────────────────┘     └───────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Chi Tiết Từng Lớp

### Layer 1 — IoT & Stream Processing

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| IoT Sensors | MQTT / EMQX | Thu thập dữ liệu vật lý (AQI, nhiệt độ, mực nước...) |
| Flink Jobs | Apache Flink | Phát hiện bất thường real-time (sliding window, threshold checks) |
| Kafka Topic | Apache Kafka | Message bus trung tâm — `UIP.flink.alert.detected.v1` |

**Ví dụ sự kiện Kafka:**
```json
{
  "module": "ENVIRONMENT",
  "sensorId": "SEN-AQI-D1-001",
  "district": "Quận 1",
  "aqiValue": 187,
  "pm25Value": 95.3,
  "timestamp": "2026-06-02T10:23:45Z",
  "alertType": "AQI_EXCEED_THRESHOLD"
}
```

---

### Layer 2 — Trigger Engine (Cổng Vào Workflow)

**Thành phần:** `GenericKafkaTriggerService`

Mỗi loại sự kiện được cấu hình bởi một **Trigger Config** — bản ghi trong DB với các trường:

| Trường | Ý nghĩa | Ví dụ |
|---|---|---|
| `scenarioKey` | ID duy nhất của kịch bản | `aiC01_aqiCitizenAlert` |
| `kafkaTopic` | Topic Kafka lắng nghe | `UIP.flink.alert.detected.v1` |
| `filterConditions` | Điều kiện lọc sự kiện (JSON) | `module=ENVIRONMENT AND aqiValue > 150` |
| `variableMapping` | Ánh xạ field payload → Camunda variable | `payload.sensorId → sensorId` |
| `processKey` | **Khóa liên kết với Process Definition** | `aiC01_aqiCitizenAlert` |
| `aiConfidenceThreshold` | Ngưỡng tự tin AI tùy chỉnh theo kịch bản | `0.75` |
| `deduplicationKey` | Tránh khởi chạy trùng lặp | `sensorId` |

**Luồng xử lý tại Trigger Engine:**

```
Kafka message đến
       ↓
Load TriggerConfigs (cached Redis, 5 phút)
       ↓
FilterEvaluator: có khớp điều kiện không?
   ├── Không khớp → bỏ qua
   └── Khớp ↓
       DeduplicationCheck: đã có process đang chạy với key này chưa?
           ├── Đã có → bỏ qua (tránh spam)
           └── Chưa có ↓
               VariableMapper: chuyển đổi payload → Camunda variables
                       ↓
               workflowService.startProcess(processKey, variables)
```

> **Điểm mấu chốt:** `TriggerConfig.processKey` = `CamundaProcessDefinition.key`  
> Đây là **cầu nối duy nhất** giữa cấu hình trigger và workflow BPMN thực thi.

---

### Layer 3 — Process Definitions (BPMN Workflow)

**Thành phần:** Camunda BPM Engine (embedded trong Spring Boot)

Process Definition là file BPMN được thiết kế bằng **Camunda Modeler** (desktop tool) và deploy vào engine. Hiện có **7 process definitions** đang hoạt động:

| Process Key | Kịch bản |
|---|---|
| `aiC01_aqiCitizenAlert` | Cảnh báo AQI cho cư dân |
| `aiC02_floodAlert` | Cảnh báo lũ lụt |
| `aiC03_trafficIncident` | Sự cố giao thông |
| `aiC04_citizenComplaint` | Khiếu nại của cư dân |
| `aiC05_energyAnomaly` | Bất thường tiêu thụ điện |
| `aiC06_wasteCollection` | Thu gom rác thải |
| `aiC07_publicSafety` | An toàn công cộng |

**Cấu trúc BPMN điển hình:**

```
[Start Event]
     │ (nhận variables từ Trigger: sensorId, aqiValue, district...)
     ▼
[Service Task: AI Analysis]
     │ delegateExpression="${aiAnalysisDelegate}"
     │ → gọi Claude API với context đầy đủ
     │ → trả về: aiDecision, aiConfidence, aiReasoning, aiSeverity
     ▼
[Exclusive Gateway: Confidence Check]
     ├── confidence > 0.85 → [Auto Execute] → tự động gửi thông báo
     ├── 0.60 – 0.85      → [Operator Queue] → đẩy lên dashboard operator
     └── < 0.60           → [Escalate] → báo cáo supervisor
     ▼
[End Event: Process Completed]
```

---

### Layer 4 — Advanced AI (Trí Tuệ Phân Tích)

**Thành phần:** `AIAnalysisDelegate` + `ClaudeApiService` + `DecisionRouter`

#### 4.1 AIAnalysisDelegate — Cầu Nối Camunda ↔ AI

Khi Camunda thực thi Service Task "AI Analysis":

1. Đọc toàn bộ Camunda variables (`sensorId`, `aqiValue`, `district`...)
2. Gọi `ClaudeApiService.analyzeAsync(scenarioKey, context)` — timeout 10 giây
3. Ghi kết quả AI trở lại thành Camunda variables:

| Variable | Kiểu | Mô tả |
|---|---|---|
| `aiDecision` | String | Hành động đề xuất (VD: `ASSIGN_TO_ENVIRONMENT`) |
| `aiConfidence` | Double | Độ tự tin 0.0 – 1.0 |
| `aiReasoning` | String | Giải thích quyết định bằng ngôn ngữ tự nhiên |
| `aiSeverity` | String | Mức độ (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) |
| `aiRecommendedActions` | List | Các bước hành động cụ thể |

#### 4.2 ClaudeApiService — Giao Tiếp AI

```
scenarioKey = "aiC01_aqiCitizenAlert"
       ↓
Load: prompts/aiC01_aqiCitizenAlert.txt
       ↓
Interpolate: {district}, {aqiValue}, {pm25Value}, {sensorId}...
       ↓
POST → Anthropic Claude API
       ↓
Parse JSON response → AIDecision DTO
```

Mỗi kịch bản có **prompt template riêng** được tối ưu cho domain đó (môi trường, giao thông, an ninh...).

#### 4.3 DecisionRouter — Phân Luồng Theo Confidence

```
┌─────────────────────────────────────────────────────┐
│              DECISION ROUTING MATRIX                 │
├──────────────────┬──────────────────────────────────┤
│ Confidence       │ Routing Action                   │
├──────────────────┼──────────────────────────────────┤
│ > 0.85           │ AUTO_EXECUTE                     │
│                  │ Hệ thống tự hành động            │
│                  │ (gửi SMS, tạo ticket, điều phối) │
├──────────────────┼──────────────────────────────────┤
│ 0.60 – 0.85      │ OPERATOR_QUEUE                   │
│                  │ Đẩy lên dashboard operator       │
│                  │ Operator xem reasoning → approve │
├──────────────────┼──────────────────────────────────┤
│ < 0.60           │ ESCALATE                         │
│                  │ Báo ngay lên supervisor           │
│                  │ Kèm full context + AI reasoning  │
└──────────────────┴──────────────────────────────────┘
```

**Redis caching:** Nếu cùng context đã được phân tích gần đây (TTL 15 phút), hệ thống dùng kết quả cache — tiết kiệm chi phí API và tăng tốc phản hồi.

---

## 4. Luồng End-to-End — Ví Dụ Thực Tế

**Kịch bản:** Cảm biến AQI tại Quận 1 phát hiện ô nhiễm vượt ngưỡng (AQI = 187)

```
T+0.0s   Cảm biến SEN-AQI-D1-001 đo AQI = 187
         ↓
T+0.5s   Flink sliding window phát hiện vượt ngưỡng (threshold: 150)
         Publish event → Kafka: UIP.flink.alert.detected.v1
         ↓
T+1.0s   GenericKafkaTriggerService nhận message
         Filter check: module=ENVIRONMENT ✓, aqiValue > 150 ✓
         Dedup check: chưa có process active cho sensorId này ✓
         startProcess("aiC01_aqiCitizenAlert", {aqiValue:187, district:"Quận 1"...})
         ↓
T+1.2s   Camunda process instance khởi động
         → Reach Service Task: AI Analysis
         ↓
T+2.8s   AIAnalysisDelegate gọi Claude API
         Input: AQI 187, PM2.5 95.3, Quận 1, 50,000+ cư dân trong vùng ảnh hưởng
         Output: {
           decision: "ISSUE_HEALTH_WARNING",
           confidence: 0.91,
           severity: "HIGH",
           reasoning: "AQI 187 falls in Unhealthy range...",
           recommendedActions: ["Send SMS to registered residents", "Post on city portal"]
         }
         ↓
T+2.9s   DecisionRouter: confidence=0.91 > 0.85 → AUTO_EXECUTE
         Gateway condition: true → Service Task: Send Notification
         ↓
T+3.1s   Hệ thống tự động:
         - Gửi push notification đến app cư dân trong 2km
         - Tạo alert record trong dashboard City Operations Center
         - Cập nhật status sensor trên bản đồ (màu đỏ)
         ↓
T+3.5s   Process instance COMPLETED
         Toàn bộ pipeline: ~3.5 giây
```

---

## 5. Quan Hệ Giữa Các Thành Phần

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  WorkflowConfigPage (Admin UI)                              │
│  └── CRUD TriggerConfig                                     │
│       ├── scenarioKey: "aiC01_aqiCitizenAlert"              │
│       ├── kafkaTopic: "UIP.flink.alert.detected.v1"         │
│       ├── filterConditions: [{field, op, value}]            │
│       ├── variableMapping: {payload_field → camunda_var}    │
│       ├── processKey: "aiC01_aqiCitizenAlert" ──────────┐   │
│       └── aiConfidenceThreshold: 0.75                   │   │
│                                                         │   │
│  Process Definitions (AiWorkflowPage Tab)               │   │
│  └── Camunda Process: id = "aiC01_aqiCitizenAlert" ◀───┘    │
│       └── BPMN file designed in Camunda Modeler             │
│            deployed via /api/v1/workflows/{id}/deploy       │
│                                                             │
│  Relationship: processKey in TriggerConfig MUST             │
│                equal key in deployed Camunda Process        │
│                (1 TriggerConfig → 1 Process Definition)     │
└─────────────────────────────────────────────────────────────┘
```

---

## 6. Điểm Mạnh Kiến Trúc — Dành Cho Investors

### 6.1 Scalability
- **Horizontal scaling:** Thêm Kafka partitions + Flink task managers — throughput tuyến tính
- **Camunda process isolation:** Mỗi event chạy process instance riêng — không blocking
- **Redis cache layer:** Giảm 60–80% Claude API calls với pattern lặp lại

### 6.2 Extensibility (No-Code Configuration)
- Thêm kịch bản mới → chỉ cần tạo TriggerConfig qua admin UI + deploy BPMN file
- **Không cần code lại backend** để xử lý loại sự kiện mới
- Filter conditions và variable mappings cấu hình dạng JSON — non-technical admin có thể quản lý

### 6.3 AI Governance
- Mọi quyết định AI có `reasoning` đầy đủ — auditable
- Confidence threshold configurable per-scenario — risk management linh hoạt
- `OPERATOR_QUEUE` đảm bảo human-in-the-loop khi AI uncertain
- Fallback: `aiDecision="ERROR"` không crash process — graceful degradation

### 6.4 Reliability
- Kafka DLQ (`UIP.workflow.trigger.dlq.v1`) cho failed events
- Camunda process state persistent — recovery sau restart
- Deduplication ngăn duplicate workflows từ retry messages

---

## 7. Metrics Hiện Tại (MVP3)

| Metric | Giá trị |
|---|---|
| Process Definitions deployed | 7 |
| Trigger Configs active | 7 |
| Avg end-to-end latency | ~3–5 giây |
| AI confidence threshold (default) | 0.75 |
| Auto-execute rate (conf > 0.85) | ~65% sự kiện thông thường |
| Redis cache TTL | 15 phút |
| Kafka DLQ monitoring | ✅ Active |

---

## 8. Roadmap AI Enhancement

| Phase | Feature | Business Value |
|---|---|---|
| MVP3 ✅ | Claude-based AI Analysis + confidence routing | Core decision automation |
| MVP4 | Multi-model ensemble (Claude + Gemini) | Higher accuracy, redundancy |
| MVP4 | Feedback loop — operator corrections retrain prompts | Continuous improvement |
| MVP5 | Predictive triggering — AI predicts before threshold breach | Proactive vs reactive |
| MVP5 | Cross-scenario correlation | Detect compound events (flood + traffic + power outage) |

---

*Document prepared by UIP Engineering Team — 2026-06-02*  
*For technical deep-dive, refer to: [system-architecture.md](system-architecture.md)*
