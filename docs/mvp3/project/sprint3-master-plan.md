# Sprint MVP3-3 — Master Plan

**Status:** DRAFT — CHO PO REVIEW
**Ngày tạo:** 2026-05-19
**Sprint:** MVP3-3 — ESG Reporting, Keycloak RSA & Infrastructure Hardening
**Thời gian sprint:** 2026-05-19 (Mon) → 2026-05-30 (Fri)
**Gate Review:** 2026-05-30 15:00 SGT — PO Demo Live
**Sprint trước:** MVP3-2 (HARD PASS, 8/8 AC)
**PO:** anhgv

---

## 1. Mục tiêu Sprint 3 — Lời PO

> **Tuyên bố mục tiêu (non-technical):**
> Sau Sprint 3, Building Cluster Manager có thể **xuất báo cáo ESG (GRI 302/305) ra Excel/PDF** gửi City Authority, hệ thống chạy trên **Keycloak RSA authentication** (an toàn hơn HMAC), và ClickHouse có **2-node cluster** chịu lỗi.

### Tại sao Sprint 3 quan trọng với business?

| Vấn đề Sprint 2 để lại | Sprint 3 giải quyết |
|------------------------|---------------------|
| ESG data chỉ xem trên dashboard, không xuất được file | **GRI Export** — Excel/PDF cho City Authority, deadline 2026-06-15 |
| Auth dùng HMAC (không salt chuẩn, không audit trail) | **Keycloak RSA** — JWT signed bởi Keycloak, có audit trail |
| ClickHouse single-node — fail = mất analytics | **ClickHouse HA** — 2-node cluster + failover |
| Flink enrichment dùng backfill, không streaming | **Enrichment inline** — BuildingMetadataAsyncFunction trong pipeline |
| Nginx proxy config manual mỗi rebuild | **Docker volume** — config persists |

### Sprint 3 KHÔNG scope

- Mobile app (React Native) → Sprint 5
- AI forecasting (ARIMA/LSTM) → Sprint 4 (detail-plan)
- Billing/invoicing → Sprint 5
- Citizen portal PWA offline → Sprint 5
- BMS SDK (Modbus/BACnet) → Sprint 4

### City Authority Deadline

**2026-06-15** — City Authority ESG reporting deadline. Sprint 3 phải deliver GRI 302/305 export đúng hạn. Đây là non-negotiable deadline.

---

## 2. PO Demo Live — Gate Nghiệm Thu

### Format

- **Thời gian:** 2026-05-30 15:00 SGT (45 phút)
- **Người trình bày:** Backend Lead + Frontend Dev + QA Lead
- **PO:** anhgv — xem demo live, ký xác nhận từng AC
- **Đối tượng thêm:** City Authority stakeholder (optional)

### Demo Flow

| Phần | Nội dung | Thời lượng | AC nghiệm thu |
|------|----------|------------|---------------|
| Part 1 | GRI 302/305 Export — Xuất file Excel/PDF live | 12 phút | AC-01 |
| Part 2 | Keycloak RSA — Login RSA token, HMAC fallback | 8 phút | AC-02 |
| Part 3 | ClickHouse HA — Kill 1 node, analytics vẫn chạy | 5 phút | AC-03 |
| Part 4 | Flink Enrichment Inline — Real-time metadata join | 5 phút | AC-04 |
| Part 5 | Regression + Bug Fixes — 103/103 PASS | 5 phút | AC-05, AC-06 |
| Part 6 | Gate Summary + Q&A | 10 phút | — |

### Verdict

- **HARD PASS** — Tất cả AC-01 đến AC-06 PASS
- **CONDITIONAL PASS** — ≤1 AC CONDITIONAL (không FAIL), có plan fix Week 1 Sprint 4
- **FAIL** — Bất kỳ AC-01/02/05 nào FAIL → Sprint 3 extend thêm 3 ngày

---

## 3. Acceptance Criteria — Danh sách nghiệm thu PO

> **Quy tắc:** PO ký xác nhận từng mục sau khi xem demo live. Tất cả 6 AC bắt buộc PASS để sprint được công nhận hoàn thành.

### AC-01: GRI 302/305 Export Excel/PDF ⭐ P0
> Building Manager chọn quarter → xuất file Excel/PDF GRI 302 (Energy) và GRI 305 (Emissions) gửi City Authority.

**Tiêu chí PASS:**
- [ ] API `POST /api/v1/esg/report/generate` trả file download
- [ ] Excel chứa: total energy kWh, per-building breakdown, period, GRI Disclosure 302-1
- [ ] PDF format printable A4
- [ ] Frontend panel: Year selector, Quarter selector, Generate button, Download link
- [ ] File size <5MB cho 48 buildings

**Demo point:** Part 1 — Xuất file live trước mặt PO

---

### AC-02: Keycloak RSA Authentication Active ⭐ P0
> Mọi API call sử dụng JWT signed bởi Keycloak RSA, HMAC chỉ là fallback.

**Tiêu chí PASS:**
- [ ] RoutingJwtDecoder verify cả HMAC và RSA tokens
- [ ] New login qua Keycloak → RSA token issued
- [ ] Old HMAC token vẫn hoạt động (grace period)
- [ ] Keycloak UI accessible, realm configured (`uip` realm)
- [ ] Logout → token invalidated

**Demo point:** Part 2 — Login RSA token, verify HMAC fallback

---

### AC-03: ClickHouse 2-node HA ⭐ P1
> ClickHouse cluster 2 node, 1 node fail → analytics vẫn available.

**Tiêu chí PASS:**
- [ ] 2 ClickHouse nodes healthy
- [ ] Kill 1 node → queries vẫn trả kết quả (qua node còn lại)
- [ ] Replica lag < 5 seconds
- [ ] Flink sink writes to both replicas

**Demo point:** Part 3 — Kill 1 CH node, dashboard vẫn load

---

### AC-04: Flink Enrichment Inline ⭐ P1
> Flink pipeline enrich building_name + district in real-time, không cần backfill.

**Tiêu chí PASS:**
- [ ] BuildingMetadataAsyncFunction trong Flink DAG
- [ ] New sensor event → building_name populated tự động
- [ ] No more manual backfill needed
- [ ] Latency impact <100ms p99

**Demo point:** Part 4 — Inject event mới, verify building_name tự populate

---

### AC-05: No Regression from Sprint 2 ⭐ P0
> 103/103 regression tests PASS, ESG dashboard vẫn hoạt động.

**Tiêu chí PASS:**
- [ ] 103/103 tier-1 API tests PASS
- [ ] ESG dashboard load + charts functioning
- [ ] Analytics API response <1s

**Demo point:** Part 5 — Chạy regression live

---

### AC-06: P2 Bug Fixes ⭐ P2
> 3 P2 bugs từ Sprint 2 đã fix.

**Tiêu chí PASS:**
- [ ] P2-001: Chart tooltip no truncation
- [ ] P2-002: AQI data fresh (poll interval reduced)
- [ ] P2-003: Filter reset smooth (no 150ms delay)

**Demo point:** Part 5 — Show fixes trên dashboard

---

## 4. Stories & Capacity

### Epic 1: ESG Reporting (GRI 302/305 Export) — 23 SP

| # | Story | SP | Owner | Priority | Week |
|---|-------|-----|-------|----------|------|
| S3-01 | GRI 302 (Energy) report generation backend | 5 | Backend Eng 1 | P0 | W1 |
| S3-02 | GRI 305 (Emissions) report generation backend | 3 | Backend Eng 1 | P0 | W2 |
| S3-03 | Excel export service (Apache POI / EasyExcel) | 5 | Backend Eng 1 | P0 | W2 |
| S3-04 | PDF export service (iText / Jasper) | 5 | Backend Eng 1 | P1 | W2 |
| S3-05 | Frontend report generation panel | 5 | Frontend Eng | P0 | W2 |

### Epic 2: Keycloak RSA Migration — 10 SP

| # | Story | SP | Owner | Priority | Week |
|---|-------|-----|-------|----------|------|
| S3-06 | RoutingJwtDecoder dual-issuer (HMAC + RSA) | 5 | Backend Lead | P0 | W1 |
| S3-07 | Keycloak realm config + client setup | 3 | DevOps | P0 | W1 |
| S3-08 | Token migration guide + fallback | 2 | Backend Lead | P1 | W2 |

### Epic 3: Infrastructure Hardening — 17 SP

| # | Story | SP | Owner | Priority | Week |
|---|-------|-----|-------|----------|------|
| S3-09 | ClickHouse 2-node HA cluster | 8 | DevOps | P1 | W2 |
| S3-10 | ClickHouse HA failover test | 3 | DevOps | P1 | W2 |
| S3-11 | Nginx config in docker-compose volume | 1 | DevOps | P2 | W1 |
| S3-12 | Flink enrichment inline (BuildingMetadataAsyncFunction) | 5 | Backend Eng 2 | P1 | W1 |

### Carry-over from Sprint 2

| # | Item | SP | Owner | Priority | Week |
|---|------|-----|-------|----------|------|
| S3-13 | P2-001: Chart tooltip truncation fix | 1 | Frontend Eng | P2 | W1 |
| S3-14 | P2-002: AQI stale data fix (reduce poll interval) | 1 | Frontend Eng | P2 | W1 |
| S3-15 | P2-003: Filter reset animation fix | 0.5 | Frontend Eng | P2 | W2 |

**Total stories: 52.5 SP**

### Capacity Analysis

| Factor | Value |
|--------|-------|
| Team capacity (2 tuần, 5 FTE) | ~75-80 SP |
| Sprint 3 stories | ~52.5 SP |
| Carry-over TD (urgent: KC, FL, NG) | ~14 SP |
| **Total demand** | **~66.5 SP** |
| **Buffer** | **~8-13 SP** |
| Confidence | **80%** |

> **Lưu ý:** Verification report Sprint 2 cảnh báo total demand ~80-85 SP. Với việc descope TD-EM-01 (3 SP) và TD-PROXY-01 (3 SP) sang Sprint 4, committed load giảm xuống ~67 SP — trong capacity.

### Descoped sang Sprint 4

| ID | Item | SP | Lý do |
|----|------|-----|-------|
| TD-EM-01 | EMQX unhealthy status | 3 | Không critical, Kafka path proven |
| TD-PROXY-01 | Remove AnalyticsProxyController | 3 | Theo ADR-028, chờ Kong production-ready |

---

## 5. Week-by-Week Plan

### Week 1: Critical Path + Foundation (2026-05-19 → 2026-05-23)

**Theme:** Keycloak RSA + GRI backend + Flink inline

| Thứ tự | Item | SP | Owner | Dependency | Deadline |
|---------|------|-----|-------|------------|----------|
| 1 | S3-06: RoutingJwtDecoder dual-issuer | 5 | Backend Lead | None | Wed 05-21 review |
| 2 | S3-07: Keycloak realm config | 3 | DevOps | None | Tue 05-20 |
| 3 | S3-01: GRI 302 energy report backend | 5 | Backend Eng 1 | None | Thu 05-22 |
| 4 | S3-12: Flink enrichment inline | 5 | Backend Eng 2 | None | Thu 05-22 |
| 5 | S3-13 + S3-14: P2 bug fixes | 2 | Frontend Eng | None | Wed 05-21 |
| 6 | S3-11: Nginx docker-compose volume | 1 | DevOps | None | Tue 05-20 |
| | **Week 1 subtotal** | **21 SP** | | | |

**Week 1 DoD:**
- [ ] RoutingJwtDecoder verify cả HMAC + RSA tokens
- [ ] Keycloak realm `uip` configured, client setup
- [ ] GRI 302 backend API returning correct energy aggregates
- [ ] BuildingMetadataAsyncFunction trong Flink DAG
- [ ] P2-001 + P2-002 fixed
- [ ] Regression 103/103 PASS (mid-week checkpoint)

### Week 2: Features + Validation (2026-05-26 → 2026-05-30)

**Theme:** GRI export UI + ClickHouse HA + Gate prep

| Thứ tự | Item | SP | Owner | Dependency | Deadline |
|---------|------|-----|-------|------------|----------|
| 7 | S3-02: GRI 305 emissions report backend | 3 | Backend Eng 1 | S3-01 | Mon 05-26 |
| 8 | S3-03: Excel export service | 5 | Backend Eng 1 | S3-01, S3-02 | Wed 05-28 |
| 9 | S3-04: PDF export service | 5 | Backend Eng 1 | S3-03 | Thu 05-29 |
| 10 | S3-05: Frontend report panel | 5 | Frontend Eng | S3-03 | Thu 05-29 |
| 11 | S3-08: Token migration guide | 2 | Backend Lead | S3-06 | Tue 05-27 |
| 12 | S3-09 + S3-10: ClickHouse HA + failover test | 11 | DevOps | None | Thu 05-29 |
| 13 | S3-15: P2-003 filter animation fix | 0.5 | Frontend Eng | None | Wed 05-28 |
| 14 | Regression + Sprint 3 demo prep | — | QA + All | All | Fri 05-30 |
| | **Week 2 subtotal** | **31.5 SP** | | | |

**Week 2 DoD:**
- [ ] Excel export: PO download file, verify data đúng
- [ ] PDF export: PO download file, printable A4
- [ ] Frontend: report panel có Year/Quarter selector + Download button
- [ ] ClickHouse HA: 2-node cluster, failover test PASS
- [ ] Migration guide documented
- [ ] Regression 103/103 PASS
- [ ] Demo dry-run thành công (Thu 05-29)

---

## 6. Dependency Graph

```
Week 1 (parallel tracks):
  Backend Lead:  S3-06 (RSA decoder) ─────────────────────────→ S3-08 (migration guide, W2)
  DevOps:        S3-07 (Keycloak realm) ──→ S3-11 (Nginx volume)
  Backend Eng 1: S3-01 (GRI 302) ─────────────────────────────→ S3-02 (GRI 305) → S3-03 (Excel) → S3-04 (PDF)
  Backend Eng 2: S3-12 (Flink inline)
  Frontend:      S3-13 + S3-14 (P2 fixes)

Week 2 (sequential):
  S3-01 + S3-02 ──→ S3-03 (Excel) ──→ S3-05 (Frontend panel)
                                  ──→ S3-04 (PDF, stretch)
  DevOps: S3-09 (CH HA) ──→ S3-10 (Failover test)

Gate:
  All ──→ Regression (103/103) ──→ Demo Prep ──→ PO Demo Live (Fri 15:00)
```

---

## 7. Sprint 3 Gate Checklist (8/8 Required for HARD PASS)

| Gate | Tiêu chí | AC link | Owner | Verify |
|------|----------|---------|-------|--------|
| G1 | GRI 302/305 export (Excel + PDF) PO download + verify data | AC-01 | Backend + Frontend | PO demo live |
| G2 | Keycloak RSA active, HMAC fallback working | AC-02 | Backend Lead + DevOps | PO demo live |
| G3 | ClickHouse 2-node HA, failover tested | AC-03 | DevOps | PO demo live (kill node) |
| G4 | Flink enrichment inline, no backfill needed | AC-04 | Backend Eng 2 | PO demo live |
| G5 | Regression 103/103 PASS | AC-05 | QA | Chạy live trước PO |
| G6 | P2 bugs fixed | AC-06 | Frontend Eng | PO demo live |
| G7 | Zero P0/P1 bugs open | — | All | Gate checklist |
| G8 | Sprint 3 demo PO sign-off | — | PM + PO | PO ký |

### Conditional Pass Criteria

| Scenario | Threshold | Action |
|----------|-----------|--------|
| AC-03 CH HA borderline | 1 node không sync kịp (<5s lag) | Root cause analysis, nếu <10s → CONDITIONAL, >10s → FAIL |
| AC-04 Flink inline latency | >100ms p99 nhưng <500ms | CONDITIONAL, investigate Sprint 4 |
| G5 Regression | 101-102/103 PASS (1-2 non-critical fail) | CONDITIONAL, fix Sprint 4 W1 |

---

## 8. Risk Register

| Risk ID | Description | Probability | Impact | Owner | Mitigation | Trigger |
|---------|-------------|-------------|--------|-------|------------|---------|
| R1 | Keycloak RSA migration break existing auth | 25% | HIGH | Backend Lead | Dual-issuer decoder, HMAC fallback, gradual rollout | S3-06 test fails |
| R2 | GRI format misalignment with City Authority | 40% | MED | BA + Backend | Build default subset (302+305), adjust after feedback | Stakeholder rejects format |
| R3 | ClickHouse HA data loss during migration | 15% | MED | DevOps | Full backup trước migration, Flink replay safety net | CH data count mismatch |
| R4 | Excel/PDF library compatibility issues | 10% | LOW | Backend Eng 1 | Apache POI + iText proven, POC Day 1 | Build failure |
| R5 | Overcommit — total demand >80 SP | 35% | HIGH | PM | Descoped TD-EM-01 + TD-PROXY-01, PDF stretch | Week 1 slip detection |
| R6 | City Authority deadline slip | 15% | CRITICAL | PM + PO | Weekly sync, lock GRI format by Wed W1 | Stakeholder không confirm format |

### Risk Mitigation Timeline

| Ngày | Mitigation Action | Owner |
|------|-------------------|-------|
| Mon 05-19 | Sprint kickoff risk review | PM |
| Tue 05-20 | Keycloak realm configured (blocker cho S3-06 test) | DevOps |
| Wed 05-21 | GRI format template confirm với stakeholder | BA + PO |
| Wed 05-21 | Week 1 carry-over gate: RSA + GRI backend progress check | PM |
| Fri 05-23 | Week 1 EOD gate: S3-06 + S3-01 merged, P2 fixes done | PM |
| Wed 05-28 | Mid-sprint review: Excel export functional, CH HA progress | PM |
| Thu 05-29 | Demo dry-run | All |
| Fri 05-30 | **GATE REVIEW — PO Demo Live 15:00** | All |

---

## 9. Daily Standup Schedule

- **Thời gian:** 09:30 SGT (Mon–Fri)
- **Thời lượng:** 15 phút
- **Format:** Blockers → Progress → Plan

### PM Checkpoints

| Ngày | Checkpoint | Go/No-Go |
|------|-----------|----------|
| Mon 05-19 10:00 | Sprint kickoff — review scope, confirm assignments | — |
| Tue 05-20 | Track S3-06 + S3-07 progress, verify DevOps setup | — |
| Wed 05-21 EOD | **WEEK 1 GATE 1:** RSA decoder testable, GRI format confirmed | YES/NO |
| Thu 05-22 | S3-01 + S3-12 progress, P2 fixes verified | — |
| Fri 05-23 EOW | **WEEK 1 GATE 2:** S3-06 merged, S3-01 functional, regression PASS | YES/NO |
| Mon 05-26 | CH HA setup, Excel export start | — |
| Wed 05-28 14:00 | **MID-SPRINT REVIEW:** Excel export functional, CH HA progress | YES/NO |
| Thu 05-29 10:00 | Demo dry-run (45 min) | — |
| Thu 05-29 15:00 | Backlog refinement (Sprint 4 prep) | — |
| Fri 05-30 13:00 | Sprint Review (Demo + PO Sign-Off) | — |
| Fri 05-30 15:00 | **GATE REVIEW — PO Demo Live** | **HARD/CONDITIONAL/FAIL** |
| Fri 05-30 16:00 | Sprint Retrospective | — |

---

## 10. Ceremony Schedule

| Ceremony | Thời gian | Thời lượng | Attendees |
|----------|-----------|------------|-----------|
| Daily Standup | 09:30 SGT, Mon–Fri | 15 min | Backend (2), Frontend (1), QA (1), DevOps (1), PM |
| Mid-Sprint Review | Wed 05-28 14:00 SGT | 30 min | Tech Lead, Backend, Frontend, QA, PM |
| Demo Dry-Run | Thu 05-29 10:00 SGT | 45 min | All team |
| Sprint Review (PO Demo) | Fri 05-30 13:00 SGT | 45 min | PO, Team, City Authority (optional) |
| Gate Review | Fri 05-30 15:00 SGT | 15 min | PO, Tech Lead, PM |
| Sprint Retrospective | Fri 05-30 16:00 SGT | 45 min | Team, PM |
| Backlog Refinement | Thu 05-29 15:00 SGT | 45 min | PM, Tech Lead, PO |

---

## 11. Escalation Matrix

| Issue | Threshold | Owner | Escalation | Authority |
|-------|-----------|-------|-----------|-----------|
| Capacity Risk | Story at risk >2 days | PM | CTO + PO | Deprioritize scope vs extend |
| Keycloak RSA break | Auth fails after migration | Backend Lead | CTO + PO | Rollback HMAC, re-plan |
| GRI format rejected | City Authority rejects format | BA + PM | PO | Emergency format adjustment |
| CH HA data loss | Data count mismatch | DevOps | Backend Lead + PM | Fallback single-node |
| P0/P1 bug found | Any P0/P1 | QA | PM + Backend Lead | Interrupt or hotfix |
| Gate failure | ≥2 gate criteria failing | PM | CTO + PO | CONDITIONAL PASS + Sprint 4 replan |
| Deadline risk | GRI export not ready by 05-28 | PM | PO | Descope PDF, focus Excel |

### Escalation Protocol

1. PM detects issue (daily standup hoặc mid-sprint review)
2. PM notifies owner + CTO (trong 2h)
3. Owner có 24h để propose mitigation (else auto-escalate PO)
4. CTO + PO meet trong 48h để decide scope/timeline
5. Decision communicated team by EOD next day

---

## 12. Pre-Sprint Requirements (Must Resolve by 2026-05-19)

| Blocker | Owner | ETA | Impact nếu miss |
|---------|-------|-----|-----------------|
| Keycloak realm + client config ready for testing | DevOps | Mon 05-19 PM | S3-06 blocked |
| GRI 302/305 format template confirm stakeholder | BA + PO | Mon 05-19 | S3-01 wrong format |
| Shadow 72h delta verify (G7 follow-up từ Sprint 2) | Backend + QA | Wed 05-22 | G7 Sprint 2 CONDITIONAL |

---

## 13. Descoped Items (Sprint 4+)

| ID | Item | SP | Lý do | Sprint |
|----|------|-----|-------|--------|
| TD-EM-01 | EMQX unhealthy status | 3 | Không critical, Kafka path proven | Sprint 4 |
| TD-PROXY-01 | Remove AnalyticsProxyController | 3 | Chờ Kong production-ready (ADR-028) | Sprint 4 |
| S3-04 | PDF export (nếu overload) | 5 | Excel là P0, PDF stretch | Sprint 4 nếu cần |

### Descope Plan (nếu velocity <50 SP/sprint)

| Item | Action |
|------|--------|
| S3-04: PDF export | → Sprint 4 (Excel đủ cho deadline) |
| S3-09/10: ClickHouse HA | → Sprint 4 (single-node stable) |
| S3-15: Filter animation | → Sprint 4 (cosmetic) |

---

## 14. Infrastructure Status at Sprint 3 Start

| Service | Status | Port | Notes |
|---------|--------|------|-------|
| uip-backend | healthy | 8080 | Analytics proxy active |
| uip-analytics-service | healthy | 8082 | 3 endpoints: energy, emissions, AQI |
| uip-clickhouse | healthy | 8123 | 302K rows, ReplacingMergeTree, 0 dups |
| uip-flink-jobmanager | healthy | 8081 | EsgDualSinkJob RUNNING |
| uip-flink-taskmanager | running | — | Dual-sink processing |
| uip-frontend | running | 3000 | Nginx + API proxy |
| uip-kafka | healthy | 29092 | 3 partitions, 0 consumer lag |
| uip-timescaledb | healthy | 5432 | Dual-sink target #1 |
| uip-redis | healthy | 6379 | Cache layer |
| uip-kong | healthy | 8000 | API Gateway |
| uip-keycloak | healthy | 8085 | RSA migration target |
| uip-minio | healthy | 9010 | Flink checkpoint storage |
| uip-emqx | unhealthy | 1883 | Known — Kafka path proven |

---

## 15. References

- `docs/mvp3/reports/sprint2-closeout-po-report.md` — Sprint 2 closeout (HARD PASS)
- `docs/mvp3/reports/sprint2-verification-report.md` — Sprint 2 verification (8 PASS + 2 CONDITIONAL)
- `docs/mvp3/project/demo-sprint2-po.md` — Sprint 2 PO demo (8/8 AC)
- `docs/mvp3/project/detail-plan.md` — MVP3 full detail plan
- `docs/mvp3/architecture/ADR-027-keycloak-hybrid-auth.md` — Keycloak architecture
- `docs/mvp3/architecture/ADR-028-kong-gateway-scope.md` — Kong scope decision
- `frontend/e2e/sprint2-po-demo.spec.ts` — Sprint 2 Playwright demo
- `docs/mvp3/qa/sprint2-shadow-72h-baseline.json` — Shadow 72h baseline

---

## 16. Sign-Off & Approval

| Role | Name | Status | Date |
|------|------|--------|------|
| **Product Owner** | anhgv | PENDING REVIEW | 2026-05-19 |
| **Tech Lead / CTO** | TBD | — | — |
| **Project Manager** | UIP PM | DRAFT APPROVED | 2026-05-19 |
| **QA Lead** | TBD | — | — |

---

**Document Version:** 1.0
**Created:** 2026-05-19
**Status:** DRAFT — CHO PO REVIEW
**Next Step:** PO review + sign-off → Sprint 3 kickoff

---

*Sprint MVP3-3 là sprint critical bridge giữa analytics foundation (Sprint 2) và AI/predictive features (Sprint 4). GRI export là non-negotiable deadline cho City Authority ESG reporting (2026-06-15). Tất cả gate criteria phải pass để proceed sang Sprint 4.*
