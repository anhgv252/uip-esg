# Sprint 1 Review — Pilot Stabilize + v3.1 Start

| Field | Value |
|---|---|
| **Sprint** | MVP4-S1 (Aug 04-15, 2026) |
| **Review Date** | 2026-06-15 (back-filled) |
| **Sprint Goal** | Pilot go-live ổn định + iOS/Android store + security hardening + BMS stub fix + testing foundation |
| **Status** | ✅ All assigned tasks DEV DONE |

---

## 1. Deliverables Completed

### Backend (#1-4) — 27.5 SP ✅
| Task | Items | Status |
|---|---|---|
| #1 JWT/Rate/SQL ITs | JwtAuthenticationFilterIT, RateLimiterIT, SqlInjectionProtectionTest | ✅ |
| #2 BMS+MQTT+TODOs | BacnetIpAdapter.sendCommand() real BACnet writeProperty, MqttPublisher thread-safe, 0 TODO markers | ✅ |
| #3 Env/Traffic+CO2 | EnvironmentControllerWebMvcTest, TrafficControllerWebMvcTest, CO2 `@Value` externalized | ✅ |
| #4 DLQ+PII+Refactor | Kafka DLQ coverage, email PII masking, ObjectMapper refactor, SecurityConfig Lombok | ✅ |

### Frontend (#5) — 7 SP ✅
- BPMN UX polish, Code-split AiWorkflowPage (648KB → 15.91KB), aria-label, MUI theme colors
- `npx tsc --noEmit` → 0 errors

### DevOps (#6) — 7.5 SP ✅ (with follow-up)
- FCM/APNs credentials wired, generate-passwords.sh, mem_limit all services, Flink Makefile targets
- iOS/Android submission guides authored
- ⚠️ **Follow-up resolved 2026-06-15:** `.env` untracked from git, CHANGE_ME placeholders in `.env.staging` externalized (see P0-2 fix)

### QA (#7) — 13 SP ✅
- Perf baseline, chaos suite (run-all-chaos.sh), REST Assured contract tests start, parameterized thresholds, Awaitility migration

### PM (#8) — 1 SP ⏳
- Coverage fix + pilot monitoring — **ongoing, see Task #2**

---

## 2. Sprint Gate Verification

| Gate Criterion | Status |
|---|---|
| Pilot running 7 days without P0 incidents | ⏳ Pending pilot go-live |
| iOS submitted | ⏳ Pending (guides ready, submission is ops task) |
| Android APK live | ⏳ Pending (guides ready) |

> Sprint gate depends on real pilot execution (Aug 2026), not just code. Code-side criteria all PASS.

---

## 3. Risks Updated

| Risk | Status |
|---|---|
| R3 iOS Apple review reject | Mitigated: submission guides ready, Android fallback documented |
| P0-2 CHANGE_ME passwords | ✅ Resolved 2026-06-15 (.env untracked, placeholders externalized) |

---

## 4. Carry-over to Sprint 2

- GAP-010 gRPC IT vs real analytics-service — deferred (Task #5)
- PM Task #8 coverage fix — ongoing (Task #2)

---

*Reviewer: Solution Architect | Back-filled from task-*.md DEV DONE records (2026-06-15)*
