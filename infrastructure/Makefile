.DEFAULT_GOAL := help

COMPOSE             = docker compose --env-file .env
COMPOSE_UAT         = docker compose --env-file .env.uat
COMPOSE_STAGING     = docker compose --env-file .env.staging
COMPOSE_FILE        = -f docker-compose.yml
COMPOSE_UAT_FILE    = -f docker-compose.yml -f docker-compose.uat.yml
COMPOSE_STAGING_FILE = -f docker-compose.yml -f docker-compose.uat.yml -f docker-compose.staging.yml

.PHONY: help up up-ha ha-health-check ch-ha-restart vault-init vault-status vault-verify down logs ps migrate test-mqtt test-pipeline check-contract-drift \
        uat-up uat-down uat-logs uat-ps uat-test uat-clean seed-uat clean \
        staging-up staging-down staging-logs staging-ps staging-smoke-test \
        regression-test regression-test-group \
        flink-list flink-build flink-submit flink-cancel flink-savepoint flink-deploy flink-rollback flink-status flink-logs \
        kong-test-jwks \
        avro-register ch-migrate \
        security-scan security-report security-open \
        spotbugs-scan spotbugs-report spotbugs-open security-full \
        owasp-scan owasp-scan-ci

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ─── Dev Stack ────────────────────────────────────────────────────────────────

up: ## Start full dev stack (standalone — recommended for local dev, low resource usage)
	@cp -n .env.example .env 2>/dev/null || true
	$(COMPOSE) $(COMPOSE_FILE) up -d
	@echo "Waiting for all services to be healthy..."
	$(COMPOSE) $(COMPOSE_FILE) ps

up-ha: ## Start HA stack (ClickHouse 2-node + Kafka 3-broker — staging/pilot only, high resource usage)
	@cp -n .env.example .env 2>/dev/null || true
	@echo "[up-ha] Checking mTLS certs for ClickHouse HA nodes..."
	@cd $(CURDIR) && bash scripts/gen-ch-mtls-certs.sh
	$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml up -d
	@echo ""
	@echo "=== HA Stack starting ==="
	@echo "  ClickHouse: clickhouse-01 (:8125) + clickhouse-02 (:8124) + keeper (:9181)"
	@echo "  mTLS:       :8126 (node-01) / :8443 (node-02) — certs in clickhouse/tls/"
	@echo ""
	@echo "NOTE: If certs were just generated (first run / fresh clone), restart CH nodes"
	@echo "      to load the new keys: make ch-ha-restart"
	@echo "Run 'make ch-migrate' after stack is healthy."

ch-ha-restart: ## Restart ClickHouse HA nodes (required after first cert generation or cert rotation)
	$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml restart clickhouse-01 clickhouse-02
	@echo "[ch-ha-restart] Waiting for nodes to be healthy..."
	@sleep 15
	@curl -sf http://localhost:8125/ping && echo "  clickhouse-01 (:8125): Ok." || echo "  clickhouse-01: FAIL"
	@curl -sf http://localhost:8124/ping && echo "  clickhouse-02 (:8124): Ok." || echo "  clickhouse-02: FAIL"

ha-health-check: ## Check health of all HA environment containers (use --wait to retry for 5min)
	@./scripts/ha-health-check.sh $(ARGS)

# ─── Vault secret injection (M5-1-T03, block gate G1) ────────────────────────
# Docs: ADR-048 §6, runbook in docs/mvp5/reports/mvp5-sprint1-vault-secret-audit.md

vault-init: ## Bootstrap Vault: start server + pre-load KV v2 secrets + render uip.env (HA overlay)
	@cp -n .env.example .env 2>/dev/null || true
	$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml up -d vault
	@echo "Waiting for Vault to be healthy..."
	@$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml exec -T vault vault status >/dev/null
	$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml run --rm vault-init
	$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml up -d vault-agent
	@echo ""
	@echo "=== Vault bootstrap complete ==="
	@echo "  Server:    http://localhost:8200 (token: root — DEV ONLY)"
	@echo "  KV engine: secret/ (KV v2)"
	@echo "  Rendered:  uip-vault-secrets:/vault/secrets/uip.env (mounted into consumers)"
	@echo "  Verify:    make vault-verify"

vault-status: ## Show Vault seal status + KV v2 secret list
	$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml exec vault vault status
	@echo "--- KV paths ---"
	$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml exec vault vault kv list secret/uip/ || true

vault-verify: ## Verify rendered secrets file exists, is fresh (<6m), and contains expected keys
	@test -n "$$(docker volume ls -q --filter name=uip-vault-secrets)" || { echo "ERROR: vault-secrets volume missing. Run 'make vault-init' first."; exit 1; }
	$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml exec vault-agent sh -c '\
		test -f /vault/secrets/uip.env || { echo "FAIL: uip.env not rendered"; exit 1; }; \
		find /vault/secrets/uip.env -mmin -6 | grep -q . || { echo "FAIL: uip.env stale (>6min) — agent not refreshing"; exit 1; }; \
		grep -qE "^POSTGRES_PASSWORD=" /vault/secrets/uip.env || { echo "FAIL: POSTGRES_PASSWORD missing"; exit 1; }; \
		grep -qE "^JWT_SECRET="         /vault/secrets/uip.env || { echo "FAIL: JWT_SECRET missing"; exit 1; }; \
		grep -qE "^CLAUDE_API_KEY="     /vault/secrets/uip.env || { echo "FAIL: CLAUDE_API_KEY missing"; exit 1; }; \
		echo "OK: uip.env rendered, fresh (<6m), all critical keys present."'

vault-rotate: ## Rotate a Vault secret: make vault-rotate secret=redis (M5-2-T09)
	@test -n "$(secret)" || { echo "ERROR: secret parameter required. Usage: make vault-rotate secret=redis"; exit 1; }
	@echo "[vault-rotate] Rotating secret/uip/$(secret)..."
	@case "$(secret)" in \
		postgres|clickhouse|redis|kafka|keycloak|kong|minio|emqx|jwt|ai-claude) \
			NEW_VALUE=$$(openssl rand -base64 32); \
			$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml exec -T vault vault kv put secret/uip/$(secret) password="$$NEW_VALUE"; \
			echo "[vault-rotate] ✅ Rotated secret/uip/$(secret) (new version created)"; \
			echo "[vault-rotate] Vault agent will refresh within 5 minutes (TTL-based)."; \
			echo "[vault-rotate] Verify with: make vault-verify"; \
			;; \
		*) \
			echo "ERROR: Unknown secret '$(secret)'. Valid: postgres, clickhouse, redis, kafka, keycloak, kong, minio, emqx, jwt, ai-claude"; \
			exit 1; \
			;; \
	esac

vault-rotation-status: ## Show rotation status for all 10 Vault secrets (M5-2-T09)
	@echo "=== Vault Secret Rotation Status ==="
	@echo ""
	@for secret in postgres clickhouse redis kafka keycloak kong minio emqx jwt ai-claude; do \
		echo "Path: secret/uip/$$secret"; \
		$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml exec -T vault vault kv metadata get secret/uip/$$secret 2>/dev/null | grep -E "(current_version|created_time|updated_time)" || echo "  [NOT FOUND]"; \
		echo ""; \
	done

down: ## Stop dev stack
	$(COMPOSE) $(COMPOSE_FILE) down

restart: ## Restart a service: make restart svc=backend
	$(COMPOSE) $(COMPOSE_FILE) restart $(svc)

logs: ## Tail logs: make logs svc=backend (omit svc for all)
	$(COMPOSE) $(COMPOSE_FILE) logs -f $(svc)

ps: ## Show service status
	$(COMPOSE) $(COMPOSE_FILE) ps

# ─── DB Migrations ────────────────────────────────────────────────────────────

migrate: ## Run Flyway migrations via Spring Boot
	$(COMPOSE) $(COMPOSE_FILE) exec backend java -jar /app.jar --spring.flyway.enabled=true

# ─── Testing ──────────────────────────────────────────────────────────────────

test-mqtt: ## Send 10 MQTT test messages
	@bash scripts/test-mqtt-send.sh

test-pipeline: ## Run integration test suite
	@cd .. && python3 poc/scripts/integration_tests.py

smoke-test: ## Run config smoke tests (Keycloak + ClickHouse + Kafka)
	@bash scripts/smoke-test-config.sh

# ─── OpenTelemetry / Observability (M5-3 T10) ────────────────────────────────

otel-agent: ## Download OpenTelemetry Java agent JAR (v2.3.0) to ./agents/
	@mkdir -p agents
	@test -f agents/opentelemetry-javaagent.jar || { \
		echo "Downloading OTel Java agent v2.3.0..."; \
		curl -L -o agents/opentelemetry-javaagent.jar \
			https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.3.0/opentelemetry-javaagent.jar; \
		echo "✅ Downloaded to infrastructure/agents/opentelemetry-javaagent.jar"; \
		echo ""; \
		echo "Next: Uncomment volume mount in docker-compose.ha.yml (backend service):"; \
		echo "  - ./agents/opentelemetry-javaagent.jar:/opt/otel/opentelemetry-javaagent.jar:ro"; \
	} || echo "✅ OTel agent already exists."

tempo-check: ## Check Tempo health + verify trace ingestion endpoint
	@echo "=== Tempo Health Check ==="
	@curl -sf http://localhost:3200/ready && echo "✅ Tempo ready" || echo "❌ Tempo not ready"
	@echo ""
	@echo "Trace search: http://localhost:3200/api/search?tags={service.name=\"uip-backend\"}&limit=10"
	@echo "Grafana explore: http://localhost:3000/explore?orgId=1&left={\"datasource\":\"tempo-uip\",\"queries\":[...]}"

# ─── API Contract ─────────────────────────────────────────────────────────────

check-contract-drift: ## Check API contract drift (runs gen-api-types and verifies git clean)
	@cd .. && npm run gen-api-types --workspace=packages/api-types
	@cd .. && git diff --exit-code packages/api-types/src/generated.ts || { \
		echo ""; \
		echo "ERROR: API type drift detected!"; \
		echo "  Run 'npm run gen-api-types' and commit packages/api-types/src/generated.ts"; \
		echo ""; \
		exit 1; \
	}
	@echo "✓ API types are in sync with openapi.json"

# ─── Flink Jobs (ADR-038, S8-OPS03, GAP-036) ──────────────────────────────────

FLINK_SCRIPT := scripts/flink-deploy.sh
FLINK_URL    ?= http://localhost:8081

flink-list: ## List running Flink jobs
	@bash $(FLINK_SCRIPT) list

flink-build: ## Build Flink jobs JAR
	@bash $(FLINK_SCRIPT) build

flink-submit: ## Submit all Flink jobs to cluster (use JAR= for specific JAR)
	@FLINK_URL=$(FLINK_URL) JAR=$(or $(JAR),) ENTRY_CLASS=$(or $(ENTRY_CLASS),) bash $(FLINK_SCRIPT) submit

flink-cancel: ## Cancel all running Flink jobs
	@FLINK_URL=$(FLINK_URL) bash $(FLINK_SCRIPT) cancel

flink-savepoint: ## Take savepoint of running jobs
	@FLINK_URL=$(FLINK_URL) bash $(FLINK_SCRIPT) savepoint

flink-deploy: ## Full deploy cycle: savepoint → cancel → submit. Use JAR=path.jar for specific JAR
	@FLINK_URL=$(FLINK_URL) JAR=$(or $(JAR),) ENTRY_CLASS=$(or $(ENTRY_CLASS),) bash $(FLINK_SCRIPT) deploy

flink-rollback: ## Rollback: cancel current jobs → restore from last savepoint
	@FLINK_URL=$(FLINK_URL) bash $(FLINK_SCRIPT) rollback

flink-status: ## Show Flink cluster + job status
	@FLINK_URL=$(FLINK_URL) bash $(FLINK_SCRIPT) status

flink-logs: ## Tail Flink JobManager logs (last 100 lines, follow)
	docker logs uip-jobmanager --tail=100 -f

# ─── Kong JWKS Verification (v3.1-15) ────────────────────────────────────────

kong-test-jwks: ## Test Kong JWKS integration (requires full stack running)
	@bash ../infra/kong/test-jwks.sh

avro-register: ## Register Avro schemas into Apicurio Registry (idempotent)
	@bash scripts/register-avro-schemas.sh

ch-migrate: ## Migrate ClickHouse data from single-node to HA cluster (run after up-ha)
	@bash scripts/ch-migrate.sh

ch-cluster-init: ## Run ClickHouse ON CLUSTER DDL migration (idempotent)
	@bash scripts/ch-cluster-init.sh

keycloak-realm-patch: ## Parameterize Keycloak realm localhost URIs for UAT/production (S9-SEC-01)
	@bash scripts/keycloak-realm-patch.sh

keycloak-realm-patch-dry: ## Preview Keycloak realm localhost URI changes (dry run)
	@bash scripts/keycloak-realm-patch.sh --dry-run

kafka-rebalance: ## Rebalance Kafka partitions across 3 brokers (run after up-ha)
	@docker compose exec kafka kafka-reassign-partitions --bootstrap-server kafka:9092 --help > /dev/null 2>&1 || true
	@bash scripts/kafka-rebalance.sh

# ─── UAT Stack ────────────────────────────────────────────────────────────────

uat-up: ## Start UAT stack (uses .env.uat — copy from .env.uat.example first)
	@test -f .env.uat || { echo "ERROR: .env.uat not found. Copy .env.uat.example and fill in values."; exit 1; }
	$(COMPOSE_UAT) $(COMPOSE_UAT_FILE) up -d --build
	@echo ""
	@echo "UAT stack starting — polling health (max 3 min)..."
	@for i in $$(seq 1 18); do \
		sleep 10; \
		healthy=$$($(COMPOSE_UAT) $(COMPOSE_UAT_FILE) ps --format json 2>/dev/null | \
			python3 -c "import sys,json; data=sys.stdin.read(); \
			rows=[json.loads(l) for l in data.strip().split('\n') if l]; \
			print(sum(1 for r in rows if r.get('Health','') in ('healthy','')))" 2>/dev/null || echo 0); \
		echo "  [$$(( i * 10 ))s] Checking services..."; \
		$(COMPOSE_UAT) $(COMPOSE_UAT_FILE) ps --format "table {{.Name}}\t{{.Status}}" 2>/dev/null | grep -v NAME || true; \
		done
	@echo ""
	@echo "=== UAT stack is up ==="
	@echo "  Frontend: http://localhost:3000"
	@echo "  Backend:  http://localhost:8080"
	@echo "  Flink UI: http://localhost:8081"
	@echo ""
	@echo "Run 'make seed-uat' to load demo data."

uat-down: ## Stop UAT stack (preserves volumes)
	$(COMPOSE_UAT) $(COMPOSE_UAT_FILE) down

uat-logs: ## Tail UAT logs: make uat-logs svc=backend (omit svc for all)
	$(COMPOSE_UAT) $(COMPOSE_UAT_FILE) logs -f $(svc)

uat-ps: ## Show UAT service status
	$(COMPOSE_UAT) $(COMPOSE_UAT_FILE) ps

seed-uat: ## Seed UAT data (50 buildings, 100 sensors, 3 citizens, 6-month history)
	@echo "Seeding UAT data..."
	@DB_HOST=localhost DB_PORT=5432 \
		DB_NAME=$$(grep -E '^POSTGRES_DB=' .env.uat | cut -d= -f2-) \
		DB_USER=$$(grep -E '^POSTGRES_USER=' .env.uat | cut -d= -f2-) \
		DB_PASSWORD=$$(grep -E '^POSTGRES_PASSWORD=' .env.uat | cut -d= -f2-) \
		python3 ../scripts/seed_uat_data.py

# ─── Staging Stack (Sprint 11) ────────────────────────────────────────────────

staging-up: ## Start Sprint 11 staging stack (uses .env.staging — copy from .env.staging.example first)
	@test -f .env.staging || { echo "ERROR: .env.staging not found. Copy .env.staging.example and fill in values."; exit 1; }
	@test -f ../infra/kong/kong.staging.yml || { echo "ERROR: kong.staging.yml not found."; exit 1; }
	@echo "Pre-flight: verifying kong.staging.yml RS256 public key is not the placeholder..."
	@grep -q "REPLACE_WITH_STAGING_KEYCLOAK_RS256_PUBLIC_KEY" ../infra/kong/kong.staging.yml && \
		{ echo "ERROR: kong.staging.yml still has placeholder RS256 key. Update jwt_secrets.rsa_public_key."; exit 1; } || true
	@echo "Building sprint11-staging image..."
	@cp -n ../backend/build/libs/uip-backend-0.1.0-SNAPSHOT.jar ../backend/build/libs/app.jar 2>/dev/null || \
		cp ../backend/build/libs/uip-backend-0.1.0-SNAPSHOT.jar ../backend/build/libs/app.jar
	$(COMPOSE_STAGING) $(COMPOSE_STAGING_FILE) build backend
	$(COMPOSE_STAGING) $(COMPOSE_STAGING_FILE) up -d
	@echo ""
	@echo "=== Staging stack starting ==="
	@echo "  Backend:            http://localhost:8080"
	@echo "  Kong proxy:         http://localhost:8000"
	@echo "  Analytics-service:  http://localhost:8082"
	@echo "  ClickHouse:         http://localhost:8123"
	@echo "  Flink UI:           http://localhost:8081"
	@echo ""
	@echo "Run 'make staging-smoke-test' after stack is healthy."

staging-down: ## Stop staging stack (preserves volumes)
	$(COMPOSE_STAGING) $(COMPOSE_STAGING_FILE) down

staging-logs: ## Tail staging logs: make staging-logs svc=backend
	$(COMPOSE_STAGING) $(COMPOSE_STAGING_FILE) logs -f $(svc)

staging-ps: ## Show staging service status
	$(COMPOSE_STAGING) $(COMPOSE_STAGING_FILE) ps

staging-smoke-test: ## Run Sprint 11 smoke tests against staging
	@echo "Running Sprint 11 smoke tests..."
	@BACKEND_URL=http://localhost:8080 \
	 KONG_PROXY_URL=http://localhost:8000 \
	 ANALYTICS_SERVICE_URL=http://localhost:8082 \
	 CLICKHOUSE_URL=http://localhost:8123 \
	 POSTGRES_USER=$$(grep -E '^POSTGRES_USER=' .env.staging | cut -d= -f2-) \
	 POSTGRES_DB=$$(grep -E '^POSTGRES_DB=' .env.staging | cut -d= -f2-) \
	 bash ../scripts/smoke-test-sprint11.sh

uat-test: ## Run smoke tests against UAT stack (10 smoke test cases)
	@echo "Running UAT smoke tests against http://localhost:8080 ..."
	@BASE_URL=http://localhost:8080 python3 ../scripts/uat_smoke_test.py
	@echo ""
	@echo "Quick manual verification:"
	@echo "  curl -s http://localhost:8080/api/v1/health | python3 -m json.tool"

regression-test: ## Run full API regression suite (Sprint 1-4 features, 14 groups)
	@echo "Running API regression tests against http://localhost:8080 ..."
	@BASE_URL=http://localhost:8080 python3 ../scripts/api_regression_test.py
	@echo ""
	@echo "Run specific group: make regression-test-group GROUP=esg"

regression-test-group: ## Run one regression group: make regression-test-group GROUP=esg
	@echo "Running regression group '$(GROUP)'..."
	@BASE_URL=http://localhost:8080 python3 ../scripts/api_regression_test.py --group $(GROUP)

uat-clean: ## Remove UAT containers AND volumes (full reset — data will be lost)
	@echo "WARNING: This will destroy all UAT data. Press Ctrl+C to cancel..."
	@sleep 5
	$(COMPOSE_UAT) $(COMPOSE_UAT_FILE) down -v --remove-orphans

# ─── Security ─────────────────────────────────────────────────────────────────
#
# Cần file infrastructure/.env.security (gitignored) với nội dung:
#   NVD_API_KEY=<your-key>   # https://nvd.nist.gov/developers/request-an-api-key
#
# Tạo lần đầu: cp .env.security.example .env.security  rồi điền API key vào.

-include .env.security
export NVD_API_KEY

security-scan: ## Run OWASP Dependency-Check CVE scan (cần NVD_API_KEY trong .env.security)
	@test -n "$(NVD_API_KEY)" || { \
		echo ""; \
		echo "ERROR: NVD_API_KEY chưa được set."; \
		echo "  1. Lấy key miễn phí: https://nvd.nist.gov/developers/request-an-api-key"; \
		echo "  2. cp infrastructure/.env.security.example infrastructure/.env.security"; \
		echo "  3. Điền NVD_API_KEY vào infrastructure/.env.security"; \
		echo ""; \
		exit 1; \
	}
	@echo "Running OWASP Dependency-Check scan..."
	@cd ../backend && NVD_API_KEY=$(NVD_API_KEY) ./gradlew dependencyCheckAnalyze --no-daemon
	@echo ""
	@echo "=== Scan complete ==="
	@echo "  HTML report: backend/build/reports/dependency-check/dependency-check-report.html"
	@echo ""
	@echo "  CRITICAL + HIGH CVEs:"
	@cd ../backend && grep -o '"severity":"[^"]*"' \
		build/reports/dependency-check/dependency-check-report.json 2>/dev/null | \
		sort | uniq -c | sort -rn || echo "  (chạy make security-report để xem chi tiết)"

security-report: ## Mở HTML report trong browser (sau khi đã chạy security-scan)
	@test -f ../backend/build/reports/dependency-check/dependency-check-report.html || { \
		echo "ERROR: Report chưa có. Chạy 'make security-scan' trước."; exit 1; \
	}
	@open ../backend/build/reports/dependency-check/dependency-check-report.html

security-open: security-report ## Alias của security-report

spotbugs-scan: ## Run SpotBugs + FindSecBugs static analysis trên main sources
	@echo "Running SpotBugs + FindSecBugs..."
	@cd ../backend && ./gradlew spotbugsMain --no-daemon
	@echo ""
	@echo "=== SpotBugs complete ==="
	@echo "  HTML report: backend/build/reports/spotbugs/main.html"
	@echo ""
	@echo "  Bug count:"
	@grep -c '<BugInstance' ../backend/build/reports/spotbugs/main.xml 2>/dev/null \
		&& echo " bugs found (xem HTML report để xem chi tiết)" \
		|| echo "  0 bugs (hoặc report XML chưa có)"

spotbugs-report: ## Mở SpotBugs HTML report trong browser (sau khi đã chạy spotbugs-scan)
	@test -f ../backend/build/reports/spotbugs/main.html || { \
		echo "ERROR: Report chưa có. Chạy 'make spotbugs-scan' trước."; exit 1; \
	}
	@open ../backend/build/reports/spotbugs/main.html

spotbugs-open: spotbugs-report ## Alias của spotbugs-report

security-full: ## Chạy toàn bộ security check: SpotBugs + OWASP (cần NVD_API_KEY)
	@$(MAKE) spotbugs-scan
	@$(MAKE) security-scan

# ─── OWASP Dependency-Check (M5-4-T13) ───────────────────────────────────────

owasp-scan: ## Run OWASP dependency check locally (does not fail build — for review only)
	@echo "Running OWASP dependency check..."
	@cd ../backend && ./gradlew dependencyCheckAnalyze --no-daemon
	@echo ""
	@echo "=== Scan complete ==="
	@echo "  HTML: backend/build/reports/dependency-check/dependency-check-report.html"
	@echo "  JSON: backend/build/reports/dependency-check/dependency-check-report.json"
	@echo ""
	@echo "📊 Vulnerability summary:"
	@cd ../backend && jq -r '.dependencies[]?.vulnerabilities[]? | .cvssv3.baseSeverity // .cvssv2.severity' \
		build/reports/dependency-check/dependency-check-report.json 2>/dev/null | \
		sort | uniq -c | sort -rn || echo "  (no vulnerabilities or report not found)"

owasp-scan-ci: ## Run OWASP scan in CI mode (fails on CRITICAL/HIGH CVEs, CVSS >= 7.0)
	@echo "Running OWASP dependency check (CI mode)..."
	@cd ../backend && ./gradlew dependencyCheckAnalyze --no-daemon
	@echo ""
	@cd ../backend && python3 -c "import json, sys; \
		report = json.load(open('build/reports/dependency-check/dependency-check-report.json')); \
		critical_high = [v for dep in report.get('dependencies', []) \
			for v in dep.get('vulnerabilities', []) \
			if (v.get('cvssv3', {}).get('baseScore', 0) >= 7.0)]; \
		print(f'Found {len(critical_high)} CRITICAL/HIGH CVEs (CVSS >= 7.0)'); \
		[print(f'  - {v[\"name\"]} (CVSS {v.get(\"cvssv3\", {}).get(\"baseScore\", \"N/A\")}): {v.get(\"description\", \"\")[:80]}...') for v in critical_high[:5]]; \
		sys.exit(1 if critical_high else 0)"

# ─── Cleanup ──────────────────────────────────────────────────────────────────

clean: ## Remove all containers, volumes, networks
	$(COMPOSE) $(COMPOSE_FILE) down -v --remove-orphans
