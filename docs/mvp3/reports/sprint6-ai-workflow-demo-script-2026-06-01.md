# Sprint 6 — AI Workflow Demo Script (Focused)

**Date:** 2026-06-01  
**Audience:** PO, City Authority Stakeholders, Tech Leads  
**Demo Owner:** Tester  
**Duration:** 15–20 phút  
**Goal:** Trình bày AI Workflow là **selling point khác biệt** của UIP — No-code BPMN Designer, AI Decision Routing, Config-Driven Trigger Engine.

---

## 0) Elevator Pitch (30 giây — nói trước khi share màn hình)

> *"Các nền tảng smart city thông thường dùng rule cứng: nếu AQI > 150 thì gửi alert. UIP khác — AI phân tích ngữ cảnh đa chiều, tự quyết định có cần hành động không, và confidence score bao nhiêu thì auto-execute, bao nhiêu thì chuyển operator. City admin tự tạo workflow mới bằng kéo thả BPMN, không cần developer. Đó là tính năng tôi muốn demo hôm nay."*

---

## 1) Pre-Demo Setup (Làm trước khi share màn hình — 3 phút)

### 1.1 Kiểm tra infra

```bash
# Tất cả phải RUNNING
docker compose -f infrastructure/docker-compose.yml ps | grep -E "(postgres|kafka|redis)" | grep -v Exit
```

### 1.2 Backend running + 7 processes deployed

```bash
# Verify backend
curl -s http://localhost:8080/actuator/health | python3 -c "import sys,json; h=json.load(sys.stdin); print('Backend:', h['status'])"

# Verify 7 Camunda processes
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/workflow/definitions | python3 -c "import sys,json; d=json.load(sys.stdin); print('Processes deployed:', len(d.get('content', d if isinstance(d,list) else [])))"
```

### 1.3 Lấy token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
```

### 1.4 Pre-fire một workflow để có sẵn data trong Process Instances

```bash
# Chuẩn bị data: fire aiC02 trước demo (citizen service request dễ giải thích)
curl -s -X POST http://localhost:8080/api/v1/workflow/trigger/aiC02_citizenServiceRequest \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"citizenId":"demo-citizen-001","requestType":"ENVIRONMENT","description":"Mùi hóa chất nồng nặc tại khu D7, ảnh hưởng sức khỏe trẻ em","district":"D7","priority":"HIGH"}'
```

### 1.5 Mở sẵn browser tabs (theo thứ tự demo)

```
Tab 1: http://localhost:3000/ai-workflow        ← mở trên tab đầu tiên
Tab 2: http://localhost:3000/ai-workflow        ← backup tab
Tab 3: Terminal API evidence
```

### 1.6 Go / No-Go Gate (bắt buộc trước demo)

| Check | Command | Pass Condition |
|---|---|---|
| Backend UP | `curl http://localhost:8080/actuator/health` | `"status":"UP"` |
| 7 processes | Xem output 1.2 | count >= 7 |
| Token lấy được | `echo $TOKEN` | non-empty |
| Pre-fire process | Check Process Instances tab | ít nhất 1 record |

**Nếu FAIL:** Dùng fallback (Section 5) — show screenshots + explain architecture.

---

## 2) Demo Script — 3 Màn Hình Chính

### Segment A — Process Instances & Definitions (3 phút)

**Navigate:** `http://localhost:3000/ai-workflow` → Tab **"Process Instances"**

**Presenter line (nói trong khi chỉ màn hình):**
> *"Đây là AI Workflow Dashboard — nơi giám sát toàn bộ workflow đang chạy trong hệ thống. Mỗi row là một process instance được trigger từ sensor, lịch cron, hoặc yêu cầu từ công dân."*

**Chỉ vào:**
- Cột `processDefinitionKey` → giải thích: mỗi key là một scenario khác nhau
- Cột `status` → ACTIVE (đang xử lý) / COMPLETED (AI đã quyết định)
- Nút refresh / filter

**Chuyển sang Tab "Process Definitions":**
> *"7 process definitions này là 7 kịch bản AI đã được deploy sẵn — từ cảnh báo AQI đến ứng phó lũ lụt đến điều phối sự cố tiện ích đô thị. Tất cả đều được quản lý qua Camunda BPMN Engine."*

**Presenter line:**
> *"Điểm quan trọng: mỗi process này có thể được tạo mới, sửa, và deploy lại mà không cần restart hệ thống — thông qua BPMN Designer mà tôi sẽ demo tiếp theo."*

**Pass criteria:**
- [ ] Tabs render, không crash
- [ ] Ít nhất 1 process instance hiển thị (từ pre-fire ở step 1.4)
- [ ] Process Definitions list có >= 7 items

---

### Segment B — BPMN Designer (5 phút) ← Selling Point #1

**Navigate:** Tab **"Designer"**

**Presenter line:**
> *"Đây là BPMN Designer — city admin không cần developer để tạo workflow mới. Chỉ cần kéo thả."*

**Demo flow — thực hiện live:**

**B1.** Click "New Workflow"
- Chỉ canvas trống với Start Event
- *"Canvas BPMN 2.0 chuẩn — bất kỳ ai biết quy trình vận hành đều tạo được."*

**B2.** Kéo node **"AI Decision"** (màu tím) từ Node Palette vào canvas
- *"Node màu tím này là AI Decision — đây là điểm Claude AI sẽ phân tích và đưa ra quyết định."*

**B3.** Click vào AI Decision node → chỉ **AI Config Panel** bên phải
- Điền Prompt: `"Phân tích mức độ ô nhiễm không khí và quyết định cảnh báo"`
- Chỉ Confidence Threshold slider (marks tại 0.6, 0.85)
- *"Đây là ngưỡng confidence — trên 85% thì auto-execute, không cần operator. 60–85% thì vào hàng đợi chờ duyệt. Dưới 60% thì escalate lên cấp cao hơn."*
- Chỉ chip "Auto-Execute" và "Operator Queue"
- *"City admin tự quyết định ngưỡng chấp nhận rủi ro — không cần code."*

**B4.** Kéo thêm **"Notification"** node và **"End Event"** → nối lại

**B5.** Click **"Save"** → chỉ snackbar
> *"Save tạo version mới — hệ thống tự quản lý version history."*

**B6.** Click **"Deploy"** → chỉ snackbar "Deployed to Camunda!"
> *"Deploy đẩy trực tiếp vào Camunda BPMN Engine. Workflow sẵn sàng trigger ngay lập tức."*

**Talking point (sau khi deploy):**
> *"Với các nền tảng khác, thêm scenario mới = viết code → PR → review → deploy. Với UIP: kéo thả → save → deploy. Time-to-live từ tuần xuống giờ."*

**Pass criteria:**
- [ ] Canvas load với Start Event
- [ ] AI Decision node kéo được, màu tím
- [ ] Config Panel hiện khi click AI node
- [ ] Save → snackbar success
- [ ] Deploy → snackbar "Deployed to Camunda!"

---

### Segment C — Config Engine + Live AI Decision (7 phút) ← Selling Point #2 + #3

**Navigate:** Tab **"Config Engine"**

**Presenter line:**
> *"Config Engine là nơi city admin cấu hình trigger cho từng AI scenario — Kafka từ sensor, REST từ citizen portal, hoặc Cron schedule định kỳ. Tất cả data-driven, không cần code, thay đổi có hiệu lực ngay."*

**C1.** Chỉ bảng 7 configs:
- *"7 scenario đang active, với 3 loại trigger khác nhau."*
- Chỉ chip màu: KAFKA (xanh) / REST (xanh lá) / SCHEDULED (tím)
- *"KAFKA trigger real-time từ sensor stream qua Flink. REST trigger từ citizen portal. SCHEDULED chạy định kỳ để kiểm tra ESG anomaly."*

**C2.** Click row **`aiC02_citizenServiceRequest`** (REST trigger — dễ demo nhất)
- Chỉ `filterConditions`: *"Chỉ xử lý request loại ENVIRONMENT hoặc FLOOD — loại GENERAL bỏ qua."*
- Chỉ `variableMapping`: *"Tự map field từ payload vào Camunda variables."*
- Chỉ `aiConfidenceThreshold`: *"Ngưỡng confidence riêng cho từng scenario."*

**C3.** Click **"Fire"** (play icon) trên row `aiC02_citizenServiceRequest`
- Nhập payload:
  ```json
  {
    "citizenId": "demo-citizen-live",
    "requestType": "ENVIRONMENT",
    "description": "Khói đen và mùi hóa chất tại khu công nghiệp Q7, trẻ em bị ho",
    "district": "D7",
    "priority": "HIGH"
  }
  ```
- Click Fire
- Chỉ response: `processInstanceId`
- *"Process vừa được start. Claude AI đang phân tích yêu cầu này."*

**C4.** Chuyển sang Tab **"Process Instances"** → tìm instance vừa tạo
- *"Process đang chạy — hoặc đã COMPLETED nếu AI quyết định nhanh."*

**C5.** Click vào instance → xem variables (hoặc dùng API trên terminal):
```bash
# Thay {instanceId} bằng ID từ response ở C3
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/workflow/instances/{instanceId} \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)
v=d.get('variables',{})
print('Decision:   ', v.get('aiDecision','N/A'))
print('Confidence: ', v.get('aiConfidence','N/A'))
print('Severity:   ', v.get('aiSeverity','N/A'))
print('Reasoning:  ', v.get('aiReasoning','N/A')[:200] if v.get('aiReasoning') else 'N/A')
"
```

**Presenter line (chỉ vào kết quả):**
> *"Claude AI đọc mô tả 'khói đen, mùi hóa chất, trẻ em bị ho' — không phải just keyword matching — và quyết định severity HIGH, confidence 0.91, tức là auto-execute. Reasoning lưu lại đầy đủ để audit sau này."*

> *"Nếu confidence 0.72 — Operator Queue. Một operator nhận notification, review reasoning của Claude, rồi approve hoặc override. AI giảm tải quyết định, không thay thế hoàn toàn con người."*

**C6.** (Optional — nếu có thêm thời gian) Chạy Flood scenario:
```bash
# Demo flood — scenario phức tạp nhất, AI lập kế hoạch ứng phó đa cơ quan
./scripts/demo-flood-alert.sh
```
> *"Scenario lũ lụt phức tạp hơn — AI phân tích 5 yếu tố: lượng mưa, mực nước, thổ nhưỡng, dự báo thời tiết, lịch sử ngập. Quyết định FULL_RESPONSE hay chỉ MONITORING, và đề xuất các agencies cần được activate."*

**Pass criteria:**
- [ ] 7 configs hiển thị với đúng trigger type chips
- [ ] Fire dialog mở, nhận payload JSON
- [ ] Response trả về `processInstanceId`
- [ ] Variables hiện `aiDecision`, `aiConfidence`, `aiReasoning` (không empty)

---

## 3) API Evidence (Terminal — chạy song song hoặc sau demo UI)

```bash
# === Verify AI Workflow backend ===

# E1: 7 process definitions
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/workflow/definitions \
  | python3 -c "import sys,json; d=json.load(sys.stdin); items=d.get('content',d) if isinstance(d,dict) else d; [print(f'  - {x[\"name\"]}') for x in items]"

# E2: All process instances (last 10)
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/workflow/instances?page=0&size=10" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); items=d.get('content',[]); [print(f'  {x.get(\"id\",\"?\")[:8]}... [{x.get(\"processDefinitionKey\",\"?\")}] {x.get(\"status\",\"?\")}') for x in items]"

# E3: 7 trigger configs
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/admin/workflow-configs \
  | python3 -c "import sys,json; d=json.load(sys.stdin); [print(f'  {x[\"scenarioKey\"]:40} {x[\"triggerType\"]:12} enabled={x[\"enabled\"]}') for x in (d.get('content',d) if isinstance(d,dict) else d)]"

# E4: Fire live (aiC02 citizen service)
curl -s -X POST http://localhost:8080/api/v1/workflow/trigger/aiC02_citizenServiceRequest \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"citizenId":"api-demo-001","requestType":"ENVIRONMENT","description":"Chemical smell near factory D7","district":"D7","priority":"HIGH"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('Process ID:', d.get('processInstanceId','?'), '| Status:', d.get('status','?'))"
```

---

## 4) Demo Acceptance Checklist

```
[ ] A. Process Instances tab render, có ít nhất 1 instance
[ ] B. Process Definitions tab có >= 7 items
[ ] C. BPMN Designer: New → kéo AI Decision → Config Panel hiện → Save/Deploy success
[ ] D. Config Engine: 7 configs hiện với đúng trigger type
[ ] E. Fire aiC02: dialog mở, response có processInstanceId
[ ] F. Process instance variables: aiDecision + aiConfidence + aiReasoning không empty
[ ] G. API evidence: E1 + E2 + E3 return data
```

**Sign-off condition:**
- P0: C, D, E, F phải PASS → đây là selling point core
- P1: A, B phải PASS → context cho audience
- P2: G (API evidence) — optional nhưng tăng trust

---

## 5) Fallback Plan — Nếu Infra Có Vấn Đề

### Fallback A: Backend lỗi / infra không lên được

Dùng screenshots từ test guide làm backup evidence:
- `docs/mvp3/testing/ai-workflow-test-guide.md` (Section 12 — Demo Checklist)
- Sprint2 demo screenshots trong `frontend/sprint2-demo-screenshots/`

Nói với audience:
> *"Môi trường local đang có issue. Tôi sẽ walk through architecture và show code để chứng minh implementation. Demo live sẽ được reschedule."*

### Fallback B: Claude API key không có (fallback mode)

Hệ thống vẫn chạy với `RuleBasedFallbackDecisionService`. Nói với audience:
> *"Hiện tại chạy ở fallback mode — AI decision dùng rule-based logic thay vì Claude API. Architecture và luồng hoàn toàn giống nhau, chỉ khác ở model inference. Live Claude integration demo có thể sắp xếp riêng với môi trường staging có key."*

Variables vẫn có `aiDecision`, `aiConfidence`, `aiReasoning` — confidence thường thấp hơn (< 0.8).

### Fallback C: BPMN Designer crash

Chỉ vào NodePalette.tsx và WorkflowModeler source code, explain:
> *"Component dùng bpmn-js v17 — MIT license, industry standard. 5 node types bao gồm AI Decision node với confidence routing tích hợp."*

---

## 6) Talking Points — Q&A Chuẩn Bị

**Q: Tại sao dùng Claude AI thay vì GPT-4?**
> A: "Claude Sonnet 4.6 cho reasoning quality tốt hơn với task phân tích context dài (sensor data + historical + weather). Circuit breaker built-in — nếu API lỗi, system tự fallback về rule-based, zero downtime."

**Q: Làm sao đảm bảo AI không quyết định sai?**
> A: "3 lớp safeguard: (1) Confidence threshold — dưới ngưỡng không auto-execute. (2) Operator queue — human review với AI reasoning. (3) Full audit trail — mọi decision lưu với reasoning để review sau. City admin có thể override bất kỳ lúc nào."

**Q: Có thể add scenario mới không cần developer không?**
> A: "Có — BPMN Designer để vẽ workflow, Config Engine để set trigger. Developer chỉ cần viết prompt template (.txt file) nếu cần fine-tune AI behavior. Prompt template không phải code."

**Q: Scale được không khi có 1000 sensor?**
> A: "Kafka consumer + Redis cache decision (TTL 15 phút) — cùng input → cache hit, không gọi Claude API lại. Horizontal scaling qua Kubernetes HPA. Backpressure handling qua DLQ."

**Q: Camunda có phải license phí không?**
> A: "Dùng Camunda BPM embedded (Apache 2.0 license). Không phải Camunda Platform SaaS. Miễn phí."

---

## 7) Demo Status Checklist (điền sau demo)

```
Demo Date: ____________________
Environment: local / staging
Claude API Mode: LIVE / FALLBACK

Segment A (Process Instances/Definitions): PASS / FAIL
Segment B (BPMN Designer):                PASS / FAIL
Segment C (Config Engine + Live AI):      PASS / FAIL
API Evidence:                              PASS / FAIL

Key AI variables verified:
  aiDecision:   ___________________
  aiConfidence: ___________________
  aiSeverity:   ___________________

Audience reaction / questions noted:
  ___________________________________________

Sign-off:
  [ ] P0 criteria met (C, D, E, F PASS)
  [ ] Demo ready for City Authority presentation
  [ ] Tester sign-off: ___________________
```
