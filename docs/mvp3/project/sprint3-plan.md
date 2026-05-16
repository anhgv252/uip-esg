# Sprint MVP3-3 — Planning Document

**Ngày tạo:** 2026-05-16
**Sprint:** MVP3-3 — ESG Reporting, Keycloak RSA & Infrastructure Hardening
**Thời gian sprint:** 2026-05-19 → 2026-05-30
**Gate Review:** 2026-05-30 15:00 SGT
**Sprint trước:** MVP3-2 (HARD PASS, 8/8 AC)
**PO:** anhgv

---

## 1. Sprint 3 Objectives

> **Tuyên bố mục tiêu (non-technical):**
> Sau Sprint 3, Building Cluster Manager có thể **xuất báo cáo ESG (GRI 302/305) ra Excel/PDF** gửi City Authority, hệ thống chạy trên **Keycloak RSA authentication** (an toàn hơn HMAC), và ClickHouse có **2-node cluster** chịu lỗi.

### Tại sao Sprint 3 quan trọng?

| Vấn đề Sprint 2 để lại | Sprint 3 giải quyết |
|------------------------|---------------------|
| ESG data chỉ xem trên dashboard, không xuất được | ✅ **GRI Export** — Excel/PDF cho City Authority |
| Auth dùng HMAC (không salt chuẩn, không audit) | ✅ **Keycloak RSA** — JWT signed bởi Keycloak, audit trail |
| ClickHouse single-node — fail = mất analytics | ✅ **ClickHouse HA** — 2-node cluster + failover |
| Flink enrichment dùng backfill, không streaming | ✅ **Enrichment inline** — BuildingMetadataAsyncFunction trong pipeline |
| Nginx proxy config manual | ✅ **Docker volume** — config persists qua rebuild |

### Sprint 3 KHÔNG scope

- Mobile app (React Native) → Sprint 5
- AI forecasting → Sprint 4
- Billing/invoicing → Sprint 5
- Citizen portal PWA offline → Sprint 5

---

## 2. Sprint 3 Stories

### Epic 1: ESG Reporting (GRI 302/305 Export)

| # | Story | SP | Owner | Priority |
|---|-------|-----|-------|----------|
| S3-01 | GRI 302 (Energy) report generation backend | 5 | Backend Eng 1 | P0 |
| S3-02 | GRI 305 (Emissions) report generation backend | 3 | Backend Eng 1 | P0 |
| S3-03 | Excel export service (Apache POI / EasyExcel) | 5 | Backend Eng 1 | P0 |
| S3-04 | PDF export service (iText / Jasper) | 5 | Backend Eng 1 | P1 |
| S3-05 | Frontend report generation panel | 5 | Frontend Eng | P0 |

**Total: 23 SP**

### Epic 2: Keycloak RSA Migration

| # | Story | SP | Owner | Priority |
|---|-------|-----|-------|----------|
| S3-06 | RoutingJwtDecoder dual-issuer (HMAC + RSA) | 5 | Backend Lead | P0 |
| S3-07 | Keycloak realm config + client setup | 3 | DevOps | P0 |
| S3-08 | Token migration guide + fallback | 2 | Backend Lead | P1 |

**Total: 10 SP**

### Epic 3: Infrastructure Hardening

| # | Story | SP | Owner | Priority |
|---|-------|-----|-------|----------|
| S3-09 | ClickHouse 2-node HA cluster | 8 | DevOps | P1 |
| S3-10 | ClickHouse HA failover test | 3 | DevOps | P1 |
| S3-11 | Nginx config in docker-compose volume | 1 | DevOps | P2 |
| S3-12 | Flink enrichment inline (BuildingMetadataAsyncFunction) | 5 | Backend Eng 2 | P1 |

**Total: 17 SP**

### Carry-over

| # | Item | SP | Owner | Priority |
|---|------|-----|-------|----------|
| S3-13 | P2-001: Chart tooltip truncation fix | 1 | Frontend Eng | P2 |
| S3-14 | P2-002: AQI stale data fix (reduce poll interval) | 1 | Frontend Eng | P2 |
| S3-15 | P2-003: Filter reset animation fix | 0.5 | Frontend Eng | P2 |

**Total: 2.5 SP**

---

## 3. Sprint 3 Capacity

| Factor | Value |
|--------|-------|
| Team capacity (2 tuần) | ~75-80 SP |
| Sprint 3 stories | ~52.5 SP |
| Buffer | ~22-27 SP |
| Confidence | **85%** |

---

## 4. Week-by-Week Plan

### Week 1: Critical Path + Foundation (2026-05-19 → 2026-05-23)

| Thứ tự | Item | SP | Owner | Dependency |
|---------|------|-----|-------|------------|
| 1 | S3-06: RoutingJwtDecoder dual-issuer | 5 | Backend Lead | None |
| 2 | S3-07: Keycloak realm config | 3 | DevOps | None |
| 3 | S3-01: GRI 302 energy report backend | 5 | Backend Eng 1 | None |
| 4 | S3-12: Flink enrichment inline | 5 | Backend Eng 2 | None |
| 5 | S3-13 + S3-14: P2 bug fixes | 2 | Frontend Eng | None |
| 6 | S3-11: Nginx docker-compose volume | 1 | DevOps | None |
| | **Week 1 subtotal** | **21 SP** | | |

### Week 2: Features + Validation (2026-05-26 → 2026-05-30)

| Thứ tự | Item | SP | Owner | Dependency |
|---------|------|-----|-------|------------|
| 7 | S3-02: GRI 305 emissions report backend | 3 | Backend Eng 1 | S3-01 |
| 8 | S3-03: Excel export service | 5 | Backend Eng 1 | S3-01, S3-02 |
| 9 | S3-04: PDF export service | 5 | Backend Eng 1 | S3-03 |
| 10 | S3-05: Frontend report panel | 5 | Frontend Eng | S3-03 |
| 11 | S3-08: Token migration guide | 2 | Backend Lead | S3-06 |
| 12 | S3-09 + S3-10: ClickHouse HA + failover test | 11 | DevOps | None |
| 13 | Regression + Sprint 3 demo prep | 3 | QA | All |
| | **Week 2 subtotal** | **34 SP** | | |

**Total committed: 55 SP. Buffer: ~20 SP.**

---

## 5. Acceptance Criteria (Draft)

### AC-01: GRI 302 Energy Report Export ⭐ P0
> Building Manager chọn quarter → xuất file Excel/PDF GRI 302 (Energy) gửi City Authority.

**Tiêu chí PASS:**
- [ ] API `POST /api/v1/esg/report/generate` trả file download
- [ ] Excel chứa: total energy kWh, per-building breakdown, period, GRI Disclosure 302-1
- [ ] PDF format printable A4
- [ ] Frontend panel: Year selector, Quarter selector, Generate button

### AC-02: Keycloak RSA Authentication Active ⭐ P0
> Mọi API call sử dụng JWT signed bởi Keycloak RSA, HMAC chỉ là fallback.

**Tiêu chí PASS:**
- [ ] RoutingJwtDecoder verify cả HMAC và RSA tokens
- [ ] New login → RSA token issued by Keycloak
- [ ] Old HMAC token vẫn hoạt động (grace period)
- [ ] Keycloak UI accessible, realm configured

### AC-03: ClickHouse 2-node HA ⭐ P1
> ClickHouse cluster 2 node, 1 node fail → analytics vẫn available.

**Tiêu chí PASS:**
- [ ] 2 ClickHouse nodes healthy
- [ ] Kill 1 node → queries vẫn trả kết quả
- [ ] Replica lag < 5 seconds
- [ ] Flink sink writes to both replicas

### AC-04: Flink Enrichment Inline ⭐ P1
> Flink pipeline enrich building_name + district in real-time, không cần backfill.

**Tiêu chí PASS:**
- [ ] BuildingMetadataAsyncFunction trong Flink DAG
- [ ] New data → building_name populated tự động
- [ ] No more backfill needed

### AC-05: No Regression from Sprint 2 ⭐ P0
> 103/103 regression tests + ESG dashboard vẫn hoạt động.

**Tiêu chí PASS:**
- [ ] 103/103 tier-1 API tests PASS
- [ ] ESG dashboard load + charts functioning
- [ ] Analytics API response <1s

### AC-06: P2 Bug Fixes ⭐ P2
> 3 P2 bugs từ Sprint 2 đã fix.

**Tiêu chí PASS:**
- [ ] P2-001: Chart tooltip no truncation
- [ ] P2-002: AQI data fresh (poll interval reduced)
- [ ] P2-003: Filter reset smooth

---

## 6. Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Keycloak RSA migration break existing auth | 25% | HIGH | Dual-issuer decoder, HMAC fallback, gradual rollout |
| GRI format misalignment with City Authority | 40% | MED | Build default subset (302+305), adjust after feedback |
| ClickHouse HA data loss during migration | 15% | MED | Full backup before migration, Flink replay safety net |
| Excel/PDF library compatibility issues | 10% | LOW | Apache POI + iText proven in Java ecosystem |

---

## 7. Sprint 3 Gate Checklist

| Gate | Tiêu chí | AC link |
|------|----------|---------|
| G1 | GRI 302/305 export (Excel + PDF) PO approved | AC-01 |
| G2 | Keycloak RSA active, HMAC fallback working | AC-02 |
| G3 | ClickHouse 2-node HA, failover tested | AC-03 |
| G4 | Flink enrichment inline, no backfill needed | AC-04 |
| G5 | Regression 103/103 PASS | AC-05 |
| G6 | P2 bugs fixed | AC-06 |
| G7 | Zero P0/P1 bugs open | — |
| G8 | Sprint 3 demo PO sign-off | — |

---

## 8. References

- `docs/mvp3/reports/sprint2-closeout-po-report.md` — Sprint 2 closeout
- `docs/mvp3/project/demo-sprint2-po.md` — Sprint 2 demo results
- `frontend/e2e/sprint2-po-demo.spec.ts` — Sprint 2 Playwright demo
- `docs/mvp3/reports/sprint1-closeout-po-report.md` — Sprint 1 closeout

---

*Tạo bởi: PM | 2026-05-16*
*Kickoff: 2026-05-19*
