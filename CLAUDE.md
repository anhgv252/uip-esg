# UIP ESG POC — Project Instructions

## Tool Selection for Codebase Analysis

Before reading code, score the task on 3 signals:

| Signal | 0 | +1 | +2 |
|---|---|---|---|
| **Scope** | test / fix bug / add field | new feature / refactor | understand flow / trace usage |
| **File count** | 1–3 files | 4–7 files | 8+ files or unknown |
| **Info needed** | code body / logic | structure / class overview | callers / dependency chain |

**Decision:**
- Score **0–1** → `Read` / `Glob` / `Grep` only
- Score **2–3** → `java-graph` to navigate, then `Read` key files to implement
- Score **4–6** → `java-graph` as primary tool

**Examples:**
- "Viết unit test cho ServiceX" → score 0 → Read trực tiếp
- "Refactor DTO, đổi field type" → score 5 → java-graph trước
- "Fix NPE trong method cụ thể" → score 0 → Read trực tiếp
- "Thêm feature end-to-end" → score 4–5 → java-graph trước
