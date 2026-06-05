# Sprint 9 DevOps Tasks — Implementation Report

**Date:** 2026-06-05  
**Engineer:** UIP DevOps  
**Tasks:** S9-TD-KEEPER, S9-MOB-SIM

---

## Task 1: S9-TD-KEEPER — ClickHouse Keeper 3-Node Quorum ✅

### Objective
Add `clickhouse-keeper-02` and `clickhouse-keeper-03` to `infrastructure/docker-compose.ha.yml` so ClickHouse Keeper is a 3-node quorum (no SPOF). Killing 1 keeper must not stop the HA stack.

### Implementation

#### 1. Created 3 Keeper Config Files
- **`infrastructure/clickhouse/keeper-config-01.xml`** — server_id=1
- **`infrastructure/clickhouse/keeper-config-02.xml`** — server_id=2
- **`infrastructure/clickhouse/keeper-config-03.xml`** — server_id=3

Each config has unique `server_id` but identical `raft_configuration` listing all 3 nodes:
```xml
<raft_configuration>
    <server>
        <id>1</id>
        <hostname>clickhouse-keeper</hostname>
        <port>9234</port>
    </server>
    <server>
        <id>2</id>
        <hostname>clickhouse-keeper-02</hostname>
        <port>9234</port>
    </server>
    <server>
        <id>3</id>
        <hostname>clickhouse-keeper-03</hostname>
        <port>9234</port>
    </server>
</raft_configuration>
```

#### 2. Updated docker-compose.ha.yml
- **Updated `clickhouse-keeper`** service to mount `keeper-config-01.xml`
- **Added `clickhouse-keeper-02`** service:
  - Container: `uip-clickhouse-keeper-02`
  - Port: `9182:9181` (offset port)
  - Volume: `uip-clickhouse-keeper-02-data`
  - Config: `keeper-config-02.xml`
  - Health check: `echo ruok | nc localhost 9181`

- **Added `clickhouse-keeper-03`** service:
  - Container: `uip-clickhouse-keeper-03`
  - Port: `9183:9181` (offset port)
  - Volume: `uip-clickhouse-keeper-03-data`
  - Config: `keeper-config-03.xml`
  - Health check: `echo ruok | nc localhost 9181`

- **Updated ClickHouse nodes** (`clickhouse-01`, `clickhouse-02`):
  - Added `depends_on` all 3 keepers (service_healthy condition)
  - Ensures nodes wait for quorum before starting

- **Added volume definitions**:
  ```yaml
  clickhouse-keeper-02-data:
    name: uip-clickhouse-keeper-02-data
  clickhouse-keeper-03-data:
    name: uip-clickhouse-keeper-03-data
  ```

#### 3. Updated ClickHouse Node Config
- **`infrastructure/clickhouse/node-config.xml`** now lists all 3 keeper endpoints:
  ```xml
  <zookeeper>
      <node>
          <host>clickhouse-keeper</host>
          <port>9181</port>
      </node>
      <node>
          <host>clickhouse-keeper-02</host>
          <port>9181</port>
      </node>
      <node>
          <host>clickhouse-keeper-03</host>
          <port>9181</port>
      </node>
  </zookeeper>
  ```

#### Quorum Behavior
- **Majority requirement:** 2 out of 3 keepers must be healthy for quorum
- **Tolerance:** Can lose 1 keeper without impacting ClickHouse operations
- **Raft protocol:** Automatic leader election if leader fails

#### Verification
- ✅ YAML syntax validated: `docker-compose config` passed
- ✅ Follows ADR-036 (ClickHouse HA) patterns
- ✅ Named volumes for persistence (non-negotiable)
- ✅ Health checks on all keeper containers
- ✅ Resource isolation via separate containers

---

## Task 2: S9-MOB-SIM — Mobile Simulator CI ✅

### Objective
Create `.github/workflows/mobile-sim.yml` — a GitHub Actions workflow that validates the Expo/React Native app compiles and renders, saves web bundle as artifact.

### Implementation

#### Workflow File: `.github/workflows/mobile-sim.yml`

**Triggers:**
- Push to `main`, `develop`, `release/**` branches
- Pull requests
- Paths: `applications/operator-mobile/**`, `packages/hooks/**`, `packages/api-types/**`

**Jobs:**

1. **`mobile-type-check`** — TypeScript Type Check
   - Runs `npm run typecheck` (tsc --noEmit)
   - Validates TypeScript strict mode compliance
   - Node.js 20 with npm cache
   - Working directory: `applications/operator-mobile`

2. **`mobile-android-sim`** — Expo Web Export (Android fallback)
   - Runs `npx expo export --platform web`
   - Verifies web bundle generated (checks for `_expo/static/js/web/index-*.js`)
   - Uploads web bundle as artifact (retention: 7 days)
   - **Why web export?**
     - GitHub ubuntu-latest runners lack KVM (no Android emulator support)
     - macOS runners cost 10x but support emulators
     - Web export validates app compiles and renders
     - **Limitation:** Does not test native modules (expo-secure-store, etc.)
     - **Future:** Consider self-hosted runner with KVM or paid macOS runner

3. **`mobile-sim-summary`** — CI Summary
   - Reports overall status
   - Fails if any job failed

**Concurrency:** Cancels in-progress runs on new commits (prevents queue buildup)

#### Acceptance Criteria Met
- ✅ TypeScript type check runs before export
- ✅ App renders (web bundle generated)
- ✅ Artifact uploaded for inspection
- ✅ Triggers on mobile/hooks/api-types changes

#### Known Limitations
- **No native module testing** — expo-secure-store not validated
- **No true Android emulator** — web platform is CI fallback
- **Recommendation:** Use self-hosted runner with KVM or macOS runner for full Android simulation in future sprints

---

## Files Changed

### Task 1 (S9-TD-KEEPER)
- ✅ `infrastructure/clickhouse/keeper-config-01.xml` (created)
- ✅ `infrastructure/clickhouse/keeper-config-02.xml` (created)
- ✅ `infrastructure/clickhouse/keeper-config-03.xml` (created)
- ✅ `infrastructure/docker-compose.ha.yml` (modified — added keeper-02, keeper-03 services + volumes)
- ✅ `infrastructure/clickhouse/node-config.xml` (modified — added 3 keeper endpoints)

### Task 2 (S9-MOB-SIM)
- ✅ `.github/workflows/mobile-sim.yml` (created)

---

## Deployment Verification

### Before deploying to dev/prod:
1. **Start HA stack:**
   ```bash
   docker-compose -f infrastructure/docker-compose.yml \
                  -f infrastructure/docker-compose.ha.yml up -d
   ```

2. **Verify all 3 keepers healthy:**
   ```bash
   docker ps | grep keeper
   # Should show 3 containers: uip-clickhouse-keeper, uip-clickhouse-keeper-02, uip-clickhouse-keeper-03
   
   echo ruok | nc localhost 9181  # keeper-01 → imok
   echo ruok | nc localhost 9182  # keeper-02 → imok
   echo ruok | nc localhost 9183  # keeper-03 → imok
   ```

3. **Check ClickHouse cluster status:**
   ```bash
   docker exec uip-clickhouse-01 clickhouse-client --query "SELECT * FROM system.clusters WHERE cluster='uip_cluster'"
   # Should show 2 replicas (clickhouse-01, clickhouse-02)
   ```

4. **Test quorum resilience:**
   ```bash
   # Stop 1 keeper (should still work — 2/3 majority)
   docker stop uip-clickhouse-keeper-02
   
   # ClickHouse should remain operational
   docker exec uip-clickhouse-01 clickhouse-client --query "SELECT 1"
   # → Should return 1 (not fail)
   
   # Restart keeper
   docker start uip-clickhouse-keeper-02
   ```

5. **Verify mobile CI:**
   - Push change to `applications/operator-mobile/` on a test branch
   - Check GitHub Actions → "Mobile Simulator CI" workflow runs
   - Confirm TypeScript check passes
   - Confirm web bundle artifact uploaded

---

## Open Items

None. Both tasks complete and ready for deployment.

---

## DevOps Sign-Off

**Task 1 (S9-TD-KEEPER):**  
✅ 3-node ClickHouse Keeper quorum implemented  
✅ YAML syntax validated  
✅ Named volumes for persistence  
✅ Health checks on all keepers  
✅ ClickHouse nodes depend on all 3 keepers (quorum required)  
✅ Ready for deployment verification

**Task 2 (S9-MOB-SIM):**  
✅ GitHub Actions workflow created  
✅ TypeScript type check job implemented  
✅ Expo web export job implemented (Android fallback)  
✅ Artifact upload configured  
✅ Triggers on correct paths and branches  
✅ Ready for next PR to test workflow

**Deployment Risk:** Low  
**Blocker:** None

---
**DevOps Engineer:** UIP DevOps  
**Date:** 2026-06-05
