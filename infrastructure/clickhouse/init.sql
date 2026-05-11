-- ClickHouse schema init — chạy tự động khi container khởi động lần đầu
-- Database đã được tạo qua CLICKHOUSE_DB env var; chỉ cần tạo bảng.

CREATE DATABASE IF NOT EXISTS uip_analytics;

CREATE TABLE IF NOT EXISTS uip_analytics.energy_readings
(
    tenant_id    String,
    building_id  String,
    kwh          Float64,
    demand_kw    Float64,
    power_factor Float64,
    ts           Int64
) ENGINE = MergeTree()
ORDER BY (tenant_id, building_id, ts);
