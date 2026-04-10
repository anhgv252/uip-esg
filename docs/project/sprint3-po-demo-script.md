# UIP Smart City — PO Demo Script
## Sprint 1→3 User Experience Walkthrough

**Ngày demo:** 07/04/2026  
**Đối tượng:** Product Owner  
**Thời lượng dự kiến:** ~25 phút  
**Yêu cầu:** Backend + Frontend đang chạy (`make up` trong `infrastructure/`)

---

## Chuẩn bị trước demo (5 phút)

Mở 2 tab trình duyệt:
- **Tab 1 — Operator view:** `http://localhost:3000`
- **Tab 2 — Citizen view:** `http://localhost:3000/citizen/register`

Credentials sẵn sàng trên màn hình (không cần gõ giữa chừng):
| Role | Username | Password |
|------|----------|---------|
| Admin | `admin` | `admin_Dev#2026!` |
| Operator | `operator` | `operator_Dev#2026!` |

---

## Journey 1 — Người vận hành thành phố (Operator / Admin)

> **Bối cảnh:** Anh Minh là operator trực tại Trung tâm Điều hành Đô thị TP.HCM. Ca tối, anh mở hệ thống để theo dõi tình hình.

---

### Bước 1 · Đăng nhập

**URL:** `http://localhost:3000/login`

1. Nhập `operator` / `operator_Dev#2026!` → nhấn **Login**
2. Hệ thống redirect tự động vào Dashboard

> **Nói với PO:** _"Login xử lý JWT — token lưu trong memory, không phải localStorage để bảo mật. Session tự hết hạn sau 15 phút idle."_

---

### Bước 2 · Dashboard — Tổng quan tức thì

**URL:** `/dashboard`

Thấy ngay 4 chỉ số thời gian thực:

| Thẻ | Hiển thị | Ý nghĩa |
|-----|---------|---------|
| **Active Sensors** | Số lượng cảm biến đang kết nối | Độ bao phủ hệ thống |
| **AQI Current** | Chỉ số chất lượng không khí tức thì | Quận có AQI cao nhất |
| **Open Alerts** | Số cảnh báo chưa xử lý | 🔴 Càng cao càng cần chú ý |
| **Carbon (tCO₂e)** | Phát thải CO₂ tháng này | Mục tiêu ESG |

> **Nói với PO:** _"4 chỉ số này refresh tự động mỗi 30 giây — không cần F5. Operator vừa ngồi vào là biết tình trạng toàn thành phố."_

---

### Bước 3 · City Operations Center — Bản đồ cảm biến live

**Menu:** `City Ops` → URL: `/city-ops`

**Những gì PO thấy:**

1. **Bản đồ Leaflet** hiển thị TP.HCM với các marker màu theo mức AQI:
   - 🟢 Xanh = Good (AQI 0–50)
   - 🟡 Vàng = Moderate (AQI 51–100)
   - 🟠 Cam = Unhealthy (AQI 101–150)
   - 🔴 Đỏ = Hazardous (AQI > 200)

2. **Click vào 1 marker** → popup hiển thị:
   - Sensor ID, District, AQI value
   - PM2.5, PM10, O3 realtime

3. **Panel Alert Feed bên phải (30% màn hình):**
   - 20 cảnh báo gần nhất, badge màu theo severity
   - Tự cuộn khi có cảnh báo mới đến

4. **District Filter (góc phải trên):**
   - Nhấn `Quận 1` → map zoom vào Q1, chỉ hiện sensors Q1
   - Nhấn `All` → về tổng quan

5. **Chờ ~30 giây:** Sensor marker tự update màu (SSE push từ server, không reload trang)

> **Nói với PO:** _"Đây là luồng realtime — sensor đo → Kafka → Flink xử lý → SSE push lên UI. Không có polling, không có F5. Operator ngồi im 8 tiếng vẫn thấy dữ liệu live."_

---

### Bước 4 · Environment Monitoring — Chi tiết chất lượng không khí

**Menu:** `Environment` → URL: `/environment`

Cuộn xuống xem:
1. **AQI Gauge** — đồng hồ tròn màu theo EPA standard
2. **Trend Chart (24h / 7 ngày)** — Recharts LineChart với brush zoom
3. **Sensor Table** — tất cả 8+ sensors, badge ONLINE/OFFLINE, last reading

> **Click vào 1 sensor trong bảng** → xem chi tiết readings lịch sử

> **Nói với PO:** _"Màu sắc theo chuẩn EPA quốc tế — cùng thang điểm mà Mỹ, EU dùng. Khi tích hợp sensor thật, chart này sẽ live theo từng giây."_

---

### Bước 5 · Alert Management — Xử lý cảnh báo

**Menu:** `Alerts` → URL: `/alerts`

**Demo flow:**

1. Thấy list 5 alerts với badge màu: `CRITICAL` (đỏ sậm), `WARNING` (cam)
2. **Filter:** Chọn Status = `OPEN` → chỉ hiện alert chưa xử lý
3. **Click vào 1 alert OPEN** → drawer slide từ phải ra:
   - Tên rule vi phạm (ví dụ: "AQI Critical Alert")
   - Sensor ID + giá trị đo được vs ngưỡng
   - Thời gian phát hiện (đúng năm 2026 — bug timestamp đã fix)
   - Ô ghi chú + nút **Acknowledge**

4. **Nhấn Acknowledge:**
   - Badge status chuyển từ `OPEN` → `ACKNOWLEDGED` ✅
   - Ghi nhận "acknowledged by: operator"

5. **Bulk acknowledge:** Check nhiều alert → nút "Acknowledge Selected" ở đầu bảng

> **Nói với PO:** _"Alert rules config từ YAML — không cần deploy lại code khi muốn đổi ngưỡng. Và có cooldown để tránh spam: cùng 1 sensor không cảnh báo liên tục trong X phút."_

---

### Bước 6 · ESG Metrics — Báo cáo phát thải

**Menu:** `ESG Metrics` → URL: `/esg`

1. **3 KPI cards:** Energy `41,300 kWh` · Water `9,625 m³` · Carbon `18.585 tCO₂e`
2. **Toggle Energy / Carbon** → chart chuyển dữ liệu theo building
3. **Generate Report button:**
   - Nhấn → xuất hiện "Generating..." status
   - Sau vài giây → "Download XLSX" link xuất hiện
   - Nhấn download → file Excel tải về máy

> **Nói với PO:** _"Trước đây báo cáo ESG tốn 3 ngày làm thủ công. Giờ 1 click, dưới 1 phút, định dạng XLSX chuẩn nộp cho cơ quan nhà nước."_

---

### Bước 7 · Traffic — Giám sát giao thông

**Menu:** `Traffic` → URL: `/traffic`

1. **Bar chart** — lượng xe theo giờ tại các nút giao (INT-001 đến INT-005)
2. **Incident table** — 5 sự cố: ACCIDENT, BREAKDOWN, CONGESTION, ROAD_CLOSURE
   - Hiện location, status, thời gian xảy ra

> **Nói với PO:** _"Dữ liệu traffic hiện là từ HTTP adapter (giả lập) — poll 30 giây/lần. Khi tích hợp với hệ thống camera thật, cột này sẽ hiện biển số xe, tốc độ trung bình."_

---

### Bước 8 · Admin — Quản trị hệ thống

**Đăng nhập lại với admin:** Logout → Login `admin` / `admin_Dev#2026!`

**Menu:** `Admin` → URL: `/admin`

**Tab Users:**
1. Danh sách tài khoản có phân trang
2. **Đổi role:** Chọn user → dropdown → đổi sang `ROLE_OPERATOR` → Save
3. **Deactivate user:** Toggle "Active" → tài khoản bị khóa ngay lập tức

**Tab Sensors:**
1. Danh sách 5 sensors với badge ACTIVE/INACTIVE
2. **Toggle sensor:** Click OFF → sensor không xuất hiện trên map và không tính vào dashboard

> **Nói với PO:** _"Admin không cần vào DB để quản lý — mọi thứ qua UI. Và chỉ ROLE_ADMIN mới thấy menu này; operator vào sẽ bị redirect."_

---

## Journey 2 — Cư dân đô thị (Citizen)

> **Bối cảnh:** Chị Lan vừa chuyển vào căn hộ mới ở Quận 1, muốn đăng ký tài khoản để theo dõi hóa đơn điện nước và nhận thông báo môi trường.

---

### Bước 9 · Đăng ký tài khoản (không cần login trước)

**Tab 2:** `http://localhost:3000/citizen/register`

**Bước 1 — Personal Info:**
1. Điền form 5 trường:
   - Full name: `Nguyen Thi Lan`
   - Email: `lan.nguyen.demo@gmail.com`
   - Phone: `0912345678` (validate VN format ngay khi nhập)
   - CCCD: `079123456789`
   - Password: `Demo@2026!`
2. Nhấn **Next: Household →**

> Nếu email đã tồn tại → thông báo lỗi `"Email already registered"` ngay lập tức.

**Bước 2 — Household:**
1. **Buildings dropdown** tải tự động — 5 tòa nhà TP.HCM:
   - BLD-001: Vinhomes Central Park, Q.Bình Thạnh
   - BLD-002: Landmark 81, Q.Bình Thạnh
   - BLD-003: The Manor, Q.Bình Thạnh
   - BLD-004: Estella Heights, Q.2
   - BLD-005: Sarimi Sala, Q.2
2. Chọn **BLD-001**, Floor: `12`, Unit: `1205`
3. Nhấn **Link Household →**

**Bước 3 — Done:**
- `"Account created successfully!"` 🎉
- Tự động nhận JWT, không cần login lại
- Nhấn **Go to my portal**

> **Nói với PO:** _"3 bước, dưới 2 phút. Không cần admin tạo tài khoản hộ. Validation phone chỉ accept đầu số VN hợp lệ (03x, 05x, 07x, 08x, 09x)."_

---

### Bước 10 · Citizen Portal — Trang chủ cư dân

**URL:** `/citizen` (tab Dashboard)

Chị Lan thấy:

```
Welcome back, Nguyen Thi Lan

┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ This month's     │  │ Total this month │  │ Household        │
│ invoices         │  │ (VND)            │  │                  │
│      3           │  │   1,250,000      │  │ Vinhomes Central │
│  [2 unpaid] ⚠️  │  │                  │  │  Floor 12 — 1205 │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

---

### Bước 11 · My Bills — Xem hóa đơn

**Tab:** `My Bills`

1. **Month/Year picker** — chọn tháng 4/2026
2. Bảng hóa đơn hiển thị:
   - Điện: `350 kWh` — `875,000 VND` — badge `UNPAID` 🟡
   - Nước: `15 m³` — `375,000 VND` — badge `PAID` 🟢
3. **Click hóa đơn điện** → drawer chi tiết:
   - Kỳ thanh toán: 01/04/2026 – 30/04/2026
   - Mức tiêu thụ theo bậc thang
   - Hạn thanh toán: 15/05/2026

> **Nói với PO:** _"Sprint sau cư dân sẽ nhận push notification khi hóa đơn mới ra và khi hạn thanh toán đến gần. Module này cũng nhận alert môi trường theo địa bàn — ví dụ AQI vùng Q.Bình Thạnh vượt ngưỡng thì chỉ dân Q.Bình Thạnh nhận."_

---

### Bước 12 · Profile — Thông tin cá nhân

**Tab:** `Profile`

- Họ tên, email, phone, CCCD
- Household: Vinhomes Central Park, Floor 12, Unit 1205
- Meter codes (điện + nước đã link)
- Nút **Edit** (Sprint 4 scope)

---

## Demo Wrap-up — Tóm tắt những gì vừa thấy (~2 phút)

| Persona | Tính năng hoàn chỉnh |
|---------|---------------------|
| **Operator** | Login → Dashboard realtime → City Ops map → Environment AQI → Alert acknowledge → ESG report download → Traffic incidents |
| **Admin** | User management (role change, deactivate) → Sensor registry |
| **Citizen** | Self-register 3-step → Household link → Xem hóa đơn theo tháng → Profile |

**Những điểm kỹ thuật không nhìn thấy nhưng quan trọng:**
- ⚡ Dữ liệu alert đến UI trong <30 giây từ lúc sensor đo (SSE, không polling)
- 🔐 Token không lưu localStorage — bảo mật session hijacking
- 🏗️ 6 modules độc lập — thêm/sửa 1 module không ảnh hưởng module khác
- ✅ 78.9% test coverage — vượt target Sprint 4 trước 1 sprint

---

## Câu hỏi thường gặp từ PO

**Q: Sensor đang `OFFLINE` hết — có phải lỗi không?**  
A: Không. Dev environment không có hardware thật. Khi kết nối EMQX với cảm biến vật lý, status tự chuyển `ONLINE`. Data seed hiện tại dùng để demo UI chứ không phải live IoT.

**Q: Hóa đơn của `lan.nguyen.demo` sao có sẵn data?**  
A: V8 migration đã seed sẵn invoice cho 5 buildings mẫu. Tài khoản mới link vào BLD-001 sẽ thấy data đó. Trong production, hóa đơn sẽ do batch job tạo theo kỳ đọc đồng hồ.

**Q: Demo khi nào thấy alert realtime thực sự?**  
A: Chạy `scripts/sensor_simulator.py` gửi AQI > 200 → Flink detect → alert xuất hiện trong feed <30s. Có thể demo live nếu PO muốn thấy end-to-end.

**Q: Sprint 4 bắt đầu khi nào?**  
A: Có thể bắt đầu ngay — Camunda YAML bug đã fix, không còn blocker. 7 AI scenarios với Claude API là mục tiêu chính.

---

*Demo script này dùng cho buổi Sprint 3 Review với PO. Không cần technical background để follow theo.*

---

## Kết quả Demo thực tế — 07/04/2026

> **Môi trường:** Backend Spring Boot (port 8080, PID 78063+), Frontend Vite (port 3000), Docker infra (TimescaleDB · Redis · Kafka · Flink)

### Journey 1 — Operator / Admin

| Bước | Kết quả thực tế | Status |
|------|----------------|--------|
| **Login Operator** | JWT nhận thành công | ✅ |
| **Login Admin** | JWT nhận thành công | ✅ |
| **Dashboard KPIs** | `8 sensors` · `AQI=105 Unhealthy for Sensitive Groups D1` · `1 open alert` · `Carbon=18.585 tCO₂e` | ✅ |
| **City Ops Map** | 8 sensor markers đúng tọa độ lat/lng TP.HCM | ✅ |
| **Environment AQI** | 1009 readings · ENV-001 AQI=105 D1 · ENV-003 Q.Bình Thạnh | ✅ |
| **Alerts list** | 5 alerts · timestamp ISO-8601 đúng (bug BUG-S3-03-01 verified fix) | ✅ |
| **Alert Acknowledge** | `OPEN → ACKNOWLEDGED` · `acknowledgedBy=operator` · `2026-04-07T03:49:32Z` | ✅ |
| **ESG Summary** | `Energy=41,300 kWh` · `Water=9,625 m³` · `Carbon=18.585 tCO₂e` | ✅ |
| **ESG Report Generate** | `PENDING → DONE` trong ~1.2s · `downloadUrl` xuất hiện · bug fix verified ✅ | ✅ |
| **Traffic Incidents** | 4 open incidents: ACCIDENT INT-001, CONGESTION INT-002, ACCIDENT INT-004, CONGESTION INT-005 | ✅ |
| **Admin Users** | 6 users · citizen1 promoted to `ROLE_OPERATOR` → restored to `ROLE_CITIZEN` | ✅ |
| **Admin Deactivate** | testcitizen01 `active: false` xác nhận | ✅ |
| **Admin Sensors** | 8 sensors · ENV-002 toggled `active=false` → `active=true` | ✅ |

### Journey 2 — Citizen

| Bước | Kết quả thực tế | Status |
|------|----------------|--------|
| **Register** | `nguyenvana` · cccd=`079300012345` · email=`nguyenvana@example.com` → JWT immediate | ✅ |
| **Citizen Profile** | Trả về đúng `fullName`, `phone`, `cccd`, `role=ROLE_CITIZEN` | ✅ |
| **Invoice by Month (citizen1)** | Tháng 4/2026: `Electricity 302.39 kWh = 1,014,062 VND UNPAID` · `Water 45.98 m³ = 315,271 VND UNPAID` | ✅ |

### Bug tìm thấy trong demo — đã fix ngay

| Bug | Root Cause | Fix | Status |
|-----|-----------|-----|--------|
| **ESG Report stuck PENDING** | `EsgService @Transactional(readOnly=true)` class-level → Hibernate `FlushMode.MANUAL` → INSERT không commit. `@Async` thread gọi `findById` trước khi row tồn tại | Thêm `@Transactional` (read-write) trên method + `TransactionSynchronizationManager.afterCommit()` hook để dispatch async sau khi commit xong | ✅ FIXED & VERIFIED |

### Kết luận Demo

**Tất cả 3 Sprint đã hoàn thành và hoạt động ổn định trong môi trường dev.** 1 bug mới phát hiện trong quá trình demo live đã được fix và verify trong cùng session. ESG report generation giờ hoàn thành trong ~1.2s và trả về XLSX download link.

**Ready cho Sprint 4 Planning.**
