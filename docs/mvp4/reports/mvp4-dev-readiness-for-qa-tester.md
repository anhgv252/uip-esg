# MVP4 — Dev Readiness Assessment for QA & Tester Handoff

| Field | Value |
|---|---|
| **Date** | 2026-06-16 |
| **Scope** | Tất cả 27 MVP4 tasks — kiểm tra trạng thái dev để chuyển sang QA/Tester |
| **Kết luận** | ✅ **DEV PHASE COMPLETE — Sẵn sàng chuyển QA/Tester, cần DevOps deploy staging trước** |

---

## 1. Tổng quan trạng thái

| Role | Tasks | SP | Dev Status | Notes |
|------|-------|----|-----------|-------|
| Backend Eng | #1–4, #9, #13, #15, #17, #21, #25 | 110 SP | ✅ **ALL DEV DONE** | 1,726 unit tests, 0 fail |
| Frontend Eng | #5, #10, #14, #18, #22 | 49 SP | ✅ **ALL DEV DONE** | tsc 0 errors, 192 FE tests PASS |
| DevOps | #6, #12, #16, #19, #24 | 21.5 SP | ✅ **ALL DEV DONE** | Grafana dashboards, monitoring wired |
| SA Code Review | Sprint 6 review | — | ✅ **APPROVED** (G8 PASS) | sprint6-code-review.md |
| QA (automated) | #7, #11, #20, #23 | 41 SP | ✅ **ALL DEV DONE** | REST Assured, Pact, E2E, UAT scaffolding |
| QA (final gate) | **#26** | 5 SP | ⏳ **PARTIAL** | 5/10 gates PASS, 5 need staging |
| PM | #8, #27 | 5 SP | ⏳ **PARTIAL** | Draft docs done, KPIs/demo pending gate |

---

## 2. Chi tiết Backend — tất cả tasks ✅ DEV DONE

| Task | ID | SP | Sprint | Status | Key Evidence |
|------|----|----|--------|--------|--------------|
| #1 JWT IT + Rate limiter IT + SQL injection | v3.1-09/10/11 | 7 | S1 | ✅ | Tests GREEN, 429 headers verified |
| #2 BMS sendCommand + MQTT race fix + TODO | GAP-005/009/011 | 8 | S1 | ✅ | BACnet IP real exec, 0 TODO markers |
| #3 Env/Traffic controllers + CO2 config | GAP-020/021/007 | 7 | S1 | ✅ | ≥80% coverage, CO2 externalized |
| #4 DLQ audit + PII mask + Refactor | v3.1-13/16, GAP-033/034 | 5.5 | S1 | ✅ | Emails masked, ObjectMapper refactor |
| #9 Analytics + Water intensity + AI batching + Routing | 10 items | 25 | S2 | ✅ | DistrictAggregationJob, ModelRouter 9T, TokenBudget 10T |
| #13 Smart pre-filter + CEP correlation + Feedback API | M4-AI-03/COR-01/06 | 12 | S3 | ✅ | SmartPreFilter 13T, CorrelationScoring 9T, AlertFeedback 6T |
| #15 Welford Universal START | M4-AI-07 | 3 | S3 | ✅ | 12 tests, 5 sensor types |
| #17 Correlation complete + Payload builder + Drift | M4-COR-01/02/05 | 16 | S4 | ✅ | CorrelationService 8T+E2E 8T, DriftDetector 11T |
| #21 BMS auto-command + Feedback loop START | M4-COR-03/04 | 13 | S5 | ✅ | 2-step confirm, BR-010 enforced |
| #25 Feedback loop complete + Incident feedback + Welford | M4-COR-04/07, M4-AI-07 | 16 | S6 | ✅ | ≥3 suggestions, 10 sensor types, ADR-041→046 |

---

## 3. Chi tiết Frontend — tất cả tasks ✅ DEV DONE

| Task | ID | SP | Sprint | Status | Key Evidence |
|------|----|----|--------|--------|--------------|
| #5 BPMN UX + Code-split + Accessibility | v3.1-04/05/17, GAP-027/028 | 7 | S1 | ✅ | Bundle 15.91KB (<200KB), aria-labels, 0 raw hex |
| #10 Mobile offline + DnD + Traffic API + RQ | v3.1-03, M4-SS-03, GAP-029/031 | 16 | S2 | ✅ | Offline banner, HTML5 DnD, React Query wired |
| #14 Template Library start + Feedback UI | M4-SS-01, M4-COR-06 | 10 | S3 | ✅ | 5 templates, TemplateGallery, AlertFeedbackButton |
| #18 Template Library complete + Wizard start | M4-SS-01/02 | 11 | S4 | ✅ | 10 templates, WorkflowWizard 3-step, 192 tests PASS |
| #22 Wizard UI complete | M4-SS-02 | 5 | S5 | ✅ | Deploy confirm dialog, success/error states |

---

## 4. Chi tiết DevOps — tất cả tasks ✅ DEV DONE

| Task | ID | SP | Sprint | Status | Key Evidence |
|------|----|----|--------|--------|--------------|
| #6 Pilot infra + Mobile stores | P0-1→4, v3.1-01/02 | 7.5 | S1 | ✅ | FCM/APNs wired, 0 CHANGE_ME, mem_limit, store guides |
| #12 Kong JWKS + Flink automation + Avro | v3.1-15, GAP-036/037 | 6 | S2 | ✅ | JWKS in kong.staging.yml, health-check.sh, Avro script |
| #16 Redis AI caching | M4-AI-04 | 3 | S3 | ✅ | @Cacheable, TTL 300s, AiCacheConfigTest 21T |
| #19 AI Cost dashboard Grafana | M4-AI-06 | 3 | S4 | ✅ | ai-cost-dashboard.json (6 panels), AiCostMetrics 10T |
| #24 BMS command monitoring | DevOps monitoring | 2 | S5 | ✅ | bms-commands-dashboard.json (7 panels), Prometheus alerts |

---

## 5. Quality Gates G1-G10 — trạng thái hiện tại

| Gate | Criterion | Status | Verify bằng | Người thực hiện |
|------|-----------|--------|-------------|----------------|
| **G1** | AI cost < $1/ngày @ 10K sensors | ⏳ **PENDING** | Grafana + 10K sensor simulation | QA + DevOps (staging) |
| **G2** | False positive < 5% (30-day data) | ⏳ **PENDING** (boundary PASS) | SQL query trên pilot data | QA (sau 30-day pilot) |
| **G3** | ≥10 templates operator-verifiable | ✅ **PASS** | sprint4-template-uat.md | — |
| **G4** | Regression ≥1,500 tests, 0 fail | ✅ **PASS** | 1,726 tests, 0 fail | — |
| **G5** | 1000 VU JMeter PASS | ⏳ **PENDING** | uip-1000vu-plan.jmx trên staging | QA (staging) |
| **G6** | iOS + Android apps live | ⏳ **PENDING** | Store links | DevOps (ops task) |
| **G7** | BMS safety (2-step confirm) | ✅ **PASS** | BmsFeedbackServiceTest PASS | — |
| **G8** | SA Code Review APPROVED | ✅ **PASS** | sprint6-code-review.md | — |
| **G9** | OWASP 0 Critical/High CVEs | ✅ **PASS** | dependencyCheckAggregate (2026-06-15) | — |
| **G10** | Pilot uptime ≥99.5%/30d | ⏳ **PENDING** | Prometheus uptime | QA/DevOps (30-day) |

**5/10 PASS** | **5/10 cần staging/pilot execution** | **0 FAIL**

---

## 6. UAT Sign-offs còn PENDING

Các UAT document đã có scaffolding nhưng chữ ký chưa được thu thập:

| UAT Document | Sprint | Status | Action cần |
|---|---|---|---|
| `uat/sprint4-template-uat.md` | S4 | Docs tạo | Verify operator sign-off column |
| `uat/sprint4-correlation-test-results.md` | S4 | Docs tạo | Verify boundary results confirmed |
| `uat/sprint5-bms-safety-uat.md` | S5 | ⚠️ **PENDING SIGN-OFF** | Safety Officer + QA Engineer ký |
| `uat/sprint5-wizard-uat.md` | S5 | ⚠️ **PENDING SIGN-OFF** | QA Engineer + City Authority Rep ký; TC-4/5 còn PENDING |

---

## 7. Handoff: Việc cần làm theo thứ tự

### Bước 1 — DevOps (prerequisite, ngay bây giờ)
- [ ] Deploy MVP4 lên staging environment (docker-compose.ha.yml)
- [ ] Verify cả 2 Flink jobs running: `DistrictAggregationJob` + `IncidentCorrelationJob`
- [ ] Confirm staging JWT / auth headers cho JMeter plan (xem runbook §G5 "Pre-run auth check")
- [ ] Configure Claude API key cho AI cost measurement (G1)

### Bước 2 — Tester (sau khi staging UP)
- [ ] **Smoke test** toàn bộ MVP4 features trên staging
- [ ] **BMS Safety UAT** — hoàn thiện `uat/sprint5-bms-safety-uat.md`: ký Safety Officer + QA Engineer
- [ ] **Wizard UAT** — hoàn thiện `uat/sprint5-wizard-uat.md`: chạy TC-4 (AQI), TC-5 (Traffic), thu chữ ký Operator
- [ ] **Regression smoke** — verify acceptance criteria của tất cả 10 BA stories
- [ ] Report bất kỳ P0/P1 bug mới trước khi QA gate run

### Bước 3 — QA (sau Tester smoke test, song song nếu staging sẵn sàng)
- [ ] **G5 — JMeter 1000 VU:** `jmeter -n -t backend/src/test/resources/jmeter/uip-1000vu-plan.jmx -l results.jtl -e -o report/` (xem [mvp4-staging-gate-runbook.md](mvp4-staging-gate-runbook.md) §G5)
- [ ] **G1 — AI cost simulation:** chạy 10K sensor load theo runbook §G1, observe Grafana 24h
- [ ] **G4 — Regression final run:** `cd backend && ./gradlew test` (Docker up for ITs) — confirm vẫn 1,726+, 0 fail
- [ ] Update Task #26 acceptance criteria với kết quả thực tế

### Bước 4 — QA + DevOps (dài hạn)
- [ ] **G6:** Hoàn thiện iOS/Android submission — verify apps live trên stores
- [ ] **G2 + G10:** Bắt đầu 30-day pilot (Aug 2026) — đo FP rate và uptime

### Bước 5 — PM (sau G5 + G1 PASS)
- [ ] Task #27: Fill KPI actuals vào `reports/mvp4-summary-draft.md`
- [ ] Schedule stakeholder demo (script đã có: `reports/mvp4-stakeholder-demo-script.md`)
- [ ] **DECLARE MVP4 DONE** khi task #26 đủ điều kiện

---

## 8. Lệnh chạy nhanh cho QA/Tester

```bash
# G4 — full regression (local, Docker required for ITs)
cd backend && ./gradlew test

# G9 — OWASP (đã PASS 2026-06-15, re-run để verify sau staging deploy)
cd backend && ./gradlew dependencyCheckAggregate

# G5 — JMeter 1000 VU (chạy trên staging)
jmeter -n -t backend/src/test/resources/jmeter/uip-1000vu-plan.jmx -l results.jtl -e -o report/

# Verify Flink jobs running (staging)
# DistrictAggregationJob + IncidentCorrelationJob phải RUNNING trong Flink UI
```

---

## 9. Kết luận

| Hạng mục | Trạng thái |
|----------|-----------|
| **Dev phase (Backend + Frontend + DevOps)** | ✅ **HOÀN THÀNH** — 24/24 tasks DEV DONE |
| **SA Code Review** | ✅ **APPROVED** — 0 P0 issues remaining |
| **OWASP G9** | ✅ **CLEARED** — 0 active CVE CVSS≥7 |
| **Automated regression** | ✅ **1,726 tests, 0 fail** |
| **Staging-dependent gates (G1/G2/G5/G6/G10)** | ⏳ **PENDING** — DevOps deploy blocking |
| **UAT sign-offs** | ⚠️ **2 docs PENDING** (BMS Safety + Wizard) |
| **QA Final Gate (#26)** | ⏳ **PARTIAL — 5/10 PASS** |

**Blocker duy nhất để bắt đầu Tester/QA execution:** DevOps cần deploy MVP4 lên staging và verify cả 2 Flink jobs running. Sau đó Tester và QA có thể chạy song song.

---

*Tạo bởi: UIP Team Orchestrator | 2026-06-16*
