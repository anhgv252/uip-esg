# Sprint M5-5 Kickoff — Final Sprint (EV Charging + Gate Closures)

**Sprint:** MVP5 Sprint M5-5 (FINAL)  
**Duration:** 2026-06-30 → 2026-07-13 (2 weeks)  
**Theme:** EV Charging OCPP + Gate G6/G7/G8 Compliance + MVP5 Close  
**PM:** UIP-project-manager  

---

## Sprint Goals

M5-5 is the **final sprint** of MVP5. Focus:
1. **EV Charging OCPP** (BA vertical 3) — T01-T04 (new feature)
2. **Mobile v3.1 GA** — T05-T06
3. **Synthetic 50-tenant full run** (R16 close) — T07
4. **Gate G6 compliance** (OWASP + Decree 13 audit log) — T08-T10
5. **Gate G7 functional UAT** (NL UAT close) — T11-T12
6. **Gate G8 architecture validation** — T13
7. **MVP5 close report** — T14

Also includes **10 carry-over tasks** from M5-3 and M5-4 (22 SP deferred + 11 SP from M5-3).

---

## Sprint Capacity

| Resource | Available Days | SP/Day | Capacity (SP) |
|---|---|---|---|
| Backend (2 engineers) | 2 × 10 days | 2.5 | 50 SP |
| Frontend (2 engineers) | 2 × 10 days | 2 | 40 SP |
| QA (1 engineer) | 10 days | 2 | 20 SP |
| DevOps (0.5 engineer) | 5 days | 2 | 10 SP |
| BA (0.5 engineer) | 5 days | 1 | 5 SP |
| **Total Capacity** | — | — | **125 SP** |

**Buffer (20%):** 100 SP effective capacity

---

## Task Breakdown

### Phase 1 — New Features (EV Charging OCPP) — 20 SP

| Task | Title | Owner | SP | AC |
|---|---|---|---|---|
| **T01** | OCPP 1.6J charge point service | Backend | 5 | WebSocket endpoint `/ocpp/ws`, BootNotification/StartTransaction/StopTransaction handlers |
| **T02** | EV charging session tracking | Backend | 3 | `ev_charge_sessions` table, CRUD API, tenant-aware |
| **T03** | EV charging dashboard | Frontend | 5 | Live map + session list, charger status badges |
| **T04** | OCPP integration tests | QA | 5 | OCPP-J simulator, 3 charge scenarios |

**Dependencies:**
- T03 depends on T01+T02 (API ready)
- T04 depends on T01 (WebSocket endpoint)

---

### Phase 2 — Mobile v3.1 GA — 8 SP

| Task | Title | Owner | SP | AC |
|---|---|---|---|---|
| **T05** | Mobile push notification (FCM) | Backend | 3 | `mobile_device_tokens` table, Kafka → FCM bridge |
| **T06** | Mobile v3.1 GA release | Mobile | 5 | PWA manifest, iOS splash, service worker |

**Note:** T06 includes M5-3 T11 (Mobile stub, 3 SP deferred).

---

### Phase 3 — Synthetic + Scale Testing — 8 SP

| Task | Title | Owner | SP | AC |
|---|---|---|---|
| **T07** | 50-tenant synthetic full run | QA + DevOps | 8 | R16 close: ≤ 2 INV-4+ severity; 2h run, HA mode |

**Critical for G7.** Includes:
- NL routing stress test (M5-3 T13 synthetic)
- Kafka lag monitoring
- HA failover validation

---

### Phase 4 — Gate G6 Compliance (OWASP + Decree 13) — 8 SP

| Task | Title | Owner | SP | AC |
|---|---|---|---|
| **T08** | OWASP Top 10 audit report | QA | 3 | Dependency-check output → security doc |
| **T09** | Decree 13 audit log validation | Backend | 3 | Audit log covers all admin actions (user CRUD, role grant, config change) |
| **T10** | Penetration test prep (checklist) | DevOps | 2 | HTTPS only, CORS policy, rate limiting enabled |

---

### Phase 5 — Gate G7 Functional UAT — 12 SP

| Task | Title | Owner | SP | AC |
|---|---|---|---|
| **T11** | NL UAT (HCMC operators) | BA + QA | 5 | ≥ 98% valid responses, 20 test cases, user feedback log |
| **T12** | NL latency optimization | Backend | 3 | P95 < 3s (ViT5 endpoint provisioning MANDATORY) |
| **T13** | UAT feedback integration | All | 4 | Bug fixes from T11, retro doc |

**Blocker for T11:** HCMC operator availability. PM MUST confirm by 2026-07-05.

**Blocker for T12:** DevOps provision ViT5 endpoint (M5-3 T05 deferred).

---

### Phase 6 — Gate G8 Architecture Validation — 5 SP

| Task | Title | Owner | SP | AC |
|---|---|---|---|
| **T14** | Architecture validation report | SA | 5 | ADR review, dependency audit, tech debt register |

---

### Phase 7 — MVP5 Close — 3 SP

| Task | Title | Owner | SP | AC |
|---|---|---|---|
| **T15** | MVP5 close report | PM | 3 | Gate G4/G6/G7/G8 summary, velocity trend, lessons learned |

---

### Carry-over Tasks from M5-4 (22 SP)

| Task | Title | Owner | SP | Priority | Week |
|---|---|---|---|---|---|
| **M5-4 T04** | Billing dispute workflow | Backend + Frontend | 3 | P0 | Week 1 |
| **M5-4 T16** | Billing integration tests | QA | 5 | P0 | Week 1 |
| **M5-4 T08** | LOTUS VN AC validation | BA | 2 | P1 | Week 2 (post pilot data) |
| **M5-4 T12** | ISO 37120 AC validation | BA | 2 | P1 | Week 2 |
| **M5-4 T17** | LOTUS VN integration tests | QA | 5 | P2 | Week 2 (depends on T08) |
| **M5-3 T14+T15** | NL UAT | BA + QA | 5 | P0 | Week 2 (→ T11 above) |

**NOTE:** NL UAT (M5-3 T14+T15) is merged into M5-5 T11.

---

### Carry-over Tasks from M5-3 (11 SP)

| Task | Title | Owner | SP | Status | Merge Target |
|---|---|---|---|---|---|
| **M5-3 T05** | NL latency optimization | Backend | 3 | ViT5 endpoint PENDING | → M5-5 T12 |
| **M5-3 T08** | ROI AC validation | BA | 2 | Pilot data pending | Deferred to Post-MVP5 |
| **M5-3 T11** | Mobile stub | Frontend | 3 | Deferred | → M5-5 T06 |
| **M5-3 T13** | Synthetic NL routing | QA | 3 | Backend done, frontend deferred | → M5-5 T07 |

**Total Carry-over:** 33 SP (M5-4: 22 SP, M5-3: 11 SP)

---

## Sprint Commitment

| Phase | SP | Priority |
|---|---|---|
| **New Tasks (T01-T15)** | 64 SP | P0 |
| **Carry-over (M5-4)** | 22 SP | P0-P2 |
| **Carry-over (M5-3)** | 11 SP | P1 |
| **Total Committed** | **97 SP** | — |
| **Capacity** | 100 SP | — |
| **Buffer** | 3 SP | — |

**Risk:** 3% buffer is tight. If T11 (NL UAT) or T07 (50-tenant) overrun, T14 (SA review) may slip.

---

## Critical Path

```
Week 1 (2026-06-30 → 2026-07-06):
  Day 1-2: T01 (OCPP service) + M5-4 T04 (Billing dispute) + M5-4 T16 (Billing tests)
  Day 3-4: T02 (EV session tracking) + T05 (Mobile push)
  Day 5:   T03 (EV dashboard)

Week 2 (2026-07-07 → 2026-07-13):
  Day 1-2: T07 (50-tenant run) + T12 (NL latency)
  Day 3-4: T11 (NL UAT) — BLOCKER: operator availability
  Day 5:   T08-T10 (Gate G6) + T13 (UAT feedback) + T14 (SA review) + T15 (MVP5 close)
```

**Gate Sequence:**
- **G6** (OWASP + Decree 13): End of Week 1 (2026-07-06)
- **G7** (Functional UAT): End of Week 2 (2026-07-13)
- **G8** (Architecture validation): 2026-07-13 (parallel with G7)

---

## Risks & Mitigation

| Risk | Likelihood | Impact | Mitigation | Owner |
|---|---|---|---|---|
| **R2 (NL hallucination)** | HIGH | CRITICAL | T11 UAT execution mandatory; PM confirm operators by 2026-07-05 | PM |
| **R16 (50-tenant scale)** | MEDIUM | HIGH | T07 must run; if INV-4+ found, SA queue batching fix (hotfix) | SA + QA |
| **ViT5 endpoint not provisioned** | HIGH | HIGH | DevOps escalate to cloud provider by 2026-07-02; fallback: external ViT5 API | DevOps |
| **Billing dispute workflow slips** | MEDIUM | MEDIUM | Backend prioritizes M5-4 T04 Week 1; if slip → revenue impact acceptable (pilot) | Backend |
| **Carry-over 33 SP overload** | HIGH | MEDIUM | Defer M5-4 T17 (LOTUS integration tests) to Post-MVP5 if buffer consumed | PM |

**NEW RISK — R18 (HCMC operator availability for NL UAT):**
- **Impact:** G7 FAIL if NL UAT not executed
- **Mitigation:** PM MUST confirm operator availability by 2026-07-05; if not → synthetic NL test with BA validation (fallback, but weaker evidence)
- **Owner:** PM

---

## Definition of Done (Sprint M5-5)

### Feature Completeness
- [ ] EV Charging OCPP service live (T01-T04)
- [ ] Mobile v3.1 GA released (T06)
- [ ] 50-tenant synthetic run PASS (≤ 2 INV-4+ severity)

### Gate Closures
- [ ] **G6** PASS: OWASP report + Decree 13 audit log validated
- [ ] **G7** PASS: NL UAT ≥ 98% valid + billing 7-day shadow 99.5%
- [ ] **G8** PASS: SA architecture validation report approved

### Carry-over Closure
- [ ] Billing dispute workflow DONE (M5-4 T04)
- [ ] Billing integration tests DONE (M5-4 T16)
- [ ] LOTUS VN AC + ISO AC signed off (M5-4 T08/T12)

### Documentation
- [ ] MVP5 close report (T15)
- [ ] Sprint M5-5 retro doc

---

## Ceremonies

| Ceremony | Day | Time | Attendees |
|---|---|---|---|
| **Sprint Planning** | 2026-06-30 (Mon) | 09:00 | All |
| **Daily Standup** | Mon-Fri | 09:30 | All |
| **Mid-Sprint Review** | 2026-07-06 (Sat) | 10:00 | PM + SA + Leads |
| **Gate G6 Review** | 2026-07-06 | 14:00 | SA + QA + DevOps |
| **Gate G7 Review** | 2026-07-13 (Sat) | 10:00 | All + BA (+ HCMC operators) |
| **Gate G8 Review** | 2026-07-13 | 14:00 | SA + PM |
| **Sprint Retro** | 2026-07-13 | 16:00 | All |
| **MVP5 Close** | 2026-07-13 | 17:00 | All |

---

## Success Criteria

| Criterion | Target | Measurement |
|---|---|---|
| **SP Delivered** | ≥ 90 SP | Velocity report |
| **Gate G6 PASS** | OWASP + Decree 13 validated | Compliance checklist |
| **Gate G7 PASS** | NL UAT ≥ 98% valid | UAT report |
| **Gate G8 PASS** | SA approval | Architecture validation report |
| **Carry-over to Post-MVP5** | ≤ 5 SP | Task closure rate |
| **P0 bugs from T07** | 0 INV-4 severity | 50-tenant run log |

---

## Go/No-Go Decision

| Criterion | Status | Blocker |
|---|---|---|
| **Core team available** | ✅ YES | — |
| **ViT5 endpoint provisioned** | 🟡 PENDING | DevOps escalate by 2026-07-02 |
| **HCMC operators confirmed** | 🟡 PENDING | PM confirm by 2026-07-05 |
| **50-tenant infra ready** | ✅ YES | HA mode validated M5-4 |
| **Billing 7-day shadow run complete** | 🟡 PENDING | DevOps run Week 1 |

**Decision:** 🟢 **GO** — Core implementations complete from M5-4. M5-5 can proceed with validation + new EV Charging vertical.

**Conditions:**
1. DevOps MUST provision ViT5 endpoint by 2026-07-02 (T12 blocker)
2. PM MUST confirm HCMC operators by 2026-07-05 (T11 blocker)
3. If R16 (50-tenant) finds INV-4+ severity bugs, SA queue batching fix MUST hotfix before G7

---

## Approvals

| Role | Name | Decision | Date |
|---|---|---|---|
| PM | UIP-project-manager | GO | 2026-06-29 |
| SA | [Pending] | — | — |
| Lead Backend | [Pending] | — | — |
| Lead Frontend | [Pending] | — | — |
| QA Lead | [Pending] | — | — |

**Sprint Start:** 2026-06-30 09:00  
**Sprint End:** 2026-07-13 17:00 (MVP5 Close)
