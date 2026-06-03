# Sprint 7 — Executive Demo Script (City Authority)

**Duration:** 15 phút | **Ngày:** 2026-06-27 | **Ngôn ngữ:** Tiếng Việt
**Audience:** Lãnh đạo City Authority + Stakeholders thí điểm
**Pre-req:** Staging UP, demo data seeded, Keycloak running

---

## Chuẩn bị (30 phút trước demo)

```
[ ] Đăng nhập staging: operator@authority.vn
[ ] Verify BLDG-001 có safety score + sensor data
[ ] Prepare demo alert inject: kafka inject command sẵn sàng
[ ] Test PDF download 3 lần thành công
[ ] Grafana dashboards mở sẵn tab khác
[ ] Backup screenshots/recording nếu staging down
```

---

## Opening (1 phút)

"Kính chào các quý vị, hôm nay tôi trình bày phiên bản mới nhất của UIP Smart City Platform — Sprint 7.

Trong tháng vừa qua, chúng tôi đã hoàn thiện 3 tính năng chính:
1. **Giám sát An toàn Công trình** — phát hiện rung động, nghiêng, nứt cấu trúc tòa nhà
2. **Quản lý Cảnh báo thống nhất** — module filter, real-time SSE, operator dashboard
3. **Báo cáo ESG + Ứng dụng Mobile** — xuất PDF chuẩn GRI, dashboard mobile

Hệ thống sẵn sàng cho giai đoạn thí điểm."

---

## 1. Building Safety — An toàn Kết cấu (4 phút)

### Bước 1: Mở Building Detail Safety Tab
```
URL: /buildings/BLDG-001?tab=safety
```
**Màn hình:** Safety Score Gauge = 85 (xanh, SAFE), sensors: VIBRATION 8.2 mm/s

> "Đây là bảng an toàn kết cấu của Tòa Phát triển Bền vững. Safety Score 85/100 — tòa nhà đang hoạt động bình thường. Hệ thống theo dõi liên tục qua cảm biến rung động, nghiêng, và vết nứt."

### Bước 2: Xem Vibration Trend Chart
> "Biểu đồ 24h hiển thị ngưỡng: vàng (10 mm/s cảnh báo) và đỏ (50 mm/s nguy hiểm) theo chuẩn TCVN 9386:2012."

### Bước 3: Trigger Structural Alert (demo)
```bash
# QA inject spike via Kafka
curl -X POST http://localhost:8080/api/v1/flood/test/inject-structural \
  -d '{"sensorId":"SENSOR-BLDG001-VIB-X","value":55.0,"tenantId":"hcm"}'
```
**Màn hình sau 5 giây:** Safety Score → đỏ (CRITICAL), sticky banner P0 xuất hiện

> **⚠ QUAN TRỌNG — BR-010:**
> "Hệ thống **KHÔNG TỰ ĐỘNG SƠ TÁN**. Cảnh báo P0 chỉ là lời nhắc để Operator xem xét. Mọi quyết định ứng phó do con người quyết định, không phải máy móc. Điều này tránh gây hoảng loạn không cần thiết khi cảnh báo sai."

**If fails:** Mở Alerts tab → lọc STRUCTURAL → xem alert trong list

---

## 2. Alert Management (2 phút)

### Bước 4: Lọc STRUCTURAL alerts
```
URL: /alerts?module=STRUCTURAL
```
**Màn hình:** Danh sách alerts, Module badge tím = STRUCTURAL

> "Operator có thể lọc cảnh báo theo module để ưu tiên xử lý."

### Bước 5: Acknowledge alert
Click alert → xem detail → Click [Acknowledge]

> "Sau khi Acknowledge, hệ thống ghi nhận: ai xem, khi nào xem. Đây là audit trail cho báo cáo."

---

## 3. ESG PDF Report (2 phút)

### Bước 6: Tạo và tải PDF
```
URL: /esg
Click: "Tải PDF" button (chỉ hiện với esg:write scope)
```
**Sau 3-5 giây:** File `esg-report-Q2-2026.pdf` tải xuống

> "Báo cáo ESG chuẩn GRI 302-1 (năng lượng) + GRI 305-4 (carbon). Tự động từ dữ liệu IoT — không cần nhân viên thủ công."

**If PDF fails:** Screenshot PDF đã chuẩn bị sẵn làm backup

---

## 4. AI Workflow (2 phút)

### Bước 7: Xem Flood Alert Workflow
```
URL: /ai-workflow
Tìm: "Flood Alert Escalation"
```
> "Workflow BPMN cấu hình bởi quản lý, không cần code. Tự động: phát hiện → phân tích AI → gửi FCM → email authority."

---

## 5. Infrastructure Health (1 phút)

### Bước 8: Xem system health
```
URL: Admin → System Health
hoặc: Grafana http://grafana:3000
```
> "Kafka, Flink, ClickHouse, Apicurio — tất cả UP. Welford algorithm đang học baseline từng cảm biến. Sau 1000 readings/sensor, anomaly detection kích hoạt tự động."

---

## 6. Closing (3 phút)

> "Tóm tắt Sprint 7:
> - **An toàn Kết cấu**: Welford + Flink CEP, SLA <15s từ cảm biến đến cảnh báo
> - **Bảo mật**: OWASP 0 Critical, RLS isolation hoàn toàn, 1,200+ tests
> - **Sẵn sàng thí điểm**: Runbook 6 incident scenarios, monitoring Grafana, ADR-034 approved

> **Kế hoạch thí điểm:** Bắt đầu 2026-07-01 tại 3 tòa nhà (BLDG-001, BLDG-002, BLDG-003).
> Tuần 1-2: Staff thành phố sử dụng + feedback.
> Tuần 3-4: Tinh chỉnh + training operator.
> Tuần 5-8: Mở rộng 10-15 tòa nhà.

> Câu hỏi?"

---

## Contingency Plans

| Tình huống | Fallback |
|-----------|---------|
| Alert không xuất hiện | Mở Alerts tab → lọc STRUCTURAL → xem historical alert |
| PDF download fail | Xem screenshot PDF backup, giải thích "email delivery trong production" |
| Dashboard load chậm | Mở Grafana tab thay thế |
| Auth fail | Đăng nhập lại, giải thích PKCE security flow |
| Staging down | Dùng demo recording đã quay sẵn |

---

*Demo Script Sprint 7 v2 | 2026-06-27 | Chuẩn bị bởi PM + SA*
