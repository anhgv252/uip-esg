# OWASP ZAP Scan — Pre-Scan Checklist

**Sprint:** 7 | **Date:** 2026-06-03 | **Environment:** Local Staging (Docker Compose)

## Prerequisites

- [x] All Tier 1 code deployed to staging
- [x] Staging environment accessible at configured URL
- [x] Test credentials configured (admin/admin_Dev#2026!)
- [x] No production data in staging database
- [x] ZAP Docker image available (`zaproxy/zap-stable:latest`)
- [x] Keycloak realm imported with test users
- [x] Kong API Gateway configured with JWT plugin

## Scan Configuration

- [x] Target URL: `http://localhost:8080` (Backend) and `http://localhost:3000` (Frontend)
- [x] Authentication: HMAC JWT via `/api/v1/auth/login`
- [x] Scan policy: Full Active Scan (not passive only)
- [x] Excluded paths: N/A (backend blocks all unauthenticated access)
- [x] Context includes: `/api/v1/*` (backend), `/*` (frontend)

## Security Controls Verified

### Authentication & Authorization
- [x] JWT validation — HMAC HS512, signature length validation active
- [x] Expired JWT token rejected (401)
- [x] Invalid JWT signature rejected (401)
- [x] Scope enforcement: `esg:write`, `alert:ack`, `alert:escalate` checked
- [x] Role-based access: ADMIN-only routes block OPERATOR/VIEWER

### Headers & Transport
- [x] CORS restricted to configured origins
- [x] X-Frame-Options: `DENY` (backend)
- [x] X-Content-Type-Options: `nosniff` (backend)
- [x] HSTS header present (`max-age=31536000`) (backend)
- [x] CSP with `frame-ancestors 'none'` (backend)
- [ ] ~~⚠️ Frontend nginx missing: X-Frame-Options, X-Content-Type-Options, Permissions-Policy, COEP~~ → ✅ **FIXED** (2026-06-03)

### Input Validation
- [x] SQL injection: PASS (all SQL injection rules PASS in ZAP active scan)
- [x] XSS (reflected + stored): PASS (all XSS rules PASS)
- [x] Path traversal: PASS
- [x] Command injection: PASS
- [x] Mass assignment: PASS

### API Security
- [x] No sensitive data in URLs
- [x] Pagination limits enforced
- [x] Tenant isolation enforced (X-Tenant-ID header filter)
- [x] Spring Actuator not exposed (ZAP confirmed PASS [40042])

## Acceptance Criteria

- [x] **0 Critical** findings ✅
- [x] **0 High** findings ✅
- [x] Frontend nginx headers FIXED: 9 WARN → 5 WARN (4 fixed, 5 accepted/informational)
- [x] Re-scan verified: 62/67 PASS, 0 FAIL, 5 remaining WARN all accepted
- [x] Scan report generated: `docs/mvp3/security/owasp-report-template.md`
- [x] ZAP HTML reports in `infrastructure/security/reports/`

## Post-Scan

- [x] Remediate all Critical/High findings — N/A (none found)
- [x] Frontend nginx headers FIXED — re-scan verified (9→5 WARN, 0 FAIL)
- [x] Final report signed by QA
- [x] Report archived in `docs/mvp3/security/`

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| QA Engineer | Claude QA | 2026-06-03 | ✅ PASS — 0 Critical/High, nginx headers fixed + verified |
| Solution Architect | | | |
| Project Manager | | | |
