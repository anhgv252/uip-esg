# Architecture — Tài liệu Kiến trúc Hệ thống

Thư mục này chứa các tài liệu kiến trúc **độc lập với sprint** — có giá trị lâu dài, phản ánh trạng thái và quyết định của toàn hệ thống theo thời gian.

> Tài liệu theo sprint (planning, demo, test report) nằm ở `docs/mvp1/`, `docs/mvp2/`, `docs/mvp3/`.

---

## Cấu trúc thư mục

```
docs/architecture/
├── README.md                  ← File này — index và hướng dẫn
│
├── reviews/                   ← SA Architecture Review toàn hệ thống
│   ├── sa-architecture-review-uip-mvp3.md
│   └── ...
│
├── adr/                       ← Architecture Decision Records (cross-MVP)
│   └── ...
│
└── decisions/                 ← Ghi chú quyết định kỹ thuật quan trọng
    └── ...
```

---

## SA Architecture Reviews

Review độc lập từ góc nhìn Solution Architect — đánh giá điểm mạnh, rủi ro, và đề xuất hành động.

| Tài liệu | Ngày | Phiên bản | Kết luận |
|---|---|---|---|
| [SA Review MVP3](reviews/sa-architecture-review-uip-mvp3.md) | 2026-06-01 | MVP3 Sprint 6 | ✅ DUYỆT CÓ ĐIỀU KIỆN |

### Cách đọc SA Review

Mỗi review gồm 5 phần:
1. **Điểm tốt** — điểm mạnh cần giữ lại
2. **Vấn đề & Phát hiện** — mỗi vấn đề kèm mức độ (NGHIÊM TRỌNG / CAO / TRUNG BÌNH / THẤP)
3. **Đề xuất ưu tiên** — action items chia theo "trước pilot" và "roadmap dài hạn"
4. **Câu hỏi còn mở** — những điều cần stakeholder trả lời
5. **Kết luận** — DUYỆT / DUYỆT CÓ ĐIỀU KIỆN / CHẶN

### Khi nào tạo SA Review mới

Tạo review mới khi có thay đổi kiến trúc **đáng kể**:
- Thêm hoặc thay thế một component lớn (database, gateway, auth provider, message bus)
- Kết thúc một MVP hoặc trước production go-live
- Phát hiện rủi ro kiến trúc nghiêm trọng cần đánh giá tổng thể
- Ít nhất **1 lần / MVP**

**Không** tạo review mới cho từng sprint — dùng sprint code review ở `docs/mvp*/reports/` cho việc đó.

---

## ADR (Architecture Decision Records)

Ghi lại quyết định kiến trúc cross-MVP (không gắn với sprint cụ thể). ADR theo sprint nằm ở `docs/mvp*/architecture/`.

> **Quy ước đánh số:** ADR-100+ dành cho thư mục này (ADR-001 đến ADR-099 đã dùng trong mvp1-mvp3).

---

## Decisions

Ghi chú kỹ thuật ngắn cho các quyết định quan trọng không đủ lớn để viết ADR đầy đủ — ví dụ: chọn thư viện, đặt convention, giải thích trade-off nhỏ.

---

## Nguyên tắc kiến trúc của UIP

Các nguyên tắc này áp dụng xuyên suốt mọi MVP và sprint:

| # | Nguyên tắc | Ý nghĩa thực tế |
|---|---|---|
| 1 | **Multi-tenant first** | Mọi data query phải có `tenant_id` context. RLS là tầng cuối cùng, không phải tầng duy nhất. |
| 2 | **Contract-first** | Định nghĩa API contract (OpenAPI) trước khi implement. Flink sink schema phải khớp với ClickHouse/TS schema. |
| 3 | **Fail-fast tại boundary** | Validate tại ingestion (Flink), không để invalid data vào OLTP store. DLQ bắt buộc. |
| 4 | **AI là enhancement, không phải dependency** | Claude API call phải async, có timeout, có fallback rule-based. Critical path không chờ AI. |
| 5 | **Idempotent writes** | Mọi sink (TimescaleDB, ClickHouse) phải safe khi replay. `ON CONFLICT DO NOTHING` hoặc `ReplacingMergeTree`. |
| 6 | **No AGPL** | Không dùng thư viện AGPL. Kiểm tra license trước khi thêm dependency. |
| 7 | **Observability is not optional** | Mọi service phải có structured logging, `/actuator/health`, Prometheus metrics endpoint. |
