# scripts/ — UIP Smart City Scripts

## Project-wide (dùng cho mọi MVP phase)

| Script | Mục đích | Dùng khi |
|--------|---------|---------|
| `sensor_simulator.py` | Giả lập 8 AQI sensor stations gửi MQTT | Dev/UAT — cần dữ liệu real-time |
| `seed_uat_data.py` | Seed demo data cho UAT (sensors, alerts, citizens, invoices) | Trước mỗi demo / UAT |
| `uat_smoke_test.py` | 10 smoke test cases verify full stack hoạt động | Sau mỗi deployment |
| `perf_benchmark.py` | Đo throughput end-to-end pipeline (Kafka → Flink → DB) | Trước release, perf regression |
| `update-openapi-spec.sh` | Lấy OpenAPI spec từ backend đang chạy → `docs/api/openapi.json` | Sau khi thêm/sửa API |

```bash
# Chạy smoke test nhanh (cần stack đang chạy)
python3 scripts/uat_smoke_test.py

# Seed dữ liệu demo
python3 scripts/seed_uat_data.py

# Giả lập sensor (background)
python3 scripts/sensor_simulator.py &

# Cập nhật OpenAPI spec
bash scripts/update-openapi-spec.sh
```

## MVP-specific

| Thư mục | Nội dung |
|---------|---------|
| `mvp1/` | Scripts viết trong Sprint 1-5, không cần chạy lại ở MVP2+ |
