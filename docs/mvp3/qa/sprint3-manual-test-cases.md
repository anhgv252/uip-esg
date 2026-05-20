# Sprint 3 — Manual Test Cases

**Module:** ESG Reporting, Keycloak RSA Auth, Flink Enrichment, Bug Fixes
**Sprint:** MVP3 Sprint 3
**Task ID:** QA-S3-04
**Tester:** ___________
**Execution Date:** ___________
**Environment:** Local Docker Compose (single-node)

---

## Environment & URLs

| Service | URL | Purpose |
|---------|-----|---------|
| Frontend | http://localhost:3000 | React SPA (nginx) |
| Backend API | http://localhost:8080 | Spring Boot monolith |
| Keycloak | http://localhost:8085 | RSA token issuer (admin/admin) |
| Kong Gateway | http://localhost:8000 | Analytics API gateway |

## Test Users

| User | Password | Auth Type | Notes |
|------|----------|-----------|-------|
| operator | password | HMAC (legacy) | `iss: uip-legacy` |
| test-operator | password | Keycloak RSA | `iss: http://localhost:8085/realms/uip` |

---

## Pre-conditions chung (ap dung cho tat ca TC)

1. Docker Compose dang day du:
   ```bash
   cd infrastructure && docker compose up -d
   ```
2. Frontend dev server chay tai http://localhost:3000:
   ```bash
   cd frontend && npm run dev
   ```
3. Backend chay tai http://localhost:8080:
   ```bash
   cd backend && ./gradlew bootRun
   ```
4. Keycloak realm `uip` imported, clients `uip-backend` + `uip-frontend` configured
5. Seed data cho tenant_01, co it nhat 5 buildings voi sensor data trong ClickHouse
6. Flink job EsgDualSinkJob trang thai RUNNING (kiem tra http://localhost:8081)

---

## TC-S3-01 — Report panel loads tren `/esg` route

**TC ID:** TC-S3-01
**Title:** Report panel loads tren `/esg` route
**Priority:** CRITICAL
**Story:** S3-05 (Frontend report generation panel)

### Preconditions

- Docker Compose up, frontend dev server running
- Da dang nhap voi tai khoan `operator/password` hoac `test-operator/password`
- It nhat 1 building co sensor data trong he thong

### Steps

1. Mo trinh duyet Chrome, truy cap `http://localhost:3000`
2. Dang nhap thanh cong (chon HMAC hoac Keycloak login)
3. Click vao menu item "ESG" hoac truy cap truc tiep `http://localhost:3000/esg`
4. Cho trang load hoan tat (toi da 10 giay)
5. Mo DevTools (F12) → tab Console, kiem tra JS errors

### Expected Results

- [ ] Trang `/esg` load khong co JS error trong Console (F12)
- [ ] Report panel hien thi trong `/esg` route (embedded, KHONG phai route rieng `/esg/report`)
- [ ] Year selector hien thi (dropdown voi cac nam)
- [ ] Quarter selector hien thi (dropdown voi Q1, Q2, Q3, Q4)
- [ ] Nut "Generate" hien thi va enabled
- [ ] Empty state hien thi: "Select period and click Generate" khi chua generate report
- [ ] ESG bar charts (Energy, Emissions, AQI) van load binh thuong phia tren report panel

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Day la gate G1 (AC-01) — CRITICAL cho City Authority deadline 2026-06-15
- Kiem tra ca Network tab: khong co failed requests (4xx/5xx) khi load `/esg`
- Neu report panel KHONG hien thi → P1 bug, escalate ngay
- Kiem tra `ReportGenerationPanel.tsx` render trong EsgPage, khong phai trang rieng

---

## TC-S3-02 — Year selector shows current + 3 previous years

**TC ID:** TC-S3-02
**Title:** Year selector shows current + 3 previous years
**Priority:** HIGH
**Story:** S3-05 (Frontend report generation panel)

### Preconditions

- TC-S3-01 PASS (report panel hien thi)
- Nam hien tai: 2026

### Steps

1. Tren trang `/esg`, tim Year selector dropdown
2. Click vao Year selector de mo dropdown
3. Danh sach cac nam hien thi
4. Dem so luong options va ghi lai tung gia tri
5. Chon mot nam khac nam hien tai (vi du 2025)
6. Chon lai nam hien tai (2026)

### Expected Results

- [ ] Year selector co 4 options: 2026, 2025, 2024, 2023
- [ ] Default selection = nam hien tai (2026)
- [ ] Sau khi chon 2025 → selector hien thi "2025"
- [ ] Sau khi chon lai 2026 → selector hien thi "2026"
- [ ] Dropdown khong co nam < 2023 (min = current - 3)
- [ ] Dropdown khong co nam > 2026 (max = current year)

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Neu year selector chi hien thi 1-2 nam → HIGH bug, khong the generate report cho quarters truoc
- Kiem tra nam default khong bi hardcode — phai lay tu `new Date().getFullYear()`

---

## TC-S3-03 — Quarter selector defaults to current quarter

**TC ID:** TC-S3-03
**Title:** Quarter selector defaults to current quarter
**Priority:** HIGH
**Story:** S3-05 (Frontend report generation panel)

### Preconditions

- TC-S3-01 PASS (report panel hien thi)
- Thoi gian test: Q2 2026 (May 2026)

### Steps

1. Load trang `/esg` (lan dau tien hoac refresh page)
2. Quan sat Quarter selector — kiem tra gia tri mac dinh
3. Click vao Quarter selector de mo dropdown
4. Kiem tra cac options hien thi
5. Doi quarter sang Q1
6. Refresh trang (F5) — kiem tra xem quarter co reset ve default

### Expected Results

- [ ] Quarter selector co 4 options: Q1, Q2, Q3, Q4
- [ ] Default selection = current quarter (Q2 neu test vao May 2026)
- [ ] Dropdown hien thi label nhu "Q1", "Q2", "Q3", "Q4" (khong phai "Quarter 1"...)
- [ ] Sau khi refresh trang → quarter reset ve current quarter (khong luu vao URL params)
- [ ] Sau khi chon Q1 va click Generate → gui request voi `quarter: 1`

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Kiem tra Network tab khi generate: request body phai co `quarter` la so nguyen (1-4), KHONG phai chuoi "Q1"
- Neu default sai (vi du luon Q1) → HIGH bug
- Boundary test: neu test vao ngay 1/1/2027 → default phai la Q1 2027

---

## TC-S3-04 — Generate -> loading -> report preview hien thi

**TC ID:** TC-S3-04
**Title:** Generate -> loading -> report preview hien thi
**Priority:** CRITICAL
**Story:** S3-05 (Frontend report generation panel)

### Preconditions

- TC-S3-01, TC-S2-02, TC-S3-03 PASS
- Seed data ton tai cho tenant_01 voi sensor data trong quarter duoc chon
- Mo DevTools → tab Network de theo doi API calls

### Steps

1. Tren trang `/esg`, chon Year = 2026, Quarter = Q1 (hoac quarter co data)
2. Click nut "Generate"
3. Quan sat trang thai sau khi click:
   a. Loading indicator xuat hien
   b. Nut "Generate" bi disable (prevent double-click)
4. Mo DevTools → Network tab, kiem tra:
   a. Request `POST /api/v1/esg/report/generate` duoc gui
   b. Polling requests `GET /api/v1/esg/report/{id}/status` (moi 3 giay)
5. Cho report generation hoan tat (status = "COMPLETED")
6. Quan sat report preview hien thi

### Expected Results

- [ ] Sau khi click Generate: loading spinner hoac "Generating..." text hien thi
- [ ] Nut Generate bi disable trong khi dang generate (prevent double-click)
- [ ] Network tab: POST request gui voi body `{ year: 2026, quarter: 1 }`
- [ ] Network tab: polling GET requests gui moi ~3 giay khi status la GENERATING/PENDING
- [ ] Polling STOP khi status = COMPLETED hoac FAILED (refetchInterval = false)
- [ ] Report preview hien thi voi cac fields:
  - Energy Total (kWh)
  - Carbon Total (tCO2e)
  - Energy Intensity (kWh/m2)
  - CO2 Intensity (tCO2e/m2)
  - Data Quality chip (COMPLETE/PARTIAL/ESTIMATED)
- [ ] Per-building breakdown table co the expand/collapse (IconButton)
- [ ] Thoi gian generate < 30 giay (detail plan SLA, gate G14)

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Day la gate G1 (AC-01) — CRITICAL cho PO demo
- Kiem tra `useEsgReportGenerate` hook: phai la `useMutation`, KHONG phai `useQuery` (POST trong useQuery la anti-pattern — correction C8)
- Kiem tra report ID duoc tra ve tu generate API va duoc dung cho polling
- Edge case: neu generate FAIL → error message hien thi, polling stop, nut Generate re-enable
- Kiem tra response time: gate G14 yeu cau p95 < 30s

---

## TC-S3-05 — Excel download -> file opens correctly

**TC ID:** TC-S3-05
**Title:** Excel download -> file opens correctly
**Priority:** CRITICAL
**Story:** S3-03 (Excel export service)

### Preconditions

- TC-S3-04 PASS (report generated thanh cong)
- MS Excel hoac Google Sheets co san tren may test
- Mo DevTools → Network tab

### Steps

1. Sau khi report preview hien thi, tim nut "Download Excel" hoac "Download XLSX"
2. Click nut download
3. Quan sat Network tab: kiem tra download request
4. Cho file download hoan tat
5. Mo file download bang MS Excel (hoac Google Sheets)
6. Kiem tra noi dung file:

   **Sheet 1 — Summary:**
   a. Tieu de report: "GRI Report — Q{quarter} {year}"
   b. Energy Total (kWh)
   c. Carbon Total (tCO2e)
   d. Energy Intensity (kWh/m2)
   e. CO2 Emissions Intensity (tCO2e/m2)
   f. Data Quality

   **Sheet 2 — GRI 302-1 Energy:**
   a. Tieu de: "GRI 302-1 Energy Consumption"
   b. Per-building breakdown table (Building, kWh, % of Total)
   c. GRI Disclosure branding trong header

   **Sheet 3 — GRI 305-4 Emissions:**
   a. Tieu de: "GRI 305-4 Emissions"
   b. Per-building breakdown table
   c. GRI Disclosure branding

7. Kiem tra file size (right-click → Properties)

### Expected Results

- [ ] Click download → browser trigger file download (khong mo trong tab moi)
- [ ] File name co dinh dang: `esg-report-{reportId}.xlsx`
- [ ] Network tab: request tra ve 200 voi Content-Type `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- [ ] File mo duoc trong MS Excel / Google Sheets (khong loi "file corrupted")
- [ ] Sheet Summary co day du 6 fields: Energy Total, Carbon Total, Energy Intensity, CO2 Intensity, Data Quality, Period
- [ ] Sheet GRI 302-1 co per-building breakdown voi it nhat 1 building
- [ ] Sheet GRI 305-4 co per-building breakdown voi it nhat 1 building
- [ ] GRI Disclosure branding (header/footer) co tren ca 2 GRI sheets
- [ ] File size < 5MB cho 48 buildings (gate AC-01)

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Day la gate G1 (AC-01) — CRITICAL cho City Authority deadline
- Kiem tra so lieu trong Excel khop voi report preview tren UI (Energy Total, Carbon Total)
- Neu file > 5MB → HIGH bug (optimize query hoac reduce data points)
- Test voi Google Sheets: upload file vao Google Drive → mo bang Sheets → kiem tra formatting
- Kiem tra `DefaultXlsxExportAdapter.java` generate dung Apache POI XSSFWorkbook

---

## TC-S3-06 — PDF download -> file opens correctly, A4 printable

**TC ID:** TC-S3-06
**Title:** PDF download -> file opens correctly, A4 printable
**Priority:** HIGH
**Story:** S3-04 (PDF export service)

### Preconditions

- TC-S3-04 PASS (report generated thanh cong)
- Adobe Reader hoac browser PDF viewer co san
- May in ao (Print to PDF) co san

### Steps

1. Sau khi report preview hien thi, tim nut "Download PDF"
2. Click nut download
3. Cho file download hoan tat
4. Mo file download bang PDF reader
5. Kiem tra noi dung PDF:

   a. Tieu de report: "GRI Report — Q{quarter} {year}"
   b. Energy Total, Carbon Total
   c. Energy Intensity, CO2 Intensity
   d. Data Quality
   e. Per-building breakdown table

6. Kiem tra PDF layout:
   a. Kich thuoc trang: A4 (210mm x 297mm)
   b. Margin hop ly (khong bi cat noi dung)
   c. Header/footer co GRI branding

7. Thuc hien Print Preview (Ctrl+P):
   a. Chon paper size = A4
   b. Kiem tra preview khong bi cat noi dung

8. Kiem tra file size

### Expected Results

- [ ] Click download → browser trigger file download
- [ ] File name co dinh dang: `esg-report-{reportId}.pdf`
- [ ] Network tab: request tra ve 200 voi Content-Type `application/pdf`
- [ ] File mo duoc trong PDF reader (khong loi "file corrupted")
- [ ] Noi dung PDF gom day du: Energy Total, Carbon Total, Energy Intensity, CO2 Intensity, Data Quality
- [ ] Per-building breakdown table hien thi ro rang trong PDF
- [ ] Layout A4 printable — khong bi cat noi dung o margin
- [ ] Header co GRI Disclosure branding
- [ ] File size < 10MB cho 48 buildings (AC-01 DoD)
- [ ] GRI 302 + 305 content giong voi Excel file

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- S3-04 la STRETCH goal — neu chua implement, skip TC nay va ghi "BLOCKED — S3-04 not merged"
- Library phai la OpenPDF (LGPL) hoac Apache PDFBox — KHONG duoc dung iText 7+ (AGPL) (correction C5)
- Kiem tra Unicode: ten building co the chua ky tu Viet Nam (vd: "Toa nha A") — phai hien thi dung
- Print test: kiem tra table khong bi ngat giua dong (page break hop ly)

---

## TC-S3-07 — Keycloak login -> RSA token -> dashboard loads

**TC ID:** TC-S3-07
**Title:** Keycloak login -> RSA token -> dashboard loads
**Priority:** CRITICAL
**Story:** S3-06 (RoutingJwtDecoder dual-issuer)

### Preconditions

- Keycloak chay tai http://localhost:8085, realm `uip` da import
- Client `uip-backend` configured voi RS256 signing
- Test user `test-operator/password` ton tai trong Keycloak

### Steps

1. Mo trinh duyet moi (incognito/private window) de dam bao khong co session cu
2. Truy cap `http://localhost:3000`
3. Click "Login with Keycloak" (hoac tuong tu — RSA login path)
4. Redirect sang Keycloak login page (http://localhost:8085/realms/uip/...)
5. Nhap credentials: `test-operator` / `password`
6. Click "Sign In"
7. Sau khi redirect ve frontend, mo DevTools → Application → Local Storage:
   a. Tim JWT token (key: `token` hoac `access_token`)
   b. Copy token, paste vao jwt.io de decode
8. Kiem tra JWT payload:
   a. `iss` claim = `http://localhost:8085/realms/uip`
   b. `sub` claim = user ID
   c. `tenant_id` claim ton tai
   d. `roles` claim ton tai
9. Truy cap `/esg` dashboard, kiem tra data load

### Expected Results

- [ ] Login redirect sang Keycloak thanh cong (URL chua `localhost:8085/realms/uip`)
- [ ] Nhap dung credentials → redirect ve frontend thanh cong
- [ ] JWT token duoc luu trong Local Storage
- [ ] Decoded JWT: `iss` = `http://localhost:8085/realms/uip` (Keycloak issuer)
- [ ] Decoded JWT: header `alg` = `RS256` (RSA, KHONG phai HS256)
- [ ] Decoded JWT: `tenant_id` claim co gia tri (vd: `tenant_01`)
- [ ] Decoded JWT: `roles` claim co gia tri (vd: `OPERATOR`)
- [ ] Dashboard `/esg` load thanh cong voi du lieu (khong bi 401/403)
- [ ] API calls gui voi `Authorization: Bearer {RSA-token}` → 200 OK
- [ ] Keycloak Admin Console accessible: `http://localhost:8085/admin` (admin/admin)

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Day la gate G2 (AC-02) — CRITICAL cho Keycloak RSA migration
- `RoutingJwtDecoder` route dua tren `iss` claim (KHONG PHAI `kid` header) — correction C1
- Kiem tra `JwtProperties.keycloakIssuer` = `http://localhost:8085/realms/uip` match voi `iss` trong JWT
- Neu login fail → kiem tra Keycloak container: `docker logs uip-keycloak --tail 50`
- Neu token bi reject (401) → kiem tra JWK Set URI: `http://localhost:8085/realms/uip/protocol/openid-connect/certs`

---

## TC-S3-09 — Flink enrichment — new event -> building_name populated

**TC ID:** TC-S3-09
**Title:** Flink enrichment — new event -> building_name populated
**Priority:** HIGH
**Story:** S3-12 (Flink enrichment inline — BuildingMetadataAsyncFunction)

### Preconditions

- Flink jobmanager + taskmanager dang chay
- EsgDualSinkJob trang thai RUNNING (kiem tra http://localhost:8081)
- BuildingMetadataAsyncFunction da duoc integrate vao DAG
- Co it nhat 1 building trong building_metadata table voi `building_code` khop voi test event

### Steps

1. Xac dinh building_code ton tai trong DB:
   ```bash
   docker exec -i uip-timescaledb psql -U uip uip_db -c \
     "SELECT building_code, name, district FROM buildings LIMIT 5;"
   ```
   Ghi lai 1 building_code (vd: `BLD-001`)

2. Tao test sensor event va inject vao Kafka topic `ngsi-ld-esg`:
   ```bash
   docker exec -i uip-kafka kafka-console-producer.sh \
     --bootstrap-server localhost:29092 \
     --topic ngsi-ld-esg <<'EOF'
   {"id":"urn:ngsi-ld:Sensor:test-flink-001","type":"EsgReading","device_id":{"value":"test-flink-001"},"building_code":{"value":"BLD-001"},"tenant_id":{"value":"tenant_01"},"category":{"value":"COMMERCIAL"},"energy_kwh":{"value":42.5},"carbon_kg":{"value":12.3},"timestamp":{"value":"2026-05-20T10:00:00Z"},"observationSpace":{"type":"Point","coordinates":[106.7,10.8]}}
   EOF
   ```

3. Doi 15 giay de Flink process event qua BuildingMetadataAsyncFunction

4. Query ClickHouse de kiem tra enrichment:
   ```bash
   curl -s "http://localhost:8123/?query=SELECT+device_id,building_id,building_name,district,energy_kwh+FROM+analytics.esg_readings+WHERE+device_id='test-flink-001'+FORMAT+JSON" \
     | jq '.data'
   ```

5. Query TimescaleDB de kiem tra enrichment:
   ```bash
   docker exec -i uip-timescaledb psql -U uip uip_db -c \
     "SELECT device_id, building_id, building_name, district, energy_kwh FROM esg_readings WHERE device_id='test-flink-001';"
   ```

6. (Cleanup) Xoa test data:
   ```bash
   # Chi lam sau khi test xong, neu can
   ```

### Expected Results

- [ ] Kafka inject thanh cong (khong loi producer)
- [ ] ClickHouse query tra ve record voi `device_id` = `test-flink-001`
- [ ] `building_name` KHONG NULL — co gia tri tu building metadata (vd: "Toa nha A")
- [ ] `district` KHONG NULL — co gia tri tu building metadata (vd: "District 1")
- [ ] `energy_kwh` = 42.5 (dung gia tri tu event)
- [ ] TimescaleDB cung co enriched record (dual-sink)
- [ ] Record duoc enrich tu Caffeine cache (lan 2) hoac tu DB lookup (lan dau)
- [ ] Thoi gian tu inject den khi record xuat hien trong CH < 15 giay (gate G12: < 100ms p99 cho enrichment step)

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Day la gate G4 (AC-04) — Flink enrichment inline, khong can backfill
- BuildingMetadataAsyncFunction su dung `RichAsyncFunction` pattern voi JDBC connection pool
- Neu `building_name` NULL → AsyncFunction khong match `building_code` hoac cache/DB lookup fail
- Neu event khong xuat hien trong CH sau 30s → kiem tra Flink logs: `docker logs uip-flink-taskmanager --tail 50`
- Kiem tra checkpoint restore: neu Flink restart tu checkpoint → enrich van hoat dong (gate G4 DoD)
- DAG target: `Kafka Source -> filter -> TenantIdValidator -> flatMap -> BuildingMetadataAsyncFunction -> TimescaleDB Sink / ClickHouse Sink`

---

## TC-S3-10 — P2-001 tooltip fix verified

**TC ID:** TC-S3-10
**Title:** P2-001 tooltip fix verified
**Priority:** MEDIUM
**Story:** S3-13 (Chart tooltip truncation fix)

### Preconditions

- TC-S3-01 PASS (ESG page load)
- Co data cho ESG bar chart (chon building co readings)

### Steps

**Case A — Desktop 1920px:**

1. Mo DevTools → set viewport 1920 x 1080
2. Truy cap `/esg`, chon 1-2 buildings co data
3. Hover chuot vao bar chart — cho tooltip xuat hiet
4. Kiem tra tooltip noi dung: toan bo text hien thi, khong bi cat

**Case B — Tablet 768px:**

1. Set viewport 768 x 1024
2. Reload trang, hover vao bar chart
3. Kiem tra tooltip: khong bi parent Paper element clip

**Case C — Mobile 320px:**

1. Set viewport 320 x 568 (iPhone SE)
2. Reload trang, hover/tap vao bar chart
3. Kiem tra tooltip: khong bi truncate, hien thi day du

**Case D — Long buildingId:**

1. Chon building co ID dai (vd: `BLD-COMMERCIAL-BUILDING-SOUTH-WING-001`)
2. Hover vao bar chart
3. Kiem tra tooltip khong bi cat boi viec buildingId qua dai

### Expected Results

- [ ] Case A (1920px): tooltip hien thi day du, khong bi clip
- [ ] Case B (768px): tooltip khong bi parent Paper overflow hidden cat
- [ ] Case C (320px): tooltip hien thi day du, co the overflow ra ngoai chart area
- [ ] Case D: buildingId dai khong bi truncate trong tooltip
- [ ] Tooltip co `z-index: 1300` (hoac cao hon) de hien thi tren cac element khac
- [ ] Khong co horizontal scrollbar xuat hien khi tooltip mo rong

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Root cause goc: Recharts `Tooltip` bi parent `Paper` clip khi buildingId dai + viewport < 768px
- Fix dung: dung Recharts `Tooltip` prop `wrapperStyle={{ zIndex: 1300 }}` + `contentStyle`
- KHONG phai CSS overflow fix — phai fix o Recharts component level
- Day la gate G6 (AC-06) — P2 bug fix verification

---

## TC-S3-11 — P2-002 AQI auto-refresh verified

**TC ID:** TC-S3-11
**Title:** P2-002 AQI auto-refresh verified
**Priority:** MEDIUM
**Story:** S3-14 (AQI stale data fix)

### Preconditions

- Dashboard `/esg` load voi data AQI
- Mo DevTools → tab Network

### Steps

1. Truy cap `/esg`, chon buildings co AQI data
2. Mo DevTools → Network tab, filter voi "analytics" hoac "aqi"
3. Cho 20 giay tren trang (khong navigate away, khong click bat ky cai gi)
4. Quan sat Network tab — dem so requests AQI duoc gui
5. Tiep tuc cho them 20 giay (tong cong 40 giay)
6. Dem them requests AQI

7. (Optional) Inject AQI event moi qua Kafka:
   ```bash
   # Inject sensor event voi AQI value khac
   ```
8. Cho 15-20 giay, kiem tra dashboard tu cap nhat

### Expected Results

- [ ] Trong 20 giay dau: co it nhat 1 request AQI tu dong (refetchInterval = 15s)
- [ ] Trong 40 giay: co khoang 2-3 requests AQI (khong tinh manual page load)
- [ ] Network tab: request url la analytics AQI endpoint
- [ ] AQI chart tu cap nhat du lieu moi ma KHONG can user reload trang
- [ ] Neu inject AQI event moi → sau 15-20 giay dashboard tu hien thi data moi
- [ ] `useAqiTrend` hook co `refetchInterval: 15_000` (kiem tra trong source code hoac Network tab timing)

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Root cause goc: `useAqiTrend` KHONG CO `refetchInterval` — data chi refresh khi navigate away
- Fix: them `refetchInterval: 15_000` vao `useAqiTrend` query options — correction C7
- Day KHONG PHAI la "reduce poll interval" — truoc do KHONG CO polling
- Kiem tra stale indicator: neu data > 30 giay cu → nen hien thi indicator (DoD item, co the chua implement)
- Day la gate G6 (AC-06) — P2 bug fix verification

---

## TC-S3-12 — P2-003 filter animation fix verified

**TC ID:** TC-S3-12
**Title:** P2-003 filter animation fix verified
**Priority:** MEDIUM
**Story:** S3-15 (Filter reset animation fix)

### Preconditions

- Dashboard `/buildings/dashboard` load voi filter panel
- Da thay doi it nhat 1 filter (chon building, doi metric)

### Steps

1. Truy cap `/buildings/dashboard`
2. Chon 2 buildings, doi Metric sang "AQI", doi Group By sang "Week"
3. Click nut "Reset"
4. Quan sat animation cua filter panel khi reset:
   a. Co delay 150ms truoc khi reset?
   b. Animation co muot hay co giật?

5. Lap lai 3 lan: chon filters → Reset → quan sat timing

6. Record screen (neu co the) de phan tich animation frame-by-frame

### Expected Results

- [ ] Click Reset → filter values reset ngay lap tuc (khong co 150ms delay)
- [ ] CSS transition bi disable trong luc reset (them `transition: none` khi `resetting` state = true)
- [ ] Sau khi reset xong → CSS transition tro lai binh thuong cho lan thay doi tiep theo
- [ ] Khong co "flash" cua gia tri cu truoc khi reset
- [ ] Filter values tro ve default: Buildings = empty, Metric = Energy, Group By = Day
- [ ] Date range reset ve: From = 30 ngay truoc, To = hom nay

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Root cause: CSS transition duration (150ms) bi apply trong luc reset → tao hieu ung "lag"
- Fix: them `resetting` state + `transition: none` khi reset dang thuc hien
- Kiem tra `AnalyticsFilterPanel.tsx` co them `resetting` state handling
- Day la gate G6 (AC-06) — P2 bug fix verification
- Cosmetic fix — khong anh huong functionality, chi UX

---

## TC-S3-13 — Report panel responsive — Tablet 768px

**TC ID:** TC-S3-13
**Title:** Report panel responsive — Tablet 768px
**Priority:** HIGH
**Story:** S3-05 (Frontend report generation panel — responsive)

### Preconditions

- TC-S3-01 PASS (report panel hien thi)
- Da generate report thanh cong (TC-S3-04 PASS) de co preview data

### Steps

1. Mo DevTools → Toggle Device Toolbar
2. Set viewport: 768 x 1024 (iPad portrait)
3. Reload trang `/esg`
4. Kiem tra report panel layout:
   a. Year selector + Quarter selector + Generate button layout
   b. Report preview cards (Energy Total, Carbon Total, etc.)
   c. Per-building breakdown table
   d. Download buttons (Excel, PDF)

5. Thuc hien generate report o viewport 768px:
   a. Chon Year, Quarter
   b. Click Generate
   c. Quan sat loading va preview o tablet viewport

6. Set viewport: 1024 x 768 (iPad landscape)
7. Reload trang, kiem tra layout

8. (Optional) Set viewport: 375 x 667 (mobile) de kiem tra degradation

### Expected Results

- [ ] 768px portrait: Year + Quarter selectors side-by-side hoac stack theo chieu doc (khong bi overflow)
- [ ] 768px: Generate button full-width hoac cung hang voi selectors
- [ ] 768px: Report preview cards wrap xuong 2 hang (flexWrap: wrap) thay vi 1 hang ngang
- [ ] 768px: Per-building breakdown table scroll ngang neu can (khong bi clip)
- [ ] 768px: Download buttons hien thi ro rang, clickable voi touch
- [ ] 768px: Khong co horizontal scrollbar tren toan trang
- [ ] 1024x768 landscape: layout tuong tu desktop (selectors cung hang, preview cards 1 hang)
- [ ] Report generation thanh cong o 768px (functional + visual)
- [ ] Khong co text bi clipped hoac overflow

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Day la DoD cua S3-05: "Responsive 768px + 1920px"
- Kiem tra MUI Grid breakpoints: `xs={12} md={6}` cho preview cards
- Kiem tra touch target size: buttons phai >= 44px cho tablet (accessibility)
- Neu layout bi loi o 768px → HIGH bug, nhieu user dung tablet cho building management
- Kiem tra ca truong hop chua generate (empty state) va da generate (preview data)

---

## TC-S3-14 — Dual-token session: HMAC tab + RSA tab cung luc

**TC ID:** TC-S3-14
**Title:** Dual-token session: HMAC tab + RSA tab cung luc
**Priority:** CRITICAL
**Story:** S3-06 (RoutingJwtDecoder dual-issuer — grace period)

### Preconditions

- Keycloak running tai http://localhost:8085
- Test users ca hai auth methods deu available
- Mo 2 tab trinh duyet hoac 2 cua so incognito

### Steps

1. **Tab 1 — HMAC (legacy) login:**
   a. Mo tab moi (incognito window 1)
   b. Truy cap `http://localhost:3000`
   c. Dang nhap voi HMAC path: `operator/password`
   d. Sau khi login thanh cong, decode JWT token (F12 → Application → Local Storage)
   e. Ghi lai `iss` claim: phai la `uip-legacy`
   f. Truy cap `/esg` → kiem tra dashboard load

2. **Tab 2 — Keycloak RSA login:**
   a. Mo tab moi (incognito window 2, RIENG BIE voi tab 1)
   b. Truy cap `http://localhost:3000`
   c. Dang nhap qua Keycloak path: `test-operator/password`
   d. Sau khi login, decode JWT token
   e. Ghi lai `iss` claim: phai la `http://localhost:8085/realms/uip`
   f. Truy cap `/esg` → kiem tra dashboard load

3. **Cross-verification:**
   a. Quay lai Tab 1 (HMAC) → refresh trang → kiem tra van hoat dong
   b. Quay lai Tab 2 (RSA) → refresh trang → kiem tra van hoat dong
   c. Gui API call tu ca 2 tab (click Generate report tren ca 2)

4. **Token routing verification:**
   a. Mo Network tab o ca 2 tab
   b. Gui 1 API call tu moi tab
   c. Kiem tra backend logs:
      ```bash
      docker logs uip-backend --tail 50 | grep "JwtDecoder"
      ```

### Expected Results

- [ ] Tab 1 (HMAC): dang nhap thanh cong, dashboard load
- [ ] Tab 1 (HMAC): JWT `iss` = `uip-legacy`, `alg` = `HS256`
- [ ] Tab 2 (RSA): dang nhap thanh cong, dashboard load
- [ ] Tab 2 (RSA): JWT `iss` = `http://localhost:8085/realms/uip`, `alg` = `RS256`
- [ ] Ca 2 tab hoat dong dong thoi ma khong xung dot
- [ ] HMAC token → `RoutingJwtDecoder` route den HMAC decoder (`withSecretKey()`)
- [ ] RSA token → `RoutingJwtDecoder` route den RSA decoder (`withJwkSetUri()`)
- [ ] API calls tu ca 2 tab deu tra 200 (khong bi 401)
- [ ] Backend log ghi: "HMAC JwtDecoder initialized" va "RSA JwtDecoder initialized"
- [ ] Sau khi refresh ca 2 tab → ca 2 van hoat dong (grace period active)

### Actual Result

___________

### Pass / Fail

[ ] Pass  [ ] Fail

### Notes

- Day la gate G2 (AC-02) — CRITICAL cho Keycloak RSA migration
- `RoutingJwtDecoder` route dua tren `iss` claim — KHONG PHAI `kid` header (correction C1)
- Day la truong hop quan trong nhat: grace period phai cho phep ca 2 auth methods song song
- Neu HMAC tab bi 401 khi RSA tab active → routing logic sai (P0 bug)
- Neu RSA tab bi 401 → kiem tra JWK Set URI connectivity
- Backend su dung `ConcurrentHashMap` cho decoder cache — phai thread-safe khi 2 decoder khoi tao dong thoi
- Kiem tra `alg=none` attack: thu gui token voi `alg: none` → phai bi reject voi 401
- Day la proof cho gate G10 (security negative test)

---

## Summary

| TC | Title | Priority | Area | Pass | Fail | Blocked | Executed By |
|----|-------|----------|------|------|------|---------|-------------|
| TC-S3-01 | Report panel loads tren `/esg` route | CRITICAL | S3-05 | | | | |
| TC-S3-02 | Year selector shows current + 3 previous years | HIGH | S3-05 | | | | |
| TC-S3-03 | Quarter selector defaults to current quarter | HIGH | S3-05 | | | | |
| TC-S3-04 | Generate -> loading -> report preview hien thi | CRITICAL | S3-05 | | | | |
| TC-S3-05 | Excel download -> file opens correctly | CRITICAL | S3-03 | | | | |
| TC-S3-06 | PDF download -> file opens correctly, A4 printable | HIGH | S3-04 | | | | |
| TC-S3-07 | Keycloak login -> RSA token -> dashboard loads | CRITICAL | S3-06 | | | | |
| TC-S3-08 | ~~ClickHouse HA — kill node -> dashboard still loads~~ | ~~HIGH~~ | ~~S3-09~~ | | | **DEFERRED** | |
| TC-S3-09 | Flink enrichment — new event -> building_name populated | HIGH | S3-12 | | | | |
| TC-S3-10 | P2-001 tooltip fix verified | MEDIUM | S3-13 | | | | |
| TC-S3-11 | P2-002 AQI auto-refresh verified | MEDIUM | S3-14 | | | | |
| TC-S3-12 | P2-003 filter animation fix verified | MEDIUM | S3-15 | | | | |
| TC-S3-13 | Report panel responsive — Tablet 768px | HIGH | S3-05 | | | | |
| TC-S3-14 | Dual-token session: HMAC tab + RSA tab cung luc | CRITICAL | S3-06 | | | | |

### Execution Priority Order

1. **CRITICAL (4 TC) — must pass for gate:** TC-S3-01, TC-S3-04, TC-S3-05, TC-S3-07, TC-S3-14
2. **HIGH (5 TC) — should pass:** TC-S3-02, TC-S3-03, TC-S3-06, TC-S3-09, TC-S3-13
3. **MEDIUM (3 TC) — bug fix verification:** TC-S3-10, TC-S3-11, TC-S3-12

### Exit Criteria

- Tat ca 5 CRITICAL test cases PASS
- It nhat 4/5 HIGH test cases PASS
- Tat ca MEDIUM test cases PASS hoac co plan fix
- Khong co P0/P1 bug moi tim thay

### Gate Mapping

| TC | Gate | AC |
|----|------|----|
| TC-S3-01, TC-S3-04, TC-S3-05 | G1 — GRI export PO download | AC-01 |
| TC-S3-07, TC-S3-14 | G2 — Keycloak RSA active | AC-02 |
| TC-S3-09 | G4 — Flink enrichment inline | AC-04 |
| TC-S3-10, TC-S3-11, TC-S3-12 | G6 — P2 bugs fixed | AC-06 |

---

**Tester sign-off:** ___________
**QA Lead review:** ___________
**Date:** ___________

---

*TC-S3-08 (ClickHouse HA) deferred cung S3-09/S3-10 sang Sprint 4 — PO confirmed.*
