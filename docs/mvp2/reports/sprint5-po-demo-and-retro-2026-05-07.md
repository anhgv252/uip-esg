# Sprint 5 Demo + Retrospective Package (PO)

- Date: 2026-05-07
- Scope: Re-test Sprint 5 for PO demo, full-system demo of delivered capabilities, with focus on multi-tenant
- Coordinator mode: uip-team workflow (BA + SA + QA + Tester + PM/SM)

## 1) Demo Objectives (PO-facing)

1. Validate end-to-end flow across implemented system modules, not only isolated screens.
2. Prove multi-tenant isolation at DB/API/cache/UI layers.
3. Demonstrate operational readiness for Tenant Admin + citizen-facing experiences.
4. Show evidence-driven quality status (test rerun, known gaps, mitigation).
5. Align on carry-over items and Sprint 6 priorities with clear owners.

## 2) Sprint 5 Re-test Plan (QA + Tester)

### 2.1 Test layers

- Smoke: environment health, login roles, RLS availability, infra connectivity.
- API regression: critical groups including tenant-admin and citizen/PWA-related endpoints.
- Integration: tenant isolation and cross-tenant negative scenarios.
- Manual E2E demo: scripted 30-45 minutes for PO walkthrough.

### 2.2 Priority test scope

- P0: multi-tenant login/claims, RLS separation, tenant-admin access control, alert visibility by tenant.
- P1: usage/config workflows, mobile responsive checks, role/scope behavior.

### 2.3 Go/No-Go criteria for demo

- Blocker pass: smoke baseline + tenant isolation + role login paths.
- Major pass: core tenant-admin flows and stable API behavior under normal load.
- Any cross-tenant data leak: automatic No-Go.

### 2.4 Demo evidence package

- Health snapshot, API result set, RLS proof, cache namespace sample, Kafka sample, performance excerpt.
- Related checklist: see docs/mvp2/qa/sprint5-demo-checklist.md.
- Related reports: see docs/mvp2/reports/sprint5-test-session-report-2026-05-06.md and docs/mvp2/reports/sprint5-e2e-rerun-2026-05-07.md.

### 2.5 Current rerun execution status (2026-05-07)

- `python3 scripts/sprint5_smoke_test.py`: 0/8 pass (blocked by environment/tooling).
- `python3 scripts/api_regression_test.py --group=pwa_citizen,tenant_admin_dashboard`: invalid syntax (`--group` accepts one group each run).
- `python3 scripts/api_regression_test.py --group=health`: aborted due backend not reachable.

Main blockers captured:

- Backend service not running at localhost:8080.
- Missing local CLI tooling in current shell (`psql`, `redis-cli`, `kafka-consumer-groups.sh`).

Demo implication:

- Demo can proceed only after environment readiness gate is recovered and smoke is rerun to green baseline.

## 3) Full-System Demo Script (focus: multi-tenant)

### Part A: Tenant isolation proof (5-8 min)

- Show two tenant contexts and compare data counts/results.
- Show token claims (tenant_id/scopes/allowed_buildings).
- Show tenant-separated key spaces and event metadata where available.

### Part B: Tenant admin operations (10-12 min)

- Login as tenant admin.
- Walk through overview/users/settings/usage paths.
- Verify access is bounded to own tenant.

### Part C: Citizen/system workflows (10-12 min)

- Login as citizen/operator and execute key business flow(s).
- Show alerts/data relevant to tenant scope.
- Confirm no cross-tenant bleed in UI behavior.

### Part D: Stability and known gaps (5-8 min)

- Present quick performance indicators and test rerun summary.
- Call out deferred items and mitigation plan, not hidden during demo.

## 4) SA Training Recap: Multi-tenant for New Modules

### 4.1 Training outcomes

- Team aligned on T1/T2/T3 strategy and why T2 relies on RLS + tenant context.
- Team aligned on safe tenant propagation contract (JWT -> context -> transaction -> DB policy).
- Team aligned on implementation gates before merge.

### 4.2 Mandatory checklist for new modules

- Backend: tenant_id model + transactional context + no hardcoded tenant.
- Frontend: tenant-aware query keys + role/scope enforcement + tenant config handling.
- Streaming: tenant_id required in event contracts and validation.
- Migration: add/backfill/enforce sequence to avoid data visibility regressions.

### 4.3 Developer onboarding (new joiners)

- Local setup with at least 2 tenants and seeded differentiated data.
- Run isolation tests (API + concurrent + cache + stream).
- Common pitfalls to avoid: context leak, missing tenant_id in events, cache key omissions.

- Detailed training artifact: docs/mvp2/project/multi-tenant-training-playbook.md.

## 5) SM/PM Sprint Retrospective Summary

### 5.1 Done this sprint

- Multi-tenant foundations and related feature paths were demo-ready at architecture/process level.
- QA/test orchestration pack and PO demo checklists were completed.
- Team has reusable retrospective template for subsequent sprints.

### 5.2 Not done / carry-over

- Some engineering items remain deferred (for example dynamic CORS policy hardening, Redis-backed rate-limit persistence, deeper performance automation).
- Several test and environment-dependent flows need continued hardening before broad release confidence.

### 5.3 Lessons learned (action-oriented)

1. Run pre-demo smoke earlier, not only right before PO session.
2. Lock demo script to evidence artifacts to avoid live-only dependency.
3. Enforce tenant isolation checks in every critical flow review.
4. Keep migration ordering explicit to avoid visibility regressions.
5. Standardize DECIDED/DONE/NEXT/OPEN handoff across roles.
6. Separate "must pass for demo" from "nice-to-have" to reduce ambiguity.
7. Require tenant-aware keys/filters in all read-heavy paths.
8. Add fallback plan for each demo segment.
9. Track deferred technical debt with owner and due date.
10. Capture sprint-level learnings into memory immediately after review.

### 5.4 Sprint-next actions

- SA/Backend: close multi-tenant edge gaps and enforce checklist in PR review.
- QA/Tester: keep rerun scripts and evidence collection repeatable.
- FE/BE: focus remaining high-impact gaps affecting PO confidence.
- PM/SM: monitor carry-over completion at sprint start, not sprint end.

## 6) Cross-role Handoff (for next cycle)

### DECIDED

- Demo acceptance prioritizes multi-tenant correctness over feature breadth.
- No cross-tenant leak is tolerated.

### DONE

- Demo/retest/training/retrospective document set prepared in docs/mvp2.

### NEXT

- Execute rehearsal with same checklist and evidence pack.
- Resolve outstanding high-risk carry-over items before next PO gate.

### OPEN

- Confirm final PO expectation for deferred PWA/deeper performance scope in next sprint window.
