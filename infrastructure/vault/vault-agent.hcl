# ─────────────────────────────────────────────────────────────────────────────
# UIP Smart City — Vault Agent configuration
# Task M5-1-T03 — block gate G1
#
# Vault Agent runs as a sidecar. It authenticates to Vault (dev root token in
# pilot — see ADR-048 §6.2 for prod hardening), pulls KV v2 secrets, renders
# them to a shared volume as a POSIX env-file, and caches them in-memory.
#
# R6 mitigation: cache { ttl = "5m" } — services keep using cached secrets for
# up to 5 minutes during brief Vault unavailability. After TTL, agent renews
# from Vault; if Vault still down, agent retains last-good value (use_cache =
# true) and logs a warning.
# ─────────────────────────────────────────────────────────────────────────────

pid_file = "/vault/pid/vault-agent.pid"

# ── Vault connection ────────────────────────────────────────────────────────
vault {
  address = "http://vault:8200"
  # Dev mode: root token. Production must use JWT/AppRole auth — see ADR-048 §6.2.
  token   = "root"
  retry {
    num_retries = 5
    backoff     = "1s"
    max_backoff = "30s"
  }
}

# ── In-memory cache (R6 mitigation) ─────────────────────────────────────────
cache {
  use_auto_auth_token = true
  # 5-minute TTL: services survive brief Vault unavailability.
  # On expiry, agent re-fetches; if Vault unreachable, last-good value is
  # served until the renewal succeeds (stale-but-available > hard fail).
  ttl = "5m"
}

# ── Listener for agent API (cache stats, manual invalidate) ─────────────────
listener "tcp" {
  address     = "0.0.0.0:8100"
  tls_disable = true
}

# ── Template: render all secrets to /vault/secrets/env (shared volume) ──────
# Each KV path becomes a KEY=value line. Services mount this file read-only.
template {
  source      = "/vault/config/vault-agent-template.tpl"
  destination = "/vault/secrets/uip.env"
  # Render atomically so services never read a half-written file.
  perms       = 0600
  # Re-render when secret rotates. KV v2 lease duration is 30d default.
  error_on_missing_key = true
}
