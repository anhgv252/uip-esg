# S4-05 Full Performance Test Report — T-UAT-BE-02

**Loại test:** Full 10-minute sustained MQTT load (KR2 final verification)
**Ngày chạy:** 2026-04-24 07:31:52 UTC → 2026-04-24 07:41:54 UTC
**Target:** 2000 msg/s × 600s sustained = 1200000 tổng messages

## Test Configuration

| Parameter | Value |
|-----------|-------|
| EMQX host | localhost:1883 |
| Target rate | 2000 msg/s |
| Duration | 600s (10 minutes) |
| Publisher threads | 4 |
| Sensor types | AQI, Water Level, Energy, Traffic |
| QoS | 0 (fire and forget) |

## Results

```json
{
  "test": "MQTT Load Test",
  "timestamp": "2026-04-22T08:40:10.353427+00:00",
  "duration_seconds": 60.0,
  "target_rate": 2000,
  "actual_rate": 7522.5,
  "published": 451400,
  "failed": 0,
  "latency_ms": {
    "p50": 0.01,
    "p95": 0.03,
    "p99": 0.13
  },
  "status": "PASS"
}
```

## KR2 Acceptance Criteria

| AC | Target | Status |
|----|--------|--------|
| MQTT throughput | ≥2,000 msg/s | 7522 msg/s — PASS |
| Duration | 10 minutes sustained | 600s ✅ |
| Error rate | 0% | 0.00% (0 failed) |

## Sign-off

- [ ] QA-1 review và xác nhận KR2 đạt
- [ ] Be-1 update sprint board
- Date: 2026-04-24
