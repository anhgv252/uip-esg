#!/usr/bin/env bash
# =============================================================================
# gen-ch-mtls-certs.sh — Generate internal CA + ClickHouse server certs +
# client cert for mTLS between JDBC consumers (analytics-service, backend)
# and the 2-node ClickHouse HA cluster.
#
# Task: MVP5 Sprint 1 — T09 (GAP-046 carry-over)
# Single-region only (DR deferred MVP6 per ADR-048).
#
# Topology covered:
#   - internal CA (self-signed)
#   - server cert for clickhouse-01 + clickhouse-02 (SAN = container hostnames)
#   - client cert for analytics-service / backend (CN=uip-jdbc-client)
#
# !!! POC / TEST-ENVIRONMENT DEV CERTS ONLY !!!
# These certs are committed intentionally so the test topology is reproducible.
# They are NOT production-grade secrets:
#   - 10-year CA, 2-year leaf certs (documented below)
#   - single internal CA, no HSM, no rotation automation
# Production MUST rotate via cert-manager (K8s) or Vault PKI before pilot.
# See docs/mvp5/runbooks/mvp5-sprint1-ch-mtls-runbook.md §3 (rotation).
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(dirname "${SCRIPT_DIR}")"
CERT_DIR="${INFRA_DIR}/clickhouse/tls"

# Expiry (days). Documented in runbook §3.
CA_DAYS="${CA_DAYS:-3650}"        # 10 years — internal CA
LEAF_DAYS="${LEAF_DAYS:-730}"     # 2 years — server + client certs

mkdir -p "${CERT_DIR}"
cd "${CERT_DIR}"

# Idempotency: refuse to overwrite unless --force
FORCE="${1:-}"
if [ -z "${FORCE}" ] && [ -f "ca.crt" ]; then
  echo "[gen-ch-mtls] ca.crt already exists in ${CERT_DIR}. Pass --force to regenerate."
  echo "[gen-ch-mtls] Exiting without changes (cert rotation is manual — see runbook §3)."
  exit 0
fi

echo "[gen-ch-mtls] Generating internal CA + server + client certs in ${CERT_DIR}"
echo "[gen-ch-mtls] Expiry: CA=${CA_DAYS}d, leaf=${LEAF_DAYS}d"

# ---------------------------------------------------------------------------
# 1. Internal CA
# ---------------------------------------------------------------------------
openssl genrsa -out ca.key 4096 2>/dev/null
openssl req -x509 -new -nodes -key ca.key -sha256 -days "${CA_DAYS}" \
  -subj "/C=VN/O=UIP-POC/OU=infra/CN=uip-internal-ca" \
  -out ca.crt 2>/dev/null
echo "[gen-ch-mtls] CA: ca.crt (expiry ${CA_DAYS}d)"

# ---------------------------------------------------------------------------
# 2. Server cert for clickhouse-01 + clickhouse-02
# SAN covers both container hostnames (in-cluster DNS on uip-network) plus
# the host-published names so host-side curl --resolve works in smoke tests.
# ---------------------------------------------------------------------------
cat > server.cnf <<'EOF'
[req]
distinguished_name = req_distinguished_name
req_extensions     = v3_req
prompt             = no
[req_distinguished_name]
C  = VN
O  = UIP-POC
OU = infra
CN = clickhouse-01
[v3_req]
keyUsage         = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName   = @alt_names
[alt_names]
DNS.1 = clickhouse-01
DNS.2 = clickhouse-02
DNS.3 = uip-clickhouse-01
DNS.4 = uip-clickhouse-02
DNS.5 = localhost
IP.1  = 127.0.0.1
EOF

openssl genrsa -out server.key 2048 2>/dev/null
openssl req -new -key server.key -out server.csr -config server.cnf 2>/dev/null
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days "${LEAF_DAYS}" -sha256 \
  -extensions v3_req -extfile server.cnf 2>/dev/null
echo "[gen-ch-mtls] Server: server.crt + server.key (CN=clickhouse-01, SAN=clickhouse-01,clickhouse-02,...)"

# ---------------------------------------------------------------------------
# 3. Client cert for JDBC consumers (analytics-service, backend)
# ClickHouse verifies client cert against ca.crt => mTLS.
# ---------------------------------------------------------------------------
cat > client.cnf <<'EOF'
[req]
distinguished_name = req_distinguished_name
req_extensions     = v3_req
prompt             = no
[req_distinguished_name]
C  = VN
O  = UIP-POC
OU = infra
CN = uip-jdbc-client
[v3_req]
keyUsage         = digitalSignature, keyEncipherment
extendedKeyUsage = clientAuth
EOF

openssl genrsa -out client.key 2048 2>/dev/null
openssl req -new -key client.key -out client.csr -config client.cnf 2>/dev/null
openssl x509 -req -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out client.crt -days "${LEAF_DAYS}" -sha256 \
  -extensions v3_req -extfile client.cnf 2>/dev/null
echo "[gen-ch-mtls] Client: client.crt + client.key (CN=uip-jdbc-client)"

# ---------------------------------------------------------------------------
# 4. Permissions — ClickHouse refuses world-readable keys; JDBC client same.
# ---------------------------------------------------------------------------
chmod 600 ca.key server.key client.key
chmod 644 ca.crt server.crt client.crt

# Clean intermediate CSRs / serial (keep .cnf for transparency)
rm -f server.csr client.csr ca.srl

echo
echo "[gen-ch-mtls] DONE. Files in ${CERT_DIR}:"
ls -la "${CERT_DIR}"
echo
echo "[gen-ch-mtls] Verify server SAN:"
openssl x509 -in server.crt -noout -subject -ext subjectAltName
echo
echo "[gen-ch-mtls] Verify client EKU (must include clientAuth):"
openssl x509 -in client.crt -noout -subject -ext extendedKeyUsage
echo
echo "[gen-ch-mtls] NEXT: mount these into clickhouse-01/02 + consumers"
echo "[gen-ch-mtls]      see docker-compose.ha.yml (services clickhouse-01/02,"
echo "[gen-ch-mtls]      analytics-service, backend) and runbook §2."
