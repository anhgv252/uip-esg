# Sprint MVP3-11 — Tech Debt Clear (Full Backlog)

**Status:** 🚀 IN PROGRESS
**Document Date:** 2026-06-09
**Sprint Duration:** 2 tuần (compressed execution)
**PO:** anhgv
**Total Committed:** 48 SP

---

## Sprint Goal

Xóa toàn bộ MVP3 tech debt carry-over: gRPC transport, error codes, mobile features, infrastructure polish, automated testing.

---

## Task Matrix

### P1 — Post-Pilot Critical (16 SP)

| ID | Task | SP | Owner | Depends on |
|----|------|-----|-------|------------|
| S11-GRPC-01 | gRPC transport: .proto + codegen + server (analytics-service) | 5 | Backend-1 | — |
| S11-GRPC-02 | gRPC transport: client adapter (backend) + capability flag | 5 | Backend-2 | S11-GRPC-01 |
| S11-GRPC-03 | gRPC tests (IT + unit) + ADR-012 status update | 3 | QA + Backend-1 | S11-GRPC-02 |
| S11-ERR-01 | Error codes — extend to all 107+ endpoints | 3 | Backend-1 | — |

### P2 — Post-Pilot Enhancement (18 SP)

| ID | Task | SP | Owner | Depends on |
|----|------|-----|-------|------------|
| S11-MOB-01 | Mobile offline mode: cache tiers + sync queue | 8 | Frontend | — |
| S11-MOB-02 | Mobile Control Panel safety confirmations | 5 | Frontend + Backend | — |
| S11-BPMN-01 | BPMN Designer UX polish (toolbar + templates) | 3 | Frontend | — |
| S11-APK-01 | Android APK Store submission pipeline | 2 | DevOps | — |

### P2 — Infrastructure (4 SP)

| ID | Task | SP | Owner | Depends on |
|----|------|-----|-------|------------|
| S11-INFRA-01 | CH Keeper memory monitoring dashboard | 2 | DevOps | — |
| S11-INFRA-02 | Pilot host RAM profile optimization (16GB) | 2 | DevOps | — |

### P3 — Future-Proofing (10 SP)

| ID | Task | SP | Owner | Depends on |
|----|------|-----|-------|------------|
| S11-CHAOS-01 | Automated chaos engineering (Chaos Mesh scripts) | 5 | DevOps + QA | — |
| S11-PACT-01 | Pact contract testing framework | 5 | QA + Backend | — |

---

## Execution Order (Critical Path)

```
Week 1 — Parallel tracks:
  Track A (Backend-1): S11-GRPC-01 → S11-GRPC-02 → S11-GRPC-03
  Track B (Backend-2): S11-ERR-01 (error codes all endpoints)
  Track C (Frontend):  S11-MOB-01 (offline) | S11-MOB-02 (control panel) | S11-BPMN-01
  Track D (DevOps):    S11-INFRA-01 + S11-INFRA-02 | S11-APK-01

Week 2 — Integration + Quality:
  Track A: S11-GRPC-03 tests + ADR update
  Track B: S11-PACT-01 contract testing
  Track C: Frontend polish + mobile testing
  Track D: S11-CHAOS-01 chaos scripts
  Final: SA Code Review + Regression Gate
```

---

## Quality Gates

- [ ] `./gradlew build` SUCCESSFUL (0 test failures)
- [ ] `npx tsc --noEmit` 0 errors (web + mobile)
- [ ] gRPC IT test PASS (analytics-service ↔ backend)
- [ ] Error codes ≥95% endpoints covered
- [ ] Mobile offline mode: read from cache when offline, sync when online
- [ ] SA Code Review APPROVED
