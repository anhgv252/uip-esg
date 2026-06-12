# ADR-043: BMS Auto-Command Safety Protocol

| Field | Value |
|---|---|
| **ADR Number** | ADR-043 |
| **Title** | BMS Auto-Command Safety Protocol — 2-Step Operator Confirmation + Expiry |
| **Status** | Accepted |
| **Date** | 2026-06-12 |
| **Author** | Solution Architect |
| **Sprint** | MVP4-S5 (Task #21 — M4-COR-03/04) |
| **Supersedes** | — |
| **Related ADRs** | ADR-042 (Correlation Engine), ADR-046 (Feedback Loop) |

---

## Context

MVP4 Trụ 2 introduces **BMS auto-command**: when the correlation engine (ADR-042) concludes an incident warrants an automated building response (e.g. HVAC_OFF during an AQI event, SPRINKLER_ON for fire, EVACUATION for flood), the system *proposes* a command to the physical actuator via BACnet/Modbus.

This is the highest-risk feature in UIP. A wrong command — sprinklers in a non-fire event, evacuation triggered by a sensor glitch — causes physical harm, property damage, and immediate loss of operator trust. Risk R2 (MVP4 README) rates this **CRITICAL**.

BR-010 (carried from Sprint 7) is non-negotiable:

> No BMS command (HVAC, sprinkler, evacuation) may execute without explicit operator approval.

Three failure modes the protocol must prevent:

1. **Autonomous execution.** The AI proposes a command and it fires without a human in the loop.
2. **Stale proposal.** The AI proposed a command 10 minutes ago based on a now-resolved event; an operator approving it now acts on stale data.
3. **Privilege escalation.** A read-only user (ANALYST, PARTNER) approves a destructive command.

---

## Decision

**A propose → approve → execute state machine with a 30-second approval window, enforced at THREE independent layers. No single layer's failure can let a command execute unauthorised.**

### State machine

```
                 ┌─────────────┐
   AI proposes → │   PENDING   │  ← 30s approval window (expires_at = createdAt + 30s)
                 └──────┬──────┘
              ┌─────────┼──────────┐
   operator   │         │          │  timeout (5s scheduled scan)
   APPROVE    │         │ REJECT   │
              ▼         ▼          ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ APPROVED │ │ REJECTED │ │ EXPIRED  │
        └────┬─────┘ └──────────┘ └──────────┘
             │  dispatch via BACnet
             ▼
        ┌──────────┐
        │ EXECUTED │  → record FeedbackStage.COMMAND_SENT
        └──────────┘
```

### Three-layer enforcement (defense-in-depth)

| Layer | Where | Mechanism | Failure mode it catches |
|---|---|---|---|
| **L1 — Controller** | `BmsCommandController` / `BmsDeviceCommandController` | `@PreAuthorize("hasRole('OPERATOR') or hasRole('ADMIN')")` | Anonymous or wrong-role HTTP call |
| **L2 — Service** | `BmsCommandService.approveCommand` | `assertOperatorOrAdmin()` reads `SecurityContextHolder`, throws `SecurityException` (BR-010) if authenticated principal lacks the role | Controller annotation bypassed (misconfigured security, test setup) |
| **L3 — AOP** | `CommandConfirmationAspect` on `@RequiresConfirmation` methods | Validates `X-Confirmation-Reason` (≥10 chars) + `X-Confirmation-Actuator-Name` for HIGH/EMERGENCY levels; audit-logs every confirmation | Programmatic call that skips the propose→approve flow |

L2 deliberately allows a **null** security context (scheduled task / system call) — but only the system can reach that path, and the system never calls `approveCommand` autonomously. The propose path has no role gate; only approve does.

### 30-second approval window

- **`expires_at = created_at + 30s`** at proposal time.
- A `@Scheduled(fixedDelay = 5000)` task (`expireTimeoutCommands`) scans for stale PENDING commands and marks them EXPIRED.
- `validatePendingAndNotExpired` proactively expires on approval attempt if the window already elapsed.
- **Why 30s:** long enough for an operator to read the incident context and decide; short enough that the underlying event is still current. Tuned during Sprint 5 UAT.

### Feedback loop integration

Every executed command opens a 4-stage feedback loop (ADR-046 adjacent, M4-COR-04):

```
COMMAND_SENT → COMMAND_ACKNOWLEDGED → ACTION_TAKEN → FEEDBACK_VERIFIED
```

`BmsCommandService.approveCommand` records `COMMAND_SENT` immediately after dispatch. The remaining stages arrive via BACnet ACK + actuator feedback + operator/sensor confirmation. `BmsFeedbackService.isLoopComplete()` returns true only when all four stages succeeded — the audit trail that the command did what it claimed.

---

## Consequences

### Positive

- **BR-010 enforced at three layers.** No single misconfiguration or bug can execute a command without an authorised operator.
- **Stale proposals auto-expire.** The 30s window + scheduled scan guarantees no PENDING command lingers. Expiry is an explicit `EXPIRED` state, observable in the UI and metrics (`bms_command_expired_total`).
- **Full audit trail.** Every approve records operator username, latency (`bms_command_approval_latency`), and the originating incident UUID (`requested_by`). Every confirmation is audit-logged by L3.
- **POC-safe.** When `targetDevice` is not a valid UUID (demo/staging mode), dispatch is skipped with a WARNING — the state machine still completes for demo purposes without touching real hardware.

### Negative

- **30s is a hard ceiling on auto-response latency.** A genuine emergency (fire) cannot act faster than an operator's reaction. This is intentional — BR-010 trades latency for safety. Fully autonomous fire response is explicitly out of scope.
- **Three layers add complexity.** A developer adding a new command endpoint must annotate at L1, ensure the service call hits L2, and decide on L3. Mitigated: the pattern is codified in existing controllers; copy-paste is safe.
- **POC UUID-skip is a footgun.** If a production deploy forgets to set real device UUIDs, commands silently no-op. Mitigated by the WARNING log + the feedback loop (COMMAND_ACKNOWLEDGED never arrives → loop never completes → visible in metrics).

### Risks & mitigations

| Risk | Mitigation |
|---|---|
| R2: wrong command sent | 2-step confirm + 30s window + L1/L2/L3; command type restricted to enum (HVAC_OFF, SPRINKLER_ON, EVACUATION) |
| Operator approves under pressure without reading | L3 requires free-text reason ≥10 chars — forces a moment of deliberation |
| Scheduled expiry task dies | fixedDelay + `@Transactional`; missed expiry caught by `validatePendingAndNotExpired` on next approval attempt |

---

## Compliance

- **BR-010**: satisfied — no command path reaches EXECUTED without an OPERATOR/ADMIN approval at L1 and L2.
- **SA checklist**: thread-safe (`@Transactional`), null-safe (`findById().orElseThrow`), metrics on every state transition, exception handling consistent (`EntityNotFoundException` / `IllegalStateException` / `SecurityException`).
- **UAT verified**: `sprint5-bms-safety-uat.md` — operator-rejects, timeout, NAK scenarios all PASS.

---

## Open questions (deferred)

1. **Tiered autonomy.** Should LOW-danger commands (e.g. dim lights) auto-execute while only HIGH/EMERGENCY require approval? Deferred — needs a per-command-type danger classification ADR.
2. **Multi-approver for EMERGENCY.** Should evacuation require two operators? Deferred — adds latency the 30s window can't absorb.

---

## References

- `backend/src/main/java/com/uip/backend/bms/service/BmsCommandService.java`
- `backend/src/main/java/com/uip/backend/bms/security/CommandConfirmationAspect.java`
- `backend/src/main/java/com/uip/backend/bms/api/RequiresConfirmation.java`
- `backend/src/main/java/com/uip/backend/bms/domain/CommandStatus.java`, `PendingBmsCommand.java`
- `docs/mvp4/uat/sprint5-bms-safety-uat.md`
- `docs/mvp4/README.md` §2 Trụ 2, §8 R2, §9 ADR-043

---

*Authored 2026-06-12 — MVP4 Sprint 5 retrospective, documented in Sprint 6 close-out.*
