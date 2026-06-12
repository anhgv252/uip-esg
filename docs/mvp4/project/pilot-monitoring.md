# Pilot Monitoring Plan — MVP4

**Document ID:** M4-PM-001
**Created:** 2026-06-12
**Owner:** Project Manager
**Period:** 2026-08-04 → Pilot go-live onward

---

## 1. Daily Health Check

### Morning Check (09:00 local time)

| Metric | Source | Target | Alert Threshold |
|--------|--------|--------|-----------------|
| Platform uptime (24h) | Grafana dashboard | ≥99.5% | <99.0% |
| Error rate (5xx) | Prometheus | <0.1% | >0.5% |
| Alert delivery rate | Notification service logs | ≥98% | <95% |
| Kafka consumer lag | Kafka metrics | <100 msgs | >1000 msgs |
| Flink job health | Flink dashboard | RUNNING | FAILED/CANCELLING |
| Database connections | HikariCP metrics | <80% pool | >90% pool |
| Response time p95 | Kong metrics | <500ms | >1000ms |

### Checklist Template (copy daily)

```markdown
## Pilot Daily Health — [DATE]

- [ ] Platform uptime 24h: ___%
- [ ] Error rate 5xx: ___%
- [ ] Alerts delivered: ___/___ (___%)
- [ ] Kafka lag: ___ messages
- [ ] Flink jobs: all RUNNING? ___
- [ ] DB connections: ___/___ (___%)
- [ ] p95 latency: ___ms
- [ ] Any P0/P1 incidents? ___
- [ ] Notes: ___

**Status:** 🟢 GREEN / 🟡 YELLOW / 🔴 RED
```

---

## 2. Weekly Stakeholder Email

### Schedule
- **Day 7** (Aug 11): First weekly report
- **Day 14** (Aug 18): Sprint 1 close + Sprint 2 start
- Ongoing bi-weekly thereafter

### Email Template

```
Subject: UIP Pilot Update — Week [N] ([DATE])

Dear Stakeholders,

PILOT STATUS: [🟢 ON TRACK / 🟡 ATTENTION NEEDED / 🔴 ISSUE]

## Key Metrics This Week
- Uptime: [X]% (target: ≥99.5%)
- Alerts processed: [N] total, [N] delivered successfully ([X]%)
- Active users: [N] operators
- P0 incidents: [N]

## Highlights
- [Key accomplishment 1]
- [Key accomplishment 2]

## Issues / Risks
- [Issue 1] — Status: [OPEN/RESOLVED] — Impact: [description]

## Next Week Plan
- [Planned item 1]
- [Planned item 2]

Best regards,
UIP Project Team
```

---

## 3. Incident Tracking

### Incident Report Template

```markdown
## Incident Report — [INC-ID]

| Field | Value |
|-------|-------|
| **ID** | INC-[YYYYMMDD]-[SEQ] |
| **Severity** | P0 / P1 / P2 / P3 |
| **Started** | [datetime UTC+7] |
| **Resolved** | [datetime UTC+7] |
| **Duration** | [minutes] |
| **Affected Users** | [N operators / N citizens] |
| **Root Cause** | [description] |
| **Resolution** | [description] |
| **Follow-up** | [action items] |
```

---

## 4. Risk Register

| ID | Risk | Severity | Prob | Status | Mitigation | Owner |
|----|------|----------|------|--------|------------|-------|
| R1 | AI batching reduces accuracy | HIGH | 20% | 🟡 Monitoring | Critical events bypass batching | Backend |
| R2 | BMS auto-command safety | CRITICAL | 10% | 🟡 Monitoring | 2-step operator confirm | Backend |
| R3 | iOS Apple review reject | MEDIUM | 25% | 🟡 Monitoring | Android fallback, Day 1 submit | DevOps |
| R4 | Correlation engine complexity | MEDIUM | 30% | 🟡 Monitoring | Reuse VibrationAnomalyJob CEP | Backend |
| R5 | Pilot data insufficient | LOW | 20% | 🟢 Acceptable | Synthetic data fallback | QA |
| R6 | Mobile offline delays | MEDIUM | 25% | 🟡 Monitoring | Descope to cache-only | Frontend |
| R7 | Low operator feedback adoption | LOW | 40% | 🟢 Acceptable | Gamification | Frontend |

---

## 5. Coverage Claims — Corrected Values

> **IMPORTANT:** Actual coverage from JaCoCo XML analysis (2026-06-11):
> - **Line coverage: 76.3%** (3,675/4,815) — NOT 86% as previously claimed in some Sprint 6-7 materials
> - **Branch coverage: 61.4%** — NOT 70-71% as previously claimed
>
> The discrepancy is due to different denominators (with/without analytics-service and iot-ingestion-service).
> All investor-facing and stakeholder materials must use **76.3% / 61.4%**.

---

*Template created: 2026-06-12*
*Next update: Pilot Day 1 (2026-08-04)*
