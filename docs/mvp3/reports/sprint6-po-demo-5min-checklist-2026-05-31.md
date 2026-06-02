# Sprint 6 — 5-Minute Live Demo Checklist

**Date:** 2026-05-31  
**Audience:** PO / Stakeholders  
**Demo Goal:** Xác nhận luồng local demo hoạt động ổn định cho các màn hình trọng tâm và có tiêu chí go/no-go rõ ràng cho Energy Forecast.

## 1) Chuẩn bị trước khi share màn hình (60s)

1. Mở sẵn tab UI: `http://localhost:3000/login`
2. Mở sẵn terminal API (backup evidence):
   - Login: `POST /api/v1/auth/login`
   - Alerts: `GET /api/v1/alerts`
   - Carbon: `GET /api/v1/esg/carbon?year=2025`
3. Đăng nhập bằng:
   - Username: `admin`
   - Password: `admin_Dev#2026!`

Expected:
- Redirect vào `/dashboard`.

4. Forecast go/no-go gate (bắt buộc trước demo full):
  - Gọi API forecast ít nhất 1 building hợp lệ.
  - Chỉ demo full nếu `points.length > 0`.
  - Nếu `points=[]`, chuyển sang chế độ demo có giới hạn (không claim forecast live).

## 2) Walkthrough 5 phút

### Minute 0-1: Dashboard
- URL: `http://localhost:3000/dashboard`
- Nói khi demo:
  - "Đây là dashboard tổng quan vận hành thành phố thông minh."
  - "Các KPI và dữ liệu đang được lấy từ backend thật."
- Xác nhận tối thiểu:
  - Page load thành công.
  - KPI cards hiển thị (Active Sensors, Open Alerts, Carbon).

### Minute 1-2: Alerts
- URL: `http://localhost:3000/alerts`
- Nói khi demo:
  - "Màn hình quản lý cảnh báo có severity/status, lọc và danh sách sự kiện."
- Xác nhận tối thiểu:
  - Header: Alert Management.
  - Danh sách alert render.
  - Trạng thái có thể hiển thị `Offline` nếu stream backend tạm gián đoạn.

### Minute 2-3: BMS Devices
- URL: `http://localhost:3000/bms/devices`
- Nói khi demo:
  - "Module BMS đã có trang quản lý thiết bị và thao tác mở rộng."
- Xác nhận tối thiểu:
  - Header: BMS Devices.
  - Có nút Add Device.

### Minute 3-4: AI Workflow
- URL: `http://localhost:3000/ai-workflow`
- Nói khi demo:
  - "AI Workflow dashboard đã có các tab Process Instances / Definitions / Live Demo."
- Xác nhận tối thiểu:
  - Tab và bảng dữ liệu hiển thị.

### Minute 4-5: ESG Metrics
- URL: `http://localhost:3000/esg`
- Nói khi demo:
  - "ESG module hiển thị trend theo building và chức năng generate report."
  - "Phần Energy Forecast hiện đang ở trạng thái degraded nếu API trả empty points."
- Xác nhận tối thiểu:
  - Header: ESG Metrics.
  - Trend chart hiển thị.
  - Khu vực Generate ESG Report hiển thị được thao tác.
  - Energy Forecast:
    - Nếu API có points: demo bình thường.
    - Nếu API rỗng: show rõ trạng thái và tham chiếu bug report.

## 3) API Evidence Script (backup trong lúc demo)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' \
  | /usr/bin/python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/alerts
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/esg/carbon?year=2025"
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/forecast/energy?buildingId=65c06d23-3cf3-4490-96a6-ac8ff2a17f2c&horizonDays=30"
```

Expected:
- Login trả 200 + accessToken.
- Alerts API trả 200.
- Carbon API trả 200.
- Forecast API trả 200 và phải có `points.length > 0` để qualify demo full forecast.

## 4) Known Demo Notes (có thể chặn demo full)

1. `GET /api/v1/alerts/stream` có thể trả 504 gián đoạn -> UI có thể hiện `Offline` tại Alerts.
2. Điều này không chặn luồng demo chính nếu các trang và API cốt lõi vẫn trả dữ liệu.
3. Dùng backup API evidence để xác nhận hệ thống core vẫn hoạt động.
4. Energy Forecast hiện có bug P1 nếu API trả `model=NONE, isFallback=true, points=[]`.
5. Bug reference: `docs/mvp3/testing/bug-energy-forecast-empty-points-2026-05-31.md`.

## 5) Demo Exit Criteria

- [x] Login thành công vào dashboard
- [x] Mở được 4 màn hình trọng tâm: Alerts, BMS, AI Workflow, ESG
- [x] Có backup API evidence cho auth + alerts + esg carbon
- [ ] Forecast gate pass (`points.length > 0`)
- [ ] Không có P0/P1 bug trong luồng đã demo

**Verdict:** CONDITIONAL READY

- Ready cho core flow demo: YES
- Ready cho full ESG forecast demo: NO (đến khi forecast gate pass)
