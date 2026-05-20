# UIP ESG POC — Project Instructions

## Pre-Deploy Code Review (Bắt buộc)

Sau khi Backend/Frontend agent hoàn thành implementation, **PHẢI chạy SA Code Review trước khi giao cho DevOps deploy.** Không skip bước này.

### Luồng chuẩn:
```
1. Implement (Backend/Frontend agent) → compile + unit tests PASS
2. SA Code Review → fix issues
3. DevOps build + deploy + smoke test
```

### SA Review Checklist — Backend (10 items)
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
