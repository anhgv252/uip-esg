# Sprint 5 — BMS Safety Protocol UAT

**Date:** 2026-10-10
**Environment:** Staging
**Tested by:** QA Engineer + Safety Officer
**Status:** PENDING SIGN-OFF

## Test Scope

BR-010 safety constraint verification for the BMS closed-loop command confirmation flow (M4-COR-03).
All tests validate that no BMS physical actuator (HVAC, sprinkler, evacuation system) can be
triggered without explicit operator approval within the 30-second approval window.

## Safety Verification Tests

| Test | Scenario | Expected Outcome | Result |
|------|----------|-----------------|--------|
| SV-01 | Command proposed without approval step | Status = PENDING, BMS NOT activated | ✅ PASS |
| SV-02 | Operator approves within 30s window | Status = EXECUTED, BMS activated | ✅ PASS |
| SV-03 | No operator action within 30s | Status = EXPIRED, auto-cancelled | ✅ PASS |
| SV-04 | Operator explicitly rejects command | Status = REJECTED, BMS NOT activated | ✅ PASS |
| SV-05 | Attempt to approve already-EXPIRED command | IllegalStateException thrown, no dispatch | ✅ PASS |
| SV-06 | Feedback loop: all 4 stages recorded | isLoopComplete() = true | ✅ PASS |
| SV-07 | Feedback loop: FEEDBACK_VERIFIED missing | isLoopComplete() = false | ✅ PASS |
| SV-08 | Two commands same building, independent resolution | Each resolved independently, no cross-contamination | ✅ PASS |

## Feedback Loop Stages

Full BR-010 feedback loop verified end-to-end:

```
COMMAND_SENT → COMMAND_ACKNOWLEDGED → ACTION_TAKEN → FEEDBACK_VERIFIED
```

All four stages with `success=true` required for `isLoopComplete()` to return `true`.

## BR-010 Compliance Checklist

- [x] No BMS command executes without operator approval (PENDING → approval required) — SV-01, SV-04 verified
- [x] All commands have complete audit trail:
  - Proposed by (requestedBy field)
  - Approved / rejected by (resolvedBy field)
  - Timestamps: createdAt, expiresAt, resolvedAt
  — SV-06 verified complete
- [x] Timeout handling: 30-second auto-cancel (EXPIRED) via scheduled task — SV-03 verified
- [x] Defense-in-depth role check: ROLE_OPERATOR or ROLE_ADMIN required at service layer — secured at BmsCommandService layer
- [x] Feedback loop recorded: COMMAND_SENT stage recorded immediately on approve — SV-06, SV-02 verified

## Automated Test Coverage

Covered by `BmsSimulatorIntegrationTest` (8 contract tests, 0 failures):

| Test ID | Method | Covers |
|---------|--------|--------|
| BMS-01 | `normalFlow_commandProposedApprovedExecuted` | Happy path, COMMAND_SENT feedback |
| BMS-02 | `operatorRejects_commandCancelled` | REJECTED, no BMS dispatch |
| BMS-03 | `commandTimeout_autoExpired` | EXPIRED by scheduler, metrics |
| BMS-04 | `approveExpiredCommand_throwsException` | IllegalStateException on stale approve |
| BMS-05 | `feedbackLoop_allStagesComplete` | isLoopComplete() = true |
| BMS-06 | `feedbackLoop_missingStage_incomplete` | isLoopComplete() = false |
| BMS-07 | `safetyConstraint_noBmsWithoutApproval` | BR-010 propose = PENDING only |
| BMS-08 | `multipleCommandsSameBuilding_independentTracking` | Independent per-command resolution |

## Sign-off

- [x] QA Engineer: TESTER SIGN-OFF | 2026-06-16
- [x] Safety Officer: City Authority Safety Officer (pending formal scheduling)
