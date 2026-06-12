# Sprint 4 — Workflow Template Library UAT Sign-off

**Date:** 2026-09-26
**Environment:** Staging
**Tested by:** QA Engineer
**Status:** PENDING SIGN-OFF

## Test Scope

10 workflow templates across 5 categories: FLOOD, AIR_QUALITY, EQUIPMENT, ESG, COMPLAINT.

## Test Cases

| # | Template | Category | Search works? | Filter works? | Params editable? | Deploy button works? | Result |
|---|----------|----------|--------------|--------------|-----------------|---------------------|--------|
| 1 | Flood Alert | FLOOD | ✅ | ✅ | ✅ | ✅ | PASS |
| 2 | AQI Threshold | AIR_QUALITY | ✅ | ✅ | ✅ | ✅ | PASS |
| 3 | Equipment Maintenance | EQUIPMENT | ✅ | ✅ | ✅ | ✅ | PASS |
| 4 | ESG Report | ESG | ✅ | ✅ | ✅ | ✅ | PASS |
| 5 | Citizen Complaint | COMPLAINT | ✅ | ✅ | ✅ | ✅ | PASS |
| 6 | Energy Optimization | EQUIPMENT | PENDING | PENDING | PENDING | PENDING | PENDING |
| 7 | Noise Alert | AIR_QUALITY | PENDING | PENDING | PENDING | PENDING | PENDING |
| 8 | Water Level Alert | FLOOD | PENDING | PENDING | PENDING | PENDING | PENDING |
| 9 | Building Safety Inspection | COMPLAINT | PENDING | PENDING | PENDING | PENDING | PENDING |
| 10 | Traffic Incident Response | COMPLAINT | PENDING | PENDING | PENDING | PENDING | PENDING |

## Acceptance Criteria

- [ ] ≥10 templates operator-verifiable
- [ ] Template gallery renders in <2s on staging
- [ ] Search returns correct results for all tested queries
- [ ] Category filter correctly filters templates
- [ ] All params are editable in workflow wizard
- [ ] Deploy button triggers correct BPMN process key

## Test Execution Notes

### Search Test Queries Verified (TC 1–5)

| Template | Search Query Used | Result Count | Correct? |
|----------|------------------|--------------|---------|
| Flood Alert | "flood" | 2 | ✅ |
| AQI Threshold | "aqi" | 1 | ✅ |
| Equipment Maintenance | "maintenance" | 1 | ✅ |
| ESG Report | "esg" | 1 | ✅ |
| Citizen Complaint | "complaint" | 3 | ✅ |

### Params Verified (TC 1–5)

| Template | Param Name | Editable? | Default Value Shown? |
|----------|-----------|----------|---------------------|
| Flood Alert | waterLevelThreshold (m) | ✅ | 1.80 |
| AQI Threshold | aqiWarningLevel | ✅ | 150 |
| Equipment Maintenance | maintenanceIntervalDays | ✅ | 30 |
| ESG Report | reportingQuarter | ✅ | Current quarter |
| Citizen Complaint | escalationHours | ✅ | 24 |

### BPMN Process Keys Triggered (TC 1–5)

| Template | Expected Process Key | Triggered? |
|----------|---------------------|-----------|
| Flood Alert | Process_FloodAlert_v2 | ✅ |
| AQI Threshold | Process_AqiThreshold_v1 | ✅ |
| Equipment Maintenance | Process_EquipMaintenance_v1 | ✅ |
| ESG Report | Process_EsgReport_v3 | ✅ |
| Citizen Complaint | Process_CitizenComplaint_v1 | ✅ |

## Known Issues

_None reported_

## Performance

| Metric | Target | Measured | Pass? |
|--------|--------|---------|-------|
| Gallery initial render | < 2s | 1.3s | ✅ |
| Search response | < 500ms | 220ms | ✅ |
| Category filter | < 200ms | 80ms | ✅ |

## Sign-off

- [ ] QA Engineer sign-off
- [ ] Product Owner acceptance

---
_Document owner: QA Engineer — Sprint 4_
_Next review: After TC 6–10 execution_
