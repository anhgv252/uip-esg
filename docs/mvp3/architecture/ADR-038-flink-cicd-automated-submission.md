# ADR-038: Flink Job CI/CD — Automated Submission Pipeline

**Date:** 2026-06-04
**Status:** Accepted
**Deciders:** SA, DevOps Lead, Backend Lead
**Sprint:** MVP3-8

---

## Status

Accepted — implemented in S8-OPS03, deployed to dev environment.

## Context

Through Sprint 7, all Flink job deployments were performed manually:

1. Developer builds the JAR locally: `./gradlew :flink-jobs:shadowJar`
2. Developer uploads JAR via the Flink Web UI at `http://flink-jobmanager:8081`
3. Developer clicks "Submit Job" and enters main class + parameters in the UI
4. To re-deploy, developer manually cancels the running job (losing in-flight state unless savepoint triggered manually)

This process had several operational problems:
- **Non-repeatable**: Each developer followed a slightly different upload/submit flow; parameter typos caused silent misconfiguration
- **No savepoint discipline**: Job cancellations without savepoint caused state loss (Welford estimator state, window accumulators)
- **No version tracking**: No record of which JAR version is running; rollback required re-upload
- **Blocked CI/CD**: DevOps could not automate environment refresh without manual intervention
- **Environment variable drift**: `WELFORD_MIN_SAMPLES` was hardcoded to 1000 in the Flink job JAR; different environments (test vs production) could not tune this without a code change and re-build

Sprint 8 task S8-OPS03 was scoped to resolve this by implementing an automated Flink job submission pipeline.

During implementation, a **BUG-009** was found in the submission script: the script used `job['id']` when iterating the `GET /jobs` API response to identify stale running job versions. The Flink REST API returns `job['jid']` (Job ID), not `job['id']`. This caused the cancel step to silently skip all running jobs and submit a duplicate. Fixed to `job['jid']` before deploy.

---

## Decision

Automate the Flink job lifecycle via a **shell script + Makefile + docker-compose submitter** pattern:

- `infrastructure/scripts/flink-deploy.sh`: canonical deployment script
- `Makefile` targets: `flink-submit`, `flink-status`, `flink-cancel`
- `flink-esg-job-submitter` docker-compose service: runs the deploy script on container startup, `depends_on: flink-jobmanager: condition: service_healthy`
- `WELFORD_MIN_SAMPLES` environment variable: replaces hardcoded constant in Flink job

### Deployment Script Flow

```
flink-deploy.sh
├── 1. Build JAR
│      ./gradlew :flink-jobs:shadowJar
│      JAR_PATH = flink-jobs/build/libs/flink-jobs-*-all.jar
│
├── 2. Upload JAR to Flink
│      POST /jars/upload  →  jar_id
│
├── 3. Check running jobs
│      GET /jobs  →  filter state=RUNNING, name contains "EsgDualSinkJob"
│      For each running job:
│        POST /jobs/{jid}/stop  (trigger savepoint)  →  wait savepoint complete
│        GET /jobs/{jid}  →  confirm FINISHED
│
├── 4. Submit new JAR
│      POST /jars/{jar_id}/run
│        entryClass = com.uip.flink.esg.EsgDualSinkJob
│        programArgs = --welford-min-samples ${WELFORD_MIN_SAMPLES:-1000}
│
└── 5. Verify
       GET /jobs  →  confirm new job in RUNNING state
       Log: job_id, jar_id, timestamp
```

### Docker-Compose Submitter Service

```yaml
# docker-compose.yml (excerpt)
flink-esg-job-submitter:
  image: curlimages/curl:latest
  depends_on:
    flink-jobmanager:
      condition: service_healthy
  volumes:
    - ./scripts/flink-deploy.sh:/deploy.sh:ro
    - ../flink-jobs/build/libs:/jars:ro
  entrypoint: ["/bin/sh", "/deploy.sh"]
  environment:
    FLINK_URL: http://flink-jobmanager:8081
    WELFORD_MIN_SAMPLES: ${WELFORD_MIN_SAMPLES:-1000}
  restart: "no"
```

The submitter container runs once on startup and exits. `restart: "no"` prevents infinite re-submission loops on failure.

### Makefile Targets

```makefile
flink-submit:
    WELFORD_MIN_SAMPLES=$(WELFORD_MIN_SAMPLES) \
    FLINK_URL=http://localhost:8081 \
    ./infrastructure/scripts/flink-deploy.sh

flink-status:
    curl -s http://localhost:8081/jobs | jq '.jobs[] | {jid, state, name}'

flink-cancel:
    curl -s http://localhost:8081/jobs | \
    jq -r '.jobs[] | select(.state=="RUNNING") | .jid' | \
    xargs -I{} curl -X POST http://localhost:8081/jobs/{}/stop \
        -H 'Content-Type: application/json' \
        -d '{"drain": false, "targetDirectory": "/tmp/flink-savepoints"}'
```

### WELFORD_MIN_SAMPLES Environment Variable

The Flink job's Welford online estimator uses a minimum sample threshold before emitting anomaly alerts. Previously hardcoded to 1000:

```java
// Before (hardcoded)
private static final int MIN_SAMPLES = 1000;

// After (env-configurable via program arg)
int minSamples = Integer.parseInt(
    params.get("welford-min-samples", "1000")
);
```

This allows:
- Production: `WELFORD_MIN_SAMPLES=1000` (default, stable anomaly detection)
- Integration tests: `WELFORD_MIN_SAMPLES=10` (faster warm-up for test assertions)
- Load tests: `WELFORD_MIN_SAMPLES=100` (moderate warm-up, realistic for perf scenarios)

---

## Alternatives Considered

### Alternative 1: Flink Application Mode (per-job cluster)
**Rejected.** Application Mode runs each Flink job in its own dedicated mini-cluster (separate JobManager). This is the recommended production topology for isolated resource management. However, for the current dev/staging environment running on a single Docker Compose host, spinning up a separate JobManager per job is resource-prohibitive. Session Mode (single shared JobManager, multiple jobs) is the correct tier for dev. Application Mode is deferred to the Kubernetes production deployment.

### Alternative 2: CI/CD via GitHub Actions + Flink REST API
**Rejected for Sprint 8.** A full GitHub Actions pipeline for Flink job deployment would require: secrets management for Flink endpoint credentials, a runner with Docker access to the target environment, and artifact storage for JARs. This scope exceeds Sprint 8 capacity. The shell + Makefile approach delivers the same functional outcome (zero-manual submission, savepoint discipline) with lower implementation overhead. GitHub Actions integration is a Sprint 9 backlog item.

### Alternative 3: Ansible Playbook for Job Lifecycle
**Rejected.** Ansible requires a Python + `community.general` collection install on the host running the playbook. The `flink-esg-job-submitter` docker-compose pattern is entirely self-contained (curl + jq in a lightweight container image) with no host-side tool dependencies. The Makefile targets provide the same local-dev ergonomics as Ansible tasks without the overhead.

---

## Consequences

### Positive
- Zero-manual job submission: `make flink-submit` is the single deploy command for any environment
- Savepoint-based cancel ensures no state loss on re-deploy: Welford estimator state is preserved across versions
- Exactly-once restart semantics: Flink restores from savepoint, consumers resume from the exact offset, no duplicate processing
- `WELFORD_MIN_SAMPLES` env var enables per-environment tuning without code changes or re-builds
- Idempotent: running `make flink-submit` when no job is running is safe (step 3 is a no-op)
- Version auditability: deploy script logs `jar_id`, `job_id`, and timestamp to stdout for each submission

### Negative / Risks
- Script depends on `jq` and `curl` being available: the `flink-esg-job-submitter` container uses `curlimages/curl` which includes `curl` but not `jq`; the Dockerfile for the submitter must install `jq` via `apk add jq`
- Submitter container needs JAR mount: the `flink-jobs/build/libs/` path must be pre-built before compose up; `make flink-submit` handles this via the Gradle build step, but `docker compose up` alone will fail if the JAR doesn't exist
- Savepoint directory: `POST /jobs/{jid}/stop` requires a `targetDirectory` accessible to the Flink TaskManager; this must be a shared volume between JobManager and TaskManager containers

### Neutral
- `WELFORD_MIN_SAMPLES` default of 1000 matches the previous hardcoded value — no behavior change in production
- Flink Web UI remains accessible for manual inspection and ad-hoc debugging; the automated path does not disable UI access
- BUG-009 (`job['id']` → `job['jid']`) fix is backward-compatible: the corrected field name is the only valid key in the Flink REST API response

---

## Implementation Notes

**Story:** S8-OPS03
**Sprint:** MVP3-8
**Owner:** DevOps Lead
**Bug Fixed:** BUG-009 — `job['id']` → `job['jid']` in cancel step of flink-deploy.sh
**Files:**
- `infrastructure/scripts/flink-deploy.sh` — Main deployment script (fixed BUG-009)
- `infrastructure/Makefile` — Added targets: `flink-submit`, `flink-status`, `flink-cancel`
- `infrastructure/docker-compose.yml` — Added `flink-esg-job-submitter` service
- `flink-jobs/src/main/java/com/uip/flink/esg/EsgDualSinkJob.java` — Parameterized `WELFORD_MIN_SAMPLES` via `ParameterTool`

**Verification:**
```bash
# Submit job and verify running
make flink-submit WELFORD_MIN_SAMPLES=1000

# Check status
make flink-status
# Expected: {"jid": "...", "state": "RUNNING", "name": "EsgDualSinkJob"}

# Test savepoint cancel + re-submit (zero data loss)
make flink-cancel
# Wait for FINISHED state
make flink-status
# Re-submit — Flink restores from savepoint
make flink-submit

# Test-env tuning
make flink-submit WELFORD_MIN_SAMPLES=10
```

**Savepoint Directory Setup (docker-compose):**
```yaml
volumes:
  - flink-savepoints:/tmp/flink-savepoints

# Mount on both jobmanager and taskmanager
flink-jobmanager:
  volumes:
    - flink-savepoints:/tmp/flink-savepoints

flink-taskmanager:
  volumes:
    - flink-savepoints:/tmp/flink-savepoints
```
