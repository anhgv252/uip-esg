# UIP Infrastructure — Ops Reference

## Quick Start

```bash
# Standard stack
make up

# HA stack (ClickHouse 2-node + Kafka 3-broker)
make up-ha

# Health check HA environment
make ha-health-check

# Stop all services
make down
```

---

## KAFKA_CLUSTER_ID

Kafka KRaft mode requires a stable cluster ID that persists across broker restarts. This must be set **once** and never changed.

### Current value (in `infrastructure/.env`)

```
KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qg
```

### Why it must not change

All 3 KRaft brokers share the same `CLUSTER_ID`. If the ID changes, broker metadata is inconsistent — all brokers must be wiped and re-initialized.

### Generate a new ID (only for fresh environments)

```bash
# Option 1: Python (no extra deps)
python3 -c "import uuid, base64; print(base64.urlsafe_b64encode(uuid.uuid4().bytes).rstrip(b'=').decode())"

# Option 2: kafka-storage tool (if Kafka binaries available)
kafka-storage.sh random-uuid
```

Add the output to `infrastructure/.env`:
```
KAFKA_CLUSTER_ID=<generated-value>
```

### Restart safety

After adding `KAFKA_CLUSTER_ID` to `.env`, all 3 brokers (`kafka-1`, `kafka-2`, `kafka-3`) can be stopped and restarted without re-generating:

```bash
docker compose restart kafka-1 kafka-2 kafka-3
```

---

## ClickHouse DDL (ON CLUSTER)

ClickHouse HA requires all DDL to run `ON CLUSTER`. Use the migration script to create tables idempotently:

```bash
make ch-migrate
# or directly:
bash scripts/ch-cluster-init.sh
```

The script is idempotent — safe to run multiple times.

---

## Keycloak Realm Parameterization

For production/UAT deployment, realm localhost URIs must be replaced. Use the patch script:

```bash
# Dry-run (preview diff, no file change)
FRONTEND_URL=https://smartcity.hcmc.gov.vn \
BACKEND_URL=https://api.smartcity.hcmc.gov.vn \
UIP_API_CLIENT_SECRET=<rotated-secret> \
bash scripts/keycloak-realm-patch.sh --dry-run

# Apply (overwrites REALM_FILE in-place)
FRONTEND_URL=https://smartcity.hcmc.gov.vn \
BACKEND_URL=https://api.smartcity.hcmc.gov.vn \
UIP_API_CLIENT_SECRET=<rotated-secret> \
bash scripts/keycloak-realm-patch.sh
```

Pre-generated production realm: `../infra/keycloak/realm-uip-production.json`  
> Replace `ROTATE_BEFORE_DEPLOY` secret with the rotated value from `POST /admin/realms/uip/clients/{id}/client-secret`.

For the full live secret rotation procedure, see:  
`docs/mvp3/reports/sprint9-s9-sec-01-keycloak-hardening.md`

---

## bootstrap.servers — All 3 Brokers

Backend Spring configuration and Flink jobs must reference all 3 Kafka brokers to survive a broker failure:

```
kafka-1:29092,kafka-2:29093,kafka-3:29094
```

This is set in `backend/src/main/resources/application.properties` and propagated via `docker-compose.yml` / `docker-compose.ha.yml` env vars.

---

## Common Make Targets

| Target | Description |
|--------|-------------|
| `make up` | Start standard stack |
| `make up-ha` | Start HA stack (3-broker Kafka + 2-node CH) |
| `make ha-health-check` | Run HA health check script |
| `make ch-migrate` | Run ClickHouse ON CLUSTER DDL migrations |
| `make kafka-rebalance` | Rebalance Kafka partitions across 3 brokers |
| `make down` | Stop all services |
| `make logs` | Tail all service logs |
| `make check-contract-drift` | Verify frontend API types match OpenAPI spec |
