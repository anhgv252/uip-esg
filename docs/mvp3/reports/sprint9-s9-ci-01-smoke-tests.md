# Sprint 9 S9-CI-01: Configuration Smoke Tests

**Date:** 2026-06-04  
**Status:** ✅ Complete  

## Overview

Added configuration smoke tests to CI pipeline that verify critical infrastructure components exist after deployment:
- Keycloak realm and clients
- ClickHouse tables
- Kafka topics

## Files Created/Modified

### 1. `infrastructure/scripts/smoke-test-config.sh` (NEW)
- Bash script with color-coded output
- Checks Keycloak realm `uip` and 4 clients (`uip-frontend`, `uip-mobile`, `pilot-operator`, `pilot-viewer`)
- Checks ClickHouse tables: `sensor_reading_hourly`, `esg_metric_monthly` (supports distributed variants)
- Checks Kafka topics: `sensor-events`, `alert-events`, `esg-metrics`
- Supports `--service keycloak|clickhouse|kafka` flag to run subset
- 10s timeout per service check
- Exits 0 if all pass, 1 if any fail
- Output format: `[SMOKE] Checking X... PASS/FAIL`

### 2. `infrastructure/Makefile` (MODIFIED)
Added target:
```makefile
smoke-test: ## Run config smoke tests (Keycloak + ClickHouse + Kafka)
	@bash scripts/smoke-test-config.sh
```

### 3. `.github/workflows/smoke-test.yml` (NEW)
- Triggers on: workflow_dispatch, workflow_call, push to main/develop (when infra files change)
- Spins up Keycloak, ClickHouse, Kafka, Redis, TimescaleDB
- Initializes ClickHouse schema with required tables
- Creates Kafka topics
- Imports Keycloak realm (if realm-export.json exists)
- Runs smoke test script
- Shows logs on failure
- Cleans up with `docker compose down -v`

## Technical Details

### Keycloak Check
- Uses admin REST API: `/admin/realms/uip/clients`
- Gets admin token from master realm
- Checks for client existence by `clientId` field

### ClickHouse Check
- Uses HTTP API on port 8123
- Queries `system.tables` for database `analytics`
- Supports both regular and distributed table variants (`_dist` suffix)

### Kafka Check
- Tries `docker exec kafka kafka-topics.sh` first (CI environment)
- Falls back to `kcat` or `kafkacat` if available
- Lists topics and checks for exact matches

## Acceptance Criteria

✅ `infrastructure/scripts/smoke-test-config.sh` exists, bash syntax valid  
✅ Script checks all 3 services (Keycloak, ClickHouse, Kafka)  
✅ Script exits 0 if all checks pass, 1 if any fail  
✅ GitHub Actions workflow exists for smoke tests  
✅ `Makefile` has `smoke-test` target  

## Usage

### Local testing:
```bash
cd infrastructure
make smoke-test
```

### Run subset:
```bash
./scripts/smoke-test-config.sh --service keycloak
./scripts/smoke-test-config.sh --service clickhouse
./scripts/smoke-test-config.sh --service kafka
```

### CI:
- Automatically runs on push to main/develop when infrastructure files change
- Can be triggered manually via workflow_dispatch
- Can be called from other workflows via workflow_call

## Integration Points

- Complements existing `.github/workflows/test.yml` (backend unit tests)
- Follows pattern of `.github/workflows/api-contract-check.yml` (contract validation)
- Uses same Docker Compose setup as dev/UAT environments

## Known Limitations

1. **Keycloak realm import**: CI workflow assumes `infrastructure/keycloak/realm-export.json` exists. If missing, realm import is skipped with warning.
2. **Kafka client**: Requires either Docker (for `docker exec`), `kcat`, or `kafkacat` installed on host.
3. **ClickHouse schema**: CI workflow creates minimal schema for smoke tests. Production deployments should use proper migrations.

## Future Enhancements

- Add checks for Kong routes/plugins
- Add checks for TimescaleDB hypertables
- Add checks for Redis keys/cluster health
- Add checks for Flink jobs deployed
- Parameterize expected table/topic names from config file
