# ─────────────────────────────────────────────────────────────────
#  UIP ESG POC  •  Makefile
#
#  Broker : Apache Kafka 3.7 (bitnami/kafka, KRaft)
#  ETL    : Redpanda Connect (Benthos, broker-agnostic)
#  UI     : Kafka UI (Provectus)
# ─────────────────────────────────────────────────────────────────

.PHONY: up down build clean logs status counts check test \
        reingest reingest-dry reingest-type \
        review review-type \
        api-overview api-quality api-docs \
        shell-ts shell-pg shell-kafka \
        topics topic-desc lag lag-connect peek

# ── Stack management ─────────────────────────────────────────────

## Build images and start the full stack
up:
	docker compose up -d --build
	@echo ""
	@echo "╔══════════════════════════════════════════════════╗"
	@echo "║  Stack starting – check progress: make logs      ║"
	@echo "╠══════════════════════════════════════════════════╣"
	@echo "║  Kafka UI      http://localhost:8080              ║"
	@echo "║  Flink UI      http://localhost:8081              ║"
	@echo "║  ESG API Docs  http://localhost:8000/docs         ║"
	@echo "╚══════════════════════════════════════════════════╝"

## Stop all services (keep data volumes)
down:
	docker compose down

## Stop all services AND remove all data volumes
clean:
	docker compose down -v
	@echo "🗑  All volumes removed."

## Rebuild images from scratch (no cache)
build:
	docker compose build --no-cache

## Tail logs for all services
logs:
	docker compose logs -f --tail=50

## Tail logs for a specific service   e.g. make logs-flink-job
logs-%:
	docker compose logs -f --tail=100 $*

## Show container status
status:
	docker compose ps

# ── Quick record counts ───────────────────────────────────────────

## Count records in both schemas (single TimescaleDB)
counts:
	@echo "── esg.clean_metrics ───────────────────────────────"
	@docker exec timescaledb psql -U esg_user -d esg_db -c \
	  "SELECT COUNT(*) AS clean_records FROM esg.clean_metrics;" -q
	@echo "── esg.aggregate_metrics ───────────────────────────"
	@docker exec timescaledb psql -U esg_user -d esg_db -c \
	  "SELECT COUNT(*) AS aggregate_windows FROM esg.aggregate_metrics;" -q
	@echo "── error_mgmt.error_records ────────────────────────"
	@docker exec timescaledb psql -U esg_user -d esg_db -c \
	  "SELECT error_type, COUNT(*) AS count \
	   FROM error_mgmt.error_records GROUP BY error_type ORDER BY count DESC;" -q

# ── Verification ─────────────────────────────────────────────────

## Run the full results checker (single TimescaleDB)
check:
	pip install psycopg2-binary tabulate -q && \
	TIMESCALE_HOST=localhost TIMESCALE_PORT=5432 \
	python3 scripts/check_results.py

## Run the full integration test suite (DB + API)
test:
	pip install psycopg2-binary requests -q && \
	TIMESCALE_HOST=localhost TIMESCALE_PORT=5432 \
	ESG_API_URL=http://localhost:8000 \
	python3 scripts/integration_tests.py

# ── Error operator workflow ───────────────────────────────────────

## Dry-run: show what would be re-ingested (sends nothing to Kafka)
reingest-dry:
	pip install confluent-kafka psycopg2-binary -q && \
	KAFKA_BOOTSTRAP_SERVERS=localhost:29092 \
	TIMESCALE_HOST=localhost TIMESCALE_PORT=5432 \
	python3 scripts/reingest_errors.py --dry-run

## Re-ingest all reviewed + auto-correctable errors (up to 5000)
reingest:
	pip install confluent-kafka psycopg2-binary -q && \
	KAFKA_BOOTSTRAP_SERVERS=localhost:29092 \
	TIMESCALE_HOST=localhost TIMESCALE_PORT=5432 \
	python3 scripts/reingest_errors.py --limit 5000

## Re-ingest a specific error type   e.g. make reingest-type TYPE=MISSING_UNIT
reingest-type:
	pip install confluent-kafka psycopg2-binary -q && \
	KAFKA_BOOTSTRAP_SERVERS=localhost:29092 \
	TIMESCALE_HOST=localhost TIMESCALE_PORT=5432 \
	python3 scripts/reingest_errors.py --error-type $(TYPE)

# ── Operator review shortcuts ─────────────────────────────────────

## Mark a single error record as reviewed   e.g. make review ID=42 BY=admin
review:
	docker exec timescaledb psql -U esg_user -d esg_db -c \
	  "UPDATE error_mgmt.error_records \
	   SET reviewed=TRUE, reviewed_by='$(BY)', reviewed_at=NOW() \
	   WHERE id=$(ID);"

## Mark all errors of a given type as reviewed   e.g. make review-type TYPE=MISSING_UNIT BY=admin
review-type:
	docker exec timescaledb psql -U esg_user -d esg_db -c \
	  "UPDATE error_mgmt.error_records \
	   SET reviewed=TRUE, reviewed_by='$(BY)', reviewed_at=NOW() \
	   WHERE error_type='$(TYPE)' AND reviewed=FALSE;"
	@echo "✅ Marked all $(TYPE) errors as reviewed."

# ── ESG API shortcuts ─────────────────────────────────────────────

## Call /esg/overview
api-overview:
	curl -s http://localhost:8000/esg/overview | python3 -m json.tool

## Call /esg/data-quality
api-quality:
	curl -s http://localhost:8000/esg/data-quality | python3 -m json.tool

## Print API doc URLs
api-docs:
	@echo "Swagger UI : http://localhost:8000/docs"
	@echo "ReDoc      : http://localhost:8000/redoc"

# ── Database shells ───────────────────────────────────────────────

## Open psql on TimescaleDB
shell-ts:
	docker exec -it timescaledb psql -U esg_user -d esg_db

## Open psql on error_mgmt schema (TimescaleDB)
shell-errors:
	docker exec -it timescaledb psql -U esg_user -d esg_db -c "SET search_path TO error_mgmt,public;" --

## Open bash shell trên Kafka container (kafka-topics.sh / kafka-console-*)
shell-kafka:
	docker exec -it kafka bash

# ── Kafka diagnostics (kafka-topics.sh / kafka-consumer-groups.sh) ─

## List all topics
topics:
	docker exec kafka /opt/kafka/bin/kafka-topics.sh \
	  --bootstrap-server localhost:9092 --list

## Describe a specific topic   e.g. make topic-desc T=ngsi_ld_telemetry
topic-desc:
	docker exec kafka /opt/kafka/bin/kafka-topics.sh \
	  --bootstrap-server localhost:9092 \
	  --describe --topic $(T)

## Consumer group lag for the Flink processor
lag:
	docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
	  --bootstrap-server localhost:9092 \
	  --describe --group flink-esg-processor

## Consumer group lag for Redpanda Connect normaliser
lag-connect:
	docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
	  --bootstrap-server localhost:9092 \
	  --describe --group redpanda-connect-esg-normalizer

## Peek at the last 5 messages on ngsi_ld_telemetry
peek:
	docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
	  --bootstrap-server localhost:9092 \
	  --topic ngsi_ld_telemetry \
	  --from-beginning --max-messages 5 \
	  --timeout-ms 5000
