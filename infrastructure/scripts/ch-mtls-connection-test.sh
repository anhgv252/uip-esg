#!/usr/bin/env bash
# =============================================================================
# ch-mtls-connection-test.sh — verify mTLS handshake to ClickHouse 8443
#
# Task: MVP5 Sprint 1 — T09 (GAP-046 carry-over)
# Runs from the host. Uses docker exec against the running clickhouse-01
# container (where the server cert + ca.crt + client cert are mounted) so the
# script has no host-side openssl/clickhouse-client dependency.
#
# Verifies:
#   1. TLS handshake completes (server presents server.crt)
#   2. Client auth succeeds (client.crt presented and accepted)
#   3. SQL round-trip returns Ok (SELECT 1)
#   4. Negative test: connection WITHOUT client cert is REJECTED (mTLS enforced)
#
# Expected PASS output is documented in
#   docs/mvp5/runbooks/mvp5-sprint1-ch-mtls-runbook.md §4
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(dirname "${SCRIPT_DIR}")"
CERT_DIR="${INFRA_DIR}/clickhouse/tls"

NODE="${CH_NODE:-clickhouse-01}"
CONTAINER="uip-${NODE}"

echo "[ch-mtls-test] Target container: ${CONTAINER}"
echo "[ch-mtls-test] Cert dir (host):  ${CERT_DIR}"

if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "[ch-mtls-test] FAIL: container ${CONTAINER} not running."
  echo "[ch-mtls-test]       Start the HA stack first:  make up-ha"
  exit 2
fi

for f in ca.crt server.crt server.key client.crt client.key; do
  if [ ! -f "${CERT_DIR}/${f}" ]; then
    echo "[ch-mtls-test] FAIL: ${CERT_DIR}/${f} missing."
    echo "[ch-mtls-test]       Generate with:  ${SCRIPT_DIR}/gen-ch-mtls-certs.sh"
    exit 2
  fi
done

echo
echo "=== Test 1: mTLS handshake + SQL round-trip (client cert presented) ==="
# clickhouse-client inside the server image speaks the native protocol on 9440
# when --secure is set; HTTP 8443 is exercised separately via curl below.
docker exec "${CONTAINER}" \
  clickhouse-client \
    --host "${NODE}" --port 9440 --secure \
    --ssl_ca /etc/clickhouse-server/tls/ca.crt \
    --ssl_cert /etc/clickhouse-server/tls/client.crt \
    --ssl_key /etc/clickhouse-server/tls/client.key \
    --query "SELECT 'Ok.' AS mTLS_OK, version()" \
    --format PrettyCompact
echo "[ch-mtls-test] PASS: native mTLS (9440) round-trip Ok."

echo
echo "=== Test 2: HTTPS mTLS via curl (HTTP 8443, JDBC-equivalent path) ==="
docker exec "${CONTAINER}" \
  curl -sS \
    --cacert  /etc/clickhouse-server/tls/ca.crt \
    --cert    /etc/clickhouse-server/tls/client.crt \
    --key     /etc/clickhouse-server/tls/client.key \
    --resolve "${NODE}:8443:127.0.0.1" \
    "https://${NODE}:8443/?query=SELECT%201%20FORMAT%20PrettyCompact"
echo "[ch-mtls-test] PASS: HTTPS mTLS (8443) round-trip Ok."

echo
echo "=== Test 3 (negative): connection WITHOUT client cert MUST be rejected ==="
if docker exec "${CONTAINER}" \
  curl -sS -o /dev/null -w "%{http_code}" \
    --cacert /etc/clickhouse-server/tls/ca.crt \
    --resolve "${NODE}:8443:127.0.0.1" \
    "https://${NODE}:8443/?query=SELECT%201" 2>/dev/null \
  | grep -qE '^(400|403|000)$'; then
  echo "[ch-mtls-test] PASS: client-cert-less connection rejected (mTLS enforced)."
else
  echo "[ch-mtls-test] FAIL: server accepted a connection without client cert!"
  echo "[ch-mtls-test]        Check tls-config.xml <verificationMode>strict</verificationMode>."
  exit 1
fi

echo
echo "[ch-mtls-test] ALL CHECKS PASSED — ClickHouse mTLS data path verified."
