# Sprint 3 — Pre-Deploy Code Review Report

**Date:** 2026-05-20
**Reviewer:** Claude (Automated + Manual Review)
**Scope:** All Sprint 3 backend + frontend changes before DevOps deploy
**Verdict:** PASS — ready for docker deploy with 2 minor notes

---

## Review Summary

| Category | Files Reviewed | Issues Found | Fixed | Status |
|----------|---------------|-------------|-------|--------|
| Backend Java | 8 files | 2 | 2 | PASS |
| Frontend TS/TSX | 5 files | 0 | 0 | PASS |
| Config (YML/nginx/docker) | 3 files | 0 | 0 | PASS |
| **Total** | **16** | **2** | **2** | **PASS** |

---

## Backend Review

### S3-04: DefaultPdfExportAdapter.java (NEW)

**Verdict: PASS**

| Check | Result |
|-------|--------|
| Implements `EsgReportExportPort` | OK — `getFormatId()="pdf"`, `getContentType()="application/pdf"`, `getFileExtension()="pdf"` |
| License compatible | OK — OpenPDF 2.0.3 (LGPL), NOT iText AGPL |
| A4 printable layout | OK — `PageSize.A4`, margins 36/36/54/36 |
| GRI 302-1 content | OK — Energy table with total, intensity, data quality, per-building breakdown |
| GRI 305-4 content | OK — Emissions table with total, intensity, data quality |
| Summary section | OK — Energy, Water, Carbon with units |
| Null safety | OK — `data.dataQuality() != null ? data.dataQuality() : "N/A"`, `fmt()` handles null Double |
| Exception handling | OK — Consistent with XLSX/CSV adapters (`RuntimeException` wrap) |
| Resource leak | OK — `ByteArrayOutputStream` in try-with-resources, `Document` closed explicitly |
| Spring registration | OK — `@Component` auto-detected by `EsgReportGenerator.exportPorts` list |

**Dependency added:** `com.github.librepdf:openpdf:2.0.3` in `build.gradle`

### S3-06: RoutingJwtDecoder.java (NEW)

**Verdict: PASS (after fix)**

| Check | Result |
|-------|--------|
| Routes by `iss` claim | OK — HMAC issuer → HMAC decoder, Keycloak issuer → RSA decoder |
| Thread safety | OK — `volatile` + double-checked locking pattern |
| `alg=none` protection | OK — NimbusJwtDecoder rejects by default |
| Unknown issuer handling | OK — throws `JwtException("Unknown JWT issuer: ...")` |
| Missing issuer handling | OK — throws `JwtException("JWT missing issuer...")` |

**Issues found & fixed:**
1. **Unused import `java.time.Duration`** — REMOVED
2. **Dead field `decoderCache` (ConcurrentHashMap)** — never used, REMOVED

### S3-06: JwtProperties.java (MODIFIED)

**Verdict: PASS**

| Check | Result |
|-------|--------|
| New properties added | OK — `hmacIssuer`, `keycloakIssuer`, `keycloakJwkSetUri` |
| Default values | OK — `uip-legacy`, `http://localhost:8085/realms/uip`, JWK URI points to Keycloak |
| Docker override | OK — env vars `JWT_HMAC_ISSUER`, `JWT_KEYCLOAK_ISSUER`, `JWT_KEYCLOAK_JWK_SET_URI` |

### S3-01/02/03: ESG Report (MODIFIED)

**Verdict: PASS**

| File | Check | Result |
|------|-------|--------|
| `EsgReportData.java` | GRI fields added | OK — `energyIntensityKwhPerM2`, `buildingBreakdown`, `dataQuality`, `co2EmissionsPerM2` |
| `EsgReportGenerator.java` | Adapter resolution | OK — `List<EsgReportExportPort>` auto-wires all 4 adapters (xlsx, csv, pdf) |
| `DefaultXlsxExportAdapter.java` | GRI sheets | OK — GRI 302-1 + 305-4 sheets with per-building breakdown |
| `EsgController.java` | Download endpoint | OK — `format` param routes to correct adapter |

### S3-16: Kong Analytics Cutover

**Verdict: PASS**

| Check | Result |
|-------|--------|
| `AnalyticsProxyController.java` | DELETED — confirmed file removed |
| `nginx.conf` split routing | OK — `/api/v1/analytics/` → `kong:8000`, `/api/` → `backend:8080` |
| No catch-all in Kong | OK — only analytics route, ADR-028 compliant |

---

## Frontend Review

### S3-05: ReportGenerationPanel.tsx (MODIFIED)

**Verdict: PASS**

| Check | Result |
|-------|--------|
| Year selector | OK — `CURRENT_YEAR` down to `CURRENT_YEAR - 2` (3 years) |
| Quarter selector | OK — defaults to current quarter via `Math.ceil((month+1)/3)` |
| Generate button | OK — `useMutation`, disabled during generation |
| Download XLSX | OK — `handleDownload('xlsx')` with blob download |
| Download PDF | OK — `handleDownload('pdf')` with blob download |
| Status polling | OK — `refetchInterval` 3s during GENERATING/PENDING, stops on DONE |
| Error states | OK — `isFailed` + `triggerMutation.isError` with Alert |
| Accessibility | OK — `aria-label` on download buttons |
| Permission check | OK — `useScope('esg:write')` gates Generate button |

### S3-05: esg.ts API (MODIFIED)

**Verdict: PASS**

| Check | Result |
|-------|--------|
| `downloadReport(id, format)` | OK — passes `format` param, `responseType: 'blob'` |
| URL construction | OK — `/esg/reports/${id}/download?format=xlsx|pdf` |

### S3-15: AnalyticsFilterPanel.tsx (MODIFIED)

**Verdict: PASS**

| Check | Result |
|-------|--------|
| Reset animation fix | OK — `resetting` state + `transition: none` via sx prop |
| `requestAnimationFrame` | OK — clears `resetting` after paint, avoids layout thrash |
| No functional regression | OK — filter values still reset correctly |

### S3-14: useAnalytics.ts (MODIFIED)

**Verdict: PASS**

| Check | Result |
|-------|--------|
| AQI auto-refresh | OK — `refetchInterval: 15_000` added to `useAqiTrend` |

### S3-13: EsgBarChart.tsx (MODIFIED)

**Verdict: PASS**

| Check | Result |
|-------|--------|
| Tooltip fix | OK — `wrapperStyle={{ zIndex: 1300 }}` + `contentStyle={{ maxWidth: '300px' }}` |

---

## Config Review

### application.yml

| Check | Result |
|-------|--------|
| HMAC issuer config | OK — `hmac-issuer: ${JWT_HMAC_ISSUER:uip-legacy}` |
| RSA issuer config | OK — `keycloak-issuer` + `keycloak-jwk-set-uri` with Keycloak defaults |

### nginx.conf

| Check | Result |
|-------|--------|
| Analytics → Kong | OK — `location /api/v1/analytics/` before `/api/` |
| Monolith → backend | OK — `location /api/` unchanged |
| Security headers | OK — X-Frame-Options, X-Content-Type-Options, etc. |

### build.gradle

| Check | Result |
|-------|--------|
| OpenPDF dependency | OK — `com.github.librepdf:openpdf:2.0.3` (LGPL) |
| No dependency conflicts | OK — no overlap with existing POI |

---

## TypeScript Compilation

```
npx tsc --noEmit → 0 errors
```

## Java Compilation

```
compileJava + compileTestJava → BUILD SUCCESSFUL
```

## Unit Tests

```
RoutingJwtDecoderTest: 6/6 PASS
EsgExportTest: 21/21 PASS
EsgReportGeneratorTest: 8/8 PASS
EsgServiceTest: 9/9 PASS
Total: 44/44 PASS, 0 failures
```

---

## Notes for DevOps

1. **New dependency:** OpenPDF 2.0.3 — cần `docker compose build backend` để pull Maven artifact
2. **Keycloak:** Verify realm `uip` loaded từ `realm-uip-export.json` sau `docker compose up`
3. **Kong:** Verify analytics route hoạt động qua `curl http://localhost:8000/api/v1/analytics/energy-aggregate` với RSA token
4. **PDF endpoint test:** `curl -o test.pdf http://localhost:8080/api/v1/esg/reports/{id}/download?format=pdf`

---

**Approved for deploy:** YES
**Signed off:** Claude Code Review — 2026-05-20
