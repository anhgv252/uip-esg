# Stakeholder Demo — Thư mời (DRAFT)

| Field | Value |
|---|---|
| **Mục đích** | Mời city authority (HCMC) + investor dự demo MVP4 → ký-off → DECLARE DONE |
| **Ngôn ngữ** | Tiếng Việt (chuyên nghiệp, phù hợp cơ quan nhà nước + nhà đầu tư) |
| **Drafted** | 2026-06-18 (SA, on behalf of PM — PM agent model unavailable this session) |
| **Cách dùng** | PM copy nội dung dưới đây vào email, điền `{{...}}`, gửi cho danh sách stakeholder. |

> **Lưu ý cho PM**: điền `{{DATE}}`, `{{TIME}}`, `{{LOCATION/MEETING_LINK}}`, `{{PM_NAME}}`, `{{PHONE}}` trước khi gửi. Đính kèm [`mvp4-summary-draft.md`](mvp4-summary-draft.md) làm tài liệu tóm tắt.

---

## Tiêu đề

`[UIP Smart City] Mời tham dự demo kết quả MVP4 — {{DATE}}`

## Nội dung

Kính gửi **{{QUY_VI/ÔNG_BÀ}}**,

Đội ngũ dự án **UIP Smart City** trân trọng kính mời **{{QUY_VI/ÔNG_BÀ}}** tham dự buổi **demo kết quả giai đoạn MVP4**, nhằm báo cáo 3 kết quả kỹ thuật trọng yếu và xin ý kiến phê duyệt để kết thúc chính thức giai đoạn MVP4, mở đường cho giai đoạn MVP5.

### Thông tin buổi demo

| Mục | Nội dung |
|---|---|
| **Thời gian** | {{DATE}}, {{TIME}} (30 phút) |
| **Hình thức** | {{LOCATION / Meeting link}} |
| **Thành phần tham dự (bên UIP)** | Project Manager, Solution Architect, Backend Lead, Frontend Lead |
| **Tài liệu đính kèm** | Tóm tắt MVP4 (`mvp4-summary-draft.md`) |

### Ba kết quả trọng yếu sẽ trình bày

1. **Tối ưu chi phí AI**: từ ~50 USD/ngày (10.000 cảm biến) xuống **dưới 1 USD/ngày** — pipeline Flink batch + cache + định tuyến model, đo thực tế trên staging ngày 18/06/2026 (~0,187 USD/ngày ngoại suy, 56% cache hit).
2. **Bộ máy tương quan đa cảm biến**: gộp nhiều cảnh báo thành một sự kiện thông minh, giảm false positive (xác minh ngưỡng 0,556 < 0,6), giảm hiện tượng "mệt mỏi cảnh báo" cho vận hành viên.
3. **Tự phục vụ vận hành**: 10 mẫu quy trình (workflow) qua giao diện no-code, vận hành viên tự tạo workflow mà không cần lập trình viên.

### Trạng thái chất lượng

- **7/10 cổng chất lượng (gate) đã PASS chính thức**: regression 1.726 test (0 fail), hiệu năng 1000 VU (p95 = 6ms, 0% lỗi), quét OWASP không có lỗ hổng nghiêm trọng (CVSS ≥ 7), nghiệm thu vận hành viên (UAT) mẫu quy trình + an toàn BMS.
- **3 cổng còn lại** (tỷ lệ false positive 30 ngày, uptime pilot 30 ngày, đăng ký app store) phụ thuộc thời gian chạy pilot thực tế (dự kiến 08/2026) — không cản trở quyết định phê duyệt MVP4.

### Yêu cầu phê duyệt

Sau khi trình bày, chúng tôi kính đề nghị **{{QUY_VI/ÔNG_BÀ}}** xem xét **ký phê duyệt kết thúc MVP4**, làm cơ sở để:
- Triển khai chính thức pilot tại các tòa nhà;
- Khởi động MVP5 (mở rộng quy mô K8s, sinh workflow từ ngôn ngữ tự nhiên tiếng Việt).

Biểu mẫu phê duyệt sẽ được cung cấp tại buổi demo.

### Xác nhận tham dự

Kính mong **{{QUY_VI/ÔNG_BÀ}}** phản hồi xác nhận tham dự trước **{{RSVP_DATE}}**. Nếu thời gian trên không phù hợp, vui lòng đề xuất khung giờ khác — chúng tôi sẵn sàng sắp xếp lại.

Trân trọng cảm ơn,

**{{PM_NAME}}**
Project Manager — UIP Smart City
Điện thoại: {{PHONE}}
Email: {{EMAIL}}

---

> **Ghi chú nội bộ (PM)**: nếu city authority yêu cầu báo cáo đầy đủ hơn (chi phí, lộ trình), đính kèm thêm `mvp4-summary-draft.md` §1 (Deliverables) và §5 (MVP5 Roadmap). Nếu investor hỏi về ROI, chuẩn bị sẵn con số $100K MRR trigger Series A (`mvp5-roadmap-draft.md` §4).
