# GitHub Copilot Instructions — UIP ESG POC

## Code Navigation — BẮT BUỘC dùng MCP tools

Project này có CodeGraph MCP server (`codegraph`) và java-graph MCP server. Khi phân tích codebase, **phải dùng MCP tools trước khi grep/read file**.

### Quy tắc:

1. **Tìm code** → dùng `codegraph_search` trước, KHÔNG dùng grep/find để tìm symbol
2. **Hiểu call flow** → dùng `codegraph_callers` / `codegraph_callees` thay vì đọc từng file
3. **Trước khi sửa class/method** → chạy `java-graph impact_analysis` để xem ảnh hưởng (CHỈ KHI tool work — cần fix bug trước)
4. **Xem file structure** → dùng `codegraph_files` thay vì ls/find
5. **Spring bean injection, bytecode analysis** → dùng `java-graph` tools (`get_resolved_callers`, `get_callers`, `impact_analysis`)
6. **Chỉ `Read` file khi đã biết chính xác** vị trí cần sửa nhờ MCP tools

### Luồng chuẩn khi nhận task:

```
1. codegraph_search              → định vị nhanh, không bao giờ miss, polyglot
2. java-graph impact_analysis    → CHỈ KHI tool work (cần fix bug trước)
3. java-graph get_resolved_callees → bytecode truth, CHỈ cho Java core refactor
4. Read                          → khi đã biết chính xác cần sửa gì
5. Edit/Write                    → implement
```

### Exceptions — KHÔNG cần MCP khi:
- Đã biết chính xác file và line cần sửa
- Viết unit test cho method đã biết vị trí
- Fix bug đơn giản ở 1 file

## Project Structure

- `backend/` — Java/Spring Boot (Gradle)
- `frontend/` — React/TypeScript (Vite)
- `docs/mvp3/` — Sprint docs, reports, ADRs
