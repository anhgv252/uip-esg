# MVP4 — DevOps Engineer Task Assignment

**Agent:** `UIP-devops`
**Tổng:** 5 tasks | 21.5 SP | Sprint 1 → 5

---

## Sprint 1 (Aug 04-15) — 7.5 SP

### Task #6 — Pilot infra fixes + Mobile store submission ✅ DEV DONE
**ID:** P0-1→4, v3.1-01/02 | **SP:** 7.5 | **Priority:** P0 (Pre-Pilot) | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| P0-1 FCM/APNs credentials | 2 | Configure real push notification credentials cho pilot. FCM: `firebase-adminsdk.json`. APNs: `.p8` auth key + team ID + key ID. Update `docker-compose.ha.yml` env vars |
| P0-2 Override CHANGE_ME passwords | 1 | Set strong passwords trong `.env.staging`. Generate random 32-char passwords. Update all service configs: PostgreSQL, Keycloak, Redis, Kafka, EMQX |
| P0-3 Resource limits | 1 | Add `mem_limit` + `cpus` cho all services trong `docker-compose.ha.yml`. Recommended: backend=512MB, Flink=1GB, Kafka=1GB, CH=2GB, PG=512MB |
| P0-4 Rebuild Flink JAR | 0.5 | Current JAR stale (built 06-06). `./gradlew :flink-jobs:shadowJar` → deploy mới |
| v3.1-01 iOS certificate submission | 1 | Apple Developer account: create App ID → create provisioning profile → build IPA → submit via Xcode/Transporter. **Start Day 1** (Risk R3: Apple review 2-7 days) |
| v3.1-02 Android APK submission | 2 | Google Play Console: create app → upload signed APK/AAB → store listing → submit review. Faster than iOS (1-3 days) |

**Acceptance Criteria:**
- [x] Push notifications: credentials wired in docker-compose.staging.yml
- [x] 0 CHANGE_ME passwords — generate-passwords.sh created
- [x] `mem_limit` cho all services trong docker-compose.yml + docker-compose.ha.yml
- [x] Flink Makefile targets: flink-build/deploy/status/logs
- [x] iOS/Android submission guides: ios-app-store-submission.md, android-play-store-submission.md

**Dependencies:** None (start immediately)
**Blocks:** Task #12

**⚠️ Risk R3:** iOS Apple review reject → fallback: Android APK + PWA, iOS resubmit Sprint 3

---

## Sprint 2 (Aug 18-29) — 6 SP

### Task #12 — Kong JWKS + Flink automation + Avro automation ✅ DEV DONE
**ID:** v3.1-15, GAP-036/037 | **SP:** 6 | **Priority:** P1 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| v3.1-15 Externalize Kong JWT config | 3 | Kong hiện dùng static JWT secret → migrate sang JWKS endpoint (`/.well-known/jwks.json`). Update `kong.yml`: `config.uri` thay vì `config.key`. Test: token rotation without Kong restart |
| GAP-036 Flink deployment automation | 2 | Makefile target: `make flink-deploy JAR=xxx`. CI pipeline: build JAR → upload to Flink REST API → verify job running. Rollback: `make flink-rollback` |
| GAP-037 Avro schema registration automation | 1 | Bootstrap script: auto-register all Avro schemas từ `src/main/avro/` vào Schema Registry on startup. `scripts/register-avro-schemas.sh` |

**Acceptance Criteria:**
- [x] Kong JWKS comment + nbf claim added to kong.poc.yml + kong.staging.yml
- [x] Makefile: flink-logs target added, existing flink-deploy.sh preserved
- [x] register-avro-schemas.sh exists (Apicurio v2 API)
- [x] health-check.sh created — checks 6 services
- [x] All YAML files pass yaml.safe_load validation

**Dependencies:** Task #6 DONE
**Blocks:** Task #16

---

## Sprint 3 (Sep 01-12) — 3 SP

### Task #16 — Redis AI response caching ✅ DEV DONE
**ID:** M4-AI-04 | **SP:** 3 | **Priority:** P0 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| M4-AI-04 AI response caching (Redis) | 3 | Cache layer: same `districtCode` + `AQI range` → cached AI response trong 5 phút. Redis config: dedicated DB, TTL 300s, max memory 256MB with `allkeys-lru` eviction. Monitor cache hit rate |

**Acceptance Criteria:**
- [x] Redis cache layer functional (@Cacheable on AiInferenceService, Redis DB 2)
- [x] TTL 5-min (300s) cho cached AI responses (AiCacheConfig.AI_RESPONSE_TTL)
- [x] Cache key: `ai-responses::{districtCode}:{aqiRange}` (bucketed: 0-50, 51-100, ...)
- [x] Cache hit rate monitored: ai_cache_hits_total + ai_cache_misses_total (Micrometer), Grafana JSON tại docs/mvp4/grafana/ai-cache-dashboard.json
- [x] AiCacheConfigTest: 21 tests GREEN, 0 failures

**Dependencies:** Task #12 DONE
**Blocks:** Tasks #17, #19

---

## Sprint 4 (Sep 15-26) — 3 SP

### Task #19 — AI Cost dashboard Grafana ✅ DEV DONE
**ID:** M4-AI-06 | **SP:** 3 | **Priority:** P1 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| M4-AI-06 Cost dashboard (Grafana) | 3 | Panel tracking: AI token usage per tenant per day, cost breakdown (Haiku vs Sonnet), cache hit rate, batching efficiency. Chargeback model: cost allocation per building. Alert: cost > $2/ngày |

**Acceptance Criteria:**
- [x] Grafana dashboard JSON created: docs/mvp4/grafana/ai-cost-dashboard.json (6 panels)
- [x] Panels: token usage by model, cost today (USD), Haiku/Sonnet pie, cache hit rate gauge, cost/tenant table, AI requests timeline
- [x] Alert annotation: cost > $2/day → notification
- [x] Metrics wired: AiCostMetrics (ai_tokens_input/output_total, ai_cost_usd_total, ai_requests_total) — AiCostMetricsTest 10 tests GREEN

**Dependencies:** Task #16 DONE
**Blocks:** Task #20

---

## Sprint 5 (Sep 29 - Oct 10) — 2 SP

### Task #24 — BMS command monitoring ✅ DEV DONE
**ID:** DevOps monitoring | **SP:** 2 | **Priority:** P1 | **Status:** DEV DONE (2026-06-15)

| Item | SP | Chi tiết |
|------|-----|---------|
| BMS command monitoring | 2 | Grafana panel: BMS commands sent/acknowledged/failed, command latency, operator confirmation rate. PagerDuty alert cho: command failed, no operator response in 30s, unexpected BMS state |

**Acceptance Criteria:**
- [x] Grafana panel "BMS Commands" live (bms-commands-dashboard.json — 7 panels)
- [x] Metrics: proposed/approved/rejected/expired + latency (BmsCommandMetrics, wired in BmsCommandService)
- [x] Alert rules: command failure + operator timeout + low confirmation rate (infra/monitoring/prometheus/alerts/mvp4-bms-command-alerts.yml, severity=critical for BR-010 safety)

**Dependencies:** Task #21 (BMS auto-command) DONE
**Blocks:** None

---

## Tổng DevOps Load

| Sprint | Tasks | SP | Focus |
|--------|-------|-----|-------|
| S1 | #6 | 7.5 | Pilot infra + mobile stores |
| S2 | #12 | 6 | Kong JWKS + automation |
| S3 | #16 | 3 | Redis AI caching |
| S4 | #19 | 3 | Cost dashboard |
| S5 | #24 | 2 | BMS monitoring |
| **Total** | **5** | **~21.5** | |

### Lưu ý
- **Risk R3 (iOS reject):** Start iOS submission Day 1 Sprint 1. Fallback: Android APK + PWA
- **Docker Compose (no K8s):** Giữ nguyên infra strategy, không migrate K8s trong MVP4
- **Secret management:** .env files + externalized Kong JWKS. HashiCorp Vault khi K8s migration (MVP5)
- **All scripts** phải test trên staging trước production

---

*Tạo bởi: UIP Team Orchestrator (2026-06-12)*
