# ADR-034: Structural Monitoring — Flink CEP + Welford Online StdDev

**Status:** Approved | **Date:** 2026-06-02 | **Sprint:** MVP3-7
**Author:** Solution Architect | **Reviewers:** Backend Lead, PM

---

## Context

Sprint 7 triển khai **Building Safety** module — giám sát structural health của tòa nhà (rung động, nghiêng, nứt) theo tiêu chuẩn **TCVN 9386:2012** và **ISO 4866**.

### Vấn đề
- Cần phát hiện bất thường structural **real-time** từ sensor readings streaming qua Kafka
- Threshold cố định (ví dụ >50 mm/s) không đủ — cần adaptive baseline vì mỗi tòa nhà có baseline vibration khác nhau
- Phải giảm false positive (rung do gió/xe cộ) nhưng không bỏ sót event nguy hiểm thực sự
- **BR-010:** Structural P0 alert = **operator review ONLY**, KHÔNG auto-evacuate

### Constraints
- Flink CEP đã được validate trong Sprint 6 (`FloodAlertJob`) — tiếp tục dùng pattern này
- Sensor readings đến qua topic `ngsi_ld_environment` (đã có Flink source)
- Output alert qua Kafka topic mới `UIP.structural.alert.critical.v1`
- Alert consumer trong monolith follow `FloodAlertConsumer` pattern

---

## Decision

Sử dụng **Welford Online Standard Deviation** kết hợp **Flink CEP pattern matching**.

### Architecture

```
ngsi_ld_environment (Kafka)
       ↓
Flink: VibrationAnomalyJob
  ├── Filter: STRUCTURAL_VIBRATION / STRUCTURAL_TILT / STRUCTURAL_CRACK sensors
  ├── KeyBy: sensorId (per-sensor state)
  ├── Process: WelfordStdDevFunction (maintain running μ, σ² per sensor)
  │     ├── Alert khi: value > μ + 4σ  AND value > WARNING_THRESHOLD (TCVN min)
  │     └── Skip khi: n < 1000 (cold start protection)
  ├── Flink CEP: 3 consecutive spikes within 10 seconds
  └── Output: StructuralAlertEvent → UIP.structural.alert.critical.v1
       ↓
StructuralAlertConsumer (monolith)
  ├── TenantContext enforcement (RLS)
  ├── P0: FCM/APNs push + Email city authority (<15s)
  ├── P1/P2: Alert record in dashboard
  └── BR-010: operator review ONLY for P0
```

### Welford Algorithm

Online calculation — không cần lưu trữ toàn bộ historical data:

```java
// Welford online algorithm
class WelfordState {
    long n = 0;        // sample count
    double mean = 0.0; // running mean μ
    double m2 = 0.0;   // running sum of squares of differences

    void update(double x) {
        n++;
        double delta = x - mean;
        mean += delta / n;
        double delta2 = x - mean;
        m2 += delta * delta2;
    }

    double stddev() {
        return n < 2 ? 0.0 : Math.sqrt(m2 / (n - 1));
    }

    boolean isAnomaly(double x) {
        if (n < 1000) return false;  // cold start protection
        double sigma = stddev();
        if (sigma < 1e-10) return false; // near-zero variance
        return Math.abs(x - mean) > 4 * sigma; // 4-sigma rule
    }
}
```

**Cold start handling:**
- `n < 1000`: Skip anomaly detection, chỉ accumulate
- Pre-seed: Load historical readings từ TimescaleDB khi Flink job restart (optional enhancement)
- Flink state backend: `EmbeddedRocksDBStateBackend(true)` — state survive restart

### Flink CEP Pattern

```
Pattern: 3 consecutive spikes > baseline+4σ within 10 seconds
→ Reduces false positives from single sensor noise
→ Follows FloodAlertJob pattern (3 consecutive readings)
```

```java
Pattern<NgsiLdMessage, ?> structuralPattern = Pattern
    .<NgsiLdMessage>begin("spike1")
    .where(SimpleCondition.of(msg -> isStructuralAnomaly(msg)))
    .timesOrMore(3)
    .consecutive()
    .within(Time.seconds(10));
```

### Threshold Values (TCVN 9386:2012 + ISO 4866)

| Sensor Type | Warning | Critical | Unit |
|------------|---------|----------|------|
| STRUCTURAL_VIBRATION | 10 | 50 | mm/s |
| STRUCTURAL_TILT | 3 | 10 | mrad |
| STRUCTURAL_CRACK | 0.3 | 2.0 | mm |
| STRUCTURAL_VIBRATION_FREQ | 5 | 20 | Hz |
| STRUCTURAL_SETTLEMENT | 5 | 20 | mm |
| STRUCTURAL_HUMIDITY | 70 | 90 | % |

**Dual threshold logic:**
- Welford 4σ check là **adaptive** (bắt subtle drift)
- TCVN threshold là **absolute floor** — alert phải vượt CẢ 2 điều kiện:
  - `value > μ + 4σ` (statistical anomaly)
  - `value > WARNING_THRESHOLD` (regulatory minimum)

### Safety Score Algorithm (0-100)

```java
int calculateSafetyScore(Building building) {
    // Base score = 100
    int score = 100;

    // Deductions per active alert
    for (Alert alert : activeStructuralAlerts) {
        switch (alert.getSeverity()) {
            case "CRITICAL" -> score -= 25;  // P0: -25 per alert
            case "WARNING"  -> score -= 10;  // P1: -10 per alert
            case "ADVISORY" -> score -= 5;   // P2: -5 per alert
        }
    }

    // Sensor health deduction
    long offlineSensors = structuralSensors.stream()
        .filter(s -> s.getStatus() == OFFLINE).count();
    score -= (int) (offlineSensors * 5); // -5 per offline sensor

    return Math.max(0, score);
}
```

### Kafka Schema — StructuralAlertEvent

```json
{
  "eventId": "uuid",
  "sensorId": "SEN-STRUCT-BLD1-001",
  "sensorType": "STRUCTURAL_VIBRATION",
  "tenantId": "hcm",
  "buildingId": "BLD-DEFAULT-001",
  "measuredValue": 55.2,
  "meanValue": 8.3,
  "stdDevValue": 2.1,
  "thresholdValue": 50.0,
  "severity": "CRITICAL",
  "district": "Quận 1",
  "observedAt": 1748860800000,
  "consecutiveSpikes": 3,
  "safetyScore": 65
}
```

### REST API Contracts

```
GET /api/v1/buildings/{id}/safety
  → Response: {
      "buildingId": "...",
      "score": 85,
      "status": "NORMAL",      // NORMAL | WARNING | CRITICAL | OFFLINE
      "lastUpdated": "2026-06-02T10:00:00Z",
      "activeAlerts": 2,
      "onlineSensors": 6,
      "offlineSensors": 1
    }

GET /api/v1/buildings/{id}/vibration/readings?range=24h&type=VIBRATION
  → Response: {
      "buildingId": "...",
      "sensorType": "VIBRATION",
      "readings": [
        {"timestamp": "...", "value": 5.2, "mean": 4.8, "stdDev": 1.2, "isAnomaly": false}
      ],
      "thresholdWarning": 10.0,
      "thresholdCritical": 50.0
    }
```

### Migration V31

```sql
-- V31__structural_sensor_types.sql
INSERT INTO alert_rules (sensor_type, measure_type, operator, threshold, severity, unit)
VALUES
  ('STRUCTURAL_VIBRATION', 'VIBRATION', '>=', 10.0, 'WARNING', 'mm/s'),
  ('STRUCTURAL_VIBRATION', 'VIBRATION', '>=', 50.0, 'CRITICAL', 'mm/s'),
  ('STRUCTURAL_TILT', 'TILT', '>=', 3.0, 'WARNING', 'mrad'),
  ('STRUCTURAL_TILT', 'TILT', '>=', 10.0, 'CRITICAL', 'mrad'),
  ('STRUCTURAL_CRACK', 'CRACK', '>=', 0.3, 'WARNING', 'mm'),
  ('STRUCTURAL_CRACK', 'CRACK', '>=', 2.0, 'CRITICAL', 'mm')
ON CONFLICT DO NOTHING;
```

---

## Alternatives Considered

| Alternative | Pros | Cons | Decision |
|------------|------|------|----------|
| **Fixed threshold only** (no Welford) | Đơn giản, dễ implement | False positive cao (gió, xe cộ); không adaptive per-building | ❌ Rejected |
| **Isolation Forest** (ML) | Bắt complex patterns | Cần training data; latency cao; overkill cho 1D time-series | ❌ Rejected (defer to post-pilot) |
| **Exponential Moving Average** | Đơn giản hơn Welford | Không có variance → không có confidence interval | ❌ Rejected |
| **Welford + CEP** ✅ | Adaptive, stateless-friendly, proven | Cold start period (n<1000) | ✅ Selected |

---

## Consequences

### Positive
- **Adaptive baseline** — mỗi sensor tự build baseline riêng, không cần manual config per-building
- **Low false positive** — dual check (4σ + TCVN threshold) + 3-spike pattern
- **Online algorithm** — không cần batch processing, state fits trong Flink RocksDB
- **Proven pattern** — reuse FloodAlertJob CEP architecture

### Negative
- **Cold start period** — n<1000 readings (~100 giây @ 10Hz) trước khi anomaly detection hoạt động
- **State size** — WelfordState per sensor (3 doubles + 1 long = ~32 bytes) — trivial cho RocksDB
- **4-sigma tuning** — có thể cần điều chỉnh nếu baseline quá narrow/wide

### Risk Mitigation
- Cold start: pre-seed từ historical data (optional Sprint 8)
- BR-010: P0 = operator review only, không auto-evacuate
- DLQ topic cho failed structural alerts

---

## Implementation Map

| Component | Package | Sprint 7 Task |
|-----------|---------|---------------|
| WelfordStdDev | `flink-jobs/com.uip.flink.structural` | B2-2 |
| VibrationAnomalyJob | `flink-jobs/com.uip.flink.structural` | B2-2 |
| StructuralAlertEvent | `flink-jobs/com.uip.flink.structural` | B2-2 |
| BuildingSafetyService | `backend/com.uip.backend.safety.service` | B2-3 |
| BuildingSafetyController | `backend/com.uip.backend.safety.controller` | B2-4 |
| StructuralAlertConsumer | `backend/com.uip.backend.safety.consumer` | B2-5 |
| V31 migration | `backend/db/migration` | B2-3 |
| SafetyScoreGauge | `frontend/components/safety` | FE-1 |
| SafetyTrendChart | `frontend/components/safety` | FE-2 |

---

*ADR-034 approved 2026-06-02 | References: [Sprint 7 Plan](../project/sprint7-plan.md) | [TCVN 9386:2012](https://vanbanphapluat.co/tcvn-9386-2012) | [ISO 4866](https://www.iso.org/standard/28051.html)*
