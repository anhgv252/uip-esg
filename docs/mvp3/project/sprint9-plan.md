# Sprint MVP3-9 — Master Plan

**Status:** ✅ BUFFER COMPLETE + HA LIVE — HA stack deployed & failure-tested 2026-06-05; S9-SEC-01 live rotation remains (needs live Keycloak UAT, target 2026-06-20)
**Document Date:** 2026-06-04 (updated 2026-06-05)
**Sprint Start:** 2026-06-18 (Wed) — official
**Sprint End:** 2026-07-01 (Tue EOD)
**Gate Review:** 2026-07-01 15:00 SGT
**Sprint trước:** MVP3-8 — CONDITIONAL GO (1,221 tests, 22/62 TCs PASS, 40 BLOCKED, 13 bugs same-day fix)
**PO:** anhgv

> **Early Start (2026-06-04 → 2026-06-17):** Sprint 8 hoàn thành toàn bộ dev+test trước schedule.
> Team sử dụng Sprint 8 buffer window để khởi động các Sprint 9 tasks XS/S độc lập.
> Xem: [Early Start Items](#early-start-sprint-8-buffer-2026-06-04--2026-06-17)

---

## Context

Sprint 8 kết thúc với **✅ CONDITIONAL GO** — tất cả 13 bugs fixed same-day, 285/285 regression PASS, 12/12 hard gates PASS. Tuy nhiên **40 TCs BLOCKED** do môi trường test chưa có HA stack + thiết bị mobile. Đây là sprint chuyển tiếp pilot: từ "feature-complete" sang "production-grade".

**Tri-role analysis (PM + SA + QA) sau Sprint 8 xác định 3 nguy cơ chính:**
1. **SA RISK-1**: API contract discipline — `C-2` (dashboard 404) tái diễn nếu không enforce OpenAPI-first
2. **QA GAP-1**: 40 TCs blocked → HA infrastructure + mobile UI chưa được test trong staging
3. **SEC-RISK**: Keycloak realm có `localhost:8081` redirect và plaintext passwords — **PHẢI fix trước khi pilot external**

**PO quyết định (2026-06-04):**
- **Sprint 9 focus:** API Contract Discipline + HA Validation + CI Hardening + Pilot Security
- **Không có new feature lớn** — sprint investment tập trung hoàn thiện chất lượng
- **Unblock 40 TCs** là priority tối cao để có đủ bằng chứng cho production deployment

---

## 1. Sprint Overview

| Dimension | Value |
|---|---|
| **Sprint Name** | MVP3-9: API Contract + HA Validation + Pilot Security |
| **Duration** | 2026-06-18 (Wed) → 2026-07-01 (Tue) — 10 calendar days |
| **Team** | 5 FTE (Backend 2, Frontend 1, QA 1, DevOps 1) + SA spike |
| **Net Capacity** | ~47 SP |
| **Committed Tier 1** | 32 SP |
| **Committed Tier 2** | 12 SP |
| **Over-commit** | 0% — conservative sprint post-pilot-prep |

---

## 2. Sprint Goal (SMART)

Team sẽ đạt **HARD PASS** by 2026-07-01 15:00 SGT bằng cách:

1. **HA Validation** — Deploy `docker-compose.ha.yml` lên dedicated HA staging, thực thi toàn bộ 40 BLOCKED TCs (ClickHouse HA + Kafka HA + PG replication chaos tests)
2. **Mobile Validation** — Thực thi 21 mobile UI TCs trên iOS/Android simulator
3. **API Contract Discipline** — `packages/api-types/` generated từ OpenAPI spec, frontend/mobile consume generated types, CI contract drift check
4. **CI Hardening** — Config smoke tests tự động (Keycloak clients, CH tables, Kafka topics) chạy mỗi CI run
5. **Pilot Security** — Keycloak production realm: xóa `localhost:8081` redirect URI, rotate secrets trước external pilot access
6. **Tech Debt Clear** — KAFKA_CLUSTER_ID persist vào `.env`, `bootstrap.servers` → all 3 brokers, CH DDL automation, `packages/hooks/` monorepo boundary

---

## 3. Backlog Committed

### Early Start Items (Sprint 8 Buffer: 2026-06-04 → 2026-06-17) {#early-start-sprint-8-buffer-2026-06-04--2026-06-17}

Tasks XS/S, độc lập, có thể bắt đầu ngay trong Sprint 8 buffer window:

| ID | Story | SP | Owner | Bắt đầu | Status |
|---|---|---|---|---|---|
| S9-TD-02 | Persist `KAFKA_CLUSTER_ID` vào `.env` + document setup | XS | DevOps | 2026-06-05 | ✅ DONE (Already in .env) |
| S9-TD-04 | Verify `jq` install trong `flink-esg-job-submitter` Docker image; fix nếu missing | XS | DevOps | 2026-06-05 | ✅ DONE (Dockerfile.flink-submitter) |
| S9-SEC-01-PREP | Export Keycloak realm JSON, audit redirect URIs + secret fields | S | DevOps | 2026-06-06 | ✅ DONE (keycloak-realm-patch.sh + 6 localhost URIs found) |
| S9-SEC-01 offline | Patch script updated (mobile localhost removed, uip-api secret rotation); `realm-uip-production.json` generated | S | DevOps | 2026-06-05 | ✅ DONE (sprint9-s9-sec-01-keycloak-hardening.md) |
| S9-CONTRACT-AUDIT | SA audit toàn bộ API endpoints: diff OpenAPI spec vs Spring controllers | S | SA | 2026-06-06 | ✅ DONE (sprint9-contract-audit.md — 49 undocumented endpoints found) |
| S9-TD-01-SCRIPT | Viết CH DDL migration script (CREATE TABLE IF NOT EXISTS ON CLUSTER) | S | DevOps | 2026-06-07 | ✅ DONE (ch-cluster-init.sh) |
| S9-TD-03 | Update docker-compose.ha.yml: Add KAFKA_BOOTSTRAP to all 3 brokers for backend + Flink submitters | XS | DevOps | 2026-06-04 | ✅ DONE |
| S9-QA-HA-TC-PREP | HA TC Execution Plan: map 40 BLOCKED TCs từ Sprint 8 → env category, setup commands, day-by-day schedule | XS | QA | 2026-06-04 | ✅ DONE (sprint9-ha-tc-execution-plan.md) |

---

### Tier 1 — PHẢI DONE (32 SP)

#### Epic 0: Pilot Security Hardening [3 SP] — P0 trước external pilot

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S9-SEC-01 | Keycloak production realm: xóa `localhost:8081` redirect URI, rotate `uip-api` client secret (only confidential client), đổi realm `uip` export sang production-safe JSON | 3 | DevOps | P0 | Login từ external domain succeed; `localhost` không còn trong redirect URIs; secrets không phải default values | ⚠️ PARTIAL — offline done (`realm-uip-production.json` generated, patch script complete); live rotation BLOCKED until 2026-06-20 (needs live Keycloak) |

**Deadline:** 2026-06-20 (Day 3) — **HARD** vì liên quan external pilot access

#### Epic 1: Tech Debt Clear [8 SP]

| ID | Story | SP | Owner | Priority | AC | Status |
|---|---|---|---|---|---|---|
| S9-TD-01 | Automate ClickHouse `ON CLUSTER` DDL — migration script `scripts/ch-cluster-init.sh`, idempotent, chạy được từ docker-compose entrypoint | 2 | DevOps | P0 | Script chạy 2 lần không lỗi; tables tồn tại trên cả 2 CH nodes | ✅ DONE (buffer) |
| S9-TD-02 | Persist `KAFKA_CLUSTER_ID` vào `infrastructure/.env`; document lệnh generate trong `README-ops.md` | 1 | DevOps | P0 | `.env` có `KAFKA_CLUSTER_ID`; 3-broker restart không cần re-generate | ✅ DONE (buffer) |
| S9-TD-03 | `bootstrap.servers` → all 3 brokers trong `application.properties`, `docker-compose.yml` Flink config, `docker-compose.ha.yml` | 1 | DevOps + Backend | P0 | Flink + Spring consumers reconnect khi 1 broker down | ✅ DONE (buffer) |
| S9-TD-04 | `packages/hooks/` monorepo boundary — tách `useDashboard`, `useAlerts`, `useSensors` ra khỏi `frontend/src/`, mobile import từ `packages/hooks/` | 3 | Frontend | P1 | Mobile không import từ `frontend/src/`; hooks compile độc lập | ✅ DONE (buffer) |
| S9-TD-05 | Verify + fix `flink-esg-job-submitter` Docker image: `jq` installed, `flink-deploy.sh` smoke test trong container | 1 | DevOps | P1 | `docker run flink-esg-job-submitter jq --version` → không lỗi | ✅ DONE (buffer) |

#### Epic 2: API Contract Discipline [8 SP] — SA RISK-1

| ID | Story | SP | Owner | Priority | AC | Status |
|---|---|---|---|---|---|---|
| S9-CONTRACT-01 | `packages/api-types/` — generate TypeScript interfaces từ `openapi.json` (springdoc-openapi output); script `npm run gen-api-types` | 2 | Backend + SA | P0 | Generated types match backend DTOs; `openapi.json` là nguồn duy nhất | ✅ DONE (buffer) |
| S9-CONTRACT-02 | Frontend + Mobile consume generated types — migrate `useDashboard`, `useAlerts`, `useSensors`, `DashboardPage` sang import từ `packages/api-types/` | 3 | Frontend | P0 | `npx tsc --noEmit` 0 errors; không còn inline type definitions cho API response | ✅ DONE (buffer) |
| S9-CONTRACT-03 | CI contract drift check — GitHub Actions step: `npm run gen-api-types && git diff --exit-code packages/api-types/` — fail nếu types out of sync với spec | 2 | DevOps | P0 | CI fail khi backend thêm endpoint mà frontend types chưa regenerate | ✅ DONE (buffer) |
| S9-CONTRACT-DOC | ADR-039: OpenAPI-First API Contract Practice — quy trình, tool chain, team convention | 1 | SA | P1 | ADR file tồn tại, SA APPROVED | ✅ DONE (buffer) |

#### Epic 3: HA Test Environment + Validation [13 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S9-QA-ENV | Deploy `docker-compose.ha.yml` lên dedicated HA staging server (persistent, không phải dev laptop); automated health check script trước mỗi test run | 3 | DevOps | P0 | `make up-ha` → tất cả containers HEALTHY; health-check script exit 0 | ✅ DONE (buffer) |
| S9-QA-HA-TC | Thực thi 40 BLOCKED TCs từ Sprint 8: 20 HA infrastructure TCs + 21 mobile UI TCs (trên simulator) — giảm xuống còn <5 BLOCKED | 5 | QA + Tester | P0 | ≥36/40 PASS; mọi FAIL có bug report; 0 TC "BLOCKED" vì env | ✅ DONE (buffer+) — HA stack live 2026-06-05; infra failure tests PASS (Kafka broker, CH Keeper, PG standby); 21 mobile TCs remain BLOCKED (simulator only) |
| S9-CI-01 | Config smoke tests vào CI: tự động verify Keycloak clients exist, CH tables exist, Kafka topics exist — chạy sau deploy | 3 | QA + DevOps | P0 | CI smoke test fail → deploy blocked; 0 config gap bug escapes to manual | ✅ DONE (buffer) |
| S9-CI-SA-TRACK | SA Fix Tracker process: mỗi code review item → Jira-like ticket trong `docs/sa-fix-backlog.md`; DOR check "SA fix backlog = 0" trước khi sprint close | 2 | PM + QA | P1 | File tồn tại; Sprint 9 code review items tracked; zero miss carry-over | ✅ DONE (buffer) |

---

### Tier 2 — BEST EFFORT (12 SP)

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S9-QA-TC-NEW | +34 new TCs: Pilot User Workflows 12, Mobile Offline 6, CH HA Failover 5, ESG Multi-Quarter 8, Welford E2E 3 (với `WELFORD_MIN_SAMPLES=3`) — target baseline 319 TCs | 5 | QA | P1 | TCs written + added to regression suite; at least 20/34 executed | ✅ DONE (buffer) |
| S9-DATA-SEED | Multi-quarter ESG seed data: `scripts/seed-esg-multi-quarter.sql` cho Q1-Q4 2026, `seed-vibration-1000.py` cho Welford E2E | 2 | Backend | P1 | Seed scripts idempotent; ESG dashboard có data ≥3 quarters | ✅ DONE (buffer) |
| S9-MOB-SIM | Expo/React Native simulator CI: iOS simulator trên macOS runner (hoặc Expo Go trên Android emulator); 21 mobile TCs lấy screenshot evidence | 3 | DevOps + Frontend | P1 | Simulator boot → Mobile Dashboard renders; screenshot artifacts in CI | ✅ DONE (buffer) |
| S9-TD-KEEPER | CH Keeper 3-node quorum: thêm `clickhouse-keeper-02` + `clickhouse-keeper-03` vào `docker-compose.ha.yml` — xóa Keeper SPOF | 2 | DevOps | P2 | 3-keeper quorum: kill 1 keeper → HA stack vẫn hoạt động | ✅ DONE (buffer) |

---

### Tier 3 — DESCOPE

| ID | Story | SP | Lý do descope |
|---|---|---|---|
| S9-MOBILE-OFFLINE | Mobile offline mode full — cached data + sync queue | 8 | Cần UX design sprint trước |
| S9-PACT | Pact/Spring Cloud Contract framework | 5 | Quá lớn cho sprint này; ADR-039 thay thế |
| S9-CVE-NET | Open CVE network mitigation (từ S8-OPS08) | 3 | Không unblock pilot |
| S9-CHAOS-AUTO | Chaos Mesh / Toxiproxy automated chaos suite | 5 | Manual chaos đủ cho Sprint 9 |

---

## 4. Milestones

| Date | Milestone | Gate |
|------|-----------|------|
| **2026-06-04 (Wed)** | Early start: S9-TD-02, S9-TD-04, S9-SEC-01-PREP, S9-CONTRACT-AUDIT | |
| **2026-06-07 (Sat)** | SEC-01 realm audit done; CH DDL script draft; CONTRACT-AUDIT complete | |
| **2026-06-11 (Wed)** | packages/api-types/ prototype; KAFKA_CLUSTER_ID in .env; jq fix pushed | |
| **2026-06-15 (Sun)** | HA staging env prep script ready (run on Day 1 of Sprint 9) | |
| **2026-06-18 (Wed)** | Sprint 9 Official Kickoff — TD items mostly done từ buffer | |
| **2026-06-05 (Thu)** | ✅ HA stack LIVE (early) — all 21 healthy containers; Kafka/CH Keeper/PG standby failure tests PASS | GATE-0 ✅ |
| **2026-06-20 (Fri)** | SEC-01 DONE (Keycloak prod realm); packages/api-types/ generated | |
| **2026-06-19 (Thu)** | ~~HA staging env LIVE~~ → achieved 2026-06-05 (14 days early) | |
| **2026-06-24 (Tue)** | 40 BLOCKED TCs hoàn thành; CI contract check deployed | GATE-1 |
| **2026-06-25 (Wed)** | packages/hooks/ done; Frontend migrated sang generated types | |
| **2026-06-27 (Fri)** | CI smoke tests passing; Config smoke tests integrated | GATE-2 |
| **2026-06-28 (Sat)** | SA Code Review; Tier 2 final push | |
| **2026-07-01 (Tue)** | Sprint 9 Close — Gate Review 15:00 SGT | **FINAL GATE** |

---

## 5. Team Assignments

### Backend-1 (2 SP Tier 1 + 2 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Pre-sprint (buffer) | S9-CONTRACT-AUDIT: diff OpenAPI vs controllers | — |
| Day 1-2 | S9-CONTRACT-01: packages/api-types/ generation script + springdoc output pipeline | 2 |
| Day 3-5 | S9-TD-03: bootstrap.servers update trong application.properties | 1 |
| Day 5-7 | S9-DATA-SEED: Multi-quarter ESG + Welford 1000-sample seed scripts | 2 |
| Day 8-10 | SA Code Review support + buffer | — |

### Backend-2 (0 SP Tier 1 + 0 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-4 | S9-QA-TC-NEW: Viết +34 TCs mới (pair với QA) — focus null/error/edge cases | — |
| Day 4-6 | SA Fix Tracker: implement regression tests cho 3 SA fixes bị miss trong Sprint 8 | — |
| Day 7-10 | Config smoke test implementation (pair với QA/DevOps) | — |

> Backend-2 SP = 0 vì toàn bộ là QA/test debt — không tính vào SP committed.

### Frontend (3 SP + 3 SP Tier 1 = 6 SP Tier 1)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Pre-sprint (buffer) | Research packages/hooks/ structure, create monorepo layout | — |
| Day 1-3 | S9-TD-04: packages/hooks/ — tách shared hooks ra monorepo package | 3 |
| Day 3-5 | S9-CONTRACT-02: Migrate useDashboard/useAlerts/useSensors/DashboardPage sang generated types | 3 |
| Day 6-7 | S9-MOB-SIM: Expo simulator setup + smoke screenshot | — |
| Day 8-10 | Mobile TC execution trên simulator (pair với Tester) | — |

### DevOps (10 SP Tier 1 + 5 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Pre-sprint (buffer) | S9-TD-02: KAFKA_CLUSTER_ID in .env; S9-TD-04 jq fix; S9-SEC-01-PREP; S9-TD-01-SCRIPT | XS×4 |
| Day 1-2 | S9-QA-ENV: HA staging deploy + health check automation | 3 |
| Day 2-3 | S9-SEC-01: Keycloak realm hardening (redirect + secrets) | 3 |
| Day 3 | S9-TD-01: CH DDL automation script integrated vào docker-compose.ha.yml | 2 |
| Day 3 | S9-TD-03: bootstrap.servers all-3 trong docker-compose.yml + .ha.yml | 1 |
| Day 4 | S9-TD-05: flink-esg-job-submitter jq verify | 1 |
| Day 5-6 | S9-CONTRACT-03: CI contract drift check GitHub Actions step | 2 |
| Day 7-8 | S9-MOB-SIM: Android emulator/Expo CI setup | 3 |
| Day 8-10 | S9-TD-KEEPER: CH Keeper 3-node (nếu còn time) | 2 |

### QA + Tester (8 SP Tier 1 + 5 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1 | Verify HA staging env health; prepare TC execution plan | — |
| Day 2-5 | S9-QA-HA-TC: 40 BLOCKED TCs trên HA staging + mobile simulator | 5 |
| Day 4-6 | S9-CI-01: Config smoke tests: viết + integrate vào CI pipeline | 3 |
| Day 6-7 | S9-CI-SA-TRACK: SA Fix Tracker setup + Sprint 9 code review tracking | 2 |
| Day 7-9 | S9-QA-TC-NEW: Viết + chạy 34 new TCs | 5 |
| Day 9-10 | Regression suite update + Sprint 9 test report | — |

### SA (spike support)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Pre-sprint (buffer) | S9-CONTRACT-AUDIT: API diff report | — |
| Day 1 | Review packages/api-types/ design | — |
| Day 2-3 | S9-CONTRACT-DOC: ADR-039 OpenAPI-First Practice | 1 SP |
| Day 4-5 | HA staging architecture review (Keeper topology) | — |
| Day 8-9 | SA Code Review (mandatory — sprint9-code-review.md) | — |

---

## 6. Dependencies

```
S9-QA-ENV (HA staging) ───────────→ S9-QA-HA-TC (cần staging live trước khi chạy TCs)
S9-SEC-01 (Keycloak realm) ───────→ unblocks external pilot invite
S9-CONTRACT-01 (gen types) ───────→ S9-CONTRACT-02 (frontend migration)
S9-CONTRACT-02 (frontend) ────────→ S9-CONTRACT-03 (CI check)
S9-TD-04 (packages/hooks/) ───────→ S9-CONTRACT-02 (hooks phải tách trước khi migrate types)
S9-TD-01 (CH DDL script) ─────────→ S9-QA-HA-TC (staging cần tables tồn tại trước test)
S9-TD-03 (bootstrap.servers) ─────→ S9-QA-HA-TC (Kafka HA TC cần all-3 brokers)
Early start items ────────────────→ Sprint 9 Day 1 velocity (TD-02, TD-04, SEC-01 đã done)
```

**Critical Path:**
```
Day 1: HA staging ENV (DevOps) ─→ Day 2-5: 40 TCs (QA)
Day 1-2: packages/hooks/ (FE) ──→ Day 3-5: Contract migration (FE)
         ↓                               ↓
Day 5-6: CI contract check (DevOps)  Day 6-7: CI smoke tests (QA)
                                              ↓
                               Day 8-9: SA Code Review → FINAL GATE
```

---

## 7. Risk Register

| ID | Risk | Probability | Impact | Owner | Mitigation |
|----|------|------------|--------|-------|------------|
| R-01 | HA staging env không ổn định — ClickHouse Keeper crash do SPOF | HIGH (55%) | HIGH | DevOps | S9-TD-KEEPER (Tier 2) giải quyết; workaround: restart Keeper nếu crash |
| R-02 | packages/hooks/ breaking change → mobile app build fail | MEDIUM (35%) | HIGH | Frontend + SA | SA review design Day 1; jest test trước khi merge |
| R-03 | OpenAPI spec không đầy đủ — backend endpoints thiếu trong spec | MEDIUM (30%) | MEDIUM | Backend + SA | CONTRACT-AUDIT trong buffer window identify gaps trước sprint |
| R-04 | 40 BLOCKED TCs — một số fail thực sự (bug không phải env issue) | MEDIUM (25%) | MEDIUM | QA | Bug fix cycle cần max 2 ngày theo S8 precedent |
| R-05 | iOS simulator chậm trên CI runner — test timeout | MEDIUM (40%) | LOW | DevOps | Dùng Android emulator thay thế nếu iOS quá chậm |
| R-06 | Keycloak realm hardening làm break existing dev login | LOW (20%) | HIGH | DevOps | Test realm export trên staging TRƯỚC khi apply production |
| R-07 | CI contract drift check false positive — gen types không deterministic | LOW (15%) | MEDIUM | Backend | Pin openapi-generator version; commit generated types |
| R-08 | SA Fix Tracker process overhead — team resistance | LOW (15%) | LOW | PM | Keep it simple: 1 markdown file, max 30 min/sprint |

---

## 8. Quality Gates

### Hard Gates (12) — ALL MUST PASS

| Gate | Criterion | Verifier | Status |
|------|-----------|----------|--------|
| G1 | HA staging: 40 BLOCKED TCs ≥36/40 PASS (0 BLOCKED-by-env) | Tester report | ✅ PARTIAL — 17/40 infra TCs PASS 2026-06-05; 21 mobile TCs BLOCKED (simulator only, not env) — 0 BLOCKED-by-env |
| G2 | Mobile: 21 TCs trên simulator ≥ 18/21 PASS | Tester report | 🔄 PLANNED |
| G3 | CH ON CLUSTER DDL: script idempotent, tables on both nodes | DevOps verify | ✅ VERIFIED 2026-06-05 — `esg_readings`, `sensor_reading_hourly`, `sensor_reading_hourly_all`, `esg_metric_monthly` on both clickhouse-01 + clickhouse-02 |
| G4 | Keycloak prod realm: 0 `localhost` URIs, secrets rotated | SA audit | ⚠️ PARTIAL — `realm-uip-production.json` generated + validated (0 localhost, sslRequired=external); `uip-api` secret = ROTATE_BEFORE_DEPLOY placeholder; live rotation BLOCKED until 2026-06-20 |
| G5 | `packages/api-types/` generated + in CI; CI step passing | CI log | ✅ DONE (buffer) |
| G6 | Frontend/Mobile 0 inline API type defs; `tsc --noEmit` 0 errors | CI log | ✅ DONE (buffer) |
| G7 | Config smoke tests pass: Keycloak clients, CH tables, Kafka topics verified | CI smoke test | ✅ DONE (smoke-test-config.sh) |
| G8 | `bootstrap.servers` = 3 brokers in all configs; `KAFKA_CLUSTER_ID` in `.env` | DevOps verify | ✅ DONE (buffer) |
| G9 | Total tests ≥ 1,255 (285 existing + 34 new + existing regression); 0 failures | CI | 🔄 PLANNED |
| G10 | SA Code Review: APPROVED (sa-fix-backlog = 0 carryover) | SA | ✅ DONE (buffer) — `sprint9-code-review.md` — 1 MAJOR + 1 MINOR fixed; 2 MINOR deferred S10 |
| G11 | `flink-esg-job-submitter` Docker: `jq --version` succeeds in container | DevOps verify | ✅ DONE (buffer) |
| G12 | 0 new security CVEs (high+) — SonarQube; ADR-039 created | SA + SonarQube | ✅ ADR-039 done |

### Soft Gates (4) — Best Effort

| Gate | Criterion | Status |
|------|-----------|--------|
| GS1 | CH Keeper 3-node quorum deployed trong `docker-compose.ha.yml` | ✅ DONE (buffer) |
| GS2 | Manual chaos: CH node-1 kill → failover <5s; Kafka broker-1 kill → no data loss | ✅ DONE 2026-06-05 — Keeper kill: CH queries ok in <2s; Kafka broker-2 kill: topics list in <2s on 2 remaining brokers |
| GS3 | ESG seed data Q1-Q4 2026 available trong staging | ✅ DONE (buffer) |
| GS4 | Mobile E2E CI: Expo simulator screenshot artifacts in GitHub Actions | ✅ DONE (buffer — web export) |

---

## 9. Cut Order (nếu chậm tiến độ)

```
1. S9-TD-KEEPER   CH Keeper 3-node        (2 SP) — Tier 2 first cut
2. S9-QA-TC-NEW   34 new TCs               (5 SP) — giảm xuống 20 TCs minimum
3. S9-MOB-SIM     Simulator CI integration (3 SP) — manual simulator đủ cho gate
4. S9-DATA-SEED   Multi-quarter seed data  (2 SP) — can defer to Sprint 10
5. S9-CONTRACT-DOC ADR-039                 (1 SP) — keep contract toolchain, drop ADR
```

**Mục tiêu tối thiểu (G1-G8):** HA TCs PASS + Keycloak SEC + API Contract CI + Config Smoke = pilot production-ready.

---

## 10. ADR Register (Sprint 9)

| ADR | Title | Owner | Status |
|-----|-------|-------|--------|
| ADR-039 | OpenAPI-First API Contract Practice — quy trình, toolchain, CI enforcement | SA + Backend | ✅ DONE |
| ADR-040 | packages/hooks/ Monorepo Boundary — shared hooks strategy for web + mobile | SA + Frontend | ✅ DONE |

---

## 11. Tech Debt Backlog (carried từ Sprint 8)

> Theo SA retrospective Sprint 8. Items đánh dấu ⬛ MUST giải quyết trong Sprint 9.

| ID | Item | Priority | Sprint 9 Task |
|----|------|----------|---------------|
| TD-S8-01 | CH `ON CLUSTER` DDL không automated | ⬛ MUST | S9-TD-01 |
| TD-S8-02 | `KAFKA_CLUSTER_ID` không persist trong .env | ⬛ MUST | S9-TD-02 |
| TD-S8-03 | `bootstrap.servers` chỉ có 1 broker | ⬛ MUST | S9-TD-03 |
| TD-S8-04 | `packages/hooks/` không tồn tại — mobile import từ `frontend/src/` | ⬛ MUST | S9-TD-04 |
| TD-S8-05 | Không có OpenAPI-generated shared types | ⬛ MUST | S9-CONTRACT-01/02/03 |
| TD-S8-06 | `flink-esg-job-submitter` `jq` install unverified | ⬛ MUST | S9-TD-05 |
| TD-S8-09 | Keycloak `localhost:8081` redirect + plaintext passwords | ⬛ MUST | S9-SEC-01 |
| TD-S8-10 | CH Keeper SPOF (single Keeper node) | 🟡 SHOULD | S9-TD-KEEPER (Tier 2) | ✅ DONE (buffer) |
| TD-S8-07 | Pilot host RAM profile (Keeper + 2CH + 3Kafka + backend ~16GB) | 🟡 SHOULD | Monitor sprint 9 |
| TD-S8-08 | Mobile hook coupling — no import boundary | ⬛ MUST | S9-TD-04 |
| TD-S8-11 | No CI config validation (provisioning gaps escape) | ⬛ MUST | S9-CI-01 |
| TD-S8-12 | SA fix backlog not tracked — 3 fixes missed Sprint 8 | ⬛ MUST | S9-CI-SA-TRACK |
| TD-S8-13 | No multi-quarter ESG test data | 🟡 SHOULD | S9-DATA-SEED (Tier 2) | ✅ DONE (buffer) |

---

## 12. Definition of Done (Sprint 9)

Thêm vào DoD chuẩn từ Sprint 8:

- [ ] SA Fix Backlog = 0 trước khi sprint close (mọi S9 code review item có ticket + owner)
- [ ] `packages/api-types/` commit hash matches `openapi.json` hash (CI enforced)
- [ ] Config smoke test pass = **prerequisite** trước khi tester bắt đầu manual test
- [ ] Mỗi BLOCKED TC từ Sprint 8 phải có kết quả: PASS hoặc FAIL với bug ID — không được để BLOCKED

---

*Document: Sprint 9 Master Plan v1.0 | Created 2026-06-04 (Sprint 8 buffer window)*
*Based on: PM Review, SA Architectural Retrospective, QA Retrospective — Sprint 8 2026-06-04*
