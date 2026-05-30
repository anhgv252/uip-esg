# Sprint 6 — PO Demo Script + Pre-Demo Checklist

**Created:** 2026-05-30 | **PM**
**Demo Date:** Sprint 6 Gate Review (Day 10)

---

## Demo Scenarios (8 minutes total)

### Scenario 1: AI Workflow Designer (2 min)

**What to show:** Visual BPMN editor for urban AI workflows

1. Navigate to **AI Workflow Dashboard** → Click **"Designer"** tab
2. Click **"New Workflow"** → Canvas loads with Start Event
3. From **Node Palette** (right side): Drag "AI Decision" node onto canvas
4. Connect Start → AI Decision (draw edge)
5. Click AI Decision node → **AI Config Panel** appears:
   - Enter prompt: "Phân tích nguy cơ ngập lụt dựa trên dữ liệu cảm biến"
   - Set confidence threshold: 0.85 (slider)
   - Select model: Claude Sonnet 4.6
6. Click **"Save"** → "Saved (v1)" notification
7. Click **"Deploy"** → "Deployed to Camunda!" confirmation

**Talking points:**
- bpmn-js modeler — industry-standard BPMN 2.0
- Custom AI node with Claude API integration
- Confidence routing: >85% auto, 60-85% operator queue, <60% escalate

### Scenario 2: Flood Alert Pipeline E2E (3 min)

**What to show:** Sensor → Flink → Alert → UI in <30 seconds

1. Open terminal → run `./scripts/demo-flood-alert.sh`
2. Show script injecting 3 RAINFALL readings:
   - Reading #1: 95 mm/h
   - Reading #2: 88 mm/h
   - Reading #3: 102 mm/h
3. Switch to **Alerts page** → Filter by "FLOOD"
4. Flood alert appears with:
   - 🔴 P1 WARNING severity badge
   - Water level: 102.0 mm/h vs threshold 80.0
   - Location: district-7
   - Water Level Gauge with P0/P1/P2 markers
5. Switch to **Map view** → Orange circle marker at sensor location

**Backup:** If Flink not running, use direct inject:
```bash
curl -X POST localhost:8080/api/v1/test/inject-flood-alert \
  "?sensorId=SENSOR-DEMO-001&sensorType=RAINFALL&value=95&severity=P1_WARNING"
```

**Talking points:**
- Flink CEP: 3 consecutive readings > threshold within 10 min
- TCVN 9386:2012 thresholds: RAINFALL P2=50, P1=80, P0=120 mm/h
- Dedup: 5-min window prevents duplicate alerts
- Latency target: <30s end-to-end

### Scenario 3: Decision Routing (1 min)

**What to show:** AI confidence-based routing logic

1. Show decision matrix:
   - Confidence > 0.85 → **AUTO_EXECUTE** (green)
   - Confidence 0.6–0.85 → **OPERATOR_QUEUE** (yellow)
   - Confidence < 0.6 → **ESCALATE** (red)
2. Explain Redis cache: similar decisions cached 15 min

### Scenario 4: Blue-Green Deploy (1 min)

**What to show:** Zero-downtime deployment

1. `./scripts/blue-green-switch.sh status` → Active: blue
2. `./scripts/blue-green-switch.sh deploy` → Deploy to green
3. `./scripts/blue-green-switch.sh switch` → Switch <1s
4. `./scripts/blue-green-switch.sh rollback` → Back to blue

### Scenario 5: EMQX MQTT (1 min)

**What to show:** IoT MQTT broker for BMS commands

1. Open EMQX Dashboard (localhost:18083)
2. Show rules: `bms/commands/+/+` and `bms/heartbeat/+`
3. Explain MQTT auth per tenant

---

## Pre-Demo Checklist (12 items)

| # | Item | Status | Verify |
|---|------|--------|--------|
| 1 | Backend running (`./gradlew bootRun`) | ⏳ | `curl localhost:8080/actuator/health` |
| 2 | Kafka running + topics created | ⏳ | `kafka-topics --list` |
| 3 | Flink FloodAlertJob submitted | ⏳ | Flink dashboard |
| 4 | EMQX healthy | ⏳ | `curl localhost:18083/status` |
| 5 | Frontend built + served | ⏳ | `curl localhost:3000` |
| 6 | Demo script tested E2E | ⏳ | Run once before demo |
| 7 | 3 flood sensors pre-seeded | ⏳ | DB check |
| 8 | Camunda processes deployed | ⏳ | `/workflow/definitions` |
| 9 | Redis running (SSE + dedup) | ⏳ | `redis-cli ping` |
| 10 | V28 + V29 migrations applied | ⏳ | Flyway validate |
| 11 | Blue-green script tested | ⏳ | Dry run |
| 12 | Network stable | ⏳ | Internet + intranet |

---

## Backup Plan

If live demo fails:

| Scenario | Backup |
|----------|--------|
| AI Workflow Designer | Pre-recorded video (Loom) |
| Flood Alert E2E | Use `/inject-flood-alert` API bypass |
| Blue-green deploy | Show script output from previous run |
| EMQX | Show dashboard screenshot |

---

## Q&A Guide for PO

### Expected Questions

**Q: Flood alert có gửi SMS cho công dân không?**
A: Sprint 6 chỉ cover SSE (web push). SMS + FCM/APNs push là Tier 2, Sprint 7.

**Q: BPMN editor có hỗ trợ tất cả node types không?**
A: Sprint 6 hỗ trợ 5 nodes: Start, Service Task, AI Decision, Notification, End. Custom nodes thêm Sprint 7.

**Q: Latency <30s đã test chưa?**
A: Unit tests PASS. Manual E2E test sẽ chạy trong QA phase.

**Q: Mobile app khi nào có?**
A: React Native scaffold deferred Sprint 7 (Tier 2 carry-over).

**Q: Building Safety feature khi nào?**
A: Sprint 7 — Flink CEP + Welford algorithm cho structural monitoring.

---

## Demo Metrics to Report

| Metric | Value |
|--------|-------|
| Tier 1 Tasks | 10/10 DONE |
| Story Points | 34.5 SP delivered |
| Tests | 58 new, ALL PASS |
| TypeScript | 0 errors |
| SA Code Review | APPROVED (3 CRITICAL fixed) |
| Tier 2 | 26 SP deferred Sprint 7 |
