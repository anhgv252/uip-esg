# UIP P0/P1 Bug Tracker — Sprint MVP3-4

**Sprint:** MVP3-4 | **Gate Date:** 2026-06-13 15:00 SGT
**Owner:** QA Engineer
**Last updated:** 2026-05-27

> File này thay thế external Jira/Linear cho Sprint MVP3-1.
> Mọi P0/P1 bug **phải** được ghi vào đây trước khi đóng gate checklist.

---

## P0 Bugs (Blocker — Gate cannot pass with any P0 open)

| ID | Title | Status | Assignee | Found | Notes |
|----|-------|--------|----------|-------|-------|
| — | *Không có P0 bug nào được ghi nhận* | — | — | — | — |

**P0 count:** 0 ✅

---

## P1 Bugs (High — Must be resolved or PM-accepted risk)

| ID | Title | Status | Assignee | Found | Resolution / Risk Accept |
|----|-------|--------|----------|-------|--------------------------|
| — | *Không có P1 bug nào được ghi nhận* | — | — | — | — |

**P1 count:** 0 ✅

---

## P2 Bugs (Medium — Deferred with PM risk-acceptance)

| ID | Title | Status | Assignee | Found | Resolution / Risk Accept |
|----|-------|--------|----------|-------|------------------------|
| BUG-S4-T04 | Forecast 503 when Python down (no NAIVE_ROLLING fallback wired) | RESOLVED | Backend Team | 2026-05-27 | Fixed in Sprint 5/6: `ForecastService.java` catches `ForecastServiceUnavailableException` and delegates to `naiveFallback.forecast()`. Controller 503 only fires if naive adapter also fails (DB down). |

**P2 count:** 0 ✅

---

## Đã đóng (Resolved)

| ID | Priority | Title | Resolution | Closed |
|----|----------|-------|------------|--------|
| BUG-S4-T04 | P2 | Forecast 503 when Python down | `ForecastService` wires naive fallback via try-catch; `ForecastHealthChecker` fixed to call `/api/v1/forecast/health` | 2026-06-17 |

---

## Hướng dẫn sử dụng

**Thêm bug mới:**
```
| BUG-001 | [Mô tả ngắn] | OPEN | @engineer | 2026-05-11 | [Context] |
```

**Status values:** `OPEN` · `IN PROGRESS` · `RESOLVED` · `WONT FIX` · `RISK ACCEPTED`

**Gate criteria:**
- [ ] P0 count = 0 (không được có bất kỳ P0 nào ở trạng thái OPEN)
- [ ] P1 count = 0 hoặc tất cả P1 có PM sign-off "RISK ACCEPTED"
