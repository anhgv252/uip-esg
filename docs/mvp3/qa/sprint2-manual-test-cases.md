# Sprint 2 — Manual Test Cases

**Module:** Analytics Dashboard, Flink Enrichment, Analytics Service Cutover  
**Sprint:** MVP3 Sprint 2  
**Tester:** ___________  
**Execution Date:** ___________  
**Environment:** Local Docker Compose (single-node)

---

## Pre-conditions chung (áp dụng cho tất cả TC)

1. Docker Compose đang chạy đầy đủ:
   ```
   cd infra && docker compose up -d
   ```
2. Frontend dev server chạy tại http://localhost:3000:
   ```
   cd frontend && npm run dev
   ```
3. Analytics Service chạy tại http://localhost:8082:
   ```
   cd applications/analytics-service && ./mvnw spring-boot:run
   ```
4. Database seed data đã được inject (xem `scripts/sprint2-api-test.sh --seed`)
5. Đã đăng nhập với tài khoản: `tenant: tenant_01`, role: `CITY_MANAGER`

---

## TC-S2-01 — Analytics Dashboard tải thành công

**Priority:** CRITICAL  
**Feature:** v3-FE-03 Cross-building Analytics Dashboard

| Field | Value |
|---|---|
| Pre-conditions | Docker Compose up, dev server up, đã đăng nhập |
| Component | CrossBuildingDashboardPage |

**Steps:**

1. Mở trình duyệt, truy cập `http://localhost:3000/buildings/dashboard`
2. Quan sát trang trong vòng 5 giây
3. Kiểm tra phần filter panel phía trên (Buildings, Metric, Group By, From, To, Reset)
4. Kiểm tra phần chart/table phía dưới

**Expected Results:**

- [ ] Trang load không có JS error trong Console (F12)
- [ ] Filter panel hiển thị đủ 6 control: Buildings multi-select, Metric select, Group By select, From date, To date, Reset button
- [ ] From mặc định = 30 ngày trước, To mặc định = hôm nay (định dạng YYYY-MM-DD)
- [ ] Metric mặc định = `Energy`
- [ ] Group By mặc định = `Day`
- [ ] AnalyticsPanel hiển thị skeleton loader hoặc empty state khi chưa chọn building

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-02 — Responsive 1920px (Desktop Full HD)

**Priority:** HIGH  
**Feature:** v3-FE-04 Responsive breakpoints

| Field | Value |
|---|---|
| Pre-conditions | TC-S2-01 PASS |
| Viewport | 1920 x 1080 |

**Steps:**

1. Mở DevTools (F12) → Toggle Device Toolbar → nhập `1920 x 1080`
2. Reload trang `/buildings/dashboard`
3. Chọn 2 buildings từ dropdown
4. Quan sát layout của charts

**Expected Results:**

- [ ] Filter panel hiển thị trên 1 hàng ngang, không bị xuống dòng
- [ ] Charts hiển thị dạng 2 cột (Grid md=6 + md=6)
- [ ] Không có horizontal scrollbar
- [ ] Không có element bị overflow

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-03 — Responsive 768px (Tablet)

**Priority:** HIGH  
**Feature:** v3-FE-04 Responsive breakpoints

| Field | Value |
|---|---|
| Pre-conditions | TC-S2-01 PASS |
| Viewport | 768 x 1024 |

**Steps:**

1. Mở DevTools → nhập `768 x 1024`
2. Reload trang `/buildings/dashboard`
3. Chọn 2 buildings, quan sát layout

**Expected Results:**

- [ ] Filter panel wrap xuống 2 hàng (flexWrap: wrap)
- [ ] Charts hiển thị 1 cột (xs=12 — mỗi chart full width)
- [ ] Không có text bị clipped
- [ ] Buildings Chip trong multi-select không bị overflow ra ngoài container

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-04 — Responsive 375px (Mobile)

**Priority:** HIGH  
**Feature:** v3-FE-04 Responsive breakpoints

| Field | Value |
|---|---|
| Pre-conditions | TC-S2-01 PASS |
| Viewport | 375 x 667 (iPhone SE) |

**Steps:**

1. Mở DevTools → nhập `375 x 667`
2. Reload trang `/buildings/dashboard`
3. Kiểm tra các control, cuộn trang xuống

**Expected Results:**

- [ ] Mỗi filter control chiếm full width (stack dọc)
- [ ] Date fields (From/To) hiển thị đúng, không bị overflow
- [ ] Charts scroll được (không bị hidden/clipped)
- [ ] Không có fixed-width element vượt quá 375px

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-05 — Chọn building và hiển thị data

**Priority:** HIGH  
**Feature:** v3-FE-03 Filter → API call flow

| Field | Value |
|---|---|
| Pre-conditions | Seed data tồn tại cho `BLD-001`, `BLD-002` (xem api-test.sh) |

**Steps:**

1. Truy cập `/buildings/dashboard`
2. Mở DevTools → tab Network
3. Click dropdown "Buildings" → chọn `BLD-001`
4. Quan sát Network tab
5. Tiếp tục chọn thêm `BLD-002`
6. Quan sát lại Network tab

**Expected Results:**

- [ ] Sau khi chọn BLD-001: URL thay đổi → `?ids=BLD-001&metric=energy&groupBy=day&from=...&to=...`
- [ ] Sau khi chọn BLD-001: gọi POST `/api/v1/analytics/energy-aggregate` với body chứa `buildingIds: ["BLD-001"]`
- [ ] Response 200 → EnergyBarChart render với dữ liệu
- [ ] Sau khi chọn BLD-002: gọi lại API với `buildingIds: ["BLD-001","BLD-002"]`
- [ ] Chart cập nhật không reload trang

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-06 — Giới hạn tối đa 10 buildings

**Priority:** MEDIUM  
**Feature:** v3-FE-03 MAX_BUILDINGS constraint

| Field | Value |
|---|---|
| Pre-conditions | Có ít nhất 11 buildings trong seed data |

**Steps:**

1. Truy cập `/buildings/dashboard`
2. Mở dropdown Buildings
3. Lần lượt chọn 10 buildings
4. Cố chọn thêm building thứ 11
5. Quan sát behavior

**Expected Results:**

- [ ] Counter dưới dropdown hiển thị `10/10`
- [ ] Khi cố chọn building thứ 11: không được thêm vào selection (onChange bị block)
- [ ] Không có error/exception trong Console
- [ ] Vẫn có thể deselect để giảm số lượng

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-07 — GroupBy auto-restrict theo date range

**Priority:** HIGH  
**Feature:** v3-FE-03 GroupBy dynamic options

| Field | Value |
|---|---|
| Pre-conditions | TC-S2-01 PASS |

**Steps:**

**Case A — Range ≤ 2 ngày:**
1. Đặt From = hôm nay, To = hôm nay
2. Mở dropdown "Group By"
3. Quan sát options hiển thị

**Case B — Range > 90 ngày:**
1. Đặt From = 4 tháng trước, To = hôm nay
2. Mở dropdown "Group By"
3. Quan sát options hiển thị

**Expected Results:**

- [ ] Case A: chỉ có `Hour`, `Day`; KHÔNG có `Week`, `Month`
- [ ] Case B: chỉ có `Week`, `Month`; KHÔNG có `Hour`, `Day`
- [ ] Default range (30 ngày): có đủ cả 4 options
- [ ] Sau khi đổi range → Group By reset về giá trị hợp lệ đầu tiên (nếu giá trị cũ không còn hợp lệ)

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-08 — Reset filter

**Priority:** MEDIUM  
**Feature:** v3-FE-03 Reset button

| Field | Value |
|---|---|
| Pre-conditions | Đã thay đổi ít nhất 1 filter |

**Steps:**

1. Chọn 2 buildings, đổi Metric sang `AQI`, đổi Group By sang `Week`
2. Kiểm tra URL params
3. Click nút `Reset`
4. Quan sát URL và filter values

**Expected Results:**

- [ ] URL về `?` (không còn params)
- [ ] Buildings selection trống
- [ ] Metric = `Energy` (default)
- [ ] Group By = `Day` (default)
- [ ] From = 30 ngày trước, To = hôm nay
- [ ] AnalyticsPanel về empty state

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-09 — CH vs TimescaleDB data consistency

**Priority:** CRITICAL  
**Feature:** TD-01 Analytics Service ClickHouse integration, data consistency

| Field | Value |
|---|---|
| Pre-conditions | Seed data đã được inject vào cả ClickHouse và TimescaleDB cho cùng tenant/building/range |
| Tool | curl, jq |

**Steps:**

1. Gọi endpoint ClickHouse (analytics-service port 8082):
   ```
   curl -s -X POST http://localhost:8082/api/v1/analytics/energy-aggregate \
     -H "Content-Type: application/json" \
     -H "X-Tenant-ID: tenant_01" \
     -d '{"tenantId":"tenant_01","buildingIds":["BLD-001"],"fromEpoch":1700000000,"toEpoch":1702999999,"groupBy":"day"}' \
     | jq '.data | map(.totalKwh) | add'
   ```

2. Gọi endpoint TimescaleDB (backend-service port 8080):
   ```
   curl -s -X GET "http://localhost:8080/api/v1/buildings/aggregations?tenantId=tenant_01&buildingIds=BLD-001&from=1700000000&to=1702999999&groupBy=day" \
     -H "X-Tenant-ID: tenant_01" \
     | jq '.data | map(.totalKwh) | add'
   ```

3. So sánh 2 kết quả

**Expected Results:**

- [ ] Cả 2 endpoint trả về HTTP 200
- [ ] Tổng `totalKwh` sai số ≤ 1% (chấp nhận floating point rounding)
- [ ] Số data points (buckets) bằng nhau
- [ ] Nếu CH trả empty mà TS có data → ghi nhận bug CRITICAL

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-10 — Analytics Service cutover từ TimescaleDB sang ClickHouse

**Priority:** CRITICAL  
**Feature:** Analytics Service Cutover Runbook

| Field | Value |
|---|---|
| Pre-conditions | Đọc `docs/mvp3/project/analytics-service-cutover-runbook.md`; CH có data |
| Tool | curl, docker |

**Steps:**

1. Verify analytics-service đang dùng CH (kiểm tra config `analytics.datasource=clickhouse`):
   ```
   curl -s http://localhost:8082/actuator/env | jq '."analytics.datasource"'
   ```

2. Gọi energy-aggregate với CH đang active:
   ```
   curl -s -X POST http://localhost:8082/api/v1/analytics/energy-aggregate \
     -H "Content-Type: application/json" \
     -H "X-Tenant-ID: tenant_01" \
     -d '{"tenantId":"tenant_01","buildingIds":["BLD-001"],"fromEpoch":1700000000,"toEpoch":1702999999}' \
     | jq '{status: .status, count: (.data | length)}'
   ```

3. Quan sát log analytics-service:
   ```
   docker logs analytics-service --tail 20
   ```

**Expected Results:**

- [ ] HTTP 200, `status: "ok"`
- [ ] `count` > 0 (có data từ CH)
- [ ] Log không có `ERROR` từ ClickHouse connection
- [ ] Response time < 2000ms (kiểm tra từ Network tab hoặc curl -w `%{time_total}`)

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-11 — Rollback analytics-service về TimescaleDB

**Priority:** CRITICAL  
**Feature:** Analytics Service Cutover Runbook — rollback path

| Field | Value |
|---|---|
| Pre-conditions | TC-S2-10 PASS; hiểu rollback config |

**Steps:**

1. Stop analytics-service
2. Đổi config `analytics.datasource=timescale` (environment hoặc application.properties)
3. Restart analytics-service
4. Gọi lại API như TC-S2-10 step 2
5. Kiểm tra log để xác nhận đang dùng TimescaleDB

**Expected Results:**

- [ ] Analytics-service start thành công (health check green)
- [ ] API trả về HTTP 200 với data từ TimescaleDB
- [ ] Log hiển thị connection đến PostgreSQL/TimescaleDB (không phải CH)
- [ ] Data count tương đương TC-S2-10 (sai số cho phép do lag sync)

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-12 — Flink enrichment building metadata trong ClickHouse

**Priority:** HIGH  
**Feature:** ADR-035 BuildingMetadataAsyncFunction

| Field | Value |
|---|---|
| Pre-conditions | Flink job EsgDualSinkJob đang chạy; đã inject ít nhất 5 NGSI-LD messages qua Kafka |
| Tool | ClickHouse CLI hoặc curl CH HTTP API |

**Steps:**

1. Inject test message vào Kafka topic `ngsi-ld-esg`:
   ```
   # Xem scripts/sprint2-api-test.sh --inject-kafka
   ```

2. Đợi 10 giây để Flink xử lý

3. Query ClickHouse để kiểm tra enrichment:
   ```
   curl -s "http://localhost:8123/?query=SELECT+building_id,building_name,district,category+FROM+analytics.esg_readings+WHERE+tenant_id='tenant_01'+LIMIT+5+FORMAT+JSON" \
     | jq '.data'
   ```

**Expected Results:**

- [ ] `building_name` không null/empty (ví dụ: `"Tòa nhà A"`)
- [ ] `district` không null/empty (ví dụ: `"District 1"`)
- [ ] `category` = `"COMMERCIAL"`
- [ ] Các record có `device_id` hợp lệ đều có đầy đủ 3 fields
- [ ] Records không khớp `building_code` vẫn được lưu (enrichment lỗi không drop record)

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-13 — Lighthouse Performance Score ≥ 90

**Priority:** HIGH  
**Feature:** v3-FE-04 Performance

| Field | Value |
|---|---|
| Pre-conditions | Production build đang serve (`npm run build && npm run preview`) |
| Tool | Chrome DevTools Lighthouse |

**Steps:**

1. Build và preview frontend:
   ```
   cd frontend && npm run build && npm run preview
   ```
2. Mở Chrome → truy cập `http://localhost:4173/buildings/dashboard`
3. F12 → Lighthouse → chọn Desktop → Run audit

**Expected Results:**

- [ ] Performance score ≥ 90
- [ ] Accessibility score ≥ 85
- [ ] Best Practices score ≥ 90
- [ ] LCP (Largest Contentful Paint) < 2.5s
- [ ] CLS (Cumulative Layout Shift) < 0.1

**Actual Result:** Performance: ___ | Accessibility: ___ | Best Practices: ___  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-14 — Accessibility — keyboard navigation

**Priority:** MEDIUM  
**Feature:** v3-FE-04 Accessibility

| Field | Value |
|---|---|
| Pre-conditions | TC-S2-01 PASS; không dùng chuột trong test này |

**Steps:**

1. Load trang `/buildings/dashboard`
2. Nhấn `Tab` nhiều lần để di chuyển qua các controls
3. Dùng `Enter`/`Space` để mở dropdown Buildings
4. Dùng phím mũi tên và `Enter` để chọn building
5. Tiếp tục `Tab` đến nút Reset, nhấn `Enter`

**Expected Results:**

- [ ] Mỗi control có focus indicator rõ ràng (outline visible)
- [ ] Thứ tự Tab logic: Buildings → Metric → Group By → From → To → Reset
- [ ] Mở/đóng dropdown bằng keyboard được
- [ ] Chọn option trong dropdown bằng keyboard được
- [ ] Reset hoạt động khi nhấn Enter trên button
- [ ] Không có "keyboard trap" (không bị kẹt trong component nào)

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## TC-S2-15 — Rapid filter changes — không crash / no duplicate requests

**Priority:** MEDIUM  
**Feature:** v3-FE-03 React Query deduplication

| Field | Value |
|---|---|
| Pre-conditions | TC-S2-05 PASS; Network tab mở |

**Steps:**

1. Chọn BLD-001
2. Nhanh chóng click đổi Metric: Energy → Emissions → AQI → Energy (trong vòng 2 giây)
3. Quan sát Network tab và Console

**Expected Results:**

- [ ] Không có JS error/exception trong Console
- [ ] Chỉ có 1 request cuối cùng được gửi (React Query deduplicate/cancel)
   - Hoặc có thể có nhiều requests nhưng chỉ request cuối cùng hiển thị data
- [ ] Không có stale data flash (data cũ không hiển thị sau khi chuyển metric)
- [ ] Trang không freeze hoặc trắng

**Actual Result:** ___________  
**Pass / Fail:** [ ] Pass  [ ] Fail  
**Notes:** ___________

---

## Tổng kết

| TC | Tên | Priority | Pass | Fail | Blocked |
|---|---|---|---|---|---|
| TC-S2-01 | Analytics Dashboard tải | CRITICAL | | | |
| TC-S2-02 | Responsive 1920px | HIGH | | | |
| TC-S2-03 | Responsive 768px | HIGH | | | |
| TC-S2-04 | Responsive 375px | HIGH | | | |
| TC-S2-05 | Chọn building → API | HIGH | | | |
| TC-S2-06 | Giới hạn 10 buildings | MEDIUM | | | |
| TC-S2-07 | GroupBy auto-restrict | HIGH | | | |
| TC-S2-08 | Reset filter | MEDIUM | | | |
| TC-S2-09 | CH vs TS consistency | CRITICAL | | | |
| TC-S2-10 | CH cutover | CRITICAL | | | |
| TC-S2-11 | Rollback to TimescaleDB | CRITICAL | | | |
| TC-S2-12 | Flink enrichment in CH | HIGH | | | |
| TC-S2-13 | Lighthouse ≥ 90 | HIGH | | | |
| TC-S2-14 | Keyboard navigation | MEDIUM | | | |
| TC-S2-15 | Rapid filter changes | MEDIUM | | | |

**Exit criteria:** 4 CRITICAL pass + ≤ 2 HIGH fail → Sprint 2 SHIP  
**Tester sign-off:** ___________  
**QA review:** ___________
