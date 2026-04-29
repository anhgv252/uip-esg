# Security Audit — OWASP Top 10 Sprint 1
**Date:** 2026-04-29 | **Scope:** UIP Smart City Backend MVP1

## Findings & Remediation Status

### A01 — Broken Access Control
| Finding | Severity | Status | Fix |
|---------|----------|--------|-----|
| Actuator endpoints exposed publicly | High | FIXED | BT-05b: SecurityConfig updated — `/actuator/prometheus`, `/actuator/metrics` requires ROLE_ADMIN, other actuator paths denied |
| No scope-based authorization | Medium | PLANNED | BT-S2SEC (Sprint 2): custom SpEL `hasScope()` + @PreAuthorize |

### A02 — Cryptographic Failures
| Finding | Severity | Status | Fix |
|---------|----------|--------|-----|
| JWT secret via env-var, no Vault | Medium | PARTIAL | BT-01a: Vault infra scaffolded, VaultHealthIndicator created. Full migration in Sprint 2 with K8s Vault Agent Injector |
| Passwords in docker-compose hardcoded | Low | DEFERRED | docker-compose is dev-only. Production uses Vault |

### A03 — Injection
| Finding | Severity | Status | Fix |
|---------|----------|--------|-----|
| JPA parameterized queries | None | OK | All queries use Spring Data JPA / JPQL — no string concatenation |
| MQTT topic injection | Low | OK | Topics are constants, not user-supplied |

### A05 — Security Misconfiguration
| Finding | Severity | Status | Fix |
|---------|----------|--------|-----|
| Actuator exposure | High | FIXED | See A01 |
| Error responses expose stack traces | Medium | FIXED | GlobalExceptionHandler returns ProblemDetail (RFC 7807), no stack trace in response |
| CORS allows multiple localhost origins | Low | OK | Dev config only. Production uses `CORS_ALLOWED_ORIGIN` env-var |

### A07 — Identification and Authentication Failures
| Finding | Severity | Status | Fix |
|---------|----------|--------|-----|
| JWT token signature length validation | None | OK | JwtTokenProvider already has `expectedSignatureLengthB64Url()` check (JJWT 0.12.3 mitigation) |
| JWT expiry configurable | None | OK | 15min access, 7-day refresh. Reasonable defaults |
| Login rate limiting | None | OK | LoginRateLimitService with Bucket4j |

### A09 — Security Logging Failures
| Finding | Severity | Status | Fix |
|---------|----------|--------|-----|
| No structured JSON logging | Medium | FIXED | BT-CC-01: logback-spring.xml with LogstashEncoder |
| No PII masking | Medium | FIXED | logback pattern masks email addresses |
| No trace correlation | Medium | FIXED | BT-04b: TraceIdFilter creates MDC traceId per request |

## Summary

| Severity | Count | Fixed | Remaining |
|----------|-------|-------|-----------|
| Critical | 0 | 0 | 0 |
| High | 2 | 2 | 0 |
| Medium | 4 | 3 | 1 (scope auth → Sprint 2) |
| Low | 3 | 0 | 3 (dev-only, deferred) |

**Overall:** Zero Critical findings. All High findings resolved in Sprint 1.
