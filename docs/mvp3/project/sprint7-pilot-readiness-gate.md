# Sprint 7 — Pilot Readiness Gate Checklist

**Sprint:** MVP3-7 | **Gate Date:** 2026-06-27 | **Decision:** GO / NO-GO

---

## Executive Summary

Checklist 25 mục xác nhận MVP3-7 sẵn sàng deploy thí điểm cho City Authority.
Tất cả mục phải **PASS** trước khi sign-off. Bất kỳ mục **FAIL** nào sẽ block deployment.

---

## 1. Security (5 mục)

| # | Mục | Verification | Status |
|---|-----|--------------|--------|
| G1 | OWASP ZAP: 0 Critical, 0 High findings | Run ZAP against /buildings, /alerts, /esg | ⬜ |
| G2 | JWT scope `esg:write` enforced — API 403 without scope | WebMvc test pass | ✅ DEV |
| G3 | RLS isolation: tenant A không thấy data tenant B | ISO-008 + ISO-009 PASS | ✅ DEV |
| G4 | Keycloak PKCE auth flow functional | Login test on staging | ⬜ |
| G5 | API rate limiting active (Kong) | 429 after threshold | ⬜ |

## 2. Performance (5 mục)

| # | Mục | Target | Status |
|---|-----|--------|--------|
| G6 | Structural alert P0 latency e2e | <15s | ⬜ QA-3 |
| G7 | Safety score API p95 | <2s | ⬜ QA-3 |
| G8 | Dashboard initial load | <3s | ⬜ QA-3 |
| G9 | BMS events sustained | 1,667/sec | ⬜ QA-3 |
| G10 | API error rate | <0.01% over 20 min | ⬜ QA-3 |

## 3. Data & Migrations (4 mục)

| # | Mục | Verification | Status |
|---|-----|--------------|--------|
| G11 | V34 migration applied (structural_alert_rules + building_id column) | Query confirms 6 rules exist | ✅ DEV |
| G12 | Structural sensor types seeded (BLDG-001) | Seeds exist in DB | ⬜ OPS |
| G13 | Alert rules seeded (WARNING+CRITICAL for VIBRATION/TILT/CRACK) | 6 rules in alert_rules | ✅ DEV |
| G14 | Tenant isolation post-migration | ISO-008/009 PASS | ✅ DEV |

## 4. Monitoring (4 mục)

| # | Mục | Verification | Status |
|---|-----|--------------|--------|
| G15 | Grafana structural dashboards live | Panels render real data | ⬜ OPS-4 |
| G16 | Prometheus alert rules configured | structural-alert-rules.yml loaded | ✅ DEV |
| G17 | Apicurio Registry healthy + 4 schemas published | Health endpoint UP | ✅ DEV |
| G18 | Flink VibrationAnomalyJob running, checkpoint <30 min | Flink dashboard | ⬜ OPS |

## 5. Documentation (4 mục)

| # | Mục | Status |
|---|-----|--------|
| G19 | Deployment runbook complete (6 incident scenarios) | ✅ OPS-3 |
| G20 | ADR-034 (Structural Monitoring + BR-010) reviewed | ✅ SA-1 |
| G21 | Kafka topic registry updated (v1+v2 topics) | ✅ B1-4 |
| G22 | QA test strategy published | ✅ QA Strategy |

## 6. Hard Gates G1–G14 (Non-Negotiable)

| Gate | Requirement | Owner | Status |
|------|------------|-------|--------|
| G1 | OWASP 0 Critical | SA+QA | ⬜ |
| G2 | ESG scope enforcement | Backend | ✅ |
| G3 | RLS tenant isolation | Backend | ✅ |
| G4 | Structural alert <15s | QA | ⬜ |
| G5 | Safety score API <2s | QA | ⬜ |
| G6 | V34 migration applied | DevOps | ✅ |
| G7 | Flink job running | DevOps | ⬜ |
| G8 | Grafana panels live | DevOps | ⬜ |
| G9 | Deployment runbook | DevOps | ✅ |
| G10 | BR-010 verified (no auto-evacuate) | SA+QA | ✅ |
| G11 | TypeScript 0 errors | Frontend | ✅ |
| G12 | Regression suite 100+ PASS | QA | ⬜ |
| G13 | E2E Playwright 34/34 PASS | QA | ⬜ |
| G14 | This checklist all PASS | PM | ⬜ |

---

## Sign-Off

| Role | Name | Status |
|------|------|--------|
| PM | anhgv | PENDING |
| SA | Solution Architect | PENDING |
| QA Lead | QA Engineer | PENDING |
| DevOps Lead | DevOps Engineer | PENDING |

---

*Document: Sprint 7 Pilot Gate v1.0 | 2026-06-02*
