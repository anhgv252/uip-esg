.DEFAULT_GOAL := help

COMPOSE          = docker compose --env-file .env
COMPOSE_UAT      = docker compose --env-file .env.uat
COMPOSE_FILE     = -f docker-compose.yml
COMPOSE_UAT_FILE = -f docker-compose.yml -f docker-compose.uat.yml

.PHONY: help up down logs ps migrate test-mqtt test-pipeline \
        uat-up uat-down uat-logs uat-ps uat-test uat-clean seed-uat clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ─── Dev Stack ────────────────────────────────────────────────────────────────

up: ## Start full dev stack
	@cp -n .env.example .env 2>/dev/null || true
	$(COMPOSE) $(COMPOSE_FILE) up -d
	@echo "Waiting for all services to be healthy..."
	$(COMPOSE) $(COMPOSE_FILE) ps

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

uat-clean: ## Remove UAT containers AND volumes (full reset — data will be lost)
	@echo "WARNING: This will destroy all UAT data. Press Ctrl+C to cancel..."
	@sleep 5
	$(COMPOSE_UAT) $(COMPOSE_UAT_FILE) down -v --remove-orphans

# ─── Cleanup ──────────────────────────────────────────────────────────────────

clean: ## Remove all containers, volumes, networks
	$(COMPOSE) $(COMPOSE_FILE) down -v --remove-orphans
