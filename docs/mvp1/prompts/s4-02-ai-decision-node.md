# Prompt: S4-02 — AI Decision Node: Unit Tests

**Story:** S4-02 AI Decision Node: Claude API Integration (8 SP)  
**Status:** Production code ✅ DONE — Còn thiếu unit tests  
**Owner:** Be-2

## Trạng thái hiện tại

Tất cả production code đã được implement. **Chỉ còn thiếu unit tests:**
- ❌ `ClaudeApiServiceTest.java`
- ❌ `AIAnalysisDelegateTest.java`

---

## Bước 1 — Phân tích codebase (BẮT BUỘC, gọi song song)

Dùng java-graph-mcp để hiểu implementation hiện tại trước khi viết test:

```
find_class("ClaudeApiService")                   // path, package, annotations
get_class_members("ClaudeApiService")            // methods: analyzeAsync, loadPromptTemplate, substituteVariables, parseAIDecision
find_class("AIAnalysisDelegate")                 // JavaDelegate implement
get_class_members("AIAnalysisDelegate")          // execute() method
find_class("RuleBasedFallbackDecisionService")   // fallback scenarios
find_class("AIDecision")                         // DTO fields
find_class("WorkflowConfig")                     // claudeRestTemplate bean config
find_class("WorkflowServiceTest")                // pattern: @ExtendWith(MockitoExtension), @Mock, @InjectMocks
```

---

## Bước 2 — Tạo `ClaudeApiServiceTest.java`

**Path:** `backend/src/test/java/com/uip/backend/workflow/service/ClaudeApiServiceTest.java`

Viết **unit test với Mockito** cho `ClaudeApiService`. Phải cover đủ các case:

### Test cases bắt buộc:

**Case 1 — API key rỗng → dùng fallback:**
- `apiKey` = blank → không gọi RestTemplate, gọi `fallbackService.getFallbackDecision()`
- Verify: result = fallback decision, RestTemplate không được gọi

**Case 2 — Happy path: Claude API trả về JSON hợp lệ:**
- Mock `claudeRestTemplate.exchange()` trả về response có `content[0].text` = valid JSON
- JSON mẫu:
```json
{
  "decision": "NOTIFY_CITIZENS",
  "reasoning": "AQI is high",
  "confidence": 0.92,
  "recommended_actions": ["Send alert", "Recommend staying indoors"],
  "severity": "HIGH"
}
```
- Verify: `AIDecision.decision` = "NOTIFY_CITIZENS", `confidence` = 0.92

**Case 3 — Claude API response wrapped trong markdown code block:**
- `content[0].text` = ` ```json\n{...}\n``` `
- Verify: JSON vẫn được parse đúng (regex extraction hoạt động)

**Case 4 — RestTemplate throw `RestClientException` → fallback:**
- Mock `exchange()` throw `RestClientException`
- Verify: trả về fallback decision, không throw exception

**Case 5 — Claude response empty content → fallback:**
- Mock `response.getBody().getContent()` = empty list
- Verify: fallback được dùng

**Case 6 — JSON parse lỗi → trả về PARSE_ERROR decision:**
- `content[0].text` = "đây là text không phải JSON"
- Verify: `decision` = "PARSE_ERROR", `confidence` = 0.0

**Case 7 — `substituteVariables` hoạt động đúng:**
- Template có `{sensorId}` và `{aqiValue}`, context map = `{"sensorId": "S001", "aqiValue": "175"}`
- Verify: placeholder được thay thế trong prompt (test qua happy path)

### Kỹ thuật mock:
```java
// Mock RestTemplate HTTP call
when(claudeRestTemplate.exchange(
    anyString(), eq(HttpMethod.POST),
    any(HttpEntity.class), eq(ClaudeApiResponse.class)
)).thenReturn(ResponseEntity.ok(mockResponse));

// Inject @Value fields bằng ReflectionTestUtils
ReflectionTestUtils.setField(claudeApiService, "apiKey", "test-api-key");
ReflectionTestUtils.setField(claudeApiService, "apiUrl", "https://api.anthropic.com/v1/messages");
ReflectionTestUtils.setField(claudeApiService, "timeoutSeconds", 10);
```

### Lưu ý:
- `analyzeAsync()` trả về `CompletableFuture<AIDecision>` → dùng `.get()` trong test
- `loadPromptTemplate()` đọc file từ classpath `prompts/<scenarioKey>.txt` → test với `scenarioKey = "aiC01_aqiCitizenAlert"` (file đã tồn tại)
- Test với `@ExtendWith(MockitoExtension.class)`, **không** dùng Spring context

### Package & Imports:
```java
package com.uip.backend.workflow.service;

import com.uip.backend.workflow.dto.AIDecision;
import com.uip.backend.workflow.dto.ClaudeApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
```

---

## Bước 3 — Tạo `AIAnalysisDelegateTest.java`

**Path:** `backend/src/test/java/com/uip/backend/workflow/delegate/AIAnalysisDelegateTest.java`

Viết **unit test** cho `AIAnalysisDelegate.execute()`.

### Test cases bắt buộc:

**Case 1 — Happy path: AI decision được set vào process variables:**
- Mock `claudeApiService.analyzeAsync()` trả về `CompletableFuture.completedFuture(decision)`
- Mock `execution.getVariable("scenarioKey")` = `"aiC01_aqiCitizenAlert"`
- Mock `execution.getVariables()` = `Map.of("scenarioKey", "aiC01_aqiCitizenAlert", "aqiValue", "175")`
- Verify các `setVariable` calls:
  - `execution.setVariable("aiDecision", "NOTIFY_CITIZENS")`
  - `execution.setVariable("aiConfidence", 0.92)`
  - `execution.setVariable("aiSeverity", "HIGH")`

**Case 2 — `analyzeAsync` throw exception → set error variables, không throw:**
- Mock `analyzeAsync()` throw `RuntimeException("API timeout")`
- Verify: `execution.setVariable("aiDecision", "ERROR")` được gọi
- Verify: `execute()` **không** throw exception (Camunda process continues)

**Case 3 — Verify tất cả 5 variables được set (happy path):**
- Verify đủ 5 variables: `aiDecision`, `aiReasoning`, `aiConfidence`, `aiRecommendedActions`, `aiSeverity`

**Case 4 — scenarioKey null → error variables:**
- `execution.getVariable("scenarioKey")` = null
- Verify: error không propagate ra ngoài `execute()`

### Kỹ thuật mock:
```java
@Mock private ClaudeApiService claudeApiService;
@Mock private DelegateExecution execution;

// Setup
when(execution.getVariable("scenarioKey")).thenReturn("aiC01_aqiCitizenAlert");
when(execution.getVariables()).thenReturn(new HashMap<>(Map.of("scenarioKey", "aiC01_aqiCitizenAlert")));
when(execution.getProcessInstanceId()).thenReturn("pi-test-001");
```

### Package & Imports:
```java
package com.uip.backend.workflow.delegate;

import com.uip.backend.workflow.dto.AIDecision;
import com.uip.backend.workflow.service.ClaudeApiService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
```

---

## Bước 4 — Chạy test và verify

```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc
./gradlew test \
  --tests "com.uip.backend.workflow.service.ClaudeApiServiceTest" \
  --tests "com.uip.backend.workflow.delegate.AIAnalysisDelegateTest" \
  -p backend
```

**Acceptance criteria S4-02 (DoD):**
- [ ] `ClaudeApiServiceTest` — tất cả 7 test cases pass
- [ ] `AIAnalysisDelegateTest` — tất cả 4 test cases pass
- [ ] Coverage `ClaudeApiService` ≥ 80%
- [ ] Coverage `AIAnalysisDelegate` ≥ 80%
- [ ] Không có hardcoded API key trong test code
- [ ] Sau khi pass → cập nhật detail-plan: S4-02 → ✅ Done
