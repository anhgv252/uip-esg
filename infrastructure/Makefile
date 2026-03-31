.DEFAULT_GOAL := help

COMPOSE = docker compose --env-file .env
COMPOSE_FILE = -f docker-compose.yml

.PHONY: help up down logs ps test seed-dev uat-up uat-down seed-uat

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

uat-up: ## Start UAT stack
	@cp -n .env.example .env 2>/dev/null || true
	$(COMPOSE) -f docker-compose.yml -f docker-compose.uat.yml up -d
	@echo "UAT stack starting — waiting 3 minutes for full health..."
	@sleep 60
	$(COMPOSE) -f docker-compose.yml -f docker-compose.uat.yml ps

uat-down: ## Stop UAT stack
	$(COMPOSE) -f docker-compose.yml -f docker-compose.uat.yml down

seed-uat: ## Seed UAT data (50 buildings, 100 sensors, test citizens)
	@cd .. && python3 poc/scripts/seed_uat_data.py

# ─── Cleanup ──────────────────────────────────────────────────────────────────

clean: ## Remove all containers, volumes, networks
	$(COMPOSE) $(COMPOSE_FILE) down -v --remove-orphans
