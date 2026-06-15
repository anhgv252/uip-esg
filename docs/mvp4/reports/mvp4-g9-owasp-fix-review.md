# SA Code Review — G9 OWASP CVE Fix (gRPC + protobuf upgrade)

| Field | Value |
|---|---|
| **Sprint** | MVP4 (G9 unblock) |
| **Date** | 2026-06-15 |
| **Reviewer** | Solution Architect |
| **Scope** | Upgrade io.grpc:* → 1.71.0 + com.google.protobuf:* → 3.25.5 to clear 7 OWASP CVEs (CVSS > 7) |
| **Status** | ✅ APPROVED — G9 PASS |

## Context

G9 (OWASP 0 Critical/High CVEs) FAILED on first scan 2026-06-15: 7 CVEs with CVSS > 7.0. JSON report triage (`build/reports/dependency-check/dependency-check-report.json`) localized all 7 to **2 dependency families** — no scattered fixes needed:

| Family | CVEs | Root cause |
|---|---|---|
| `io.grpc:*` (5 CVEs) | CVE-2026-33186 (9.1 CRITICAL), CVE-2024-11407, CVE-2023-33953, CVE-2023-44487, CVE-2023-4785 | gRPC pinned at 1.63.0 (grpc-protobuf/stub) but `firebase-admin:9.2.0 → google-cloud-storage:2.22.4` pulled `grpc-xds/alts/grpclb/services/rls` at 1.55.1 transitively |
| `com.google.protobuf:*` (2 CVEs) | CVE-2024-7254, CVE-2026-0994 | runtime `protobuf-java@3.25.1` + `protobuf-java-util@3.23.2` |

## Fix

DevOps agent applied a `resolutionStrategy.eachDependency` force in `backend/build.gradle` (config block `all{}` so compile/runtime/test all align):

```groovy
all {
    resolutionStrategy.eachDependency { details ->
        if (details.requested.group == 'io.grpc') {
            details.useVersion '1.71.0'
            details.because 'OWASP 2026-06-15: clear CVE-2026-33186 (CVSS 9.1), CVE-2024-11407, CVE-2023-33953, CVE-2023-44487, CVE-2023-4785'
        }
        if (details.requested.group == 'com.google.protobuf' &&
            details.requested.name.startsWith('protobuf-java')) {
            details.useVersion '3.25.5'
            details.because 'OWASP 2026-06-15: clear CVE-2024-7254, CVE-2026-0994'
        }
    }
}
```

Plus direct dependency pins (1.63.0 → 1.71.0) for `grpc-protobuf`, `grpc-stub`, `protoc-gen-grpc-java`, and protoc `3.25.3 → 3.25.5`.

## SA Review Checklist — Backend (10 items, scoped to dep upgrade)

| # | Item | Result | Notes |
|---|---|---|---|
| 1 | Unused imports / dead code | ✅ | No code changes, only build.gradle |
| 2 | Spring bean registration | ✅ | gRPC client bean (grpc-client-spring-boot-starter:3.1.0) still wires — regression confirms |
| 3 | Null safety | N/A | Dependency-only change |
| 4 | Exception handling | ✅ | gRPC 1.71 API compatible with existing client code (compile + test pass) |
| 5 | JWT claims | N/A | |
| 6 | Resource leak | ✅ | grpc-netty-shaded 1.71 bundled, no manual netty version |
| 7 | Thread safety | N/A | |
| 8 | Config env vars default | N/A | |
| 9 | Dependency license | ✅ | gRPC + protobuf remain Apache 2.0 |
| 10 | API contract match frontend | N/A | gRPC is backend-internal (analytics-service) |

## Verification

| Check | Result |
|---|---|
| `./gradlew compileJava` | ✅ BUILD SUCCESSFUL |
| `./gradlew test` (full regression) | ✅ **1,726 tests, 0 failures, 4 skipped** (unchanged from pre-upgrade baseline) |
| `./gradlew dependencyCheckAggregate` (final re-scan) | ✅ **BUILD SUCCESSFUL — 0 active CVEs CVSS ≥ 7.0** (52 suppressed, all documented) |

## Triage outcome (7 → 5 fixed → 2 suppressed)

| CVE | CVSS | Family | Resolution |
|---|---|---|---|
| CVE-2024-11407 | 7.5 | grpc | ✅ Fixed by upgrade to 1.71.0 |
| CVE-2023-33953 | 7.5 | grpc | ✅ Fixed by upgrade to 1.71.0 |
| CVE-2023-44487 | 7.5 | grpc | ✅ Fixed by upgrade to 1.71.0 |
| CVE-2023-4785 | 7.5 | grpc | ✅ Fixed by upgrade to 1.71.0 |
| CVE-2024-7254 | 7.5 | protobuf | ✅ Fixed by upgrade to 3.25.5 |
| CVE-2026-33186 | 9.1 | grpc (false positive) | ✅ Suppressed — advisory is grpc-go (Go), matched to io.grpc (Java) via CPE mismatch |
| CVE-2026-0994 | 7.5 | protobuf (false positive) | ✅ Suppressed — advisory is protobuf Python `json_format.ParseDict`, Java has no equivalent |

## Risk notes

- **Version drift prevention:** the `eachDependency` force catches future transitive pulls of old gRPC/protobuf. Any new dep that brings grpc <1.71 or protobuf <3.25.5 is auto-upgraded.
- **gRPC 1.71 vs grpc-client-spring-boot-starter 3.1.0:** the starter targets gRPC 1.59-era; force-override to 1.71 worked (tests pass) but this is a forward-compat risk. Flag for follow-up: bump starter when a 1.71-compatible release ships.
- **gRPC IT vs analytics-service (GAP-010)** remains deferred — this upgrade does not change that deferral.
- **Suppression review cadence:** the 2 new FP suppressions should be re-validated quarterly (same as existing FP entries) — if OWASP/adopts stricter CPE matching or the Java artifact gains an analogous vuln, the suppression must be revisited.

## Verdict

✅ **APPROVED — G9 PASS.** 5 real CVEs fixed by version upgrade, 2 false positives suppressed with rationale. Regression 1,726/0 intact. Final OWASP scan: BUILD SUCCESSFUL with `failBuildOnCVSS=7.0`.
