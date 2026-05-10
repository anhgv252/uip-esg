# UIP Smart City — Executive Demo Script

**Thời lượng:** 10 phút | **Đối tượng:** Lãnh đạo, Đối tác, Nhà đầu tư
**Ngày:** 2026-05-09
**Lưu ý:** Không dùng术语 kỹ thuật. Focus giá trị kinh doanh.

---

## SETUP (T-30 phút trước demo)

Chạy auto-setup:
```bash
bash scripts/demo-setup.sh
```
Verify: mở http://localhost:3000 → thấy trang login.

---

## PHẦN 1: BÀI TOÁN (1 phút)

> "Thành phố có 8 hệ thống rời rạc — điện, nước, không khí, giao thông — không giao tiếp với nhau. Báo cáo ESG mất 3 ngày làm tay. Cảnh báo ô nhiễm phát hiện sau 4-6 giờ. Người dân không biết không khí khu vực mình đang ở mức nào."

> "UIP giải quyết tất cả trong 1 nền tảng duy nhất."

---

## PHẦN 2: TỔNG QUAN DASHBOARD (2 phút)

**Màn hình:** Login → Dashboard

1. **Login** bằng `admin` → Dashboard tổng quan hiện 4 KPI:
   - Sensors hoạt động: 8
   - AQI hiện tại: 105 (Unhealthy for Sensitive Groups)
   - Alerts mở: X alerts
   - Carbon footprint: 18.585 tCO₂e

> "Một màn hình duy nhất — lãnh đạo biết ngay thành phố đang 'khỏe' hay 'bệnh'."

2. **City Operations Center** — bản đồ HCMC:
   - 8 sensor markers, màu theo AQI (xanh→vàng→cam→đỏ)
   - Click marker → popup chi tiết

> "Mọi sensor đang streaming real-time. Từ cảm biến đến màn hình chỉ mất 30 giây."

---

## PHẦN 3: ESG — TỰ ĐỘNG HÓA BÁO CÁO (2 phút)

**Màn hình:** ESG Metrics → Generate Report

1. **ESG Dashboard**: 3 KPI cards — Energy 41.3k kWh, Water 9.6k m³, Carbon 18.585 tCO₂e
2. **Click "Generate ESG Report"** → download file XLSX ngay lập tức

> "Trước: 3 ngày tổng hợp thủ công. Sau: 1 click, 2 giây. Gửi ngay cho Sở TN&MT, nhà đầu tư ESG."

> "Với 2.4 triệu dòng dữ liệu, hệ thống vẫn trả kết quả trong 0.25 giây nhờ Redis cache + TimescaleDB."

---

## PHẦN 4: AI TỰ ĐỘNG PHẢN ỨNG SỰ CỐ (2 phút)

**Màn hình:** Alert Management → AI Workflow

1. **Alert "AQI Critical >150"** — hiện trên feed
2. **AI Workflow**: mở BPMN diagram, chỉ ra bước AI phân tích:
   - Detect → Claude AI quyết định → Gửi SMS → Phối hợp

> "AI phát hiện ô nhiễm, tự phân tích nguyên nhân, đề xuất hành động — tất cả trong 5 phút. Trước đây cần 30-45 phút gọi điện thủ công."

---

## PHẦN 5: CƯ DÂN (2 phút)

**Màn hình:** Citizen Portal (mobile viewport)

1. **Mobile PWA**: bottom navigation, bills, AQI, notifications
2. **Xem hóa đơn**: từng kWh, từng m³, bậc thang rõ ràng
3. **Push notification**: AQI alert tự động gửi đến điện thoại

> "Cư dân biết ngay không khí khu vực mình. Tranh cãi hóa đơn giảm 60% vì minh bạch từng chỉ số."

---

## PHẦN 6: ROI & ROADMAP (1 phút)

| Tier | Khách hàng | ROI | Giá |
|------|-----------|-----|-----|
| T1 — Tòa nhà | Chủ đầu tư chung cư | 4x | 150M + 50M/năm |
| T2 — Khu đô thị | Ban quản lý | 7.75x | 400M + 150M/năm |
| T3 — Quận/Huyện | UBND quận | 23x | 2B + 600M/năm |
| T4 — Thành phố | UBND TP | 10x | 50B + 10B/năm |

> "MVP2 hoàn thành — multi-tenant, cache 11x faster, PWA mobile, 15/15 UAT pass, 0 lỗ hổng bảo mật. Sẵn sàng triển khai khách hàng đầu tiên."

---

## Q&A CHUẨN BỊ SẴN

| Câu hỏi | Trả lời |
|---------|---------|
| "Bảo mật thế nào?" | OWASP Top 10 audit 0 Critical. Vault secrets, RLS multi-tenant, JWT RBAC 4 roles |
| "Scale được bao nhiêu?" | 1 instance: 80 VU ổn định. 3 replicas: 500+ VU. ClickHouse cho 50K+ sensors |
| "Tích hợp hệ thống cũ?" | REST API + Kafka. Tích hợp 1 ngày với bất kỳ hệ thống nào có HTTP/MQTT |
| "Offline được không?" | PWA offline mode cho citizen — xem hóa đơn kể cả mất mạng |
| "Bao lâu triển khai?" | Tier 1: 2 tuần. Tier 2: 1 tháng. Tier 3: 3 tháng |

---

*Script dành cho demo executive — không đòi hỏi kiến thức kỹ thuật từ người xem*
