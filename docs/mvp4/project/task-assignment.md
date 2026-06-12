# MVP4 — Task Assignment Tổng hợp

**Ngày lập:** 2026-06-12
**Tổng:** 27 tasks | ~255 SP | 5.5 FTE | 6 Sprints (Aug → Oct 2026)

---

## 1. Phân công theo Role

| Role | Agent | Tasks | Tổng SP | Sprint hoạt động |
|------|-------|-------|---------|-----------------|
| Backend Eng 1+2 | `UIP-backend-engineer` | #1-4, #9, #13, #15, #17, #21, #25 | 110 SP | S1→S6 |
| Frontend Eng | `UIP-frontend-engineer` | #5, #10, #14, #18, #22 | 49 SP | S1→S5 |
| DevOps | `UIP-devops` | #6, #12, #16, #19, #24 | 21.5 SP | S1→S5 |
| QA Eng | `UIP-qa-engineer` | #7, #11, #20, #23, #26 | 46 SP | S1→S6 |
| PM | `UIP-project-manager` | #8, #27 | 5 SP | S1+S6 |

> **Tổng:** 231.5 SP (trong ~255 SP tổng, phần còn lại là SA/BA/UX support tasks)

---

## 2. Timeline tổng

```
Jul 2026  │ Pre-Pilot P0 Fixes
──────────┼──────────────────────────────────────────────────────────────
Aug 04-15 │ Sprint 1 — Pilot Stabilize + v3.1 Start         ~50 SP  Gate: Pilot 7d P0-free
Aug 18-29 │ Sprint 2 — v3.1 Complete + AI Cost Foundation   ~55 SP  Gate: v3.1 DONE + AI batching
Sep 01-12 │ Sprint 3 — AI Optimization + Correlation Start  ~30 SP  Gate: AI < $5/day
Sep 15-26 │ Sprint 4 — Correlation Engine + Self-Service    ~35 SP  Gate: FP < 10%
Sep 29-Oct10│ Sprint 5 — BMS Automation + Self-Service Done ~30 SP  Gate: BMS auto-cmd
Oct 13-24 │ Sprint 6 — Feedback Loop + MVP4 Gate           ~25 SP  Gate: DECLARE MVP4 DONE
```

---

## 3. Dependency Chain (Critical Path)

```
                     ┌─ #1 JWT/Rate/SQL ITs ─┐
                     ├─ #2 BMS+MQTT+TODOs ────┤
S1 (#1→#8) ────────► ├─ #3 Env/Traffic+CO2 ──┼──► S2 #9 Backend (25 SP) ──► S3 #13 Correlation ──► S4 #17 Complete
                     └─ #4 DLQ+PII+Refactor ─┘         │                           │
                                                        │                     S3 #15 Welford ──┘
S1 #5 Frontend ───► S2 #10 Frontend ──► S3 #14 Template ──► S4 #18 Wizard ──► S5 #22 Complete
S1 #6 DevOps ─────► S2 #12 DevOps ─────► S3 #16 Redis ─────► S4 #19 Cost dashboard
S1 #7 QA ─────────► S2 #11 QA ──────────────────────────────────────────────► S4 #20 E2E ──► S5 #23 ──► S6 #26 Gate
                                                                                                   S5 #21 BMS ──► S6 #25 ──► #26
S5 #21 BMS ──► S6 #25 Feedback ──► S6 #26 QA Gate ──► S6 #27 PM Declare DONE
```

---

## 4. File Assignment chi tiết

| File | Role | Nội dung |
|------|------|----------|
| [task-backend.md](task-backend.md) | Backend Eng 1+2 | 10 tasks, 110 SP, S1→S6 |
| [task-frontend.md](task-frontend.md) | Frontend Eng | 5 tasks, 49 SP, S1→S5 |
| [task-devops.md](task-devops.md) | DevOps | 5 tasks, 21.5 SP, S1→S5 |
| [task-qa.md](task-qa.md) | QA Eng | 5 tasks, 46 SP, S1→S6 |
| [task-pm.md](task-pm.md) | PM | 2 tasks, 5 SP, S1+S6 |

---

## 5. Sprint SP Load per Role

| Sprint | Backend | Frontend | DevOps | QA | PM | Total |
|--------|---------|----------|--------|-----|-----|-------|
| S1 | 27.5 SP (#1-4) | 7 SP (#5) | 7.5 SP (#6) | 13 SP (#7) | 1 SP (#8) | **~50 SP** |
| S2 | 25 SP (#9) | 16 SP (#10) | 6 SP (#12) | 18 SP (#11) | — | **~55 SP** |
| S3 | 15 SP (#13+#15) | 10 SP (#14) | 3 SP (#16) | — | — | **~30 SP** |
| S4 | 16 SP (#17) | 11 SP (#18) | 3 SP (#19) | 5 SP (#20) | — | **~35 SP** |
| S5 | 13 SP (#21) | 5 SP (#22) | 2 SP (#24) | 5 SP (#23) | — | **~30 SP** |
| S6 | 16 SP (#25) | — | — | 5 SP (#26) | 4 SP (#27) | **~25 SP** |

---

## 6. Risks cần theo dõi

| Risk | Severity | Owner | Mitigation |
|------|----------|-------|-----------|
| R1: AI batching miss critical alerts | HIGH | Backend | Critical events bypass batching, route trực tiếp |
| R2: BMS auto-command wrong | CRITICAL | Backend | 2-step confirm: AI đề xuất → Operator approve |
| R3: iOS Apple review reject | MEDIUM | DevOps | Start S1 Day 1, Android fallback |
| R4: Correlation complexity > estimate | MEDIUM | Backend | Reuse VibrationAnomalyJob CEP pattern |
| R5: Pilot data insufficient | LOW | QA | Synthetic data cho initial tuning |
| R6: Mobile offline delays S2 | MEDIUM | Frontend | Descope to cache-only |
| R7: Low operator feedback adoption | LOW | Frontend | Gamification + default prompt |

---

## 7. ADRs cần SA viết

| ADR | Sprint | Trigger Task |
|-----|--------|-------------|
| ADR-041: AI Cost Optimization | S2 | #9 (M4-AI-01 batching) |
| ADR-042: Correlation Engine | S3 | #13 (M4-COR-01) |
| ADR-043: BMS Safety Protocol | S5 | #21 (M4-COR-03) |
| ADR-044: Self-Service Architecture | S3-4 | #14 (M4-SS-01) |
| ADR-045: Welford Universal | S3-4 | #15 (M4-AI-07) |
| ADR-046: Incident Feedback Loop | S5-6 | #25 (M4-COR-07) |

---

*Tạo bởi: UIP Team Orchestrator (2026-06-12)*
*Input: docs/mvp4/README.md*
