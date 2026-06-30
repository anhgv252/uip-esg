# UIP ESG POC — Project Instructions

## Pre-Deploy Code Review (Bắt buộc)

Sau khi Backend/Frontend agent hoàn thành implementation, **PHẢI chạy SA Code Review trước khi giao cho DevOps deploy.** Không skip bước này.

### Luồng chuẩn:
```
1. Implement (Backend/Frontend agent) → compile + unit tests PASS
2. SA Code Review → fix issues
3. DevOps build + deploy + smoke test
```

### ⚠️ Cross-module dependency — BẮT BUỘC tuân ADR-052 (kiến trúc cốt lõi)

Khi 1 module (bounded context) cần đọc data/logic của module KHÁC, **KHÔNG inject trực tiếp repository/service/domain** của module kia. Phải qua **Hexagonal Port**:

```
common.spi.<Entity>Port              ← Port interface (neutral package, consumer-agnostic)
<provider-module>.adapter.<Adapter>  ← @Component implement Port, delegate repository nội bộ
<consumer-module>.service.<Service>  ← inject Port (KHÔNG import provider.repository)
```

- **Port ở `common.spi.*`** (neutral) — KHÔNG ở consumer module, để provider không phải reference ngược (vi phạm chiều ngược).
- **Trước mỗi PR có `import com.uip.backend.<moduleX>...` (X≠Y)** → chạy `./gradlew test --tests "com.uip.backend.arch.ModuleBoundaryArchTest"`. Vi phạm → extract Port, **KHÔNG relax rule / @ArchIgnore**.
- Chi tiết + anti-pattern + migration plan: `docs/mvp5/adr/ADR-052-hexagonal-port-cross-module-dependency.md`.

### SA Review Checklist — Backend (11 items)
1. Unused imports / dead code
2. Spring bean registration (`@Component`, auto-wire)
3. Null safety (nullable fields, Optional)
4. Exception handling consistent với existing pattern
5. JWT claims đúng (`iss`, `sub`, `tenant_id`)
6. Resource leak (try-with-resources, stream close)
7. Thread safety (volatile, synchronized, ConcurrentHashMap)
8. Config env vars có default (`@Value("${x:default}")`)
9. Dependency license compatible (KHÔNG AGPL)
10. API contract match frontend (path, method, DTO)
11. **Cross-module boundary (ADR-052):** mọi `import com.uip.backend.<moduleX>...` ở module Y (X≠Y) phải qua `common.spi.Port` — KHÔNG reference trực tiếp `<moduleX>.repository`/`.domain`/`.service`. Verify `./gradlew test --tests "com.uip.backend.arch.ModuleBoundaryArchTest"` PASS. Nếu rule vi phạm → extract Port, **KHÔNG relax rule**. (Bài học BUG-M5-009: M5-4 inject `environment.repository` vào `esg` → vi phạm silent 5 ngày vì sprint-review không re-run ArchTest)

### SA Review Checklist — Frontend (10 items)
1. `npx tsc --noEmit` → 0 errors
2. API call signature match backend
3. React Query patterns đúng (useMutation cho POST, useQuery cho GET)
4. Null/undefined safety (optional chaining, defaults)
5. Accessibility (aria-label, form labels)
6. Memory leak (URL.revokeObjectURL, cleanup useEffect)
7. Bundle size impact
8. Responsive breakpoints (768px + 1920px)
9. Error states (loading, error, empty)
10. Auth guard (permission check trước actions)

### Output: `docs/mvp3/reports/sprint{N}-code-review.md`

## Code Navigation — BẮT BUỤC dùng MCP tools trước grep/read

Project này có `.codegraph/` (CodeGraph) và `java-graph` MCP server. **KHÔNG dùng grep/glob/read để tìm code** khi MCP tools có thể trả lời nhanh hơn.

### Quy tắc ưu tiên tool:

| Nhu cầu | Tool dùng đầu tiên | Fallback |
|---|---|---|
| Tìm symbol/class/method theo tên | `codegraph_search` | `Grep` |
| Hiểu flow: ai gọi ai, call chain | `codegraph_callers` / `codegraph_callees` | `java-graph get_resolved_callees` (bytecode truth) |
| Impact analysis trước khi sửa | `java-graph impact_analysis` (⚠️ cần fix bug) | `codegraph_impact` (fallback) |
| Xem tổng quan kiến trúc / vùng code | `codegraph_explore` (trong Explore agent) | Nhiều lần `Read` |
| Spring bean injection, @Transactional flow | `java-graph get_resolved_callees` | `codegraph_callees` |
| Quick file structure | `codegraph_files` | `find` / `Glob` |

### Luồng bắt buộc trước khi sửa code:

```
Bước 1 — Định vị nhanh:   codegraph_search("symbol")         → không bao giờ miss, polyglot
Bước 2 — java-graph impact: impact_analysis("Symbol")          → CHỈ KHI tool work (cần fix bug trước)
Bước 3 — Bytecode truth:   java-graph get_resolved_callees    → CHỈ cho Java core refactor
Bước 4 — Đọc detail:       Read file                          → khi đã biết chính xác
```

### Khi nào KHÔNG cần MCP tools:
- Đã biết chính xác file path và line cần sửa (score 0)
- Viết unit test cho method cụ thể (score 0)
- Fix bug ở method đã biết vị trí (score 0)

### Scoring:

| Signal | 0 | +1 | +2 |
|---|---|---|---|
| **Scope** | test / fix bug / add field | new feature / refactor | understand flow / trace usage |
| **File count** | 1–3 files | 4–7 files | 8+ files or unknown |
| **Info needed** | code body / logic | structure / class overview | callers / dependency chain |

- Score **0–1** → `Read` trực tiếp, không cần MCP
- Score **2–3** → `codegraph_search` → `Read` để implement
- Score **4–6** → `codegraph_context` hoặc `codegraph_explore` (Explore agent)

## java-graph — Trạng thái & lưu ý

### java-graph hiện có bug lookup mismatch
- `find_class` / `search_package` → **OK** (dùng SQLite FTS)
- `impact_analysis` / `get_class_members` / `get_field_accesses` → **FAIL** (Neo4j node ID mismatch)
- `get_resolved_callees` / `get_resolved_callers` → **OK** (bytecode accuracy, depth=2)
- `summarize_project` → **FAIL** (MCP_SAMPLING_UNSUPPORTED với model hiện tại)
- `find_class("AlertEngine")` → **MISS** (không index class này)

### Chỉ dùng java-graph khi:
- Cần **bytecode-accurate call chain** (depth=2) cho Java core — `get_resolved_callees`
- `codegraph` không cover đủ → fallback `find_class`, `search_package`
- **KHÔNG** dùng `impact_analysis`, `get_class_members`, `get_field_accesses` cho đến khi fix bug

