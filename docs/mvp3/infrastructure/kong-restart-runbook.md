# Kong DB-less Restart Runbook

**Service:** Kong API Gateway (DB-less mode)
**Scope:** analytics-service, iot-ingestion-service (extracted services only)
**Owner:** DevOps

---

## Restart Procedure

### Normal Restart

```bash
docker compose -f infra/kong/kong.local.yml restart kong
```

Or via Kubernetes:

```bash
kubectl -n uip rollout restart deployment/kong
kubectl -n uip rollout status deployment/kong --timeout=120s
```

### Health Check

```bash
# Admin API status
curl -s http://localhost:8001/status

# Quick health check script
./scripts/kong-health-check.sh
```

### Verify Routes + Plugins

```bash
curl -s http://localhost:8001/routes | python3 -c "import sys,json; print(json.dumps(json.load(sys.stdin), indent=2))"
curl -s http://localhost:8001/plugins | python3 -c "import sys,json; print(json.dumps(json.load(sys.stdin), indent=2))"
```

## Common Failure Modes

| Symptom | Cause | Fix |
|---------|-------|-----|
| 503 Service Unavailable | Upstream service down | Check analytics-service health: `curl analytics-service:8081/actuator/health` |
| 401 on all requests | JWT plugin misconfigured | Verify `KEYCLOAK_RS256_PUBLIC_KEY` env var |
| Routes missing after restart | Config file not mounted | Verify volume mount for `kong.yml` |
| High latency >1s | Connection pool exhausted | Check `read_timeout` + upstream response times |

## Post-Restart Verification

1. Run health check: `./scripts/kong-health-check.sh`
2. Run restart test: `./scripts/kong-restart-test.sh`
3. Verify alg=none rejection: `./infra/kong/test-alg-none.sh`

## Alerting

- **KongDown**: fires when Kong unreachable for >1 minute
- **KongHighErrorRate**: fires when 5xx rate >5% for 5 minutes
- **KongHighLatency**: fires when p95 >1s for 5 minutes

Alerts route to Slack #uip-alerts + PagerDuty.
