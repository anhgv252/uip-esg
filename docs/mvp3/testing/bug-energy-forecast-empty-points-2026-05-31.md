## BUG: Energy Forecast trả dữ liệu rỗng (points=[]) trên ESG page
**Date**: 2026-05-31  
**Tester**: Manual Tester (Copilot)  
**Severity**: P1  
**Module**: esg-module / forecast API / frontend ESG  
**Environment**: local

### Steps to Reproduce
1. Login UI tại http://localhost:3000/login với user `admin`.
2. Mở trang http://localhost:3000/esg.
3. Quan sát section Energy Forecast (đã chọn building mặc định).
4. Song song gọi API:
   - POST http://localhost:8080/api/v1/auth/login để lấy token
   - GET http://localhost:8080/api/v1/forecast/energy?buildingId=65c06d23-3cf3-4490-96a6-ac8ff2a17f2c&horizonDays=30
5. Lặp lại với nhiều building IDs lấy từ GET /api/v1/buildings (header `X-Tenant-ID: hcm`).

### Expected
- API trả dữ liệu forecast khả dụng (ít nhất 1 điểm) cho building hợp lệ:
  - `points.length > 0`
  - model dự báo hợp lệ (ví dụ ARIMA/naive) không rỗng
- UI hiển thị biểu đồ/danh sách forecast thay vì trạng thái empty.

### Actual
- UI hiển thị: `No forecast data available`.
- API trả 200 nhưng payload rỗng:
  - `model = NONE`
  - `isFallback = true`
  - `points = []`
- Kiểm tra 4/4 building IDs đều trả `points=[]`.

### Evidence
- UI state: ESG page -> Energy Forecast -> "No forecast data available".
- API response snippet:
```json
{
  "model": "NONE",
  "isFallback": true,
  "points": []
}
```
- Building list call cần tenant header:
  - `GET /api/v1/buildings` không có `X-Tenant-ID` -> 400
  - Có `X-Tenant-ID: hcm` -> 200

### Frequency
Always (10/10)

### Impact
- Demo ESG Forecast không đạt acceptance criterion về hiển thị dữ liệu dự báo.
- Giảm độ tin cậy demo vì phần forecast là trọng tâm của ESG analytics.

### Suggested Fix Direction
1. Backend forecast service:
   - Kiểm tra pipeline tạo forecast points cho building hợp lệ.
   - Nếu fallback mode, vẫn cần trả tối thiểu N điểm dự báo baseline thay vì rỗng.
2. API observability:
   - Thêm endpoint trạng thái forecast (`/api/v1/forecast/health` hoặc tương đương) để demo và vận hành.
3. Frontend UX:
   - Phân biệt rõ "No data" vs "Forecast service degraded" với thông báo actionable.
