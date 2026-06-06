# JaCoCo Test Coverage Report — 2026-05-22

**Build**: `./gradlew test jacocoTestReport --no-daemon`  
**Result**: BUILD SUCCESSFUL (6m 35s)  
**Report location**: `backend/build/reports/jacoco/test/jacocoTestReport.xml`

---

## Test Execution Summary

| Metric     | Value |
|------------|-------|
| Tests run  | 838   |
| Failures   | 0     |
| Errors     | 0     |
| Skipped    | 0     |

> Infrastructure warnings (Kafka localhost:9999 unreachable, PostgreSQL localhost:55333/55335/60332 refused) during test execution are **expected** — the backend Spring context starts without local infra, and all tests use mocked/Testcontainers-based dependencies.

---

## Overall Coverage

| Counter      | Covered | Total  | Coverage |
|--------------|---------|--------|----------|
| LINE         | 2,533   | 2,716  | **93.3%** |
| INSTRUCTION  | 13,776  | 16,186 | **85.1%** |
| METHOD       | 568     | 656    | **86.6%** |
| BRANCH       | 617     | 1,087  | **56.8%** |
| COMPLEXITY   | 827     | 1,210  | **68.3%** |
| CLASS        | 88      | 89     | **98.9%** |

---

## Package-level Line Coverage

| Package | Lines | Covered | Coverage |
|---------|-------|---------|----------|
| `com.uip.backend` | 3 | 3 | 100.0% |
| `com.uip.backend.auth.api` | 4 | 4 | 100.0% |
| `com.uip.backend.auth.service` | 161 | 130 | 80.7% |
| `com.uip.backend.alert.kafka` | 52 | 45 | 86.5% |
| `com.uip.backend.alert.service` | 121 | 113 | 93.4% |
| `com.uip.backend.building.service` | 64 | 63 | 98.4% |
| `com.uip.backend.citizen.service` | 146 | 128 | 87.7% |
| `com.uip.backend.common.filter` | 9 | 9 | 100.0% |
| `com.uip.backend.common.logging` | 3 | 3 | 100.0% |
| `com.uip.backend.common.ratelimit` | 64 | 63 | 98.4% |
| `com.uip.backend.common.service` | 31 | 31 | 100.0% |
| `com.uip.backend.environment.service` | 165 | 154 | 93.3% |
| `com.uip.backend.esg.common` | 14 | 14 | 100.0% |
| `com.uip.backend.esg.dto` | 1 | 1 | 100.0% |
| `com.uip.backend.esg.export` | 256 | 249 | 97.3% |
| `com.uip.backend.esg.kafka` | 12 | 12 | 100.0% |
| `com.uip.backend.esg.service` | 297 | 288 | 97.0% |
| `com.uip.backend.notification.service` | 219 | 182 | 83.1% |
| `com.uip.backend.partner` | 5 | 4 | 80.0% |
| `com.uip.backend.scheduler` | 21 | 21 | 100.0% |
| `com.uip.backend.tenant.context` | 16 | 16 | 100.0% |
| `com.uip.backend.tenant.filter` | 45 | 37 | 82.2% |
| `com.uip.backend.tenant.hibernate` | 23 | 23 | 100.0% |
| `com.uip.backend.tenant.kafka` | 15 | 15 | 100.0% |
| `com.uip.backend.tenant.repository` | 4 | 4 | 100.0% |
| `com.uip.backend.tenant.service` | 160 | 145 | 90.6% |
| `com.uip.backend.traffic.service` | 114 | 114 | 100.0% |
| `com.uip.backend.workflow.controller` | 103 | 103 | 100.0% |
| `com.uip.backend.workflow.delegate` | 26 | 23 | 88.5% |
| `com.uip.backend.workflow.delegate.citizen` | 76 | 74 | 97.4% |
| `com.uip.backend.workflow.delegate.management` | 95 | 90 | 94.7% |
| `com.uip.backend.workflow.dto` | 43 | 37 | 86.0% |
| `com.uip.backend.workflow.service` | 247 | 244 | 98.8% |
| `com.uip.backend.workflow.trigger` | 86 | 76 | 88.4% |
| `com.uip.backend.workflow.trigger.strategy` | 15 | 15 | 100.0% |

---

## Coverage Highlights

### Strong (≥95%)
- `traffic.service` — 100%
- `workflow.controller` — 100%
- `esg.service` — 97.0%
- `esg.export` — 97.3%
- `workflow.service` — 98.8%
- `building.service` — 98.4%
- `scheduler` — 100%

### Needs Attention (<85%)
- `auth.service` — 80.7% (161 lines, 31 uncovered)
- `notification.service` — 83.1% (219 lines, 37 uncovered)
- `tenant.filter` — 82.2% (45 lines, 8 uncovered)
- `partner` — 80.0% (5 lines, 1 uncovered)

### Branch Coverage Gap
Overall branch coverage is **56.8%** — significantly lower than line coverage (93.3%). This indicates many conditional branches (if/else, switch, try/catch) are not exercised by tests. Primary areas to improve:
- Notification service edge cases
- Auth service token/role branches
- Workflow trigger conditions

---

## Uncovered Class
- 1 class out of 89 has 0% coverage (CLASS coverage: 98.9%)
