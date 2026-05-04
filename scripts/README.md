# scripts/ — UIP Smart City Scripts

## Project-wide (dùng cho mọi MVP phase)

| Script | Mục đích | Dùng khi |
|--------|---------|---------|
| `sensor_simulator.py` | Giả lập 8 AQI sensor stations gửi MQTT | Dev/UAT — cần dữ liệu real-time |
| `seed_uat_data.py` | Seed demo data cho UAT (sensors, alerts, citizens, invoices) | Trước mỗi demo / UAT |
| `uat_smoke_test.py` | 10 smoke test cases verify full stack hoạt động | Sau mỗi deployment |
| `perf_benchmark.py` | Đo throughput end-to-end pipeline (Kafka → Flink → DB) | Trước release, perf regression |
| `update-openapi-spec.sh` | Lấy OpenAPI spec từ backend đang chạy → `docs/api/openapi.json` | Sau khi thêm/sửa API |
| `api_regression_test.py` | 44 API regression tests kiểm tra toàn bộ endpoints (health, auth, env, ESG, alerts, traffic, tenant, citizen, admin, workflow) | Sau mỗi lần thêm feature mới |
| `e2e_regression_test.py` | E2E / UI regression tests cho 13 Playwright spec files (auth, dashboard, environment, ESG, alerts, traffic, citizen, AI workflow, v.v.) | Sau mỗi lần thêm feature mới |
| `regression_test.sh` | Orchestrator: chạy tuần tự Gradle unit tests → API regression → E2E UI regression (tuỳ chọn) | CI / trước merge PR |

```bash
# Chạy smoke test nhanh (cần stack đang chạy)
python3 scripts/uat_smoke_test.py

# Seed dữ liệu demo
python3 scripts/seed_uat_data.py

# Giả lập sensor (background)
python3 scripts/sensor_simulator.py &

# Cập nhật OpenAPI spec
bash scripts/update-openapi-spec.sh

# API regression test — kiểm tra 44 endpoints sau khi thêm feature
python3 scripts/api_regression_test.py

# Chạy theo group cụ thể (esg, auth, environment, alerts, traffic, ...)
python3 scripts/api_regression_test.py --group esg

# E2E / UI regression test — chạy 13 Playwright specs (cần frontend + backend đang chạy)
python3 scripts/e2e_regression_test.py

# Chạy 1 UI group cụ thể (auth, dashboard, esg-metrics, alerts, ai-workflow, ...)
python3 scripts/e2e_regression_test.py --group esg-metrics

# Mở browser để xem test chạy
python3 scripts/e2e_regression_test.py --headed

# Verbose — hiển thị error detail khi fail
python3 scripts/e2e_regression_test.py --verbose

# Full regression suite (unit + API)
bash scripts/regression_test.sh

# Full regression suite bao gồm cả E2E UI (cần frontend đang chạy)
bash scripts/regression_test.sh --e2e

# Chỉ chạy API tests (bỏ qua Gradle build)
bash scripts/regression_test.sh --api-only

# Chỉ chạy unit tests
bash scripts/regression_test.sh --unit-only
```

### api_regression_test.py — Test Groups (44 tests)

| Group | Tests | Kiểm tra |
|-------|-------|---------|
| `health` | 4 | Actuator health, Prometheus auth, /api/v1/health |
| `auth` | 7 | Login admin/operator, wrong password, JWT token fields, unauthenticated access, refresh token |
| `environment` | 7 | Sensors list, AQI current/history, sensor readings |
| `esg` | 8 | Energy/Carbon/Summary data, report generation, RBAC (esg:write, esg:read) |
| `alerts` | 5 | Alerts pageable, totalElements field, alert-rules, RBAC |
| `traffic` | 3 | Traffic counts, incidents, congestion map |
| `tenant` | 3 | Tenant config, features object, branding object |
| `citizen` | 1 | Buildings list |
| `admin` | 3 | Users list, sensors list, RBAC (operator 403) |
| `workflow` | 3 | Workflow-configs, definitions, instances |

### e2e_regression_test.py — Test Groups (13 Playwright specs, ~45 tests)

| Group | Spec file | Kiểm tra |
|-------|-----------|---------|
| `auth` | auth.spec.ts | Login thành công, sai mật khẩu, redirect khi chưa auth |
| `dashboard` | dashboard.spec.ts | Map Leaflet, alerts panel, sidebar navigation |
| `environment` | environment.spec.ts | AQI by station, sensor counter, loading state |
| `esg-metrics` | esg-metrics.spec.ts | KPI cards (Energy/Water/Carbon), chart visualization |
| `esg-reports` | esg-reports.spec.ts | Generate ESG Report UI, Year/Quarter selectors |
| `alerts` | alerts.spec.ts | Alert table, severity chips, Severity filter |
| `alert-pipeline` | alert-pipeline.spec.ts | Alert drawer, Acknowledge, Escalate, SSE feed |
| `traffic` | traffic.spec.ts | Incidents table, empty state, page heading |
| `citizen` | citizen-rbac.spec.ts | Admin bị block khỏi citizen portal, RBAC check |
| `citizen-register` | citizen-register.spec.ts | Form validation, step 1, phone format |
| `ai-workflow` | ai-workflow.spec.ts | Process Definitions tab, Instances tab, tablist |
| `workflow-config` | workflow-config.spec.ts | Config table, 8 rows, Enable toggle, action columns |
| `multi-tenancy` | sprint2-multi-tenancy.spec.ts | JWT tenant claims, feature flags, RBAC (mocked API) |

## MVP-specific

| Thư mục | Nội dung |
|---------|---------|
| `mvp1/` | Scripts viết trong Sprint 1-5, không cần chạy lại ở MVP2+ |
