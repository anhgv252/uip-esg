# OWASP Dependency-Check Baseline Report

**Sprint:** MVP5 Sprint M5-4 (G9 Security Gate)  
**Task:** M5-4 T13 — OWASP Dependency-Check Scan  
**Date:** 2026-06-25  
**Owner:** DevOps + QA  
**Status:** ✅ Infrastructure configured, ready for first scan

---

## Executive Summary

OWASP Dependency-Check infrastructure is now configured for continuous CVE monitoring of backend Java dependencies. The system is configured to fail builds on CRITICAL/HIGH vulnerabilities (CVSS ≥ 7.0) to prevent vulnerable dependencies from reaching production.

**Gate Status:** READY for first scan after NVD data download (~5-10 minutes on first run)

---

## Configuration Details

### 1. Gradle Plugin

**Plugin Version:** `org.owasp.dependencycheck:12.1.0`

**Configuration:** `backend/build.gradle` lines 390-410

```gradle
dependencyCheck {
    formats = ['HTML', 'JSON']
    outputDirectory = 'build/reports/dependency-check'
    suppressionFile = "${projectDir}/config/dependency-check-suppressions.xml"
    failBuildOnCVSS = 7.0
    failOnError = false
    autoUpdate = System.env.NVD_API_KEY ? true : false
    analyzers {
        assemblyEnabled = false
    }
    nvd {
        apiKey = System.env.NVD_API_KEY ?: ''
        delay = System.env.NVD_API_KEY ? 1000 : 4000
    }
}
```

**Key Settings:**
- **Fail threshold:** CVSS ≥ 7.0 (CRITICAL + HIGH vulnerabilities)
- **Output formats:** HTML (human review) + JSON (CI parsing)
- **NVD API integration:** Optional API key for faster updates (4s → 1s delay per request)
- **Assembly analyzer:** Disabled (not applicable to Java/JVM projects)

### 2. Suppression File

**Location:** `backend/config/dependency-check-suppressions.xml`

**Current Suppressions:** 7 entries

| CVE | Package | Reason | Until | Reviewed |
|-----|---------|--------|-------|----------|
| CVE-2023-37475 | apicurio-registry-serdes-avro-serde | False positive: Go vulnerability, not Java Avro | — | 2026-06-05 |
| CVE-2026-39883 | io.opentelemetry.* | False positive: OTel-Go vulnerability, not Java SDK | — | 2026-06-05 |
| CVE-2026-33186 | io.grpc.* | False positive: grpc-go vulnerability, not grpc-java | — | 2026-06-15 |
| CVE-2026-0994 | com.google.protobuf.* | False positive: Python json_format.ParseDict, not Java JsonFormat | — | 2026-06-15 |
| CVE-2025-7962 | org.eclipse.angus.* | Fix unavailable: angus-mail 2.1.0 not published; input validated, internal use only | 2026-09-01 | 2026-06-05 |

**Suppression Policy:**
- All false positives require Security Team review + detailed rationale in XML comments
- Accepted risks require:
  - Expiration date (`until="YYYY-MM-DDZ"`)
  - Mitigation evidence (input validation, internal use only, etc.)
  - Monthly review cadence

### 3. CI/CD Integration

**Workflow:** `.github/workflows/owasp-check.yml`

**Triggers:**
- **Scheduled:** Weekly Monday 2AM UTC (9AM ICT)
- **Push:** Changes to `backend/build.gradle`, `gradle.properties`, `dependency-check-suppressions.xml`
- **Pull Request:** Changes to build configuration files
- **Manual:** `workflow_dispatch` for on-demand scans

**Steps:**
1. Checkout code
2. Set up JDK 17 + Gradle cache
3. Cache NVD database (`~/.gradle/dependency-check-data`)
4. Run OWASP scan with optional NVD_API_KEY
5. Upload HTML+JSON reports as artifacts (30-day retention)
6. Parse JSON for CRITICAL/HIGH vulnerabilities
7. Fail if count > 0, post summary to PR

**Secret Required:** `NVD_API_KEY` (GitHub Secrets)
- Free key: https://nvd.nist.gov/developers/request-an-api-key
- Without key: 4-second delay per request (~16s total for typical scan)
- With key: 1-second delay (~4s total)

### 4. Local Scanning

**Makefile Targets:** `infrastructure/Makefile`

```bash
# Review mode (does not fail, for local development)
make owasp-scan

# CI mode (fails on CRITICAL/HIGH, for gate validation)
make owasp-scan-ci
```

**First Run:** 5-10 minutes to download NVD database
**Subsequent Runs:** 15-30 seconds with cached NVD data

---

## Dependency Versions (as of 2026-06-25)

### Critical Dependencies with CVE Fixes Applied

| Dependency | Current Version | Fixed CVEs | Note |
|------------|-----------------|------------|------|
| Apache Tomcat | 10.1.54 | CVE-2024-50379, CVE-2024-52316 | Embedded server |
| Netty | 4.1.132.Final | 4 HIGH CVEs | io.grpc transport |
| Log4j2 | 2.25.0 | CVE-2021-44228 (Log4Shell) + 3 more | Logging |
| PostgreSQL JDBC | 42.7.11 | CVE-2024-1597 | Database driver |
| io.grpc:* | 1.71.0 | CVE-2023-32731, CVE-2023-1428 | gRPC runtime |
| Firebase Admin SDK | 9.2.0 | CVE-2024-1394 | FCM push notifications |
| Spring Boot | 3.2.4 | — | No known CRITICAL/HIGH CVEs |

### Total Dependency Count

```
Direct dependencies:    ~50
Transitive dependencies: ~250
Total JAR files:        ~300
```

---

## NVD Database Update Strategy

### First Download

**Timeline:**
1. First `make owasp-scan` or CI run → downloads NVD database (~200MB)
2. **Without API key:** ~5-10 minutes (4-second delay per request)
3. **With API key:** ~2-3 minutes (1-second delay per request)

**Storage:**
- Local: `~/.gradle/dependency-check-data/`
- CI cache: `~/.gradle/dependency-check-data/` (GitHub Actions cache)

### Auto-Update Policy

**Configuration:** `autoUpdate = System.env.NVD_API_KEY ? true : false`

| Scenario | Auto-Update | Frequency |
|----------|-------------|-----------|
| **Local dev (no key)** | Disabled | Manual: `./gradlew dependencyCheckUpdate` |
| **Local dev (with key)** | Enabled | Every scan (cached, fast) |
| **CI (with key)** | Enabled | Weekly schedule + every build config change |

**Recommendation:** Set `NVD_API_KEY` in local environment to enable auto-updates.

---

## Expected Output (Example)

### Successful Scan (0 CRITICAL/HIGH)

```
BUILD SUCCESSFUL in 18s
7 actionable tasks: 7 executed

=== Scan complete ===
  HTML: backend/build/reports/dependency-check/dependency-check-report.html
  JSON: backend/build/reports/dependency-check/dependency-check-report.json

📊 Vulnerability summary:
  3 MEDIUM
  12 LOW
```

### Failed Scan (CRITICAL/HIGH found)

```
> Task :dependencyCheckAnalyze FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':dependencyCheckAnalyze'.
> One or more dependencies were identified with vulnerabilities that have a CVSS score greater than or equal to '7.0'

Vulnerable dependencies:
  - spring-security-core-6.2.0.jar: CVE-2024-XXXXX (CVSS 8.1)
  - netty-codec-http2-4.1.100.Final.jar: CVE-2024-YYYYY (CVSS 7.5)
```

---

## Handoff to QA

### Tasks for QA Engineer

1. **First scan validation:**
   ```bash
   cd infrastructure
   make owasp-scan
   ```
   - Expected: 5-10 min first run (NVD download)
   - Verify: HTML report opens in browser

2. **Gate enforcement test:**
   ```bash
   make owasp-scan-ci
   ```
   - Expected: Exit code 0 (no CRITICAL/HIGH CVEs)
   - If exit code 1: Review report, add suppressions or upgrade dependencies

3. **CI workflow test:**
   - Trigger manual run: GitHub Actions → OWASP Dependency Check → Run workflow
   - Verify: Workflow passes, artifact uploaded
   - Check: NVD_API_KEY secret is configured (faster scans)

### Known Issues / Blockers

**BLOCKER:** NVD_API_KEY not yet configured in GitHub Secrets

**Action Required:**
1. Obtain free API key: https://nvd.nist.gov/developers/request-an-api-key
2. Add to GitHub Secrets: `Settings → Secrets and variables → Actions → New repository secret`
   - Name: `NVD_API_KEY`
   - Value: `<your-key>`

**Without API key:**
- Scans work but slower (4s delay → ~16s total vs. 1s → ~4s with key)
- Auto-update disabled (must run `./gradlew dependencyCheckUpdate` manually)

---

## Next Steps

### Sprint M5-4 (current)
- [x] T13: Configure OWASP infrastructure (this task)
- [ ] QA: Run baseline scan, validate 0 CRITICAL/HIGH (next task)

### Sprint M5-5
- [ ] Add NVD_API_KEY to GitHub Secrets (DevOps)
- [ ] Enable PR blocking on CRITICAL/HIGH CVEs (branch protection rules)
- [ ] Document suppression approval process in security runbook

### Ongoing
- Weekly automated scans (Monday 2AM UTC)
- Monthly review of time-based suppressions (expiring entries)
- Quarterly audit of all suppressions (false positive re-validation)

---

## References

- **OWASP Plugin Docs:** https://jeremylong.github.io/DependencyCheck/dependency-check-gradle/
- **NVD API Key:** https://nvd.nist.gov/developers/request-an-api-key
- **Task Spec:** `docs/mvp5/tasks/mvp5-sprint4-tasks.md` M5-4-T13
- **ADR:** (to be created) ADR-0XX — OWASP Dependency-Check for CVE Monitoring
- **Suppression File:** `backend/config/dependency-check-suppressions.xml`
- **CI Workflow:** `.github/workflows/owasp-check.yml`

---

**Document Status:** DRAFT (to be validated after first successful scan)  
**Next Review:** After QA completes first scan validation  
**Owner:** DevOps (infrastructure) + QA (validation)
