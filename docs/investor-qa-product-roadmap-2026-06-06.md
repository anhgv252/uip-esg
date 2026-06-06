# UIP Smart City — Investor Q&A & Product Roadmap
## Tổng hợp từ Demo Session 2026-06-06

**Bối cảnh:** Nhà đầu tư và PO đặt 5 câu hỏi chiến lược trong buổi demo MVP3.  
**Mục đích tài liệu:** Ghi lại câu hỏi + câu trả lời hiện tại + hướng phát triển cho MVP4+.  
**Live metrics tại thời điểm demo:**
- AI workflow instances đã xử lý: **207** (trong 1 ngày demo)
- Sensor readings trong DB: **1,323** từ **10 sensors**
- AI decisions: NOTIFY_CITIZENS, ASSIGN_TO_ENVIRONMENT, ASSIGN_TO_UTILITY

---

## Câu hỏi 1: Scale — 10,000 thiết bị, AI có đáp ứng được?

### Vấn đề nhà đầu tư đặt ra
> *"Platform hiện demo với 8-10 sensors. Khi mở rộng lên 10,000 thiết bị thực tế tại HCMC, luồng AI processing có bottleneck không?"*

### Trả lời hiện tại (MVP3)
**Có bottleneck nếu không có batching.** Hiện tại: mỗi sensor event AQI > 150 → 1 Claude API call → nếu 10,000 sensors × 1 event/giây = 10,000 API calls/giây → không khả thi về chi phí và latency.

**Giải pháp đã có trong stack:**

```
10,000 sensors/sec
     ↓
Kafka 3-broker (đã RUNNING — tested 2,500 msg/s)
     ↓
Flink Sliding Window 60s per district
     ↓
8 districts HCMC = max 8 AI calls/phút (thay vì 600,000)
     ↓
AlertEngine Redis dedup (cooldownMinutes=10) → đã có
```

**Code đã có:**
- `AlertEngine.java`: Redis TTL dedup per sensor per rule — ngăn flood alerts
- `Flink Sliding Window`: có trong `FloodAlertJob.java` (5min/1min)
- Kafka 3-broker KRaft: tested 2,500 msg/s, scale horizontal không cần code change

### Hướng phát triển

#### MVP3 (hiện tại — có thể làm ngay)
- [ ] **District-level aggregation trong Flink**: Group events by districtCode + 60s window trước khi trigger AI. Code basis: `FloodAlertJob` sliding window → reuse pattern cho AQI, CO, Energy.
- [ ] **Kafka consumer group scaling**: `uip-workflow-generic` consumer group đã có → tăng partitions + consumer instances khi cần scale.

#### MVP4 (Sprint 11-12, Q3 2026)
- [ ] **Adaptive cooldown**: Thay vì fixed 10 minutes, tự động điều chỉnh cooldown dựa trên sensor reliability history.
- [ ] **Priority queue**: Critical events (flood, fire) bypass window aggregation → immediate AI call. Normal events → batched.
- [ ] **Horizontal Flink scaling**: TaskManager auto-scaling trên K8s dựa trên Kafka consumer lag.

**Target:** 10,000 sensors → **~50 AI calls/phút** (district-level batching) → feasible với $0.15/ngày.

---

## Câu hỏi 2: Chi phí AI Token — Có cách nào tối ưu?

### Vấn đề nhà đầu tư đặt ra
> *"207 AI workflow instances trong 1 ngày demo. Với 10,000 sensors production, chi phí AI token sẽ rất lớn. Làm sao kiểm soát?"*

### Trả lời hiện tại (MVP3)
**Hiện tại:** `ClaudeApiService.java` dùng `claude-sonnet-4-6`, 1,024 max tokens/call.  
**Ước tính:** 207 calls × ~500 tokens × $0.003/1K = ~$0.30/ngày (demo).  
**Production risk:** Nếu không optimize → $10-50/ngày với 10,000 sensors.

**Đã có safeguard:**
- `CircuitBreaker` (Resilience4j): tự fallback khi Claude API lỗi
- `RuleBasedFallbackDecisionService`: 0 tokens, cho phép fallback hoàn toàn
- `aiConfidenceThreshold: 0.85`: chỉ trust AI decision khi confidence đủ cao

### Kiến trúc 3-tier đề xuất (không phải AI research — là config thay đổi)

```
TIER 0 — Rule-based (0 tokens)
  │   RuleBasedFallbackDecisionService đã có ✅
  │   95% normal events: AQI < 150, CO < 200, temp bình thường
  ↓
TIER 1 — claude-3-haiku ($0.00025/call)
  │   Moderate anomalies: AQI 150-200, minor threshold breach
  │   Simple classification, short prompt (~50 tokens)
  ↓
TIER 2 — claude-sonnet ($0.003/call)
  │   Complex decisions: AQI > 200, correlated signals
  │   Full reasoning, BPMN workflow trigger
  ↓
TIER 3 — Correlated Incident ($0.003 × 1, không × N)
      Flink batches N signals → 1 AI call với full context
      Tiết kiệm 80% so với per-device approach
```

### Hướng phát triển

#### MVP3 (có thể làm ngay — chỉ config change)
- [ ] **Model routing**: Thêm field `aiModelTier` vào `TriggerConfig` → haiku cho tier 1, sonnet cho tier 2. ClaudeApiService đọc tier khi call.
- [ ] **Token budgeting**: Thêm `maxTokens` per scenario vào `TriggerConfig` (đã có field, chưa dùng). aiC01_aqi: 256 tokens, aiC03_flood: 1024 tokens.
- [ ] **Prompt compression**: Tối ưu 7 prompt templates — loại bỏ redundant text, từ ~140 words → ~60 words mỗi prompt.

#### MVP4 (Sprint 11, Q3 2026)
- [ ] **Smart pre-filter**: Trước khi gọi AI, rule-based filter xử lý 80% cases → chỉ escalate "uncertain" cases lên AI.
- [ ] **Response caching**: Redis cache AI responses cho patterns giống nhau trong 5 phút (e.g., same district, same AQI range).
- [ ] **Cost dashboard**: Grafana panel tracking AI token usage + cost per tenant per day → chargeback model cho Tier 2/3 customers.

**Target:** $0.60/ngày cho 10,000 sensors (vs $50/ngày không optimize) → **83× cost reduction**.

---

## Câu hỏi 3: Multi-device Correlation — Đây mới là giá trị thực

### Vấn đề nhà đầu tư đặt ra
> *"Hiện tại mỗi thiết bị gửi event riêng lẻ. Nhưng trong thực tế, một sự cố thực địa cần nhiều thiết bị phối hợp: sensor CO báo + BMS điều khiển thiết bị + sensor nhiệt độ xác nhận. Làm sao kết hợp?"*

### Trả lời hiện tại (MVP3)
**Demo đã chứng minh concept:** 5 signals (CO + Temp + Smoke + BMS + Vibration) trong 28 giây → 1 AI call → unified decision với đầy đủ context.

**Vs. approach đơn lẻ:**
```
❌ CO sensor riêng lẻ  → AI: "hơi cao, theo dõi"
❌ Temp riêng lẻ       → AI: "nhiệt độ tăng, không rõ nguyên nhân"
❌ Smoke riêng lẻ      → AI: "có thể là nấu ăn, low priority"

✅ CO + Temp + Smoke + BMS + Vibration trong 28s
   → AI: "GAS_LEAK + FIRE_RISK confirmed, EVACUATE floors 12-15"
```

**Code basis đã có:**
- `WelfordKeyedProcessFunction`: per-sensor anomaly detection (structural)
- `VibrationAnomalyJob`: Flink CEP pattern (≥3 anomalies / 10 seconds)
- `FloodAlertJob`: multi-sensor confirmation (≥3 sensors)

### Hướng phát triển

#### MVP3 (Sprint 10-11)
- [ ] **IncidentCorrelationFlinkJob**: Flink CEP window `30s per building` — detect khi 3+ sensor types của cùng building trigger trong cùng window. Template: `VibrationAnomalyJob` CEP pattern → reuse.
- [ ] **Correlated payload builder**: `CorrelationPayloadBuilder.java` → merge N sensor events → 1 unified payload cho AI với full context.
- [ ] **BMS auto-command**: Khi AI quyết định EVACUATE → tự động gửi BMS command (HVAC_OFF, SPRINKLER_ON) qua existing `BmsCommandService`.

#### MVP4 (Sprint 12-13, Q4 2026)
- [ ] **Cross-building correlation**: Phát hiện pattern lan rộng → nhiều buildings cùng khu vực. Kafka topic `UIP.incident.correlation.v1`.
- [ ] **Temporal correlation**: Phân tích time-series — "3 buildings cùng AQI spike trong 10 phút" → có thể cùng nguồn ô nhiễm.
- [ ] **BMS bidirectional loop**: AI decision → BMS command → BMS feedback (thiết bị đã chấp hành chưa) → AI confirmation.

**Giá trị kinh doanh:** Từ "smart monitoring" (cảnh báo) → "smart intervention" (tự xử lý). Đây là sự khác biệt Tier 2 ($2,000/tháng) vs Tier 3 ($8,000/tháng).

---

## Câu hỏi 4: Tự học — Không ai biết đủ case để viết prompt

### Vấn đề nhà đầu tư đặt ra
> *"Không phải ai cũng biết hết các hệ thống để viết prompt phù hợp. Không phải lúc nào cũng đủ cases để tổng hợp. Người vận hành không nắm hết chỉ số. Có thể học từ sự kiện thật không?"*

### Trả lời hiện tại (MVP3)
**Đây là gap lớn nhất.** Hiện tại 7 prompt templates được viết thủ công bởi developer. Operator không thể tự tạo hay cải thiện.

**Tuy nhiên, training data đã đang accumulate:**
- `AlertEvent.acknowledgedBy` + `AlertEvent.note` → operator feedback có sẵn
- `WelfordStdDev.isAnomaly()` → per-device statistical baseline, tự cập nhật
- 207 AI decisions trong demo → có thể review accuracy

### Hướng phát triển

#### MVP3 (có thể start ngay — không cần thay đổi schema)
- [ ] **Welford Universal**: Extend `WelfordKeyedProcessFunction` từ structural sensors → tất cả sensor types (AQI, CO, Temperature, Water Level, Energy). Cold-start 1,000 samples → sau đó không cần hardcoded threshold.
- [ ] **Operator feedback capture**: Thêm UI button trên Alert page: "AI Decision này đúng không?" → lưu vào `alert_feedback` table. Training data cho tương lai.

#### MVP4 (Sprint 12, Q3 2026)
- [ ] **Incident Feedback Loop**:
  ```
  AlertEvent.acknowledged_by + note (đã có)
      ↓ 30 ngày × 1,000 incidents
  Monthly scheduled job: AI analyzes incident patterns
      ↓
  AI generates improved prompt suggestions
      ↓ Human review (operator/admin confirms)
  Deploy improved prompts → better decisions
  ```
- [ ] **Baseline drift detection**: Nếu AQI baseline của một khu dần tăng qua 3 tháng → tự điều chỉnh ngưỡng anomaly detection.

#### MVP5 (Q1 2027 — AI maturity phase)
- [ ] **Self-improving prompt engine**: AI analyzes cases where confidence < 0.7 → auto-generates alternative prompt → A/B test → deploy winner.
- [ ] **Few-shot learning từ incidents**: Thay vì hardcoded prompt, AI học từ "đây là incident thật + outcome đúng" → few-shot examples trong prompt.

**Timeline thực tế:** Welford Universal trong Sprint 11 (2 tuần). Feedback loop trong Sprint 12. Sau 3 tháng production data → có thể đánh giá quality improvement.

---

## Câu hỏi 5: Camunda BPMN quá kỹ thuật — Operator không tự build được

### Vấn đề nhà đầu tư đặt ra
> *"Nghiệp vụ đâu phải ai cũng hiểu được kỹ thuật BPMN. Operator không thể tự build process flow. Phải có cách để người không biết kỹ thuật cũng tự tạo workflow được."*

### Trả lời hiện tại (MVP3)
**BPMN Designer đã có trên UI** (bpmn-js, drag-and-drop). Nhưng vẫn cần hiểu BPMN concepts (Gateway, Service Task, etc.).

**Đã demo được:** Operator mô tả bằng tiếng Việt → AI trigger đúng workflow. Nhưng tạo workflow mới vẫn cần developer.

### Hướng phát triển

#### MVP3 (Sprint 10-11)
- [ ] **Workflow Template Library**: Thêm 10-15 pre-built workflow templates (Flood Alert, AQI Notification, Equipment Failure, ESG Report). Operator chọn template → customize parameters → deploy. Không cần biết BPMN.
- [ ] **No-code Trigger Config**: UI hiện có `TriggerConfig` nhưng chỉ admin mới dùng. Thêm wizard UI: "Khi nào trigger?" → select sensor type + threshold → "Làm gì?" → select action template → Done.

#### MVP4 (Sprint 13, Q4 2026)
- [ ] **Natural Language → BPMN**:
  ```
  Operator gõ:
  "Khi cảm biến CO vượt 200ppm, đợi 3 phút xem có smoke không.
   Nếu có smoke → gọi 113 và sơ tán. Nếu không → tắt HVAC."
  
  AI generates:
  StartEvent → CO_Check → Timer(3min) → Gateway
      → [Smoke=Yes] → Call113 + Evacuate → End
      → [Smoke=No]  → ShutHVAC + NotifyOp → End
  
  System shows BPMN diagram → Operator confirms → Deploy
  ```
  **Code basis:** BPMN Designer UI đã có. Cần thêm `NaturalLanguageToBpmnService` gọi Claude với BPMN expert system prompt.
  
- [ ] **Guided workflow builder**: Step-by-step wizard thay vì raw BPMN canvas. "Trigger → Condition → Action → Notification" form-based.

#### MVP5 (Q1 2027)
- [ ] **Workflow learning từ incidents**: Sau khi operator manually handle 10 incidents cùng loại → AI suggests: "Tôi thấy bạn thường làm X khi Y xảy ra. Muốn tôi tự động hóa không?"
- [ ] **Multi-language support**: Vietnamese voice input → workflow creation.

---

## Tổng hợp Roadmap theo MVP

### MVP3 Final (đang là) — Pilot Ready
**Đã có:**
- 7 AI scenarios với Claude Sonnet
- 207 AI decisions/ngày trong demo
- Flink CEP cho structural vibration (Welford)
- BPMN Designer UI
- CircuitBreaker + RuleBasedFallback

### MVP4 — Scale & Cost (Q3 2026, Sprint 11-14)

| Sprint | Feature | Effort | Impact |
|--------|---------|--------|--------|
| S11 | Welford Universal (all sensors) | 2w | Anomaly-first, no threshold config |
| S11 | District-level Flink batching | 1w | 99% AI call reduction |
| S12 | Operator feedback capture UI | 1w | Training data accumulation |
| S12 | IncidentCorrelationFlinkJob | 2w | Multi-device → 1 AI call |
| S12 | Model routing (haiku/sonnet) | 3d | 80% cost reduction |
| S13 | Workflow Template Library (15 templates) | 2w | Operator self-service |
| S13 | No-code Trigger Config wizard | 2w | No-developer workflow creation |
| S14 | BMS bidirectional loop | 2w | AI → BMS → confirm → AI |

**Target metrics:**
- AI cost: $0.60/ngày (từ $50/ngày) → **83× cheaper**
- Incident detection: False positive < 5% (từ 20% per-device approach)
- Operator self-service: 80% workflows không cần developer

### MVP5 — Intelligence & Self-Learning (Q1-Q2 2027, Sprint 15-20)

| Feature | Description |
|---------|-------------|
| Incident Feedback Loop | 30-day data → AI improves own prompts |
| NL → BPMN Generator | Mô tả tiếng Việt → BPMN XML auto-deploy |
| Cross-building Correlation | Spatial-temporal incident detection |
| Baseline drift detection | Adaptive thresholds per device over time |
| Workflow learning | AI suggests automation từ manual patterns |
| Few-shot prompt engine | Incidents as training examples, no manual prompts |

---

## Competitive Moat Analysis

### Tại sao 3 tính năng này tạo defensible moat

| Tính năng | Time to replicate (competitor) | Lý do |
|-----------|-------------------------------|-------|
| Welford Universal | 3-6 months | Cần production data để train baseline |
| Incident Feedback Loop | 6-12 months | Cần labeled incident data (chỉ có từ pilot) |
| NL → BPMN + Domain | 6-9 months | Domain-specific Vietnamese + building codes |
| Correlation Engine | 4-6 months | Cần device integration data |

**Kết luận:** Mỗi tháng pilot HCMC chạy = thêm ~1,000 labeled incidents = competitive moat tăng. Competitor muốn replicate phải tự accumulate data từ đầu.

---

## Investment Ask — Tại sao cần funding ngay

### Use of Funds cho MVP4 ($2.5M Seed)

| Category | Amount | Deliverable |
|----------|--------|-------------|
| Engineering (4 sprints × 8 engineers) | $700K | MVP4 features above |
| Infrastructure (K8s production HCMC) | $300K | 99.9% SLA production deployment |
| Data & AI (model fine-tuning, prompt engineering) | $200K | Domain-specific AI improvement |
| Sales & Onboarding | $1M | 10 Tier 2+ customers by Q4 2026 |
| Contingency | $300K | Buffer for regulatory/integration surprises |

### Revenue Projection với MVP4 features

| Quarter | Customers | MRR | Key Driver |
|---------|-----------|-----|------------|
| Q3 2026 | 3 Tier 2 | $6K | HCMC pilot + 2 early adopters |
| Q4 2026 | 8 Tier 2, 1 Tier 3 | $24K | No-code workflow → faster onboarding |
| Q1 2027 | 15 Tier 2, 3 Tier 3 | $54K | Correlation engine → upsell to Tier 3 |
| Q2 2027 | 20 Tier 2, 5 Tier 3, 1 Tier 4 | $110K | City-wide contract |

**Series A trigger:** $100K MRR + proven correlation engine → Q2 2027 at $30-50M valuation.

---

## Action Items (ngay sau demo)

### Tuần tới (2026-06-09 → 2026-06-13)
- [ ] **PM**: Đưa 3 tính năng MVP4 vào Sprint 11 backlog với story points
- [ ] **SA**: ADR cho IncidentCorrelationFlinkJob — CEP pattern design
- [ ] **Backend**: Prototype WelfordUniversalFlinkJob (reuse existing code)
- [ ] **Frontend**: UI mockup cho Workflow Template Library
- [ ] **BA**: User stories cho Operator Feedback capture

### Sprint 11 kickoff (2026-06-16)
- [ ] IncidentCorrelationFlinkJob design review
- [ ] Welford Universal implementation start
- [ ] Model routing config schema design

---

*Tài liệu: Investor Q&A Product Roadmap | Version 1.0 | 2026-06-06*  
*Demo session: 207 AI instances, 1,323 sensor readings, 5 investor questions*  
*Next review: Sprint 11 kickoff 2026-06-16*
