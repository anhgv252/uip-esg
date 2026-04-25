# Manual Test Prompts — java-graph-mcp

Dùng các prompt này trong **Claude Code** (mở tại `/Users/anhgv/working/my-project/smartcity/uip-esg-poc`)
để kiểm tra xem MCP tools và hook tự động có hoạt động đúng không.

Sau mỗi nhóm prompt, chạy **Prompt Kiểm Tra Activity** để xem Claude đã dùng tool gì.

---

## Cách dùng

1. Copy từng prompt vào Claude Code chat
2. Quan sát tool calls nào xuất hiện trong sidebar
3. Sau đó gọi: `dùng tool_activity để show history gần đây`

---

## 1. WARM-UP — Hook injection trigger

> **Mục đích**: Kiểm tra `UserPromptSubmit` hook có inject context tự động không.
> Hook sẽ fire khi prompt chứa tên class có suffix `Service|Controller|Repository|...`

```
Cho tôi biết tổng quan về EsgService trong hệ thống này.
```

**Kỳ vọng**: Claude nhận được context tự động về `EsgService` từ hook, trả lời ngay mà không cần call tool thêm — hoặc call `find_class` để xác nhận.

---

```
AlertService đang làm gì? Nó có những method nào?
```

**Kỳ vọng**: Hook inject `AlertService`, Claude gọi thêm `get_class_members` hoặc `find_method`.

---

## 2. CALL GRAPH — get_callers / get_callees

> **Mục đích**: Kiểm tra traversal với bytecode-enriched edges (`calls_resolved`).

```
Tôi muốn refactor method generateReport trong EsgService.
Trước khi làm, cho tôi biết tất cả các nơi đang gọi method này.
```

**Kỳ vọng**: Claude gọi `get_callers("generateReport", "EsgService")` → thấy `EsgController` là caller.

---

```
Method broadcastSensorUpdates trong EnvironmentBroadcastScheduler
đang gọi tới những class/method nào? Vẽ luồng cho tôi.
```

**Kỳ vọng**: Claude gọi `get_callees("broadcastSensorUpdates", "EnvironmentBroadcastScheduler", depth=3)` → thấy `SseEmitterRegistry` xuất hiện trong chain.

---

```
Tìm đường đi từ EsgController tới EsgReportGenerator — có bao nhiêu bước?
```

**Kỳ vọng**: Claude gọi `trace_call_path("generateReport", "buildReport")` → thấy path qua `EsgService`.

---

## 3. IMPACT ANALYSIS — trước khi thay đổi

> **Mục đích**: Claude dùng `impact_analysis` trước khi suggest code changes.

```
Tôi cần thay đổi interface của SseEmitterRegistry — thêm method broadcastToUser(userId, message).
Thay đổi này ảnh hưởng tới những component nào trong backend?
```

**Kỳ vọng**: Claude gọi `impact_analysis("SseEmitterRegistry")` → thấy `NotificationService`, `NotificationController`, `EnvironmentBroadcastScheduler` bị ảnh hưởng.

---

```
Nếu tôi đổi signature của AlertService, class nào cần update theo?
```

**Kỳ vọng**: Claude gọi `class_dependencies("AlertService")` và/hoặc `impact_analysis("AlertService")` → trả về `AlertController`, `AlertRuleController`.

---

## 4. NEW FEATURE — Claude tự khám phá pattern

> **Mục đích**: Khi yêu cầu tính năng mới, Claude phải hiểu pattern hiện tại trước khi gen code.

```
Tôi muốn thêm module `parking` vào hệ thống, tương tự module `traffic`.
Module mới cần: ParkingController, ParkingService, ParkingRepository, domain Parking.
Hãy tham khảo cấu trúc module traffic để gen code theo đúng pattern.
```

**Kỳ vọng**: Claude gọi:
- `search_package("com.uip.backend.traffic")` để xem cấu trúc
- `get_class_members("TrafficService")` hoặc `find_class("TrafficController")`
- Sau đó gen code theo đúng pattern class đã thấy

---

```
Tôi muốn thêm tính năng push notification khi có alert mới.
NotificationService đã có sẵn chưa? Nó support những loại notification nào?
Workflow nào đang trigger notification hiện tại?
```

**Kỳ vọng**: Claude gọi:
- `find_class("NotificationService")` → thấy `SseEmitterRegistry`
- `get_callers("broadcast", "SseEmitterRegistry")` → thấy `EnvironmentBroadcastScheduler`
- `find_by_annotation("KafkaListener")` → tìm consumer hiện có

---

## 5. BYTECODE-SPECIFIC — Phase 2 tools

> **Mục đích**: Confirm bytecode enrichment tools hoạt động.

```
Chạy bytecode_status để xem tình trạng enrichment của dự án này.
```

**Kỳ vọng**: Tool trả về `enriched: true`, liệt kê `calls_resolved`, `type_depends`, `reads_field`, `writes_field` edge counts.

---

```
EsgReportGenerator đang đọc và ghi những field nào?
Có method nào bên ngoài class đang truy cập trực tiếp vào field không?
```

**Kỳ vọng**: Claude gọi `get_field_accesses("EsgReportGenerator")` → liệt kê readers/writers của từng field.

---

```
WorkflowService phụ thuộc vào những class nào ở bytecode level?
So sánh với AST dependencies xem có chênh lệch không.
```

**Kỳ vọng**: Claude gọi `get_type_dependencies("WorkflowService")` → so sánh `bytecode.uses` vs `ast.uses`.

---

## 6. DEAD CODE & COUPLING

> **Mục đích**: Kiểm tra phân tích chất lượng.

```
Trong package com.uip.backend.admin, có method nào public nhưng chưa được gọi không?
```

**Kỳ vọng**: Claude gọi `find_dead_code("com.uip.backend.admin")` → thấy các Admin REST endpoints không có caller (đúng vì HTTP callers không được index).

---

```
Package nào trong backend có coupling cao nhất với nhau?
Đề xuất hướng cải thiện.
```

**Kỳ vọng**: Claude gọi `package_coupling_report()` → thấy `citizen.service ↔ citizen.repository`, đề xuất cải thiện.

---

```
Những method nào trong hệ thống có logic phức tạp nhất (nhiều dòng code nhất)?
```

**Kỳ vọng**: Claude gọi `find_complex_methods(min_lines=30)`.

---

## 7. FRONTEND — TypeScript/TSX tools

> **Mục đích**: Confirm TS tools hoạt động.

```
EsgPage component đang gọi những API nào? Frontend đang dùng axios hay fetch?
```

**Kỳ vọng**: Claude gọi `find_component("EsgPage")` và `find_api_calls("esg")`.

---

```
Có những custom hook nào liên quan đến authentication trong frontend?
```

**Kỳ vọng**: Claude gọi `find_hooks("Auth")` hoặc `find_by_annotation("useAuth")`.

---

```
Tôi muốn thêm component TrafficHeatMap vào TrafficPage.
Component tương tự nào đã có trong codebase để tham khảo pattern?
```

**Kỳ vọng**: Claude gọi `find_component("TrafficBarChart")` hoặc `find_by_language("tsx", "component")`.

---

## 8. WORKFLOW — Multi-step analysis

> **Mục đích**: Claude tự tổng hợp nhiều tool calls.

```
Hãy làm một code review nhanh cho module `esg`:
1. Kiểm tra class nào có coupling cao
2. Method nào phức tạp cần refactor
3. Method nào dead code
4. Bytecode dependencies có match với source không
```

**Kỳ vọng**: Claude tự orchestrate 4-5 tool calls: `search_package`, `find_complex_methods`, `find_dead_code`, `get_type_dependencies`, `package_coupling_report`.

---

```
Workflow AlertDetection hiện tại chạy như thế nào từ khi Flink nhận event 
đến khi user nhận notification? Trace toàn bộ luồng.
```

**Kỳ vọng**: Claude dùng `trace_call_path`, `get_callees`, `find_by_annotation("KafkaListener")` để reconstruct luồng từ `AlertDetectionJob` → `AlertController` → `NotificationService` → `SseEmitterRegistry`.

---

## 9. KIỂM TRA ACTIVITY

> Chạy sau mỗi nhóm để kiểm tra xem Claude đã dùng tools gì.

```
Dùng tool_activity() để show tôi xem trong session này Claude đã gọi những 
java-graph tools nào, bao nhiêu lần, với input gì.
```

**Kỳ vọng**: Trả về danh sách tool calls đã logged trong `.java-graph/tool-calls.jsonl`.

---

```
So sánh: bao nhiêu lần context được inject tự động qua hook 
vs bao nhiêu lần Claude chủ động gọi tool?
```

**Kỳ vọng**: `tool_activity()` hiển thị `hook_injections_logged` vs `total_tool_calls_logged`.

---

## 10. RE-INDEX sau phát triển

> **Mục đích**: Test auto-reindex sau khi code được thêm.

```
Tôi vừa thêm class ParkingService vào package com.uip.backend.parking.service.
Hãy index lại file này và verify class đã được graph nhận diện.
```

**Kỳ vọng**: Claude gọi `index_file("backend/src/main/java/com/uip/backend/parking/service/ParkingService.java")` rồi `find_class("ParkingService")`.

---

```
Re-index toàn bộ dự án và chạy bytecode enrichment lại.
```

**Kỳ vọng**: Claude gọi `index_project(force=False)` → auto triggers jQAssistant scan vì `enrich_after_index: true`.

---

## Cheat Sheet — Mapping Prompt → Tool

| Scenario | Tool nên được gọi |
|----------|-------------------|
| "ai đang gọi method X" | `get_callers` / `get_resolved_callers` |
| "X gọi tới đâu" | `get_callees` / `get_resolved_callees` |
| "đường từ A đến B" | `trace_call_path` |
| "đổi class X thì ảnh hưởng gì" | `impact_analysis` |
| "pattern của module Y" | `search_package` + `get_class_members` |
| "annotation @KafkaListener" | `find_by_annotation` |
| "dead code" | `find_dead_code` |
| "coupling giữa packages" | `package_coupling_report` |
| "field access" | `get_field_accesses` |
| "bytecode vs AST deps" | `get_type_dependencies` |
| "component React/TSX" | `find_component` |
| "custom hook" | `find_hooks` |
| "API call frontend" | `find_api_calls` |
| "Claude đã dùng gì" | `tool_activity` |
