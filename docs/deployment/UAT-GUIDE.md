# UIP Smart City — Hướng dẫn UAT

**Phiên bản:** Sprint 4 · Ngày cập nhật: 2026-04-22  
**Dành cho:** City Authority Demo Team & PM

---

## Mục lục

1. [Yêu cầu hệ thống](#1-yêu-cầu-hệ-thống)
2. [Chuẩn bị môi trường](#2-chuẩn-bị-môi-trường)
3. [Khởi động stack](#3-khởi-động-stack)
4. [Load demo data](#4-load-demo-data)
5. [Thông tin đăng nhập](#5-thông-tin-đăng-nhập)
6. [URLs truy cập](#6-urls-truy-cập)
7. [Luồng demo chính](#7-luồng-demo-chính)
8. [Dừng stack](#8-dừng-stack)
9. [Reset dữ liệu](#9-reset-dữ-liệu)
10. [Xử lý sự cố](#10-xử-lý-sự-cố)

---

## 1. Yêu cầu hệ thống

| Thành phần   | Phiên bản tối thiểu | Ghi chú                                  |
|--------------|---------------------|------------------------------------------|
| Docker       | 24.x                | `docker --version`                       |
| Docker Compose | 2.20.x            | Bundled với Docker Desktop               |
| RAM          | **16 GB**           | Stack chiếm ~10 GB khi full load         |
| CPU          | 4 cores             | Flink cần ít nhất 2 cores               |
| Disk         | 20 GB free          | DB data + Kafka logs + Docker images     |
| Python       | 3.9+                | Cho seed script (`python3`)              |
| pip package  | psycopg2-binary     | `pip3 install psycopg2-binary`           |

**OS được kiểm thử:** macOS 14+, Ubuntu 22.04 LTS, Windows 11 (Docker Desktop WSL2)

---

## 2. Chuẩn bị môi trường

### 2.1. Clone / lấy code

```bash
cd uip-esg-poc/infrastructure
```

### 2.2. Tạo file `.env.uat`

```bash
cp .env.uat.example .env.uat
```

Mở `.env.uat` và điền các giá trị:

| Biến                  | Mô tả                                        | Bắt buộc thay |
|-----------------------|----------------------------------------------|---------------|
| `POSTGRES_PASSWORD`   | Mật khẩu DB (min 16 ký tự)                  | ✅            |
| `REDIS_PASSWORD`      | Mật khẩu Redis                               | ✅            |
| `EMQX_DASHBOARD_PASSWORD` | Mật khẩu dashboard EMQX                 | ✅            |
| `JWT_SECRET`          | Secret key JWT (min 64 hex chars)            | ✅            |
| `CLAUDE_API_KEY`      | API key Anthropic (cho AI scenarios)         | ✅ nếu demo AI |
| `VITE_API_BASE_URL`   | URL backend từ trình duyệt demo              | ✅ nếu demo từ xa |

> **Lưu ý:** `VITE_API_BASE_URL` phải là URL mà **máy demo participants** có thể truy cập.  
> Ví dụ nếu demo qua LAN: `http://192.168.1.50:8080`

### 2.3. Cài Python dependency

```bash
pip3 install psycopg2-binary
```

---

## 3. Khởi động stack

```bash
make uat-up
```

Lệnh này sẽ:
1. Kiểm tra `.env.uat` tồn tại
2. Build và khởi động tất cả services với resource limits
3. Poll health status trong tối đa 3 phút

**Thời gian khởi động bình thường:**

| Giai đoạn              | Thời gian      |
|------------------------|----------------|
| Pull/build images      | 2-5 phút (lần đầu) |
| TimescaleDB + Redis up | 30-45 giây     |
| Kafka broker ready     | 45-60 giây     |
| Backend (Flyway + Spring Boot) | 60-90 giây |
| Flink JobManager ready | 30-45 giây     |
| Frontend (Nginx)       | 10-15 giây     |
| **Tổng cộng**          | **~3-4 phút**  |

Khi hoàn thành, màn hình sẽ hiển thị:
```
=== UAT stack is up ===
  Frontend: http://localhost:3000
  Backend:  http://localhost:8080
  Flink UI: http://localhost:8081
```

### Kiểm tra trạng thái services

```bash
make uat-ps
```

Tất cả services phải ở trạng thái `healthy` hoặc `running`.

---

## 4. Load demo data

```bash
make seed-uat
```

Script này insert (idempotent — chạy nhiều lần an toàn):
- **50 buildings** phân bổ trên 20 quận TPHCM
- **100 sensors** (40 AQI + 30 Traffic + 20 Water Quality + 10 Noise)
- **3 citizen accounts** với meter và 6 tháng invoices + lịch sử tiêu thụ
- **6 tháng ESG metrics** cho 30 buildings
- **40 alert events** mẫu trong 7 ngày qua

Kết quả seed sẽ in summary table:
```
UAT DATA SUMMARY
  Citizens buildings              50
  Environment sensors            108  (8 từ Flyway + 100 UAT)
  Citizen accounts                 3
  Invoices                        36
  Consumption records           1086  (3 citizens × 2 meters × 181 days)
  ESG metrics (last 6mo)      16290  (30 buildings × 181 days × 3 types)
  Alert events (last 7d)          40
```

### Verify data

```bash
# Kiểm tra health
curl -s http://localhost:8080/api/v1/health | python3 -m json.tool

# Smoke test tự động (10 test cases)
make uat-test
```

---

## 5. Thông tin đăng nhập

### Tài khoản hệ thống

| Role     | Username   | Password          | Quyền                                  |
|----------|------------|-------------------|----------------------------------------|
| Admin    | `admin`    | `admin_Dev#2026!` | Toàn quyền: users, sensors, workflow  |
| Operator | `operator` | `operator_Dev#2026!` | Xem dashboard, xử lý alerts        |
| Citizen  | `citizen1` | `citizen_Dev#2026!` | Citizen portal: invoices, meters    |
| Citizen  | `citizen2` | `citizen_Dev#2026!` | Citizen portal                      |
| Citizen  | `citizen3` | `citizen_Dev#2026!` | Citizen portal                      |

### Management UIs (nội bộ — không demo ra ngoài)

| Service      | URL                      | Credentials                         |
|--------------|--------------------------|--------------------------------------|
| Flink Web UI | http://localhost:8081    | Không cần auth                       |
| Kafka UI     | http://127.0.0.1:8090   | Không cần auth (chỉ localhost)       |
| EMQX Dashboard | http://127.0.0.1:18083 | admin / `EMQX_DASHBOARD_PASSWORD` từ .env.uat |

> **Security note:** Kafka UI và EMQX Dashboard đều bind `127.0.0.1` trong UAT mode — không thể truy cập từ máy khác trên mạng.

---

## 6. URLs truy cập

| Màn hình                   | URL                                           | Account cần |
|----------------------------|-----------------------------------------------|-------------|
| Trang chủ / Login          | http://localhost:3000                         | —           |
| City Operations Center     | http://localhost:3000/dashboard/city-ops      | operator    |
| ESG Analytics Dashboard    | http://localhost:3000/dashboard/esg           | admin       |
| Environmental Monitoring   | http://localhost:3000/dashboard/environment   | operator    |
| Traffic Management         | http://localhost:3000/dashboard/traffic       | operator    |
| AI Workflow Dashboard      | http://localhost:3000/dashboard/ai-workflow   | admin       |
| Citizen Portal             | http://localhost:3000/citizen                 | citizen1    |
| Admin Panel                | http://localhost:3000/admin                   | admin       |

---

## 7. Luồng demo chính

### Luồng 1 — City Operations Center (City Manager)

1. Login với `operator`
2. Mở **City Operations Center** (`/dashboard/city-ops`)
3. Map TPHCM với 108 sensor markers, màu sắc theo AQI level
4. Panel bên phải: danh sách alert events mới nhất
5. Click sensor marker → xem sensor detail + realtime chart

### Luồng 2 — AI Workflow (AQI Alert)

1. Login với `admin`
2. Mở **AI Workflow Dashboard** (`/dashboard/ai-workflow`)
3. Xem workflow instances đang chạy
4. Chạy simulator để trigger AQI alert:
   ```bash
   # Từ thư mục gốc project:
   python3 scripts/sensor_simulator.py --once
   ```
5. Quan sát workflow instance được tạo tự động, AI phân tích, quyết định alert level

### Luồng 3 — ESG Report Generation

1. Login với `admin`
2. Mở **ESG Dashboard** (`/dashboard/esg`)
3. Xem metrics 6 tháng cho 30 buildings
4. Click **Generate Report** → chọn Quarter → submit
5. Theo dõi status từ PENDING → IN_PROGRESS → COMPLETED
6. Download PDF report

### Luồng 4 — Citizen Portal (Hóa đơn)

1. Login với `citizen1`
2. Mở **Citizen Portal** (`/citizen`)
3. Xem danh sách hóa đơn điện/nước 6 tháng
4. Click hóa đơn → xem chi tiết + biểu đồ tiêu thụ
5. Demo submission complaint/request

### Luồng 5 — Alert Management

1. Login với `operator`
2. Mở **Environmental Monitoring** → tab Alerts
3. Xem 40 alert events đã seed
4. Click alert → **Acknowledge** → nhập ghi chú
5. Xem status thay đổi: OPEN → ACKNOWLEDGED

---

## 8. Dừng stack

### Dừng nhưng giữ data (resume sau):
```bash
make uat-down
```

### Dừng và xóa toàn bộ data (reset hoàn toàn):
```bash
make uat-clean
# Lệnh này sẽ chờ 5 giây trước khi xoá — nhấn Ctrl+C để huỷ
```

---

## 9. Reset dữ liệu

Để reset về trạng thái ban đầu và seed lại:

```bash
# 1. Dừng và xóa toàn bộ volumes (sẽ hỏi confirm 5 giây)
make uat-clean

# 2. Khởi động lại (Flyway tự chạy lại migrations V1..V11)
make uat-up

# 3. Load demo data
make seed-uat
```

> Flyway migrations (`V1__` đến `V11__`) sẽ tự động tạo lại schema + seed data cơ bản.  
> `make seed-uat` bổ sung 100 sensors, 50 buildings, 6-month history.

---

## 10. Xử lý sự cố

### Backend không start (Flyway error)

```bash
make uat-logs svc=backend | tail -50
```

Nguyên nhân thường gặp:
- `POSTGRES_PASSWORD` sai trong `.env.uat`
- TimescaleDB chưa ready → backend restart tự động

### Kafka không healthy

```bash
make uat-logs svc=kafka | tail -30
```

Nếu gặp `CLUSTER_ID mismatch`:
```bash
# Xóa volume và restart
docker volume rm infrastructure_kafka_data
make uat-up
```

### Flink job không submit

Kiểm tra `flink-jobs/target/` có file `.jar`:
```bash
ls ../flink-jobs/target/*.jar 2>/dev/null || echo "No Flink JAR found"
```

Nếu không có JAR: Flink services vẫn healthy, chỉ không có job nào chạy.  
Điều này không ảnh hưởng đến demo các tính năng khác.

### Seed script lỗi kết nối DB

```bash
# Kiểm tra DB accessible:
docker exec uip-timescaledb psql -U uip_uat -d uip_smartcity_uat -c "SELECT version();"

# Chạy seed trực tiếp với env:
DB_HOST=localhost DB_PORT=5432 \
  DB_NAME=uip_smartcity_uat \
  DB_USER=uip_uat \
  DB_PASSWORD=<giá_trị_trong_.env.uat> \
  python3 ../scripts/seed_uat_data.py
```

### Xem logs tất cả services

```bash
make uat-logs
# Ctrl+C để thoát
```

### Ports đang bị chiếm

Kiểm tra ports: 3000, 5432, 6379, 8080, 8081, 8090, 9092, 1883, 18083

```bash
lsof -i :3000 -i :8080 -i :5432 | head -20
```

### Giải phóng tài nguyên (nếu thiếu RAM)

Tắt Flink nếu không demo AI workflows:
```bash
docker compose --env-file .env.uat \
  -f docker-compose.yml -f docker-compose.uat.yml \
  stop flink-jobmanager flink-taskmanager
```

---

## Liên hệ hỗ trợ

- **Kỹ thuật:** Be-1 / Ops-1 (xem sprint board)
- **Demo coordination:** PM-1
- **Slack:** `#uip-demo-support`
