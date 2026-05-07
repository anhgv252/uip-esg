#!/bin/bash
#
# Sprint 5 Demo Evidence Collector
# Collects all evidence files needed for PO demo
# Run 30 minutes before demo starts
#

set -e

EVIDENCE_DIR="docs/mvp2/reports/sprint5-demo-evidence-$(date +%Y-%m-%d)"
mkdir -p "$EVIDENCE_DIR"

echo "===================================================="
echo "Sprint 5 Demo Evidence Collector"
echo "===================================================="
echo "Output directory: $EVIDENCE_DIR"
echo ""

# 1. Backend health report
echo "[1/10] Collecting backend health report..."
curl -s http://localhost:8080/actuator/health | jq > "$EVIDENCE_DIR/backend-health.json"
echo "✅ Saved to: backend-health.json"

# 2. API contract test results
echo "[2/10] Running API regression tests..."
python scripts/api_regression_test.py --group=15,16 --html-report="$EVIDENCE_DIR/api-tests.html" || true
echo "✅ Saved to: api-tests.html"

# 3. RLS verification query
echo "[3/10] Verifying RLS policies..."
cat > /tmp/rls_test.sql <<'EOF'
-- Test cross-tenant isolation
SET LOCAL app.tenant_id = 'hcm';
SELECT 'Tenant HCM alert count:' as label, COUNT(*) as count FROM alerts;

SET LOCAL app.tenant_id = 'dn';
SELECT 'Tenant DN alert count:' as label, COUNT(*) as count FROM alerts;

-- Show RLS policies
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual
FROM pg_policies
WHERE schemaname='public'
ORDER BY tablename, policyname;
EOF

psql -h localhost -p 5432 -U uip_admin -d uip_smartcity -f /tmp/rls_test.sql > "$EVIDENCE_DIR/rls-isolation.txt" 2>&1
echo "✅ Saved to: rls-isolation.txt"

# 4. Cache key namespace verification
echo "[4/10] Checking Redis cache keys..."
redis-cli -h localhost -p 6379 KEYS "esg:hcm:*" | wc -l > "$EVIDENCE_DIR/redis-hcm-keys.txt"
redis-cli -h localhost -p 6379 KEYS "esg:dn:*" | wc -l > "$EVIDENCE_DIR/redis-dn-keys.txt"
redis-cli -h localhost -p 6379 KEYS "esg:default:*" | wc -l > "$EVIDENCE_DIR/redis-default-keys.txt"
echo "✅ Saved to: redis-*-keys.txt"

# 5. Kafka event sample
echo "[5/10] Sampling Kafka events..."
timeout 10 kafka-console-consumer.sh --bootstrap-server localhost:29092 \
  --topic UIP.iot.sensor.reading.v1 --max-messages 5 \
  > "$EVIDENCE_DIR/kafka-events.json" 2>&1 || true
echo "✅ Saved to: kafka-events.json"

# 6. Performance benchmark (admin overview API)
echo "[6/10] Running performance benchmark..."
# Get token first
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@uip.vn","password":"admin123"}' | jq -r .token)

ab -n 1000 -c 50 -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/tenant-admin/overview \
  > "$EVIDENCE_DIR/perf-overview-api.txt" 2>&1 || true
echo "✅ Saved to: perf-overview-api.txt"

# 7. JWT token decode
echo "[7/10] Decoding JWT token..."
if command -v jwt &> /dev/null; then
  echo "$TOKEN" | jwt decode - > "$EVIDENCE_DIR/jwt-decoded.json"
  echo "✅ Saved to: jwt-decoded.json"
else
  echo "⚠️  jwt-cli not installed, skipping JWT decode"
fi

# 8. Feature flags config
echo "[8/10] Extracting feature flags..."
grep -A 20 "tenants:" backend/src/main/resources/application.yml > "$EVIDENCE_DIR/feature-flags.yml"
echo "✅ Saved to: feature-flags.yml"

# 9. Database migration status
echo "[9/10] Checking Flyway migrations..."
psql -h localhost -p 5432 -U uip_admin -d uip_smartcity \
  -c "SELECT version, description, installed_on FROM flyway_schema_history WHERE version IN ('14', '15', '16', '17', '18', '19') ORDER BY installed_rank;" \
  > "$EVIDENCE_DIR/flyway-migrations.txt"
echo "✅ Saved to: flyway-migrations.txt"

# 10. System info
echo "[10/10] Collecting system info..."
cat > "$EVIDENCE_DIR/system-info.txt" <<EOF
Hostname: $(hostname)
Date: $(date)
Backend version: $(curl -s http://localhost:8080/actuator/info | jq -r .build.version)
Java version: $(java -version 2>&1 | head -n 1)
Node version: $(node -v)
Postgres version: $(psql --version)
Redis version: $(redis-cli --version)

Active connections to database:
$(psql -h localhost -p 5432 -U uip_admin -d uip_smartcity -c "SELECT count(*) FROM pg_stat_activity WHERE datname='uip_smartcity';")
EOF
echo "✅ Saved to: system-info.txt"

# Summary
echo ""
echo "===================================================="
echo "Demo Evidence Collection Complete"
echo "===================================================="
echo "All evidence files saved to:"
echo "  $EVIDENCE_DIR"
echo ""
echo "File count: $(ls -1 "$EVIDENCE_DIR" | wc -l)"
echo ""
echo "Next steps:"
echo "  1. Review all files for correctness"
echo "  2. Run E2E tests and record video"
echo "  3. Run smoke tests: python scripts/sprint5_smoke_test.py"
echo "  4. Archive evidence for PO review"
echo ""
