.DEFAULT_GOAL := help

COMPOSE          = docker compose --env-file .env
COMPOSE_UAT      = docker compose --env-file .env.uat
COMPOSE_FILE     = -f docker-compose.yml
COMPOSE_UAT_FILE = -f docker-compose.yml -f docker-compose.uat.yml

.PHONY: help up up-ha down logs ps migrate test-mqtt test-pipeline \
        uat-up uat-down uat-logs uat-ps uat-test uat-clean seed-uat clean \
        regression-test regression-test-group \
        flink-list flink-build flink-submit flink-cancel flink-savepoint flink-deploy flink-status \
        avro-register ch-migrate \
        security-scan security-report security-open \
        spotbugs-scan spotbugs-report spotbugs-open security-full

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ─── Dev Stack ────────────────────────────────────────────────────────────────

up: ## Start full dev stack
	@cp -n .env.example .env 2>/dev/null || true
	$(COMPOSE) $(COMPOSE_FILE) up -d
	@echo "Waiting for all services to be healthy..."
	$(COMPOSE) $(COMPOSE_FILE) ps

up-ha: ## Start HA stack (ClickHouse 2-node + Kafka 3-broker — Tier 2 pilot)
	@cp -n .env.example .env 2>/dev/null || true
	$(COMPOSE) -f docker-compose.yml -f docker-compose.ha.yml up -d
	@echo ""
	@echo "=== HA Stack starting ==="
	@echo "  ClickHouse: clickhouse-01 (:8123) + clickhouse-02 (:8124) + keeper (:9181)"
	@echo ""
	@echo "Run 'make ch-migrate' after stack is healthy."

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

# ─── Flink Jobs (ADR-038, S8-OPS03) ────────────────────────────────────────────

FLINK_SCRIPT := scripts/flink-deploy.sh
FLINK_URL    ?= http://localhost:8081

flink-list: ## List running Flink jobs
	@bash $(FLINK_SCRIPT) list

flink-build: ## Build Flink jobs JAR
	@bash $(FLINK_SCRIPT) build

flink-submit: ## Submit all Flink jobs to cluster
	@FLINK_URL=$(FLINK_URL) bash $(FLINK_SCRIPT) submit

flink-cancel: ## Cancel all running Flink jobs
	@FLINK_URL=$(FLINK_URL) bash $(FLINK_SCRIPT) cancel

flink-savepoint: ## Take savepoint of running jobs
	@FLINK_URL=$(FLINK_URL) bash $(FLINK_SCRIPT) savepoint

flink-deploy: ## Full deploy cycle: savepoint → cancel → submit (zero-downtime)
	@FLINK_URL=$(FLINK_URL) bash $(FLINK_SCRIPT) deploy

flink-status: ## Show Flink cluster + job status
	@FLINK_URL=$(FLINK_URL) bash $(FLINK_SCRIPT) status

avro-register: ## Register Avro schemas into Apicurio Registry (idempotent)
	@bash scripts/register-avro-schemas.sh

ch-migrate: ## Migrate ClickHouse data from single-node to HA cluster (run after up-ha)
	@bash scripts/ch-migrate.sh

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

# ─── Cleanup ──────────────────────────────────────────────────────────────────

clean: ## Remove all containers, volumes, networks
	$(COMPOSE) $(COMPOSE_FILE) down -v --remove-orphans
