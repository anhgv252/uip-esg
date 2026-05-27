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
| BUG-S4-T04 | Forecast 503 when Python down (no NAIVE_ROLLING fallback wired) | OPEN — P2 DEFERRED | Backend Team | 2026-05-27 | `ForecastController` catches `ForecastServiceUnavailableException` → 503; `NaiveForecastAdapter` bean exists but not wired as fallback. Service healthy in prod. Deferred Sprint 5. **PM risk-acceptance required.** |

**P2 count:** 1 — deferred, non-blocking for demo ✅

---

## Đã đóng (Resolved)

| ID | Priority | Title | Resolution | Closed |
|----|----------|-------|------------|--------|
| — | — | — | — | — |

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
