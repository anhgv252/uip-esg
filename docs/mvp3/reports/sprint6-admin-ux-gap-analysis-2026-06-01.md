# Admin UX Gap Analysis — Cấu hình hệ thống chỉ qua API
**Sprint 6 — 2026-06-01**  
**Loại:** Phát hiện UX / Backlog đề xuất  
**Mức độ ưu tiên:** HIGH — Blocker cho triển khai thực tế

---

## Bối cảnh phát hiện

Trong quá trình demo Sprint 6 cho nhà đầu tư, nhóm cần trình bày **cấu hình tenant** và **tích hợp hệ thống mới** (Workflow Trigger Config với filterConditions, variableMapping). Thực tế phát sinh vấn đề nghiêm trọng:

> **Tất cả các cấu hình cốt lõi đều chỉ có thể thực hiện qua REST API — không có giao diện quản trị UI.**

Quản trị viên phải dùng `curl` hoặc Postman để thao tác, điều này **không chấp nhận được** trong môi trường vận hành thực tế.

---

## Hiện trạng: Những gì có UI vs. Chỉ có API

### ✅ CÓ giao diện Admin UI (hoạt động)

| Tính năng | URL | Mô tả |
|---|---|---|
| Quản lý User | `/admin` → USERS tab | Xem danh sách, đổi role, deactivate user |
| Sensor Registry | `/admin` → SENSORS tab | Xem 8 sensors, toggle active/inactive |
| BMS Devices | `/bms/devices` | Xem 5 thiết bị, các protocol |
| Workflow Trigger Config | `/workflow-config` | Xem 7 configs, edit form có filter/mapping |
| AI Workflow | `/ai-workflow` | Xem process instances, variables |

### ❌ KHÔNG CÓ giao diện — chỉ có API

| Tính năng | API Endpoint | Vấn đề |
|---|---|---|
| **Quản lý Tenant** | `GET/POST /api/v1/admin/tenants/{id}` | Không có trang tạo/sửa/xóa tenant |
| **Cấu hình Tenant** | `GET /api/v1/tenant/config` | Có đọc config nhưng không có UI chỉnh sửa feature flags, branding |
| **Tenant Users** | `GET /api/v1/admin/tenants/{id}/users` | Không có UI quản lý user theo từng tenant |
| **Mời User vào Tenant** | `POST /api/v1/admin/tenants/{id}/users/invite` | Admin phải gọi API thủ công |
| **Thêm Sensor mới** | (chưa có endpoint) | Không có UI/API tạo sensor mới |
| **Thêm Tenant mới** | (chưa có endpoint) | Không có API khởi tạo tenant |
| **Building Cluster** | `GET /api/v1/buildings` (cần X-Tenant-ID) | Không có UI quản lý tòa nhà |
| **Cấu hình Rate Limit** | (TenantRateLimiter — chỉ code) | Không thể thay đổi giới hạn API per-tenant |

---

## Phân tích tác động

### Tình huống 1: Thêm khu đô thị mới (Tenant mới)

**Hiện tại — Admin phải làm:**
```bash
# Bước 1: Lấy token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' \
  | jq -r '.accessToken')

# Bước 2: Chèn trực tiếp vào database (không có API tạo tenant)
psql -U uip -d uip_db -c "
INSERT INTO public.tenants (tenant_id, tenant_name, tier, is_active, config_json)
VALUES ('hanoi', 'Hà Nội Smart City', 'T2', true,
  '{\"features\":{\"environment-module\":{\"enabled\":true},\"esg-module\":{\"enabled\":false}}}');
"

# Bước 3: Khởi tạo building cluster (cũng chưa có API)
# ...tiếp tục nhiều bước thủ công
```

**Thời gian ước tính:** 30–60 phút, cần kỹ sư backend  
**Rủi ro:** Sai cú pháp JSON config → feature không hoạt động; không có validation

---

### Tình huống 2: Thay đổi filter condition Kafka workflow

**Hiện tại:** Có Edit form trên `/workflow-config` → admin có thể edit filterConditions qua UI.  
**Nhưng:** Field `filterConditions` hiển thị dạng raw JSON string trong textarea — không có builder dạng drag-drop hay form fields.

**Ví dụ raw JSON admin phải tự nhập:**
```json
[
  {"op": "EQ", "field": "module",      "value": "ENVIRONMENT"},
  {"op": "EQ", "field": "measureType", "value": "AQI"},
  {"op": "GT", "field": "value",       "value": 150.0}
]
```

Nếu nhập sai cú pháp → workflow không trigger, không có thông báo lỗi rõ ràng.

---

### Tình huống 3: Cấu hình branding / feature flags cho tenant

**Hiện tại — chỉ đọc được qua API:**
```bash
curl http://localhost:8080/api/v1/tenant/config \
  -H "Authorization: Bearer $TOKEN"
# Response: {"tenantId":"default","features":{...},"branding":{...}}
```

Không có màn hình nào để admin **tắt/bật từng module** (`esg-module`, `traffic-module`, v.v.) hay thay đổi branding (`partnerName`, `primaryColor`, `logoUrl`) qua UI.

---

## Danh sách Gap theo mức độ ưu tiên

### P0 — Blocker (không thể vận hành nếu thiếu)

| Gap | Mô tả | Sprint đề xuất |
|---|---|---|
| **Tạo Tenant mới** | Không có UI/API — phải thao tác trực tiếp DB | Sprint 7 |
| **Cấu hình Feature Flags per Tenant** | Bật/tắt modules cho từng khu đô thị qua UI | Sprint 7 |
| **Thêm Sensor vào Registry** | Admin cần thêm sensor mới khi lắp thiết bị mới | Sprint 7 |

### P1 — High (cần có trước go-live)

| Gap | Mô tả | Sprint đề xuất |
|---|---|---|
| **Filter Condition Builder** | Visual rule builder thay vì raw JSON textarea | Sprint 7 |
| **Tenant User Management** | UI quản lý users theo tenant, invite flow | Sprint 8 |
| **Variable Mapping Editor** | Form editor cho payload → BPMN variable mapping | Sprint 8 |
| **Building Cluster Management** | UI tạo/sửa/xóa building clusters per tenant | Sprint 8 |

### P2 — Medium (nâng cao trải nghiệm)

| Gap | Mô tả | Sprint đề xuất |
|---|---|---|
| **Tenant Branding** | Upload logo, chọn primary color per tenant | Sprint 9 |
| **Rate Limit Config** | Điều chỉnh API rate limits per tenant | Sprint 9 |
| **Tenant Usage Dashboard** | Xem API calls, sensor events, storage per tenant | Sprint 9 |
| **Audit Log** | Xem lịch sử thay đổi config | Sprint 9 |

---

## Đề xuất giải pháp kỹ thuật

### Ngắn hạn (Sprint 7): Admin Config Page

Thêm tab mới vào `/admin`:

```
/admin
├── Users          ← đã có
├── Sensors        ← đã có
├── Data Quality   ← đã có
├── Tenants        ← [MỚI] danh sách tenants, tạo/sửa
│   ├── Feature Flags (toggle switches)
│   ├── Branding (name, color, logo)
│   └── Users (invite, role assignment)
└── System Config  ← [MỚI] global configs
```

**Backend cần bổ sung:**
- `POST /api/v1/admin/tenants` — tạo tenant mới
- `PUT /api/v1/admin/tenants/{id}/features` — update feature flags
- `PUT /api/v1/admin/tenants/{id}/branding` — update branding
- `POST /api/v1/admin/sensors` — tạo sensor mới

### Trung hạn (Sprint 7–8): Filter Condition Builder

Thay raw JSON textarea trong Workflow Config Edit bằng **visual rule builder**:

```
┌─────────────────────────────────────────────────────────┐
│ Filter Conditions                                [+ Add] │
├─────────────────────────────────────────────────────────┤
│ [Field: module ▼] [Op: EQ ▼] [Value: ENVIRONMENT    ]  │
│ [Field: measureType ▼] [Op: EQ ▼] [Value: AQI       ]  │
│ [Field: value ▼] [Op: GT ▼] [Value: 150.0           ]  │
└─────────────────────────────────────────────────────────┘
```

- Dropdowns có danh sách fields từ schema
- Validation ngay khi nhập
- Preview JSON trước khi save

---

## So sánh với Best Practice

| Tiêu chí | Hiện tại (Sprint 6) | Target |
|---|---|---|
| Onboarding tenant mới | DB thủ công, 30–60 phút | UI wizard, 5 phút |
| Thay đổi config | API call, cần kỹ sư | UI admin, cần quản trị viên |
| Kiểm tra config | `curl` + JSON parsing | Dashboard xem ngay |
| Error feedback | HTTP status codes | Validation message tức thì |
| Audit trail | Không có | Log lịch sử thay đổi |

---

## Kết luận

Nền tảng UIP có **đủ tính năng kỹ thuật** (API đầy đủ, multi-tenant isolation, workflow config, BMS integration), nhưng **thiếu lớp quản trị UI** cho phép vận hành mà không cần kỹ sư backend. Đây là gap tiêu biểu của giai đoạn POC → Production readiness.

**Khuyến nghị:**
- Sprint 7 ưu tiên P0 gaps: Tenant CRUD UI + Feature Flags UI + Sensor Create API
- Sprint 8 hoàn thiện Filter Builder + Tenant User Management
- Định nghĩa rõ **"Admin persona"** (IT admin của city authority) trong các user stories mới

---

*Tài liệu này được tạo dựa trên kết quả demo thực tế ngày 2026-06-01 — các API đã được verify live.*
