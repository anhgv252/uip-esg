# Sprint 5 — Workflow Wizard UAT Sign-off

**Date:** 2026-10-10
**Environment:** Staging
**Tested by:** QA Engineer + 1 Operator
**Status:** PENDING SIGN-OFF

## Test Scope

WorkflowWizard 3-step flow for ≥3 templates:
- Step 1: Template selection
- Step 2: Parameter entry (field-type-specific form)
- Step 3: Review & deploy confirmation

## Test Cases

| # | Template | Step 1 | Step 2 (Params) | Step 3 (Review) | Deploy | Result |
|---|----------|--------|-----------------|-----------------|--------|--------|
| 1 | Flood Alert | ✅ | ✅ (waterLevelM=1.5) | ✅ | ✅ | PASS |
| 2 | Energy Optimization | ✅ | ✅ (targetReduction=20) | ✅ | ✅ | PASS |
| 3 | Building Safety Inspection | ✅ | ✅ (inspectionType=FULL) | ✅ | ✅ | PASS |
| 4 | AQI Threshold | PENDING | PENDING | PENDING | PENDING | PENDING |
| 5 | Traffic Incident | PENDING | PENDING | PENDING | PENDING | PENDING |

## UX Observations

- Step 2 form renders correctly for all field types (text, number, select, boolean)
- Required field validation prevents advancing with empty required fields
- Step 3 review shows correct param values
- Navigation between steps (Back/Next) preserves entered values

## Acceptance Criteria

- [ ] Wizard end-to-end functional for ≥3 templates (3/5 PASS — minimum met)
- [ ] Deploy confirmation dialog appears before execution
- [ ] Deploy success → toast notification + Process Instances table updated
- [ ] Deploy failure → error message shown + retry button works
- [ ] Operator can complete full flow without developer assistance

## Bugs Found

_None reported during tested cases (TC-1 through TC-3)._

## Sign-off

- [ ] QA Engineer: _______________
- [ ] Operator (City Authority Rep): _______________
