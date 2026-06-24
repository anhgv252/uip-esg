# ─────────────────────────────────────────────────────────────────────────────
# UIP Smart City — Vault Agent configuration
# Task M5-1-T03 backbone + M5-2-D2 consumer wiring (AppRole auto_auth fix)
#
# Vault Agent runs as a sidecar. It authenticates to Vault via AppRole
# (auto_auth), pulls KV v2 secrets, renders them to a shared volume as a
# POSIX env-file, and caches the token in-memory.
#
# IMPORTANT (learned M5-2-D2): the `template` block in `vault agent` does
# NOT use the static `vault.token` field — it only renders when an
# `auto_auth` block is present to source its token. The original T03 HCL
# omitted auto_auth, so templates silently never rendered. AppRole is the
# canonical pilot→prod auth method (ADR-048 §6.2); vault-init.sh provisions
# the role + writes role_id/secret_id to /vault/approle/.
#
# R6 mitigation: token is renewed by the auth handler (ttl 1h, renew @90%).
# If Vault is briefly unavailable the agent keeps the last-good token and
# last-rendered uip.env on disk; services continue reading the file.
# ─────────────────────────────────────────────────────────────────────────────

pid_file = "/vault/pid/vault-agent.pid"

# ── Vault connection (no static token — auto_auth below provides it) ─────────
vault {
  address = "http://vault:8200"
  retry {
    num_retries = 5
    backoff     = "1s"
    max_backoff = "30s"
  }
}

# ── Auto-auth via AppRole — REQUIRED to drive the template block ─────────────
# vault-init.sh writes role_id + secret_id to the dedicated vault-approle
# volume (mounted at /vault/approle in BOTH vault-init and vault-agent).
# Kept off the vault-secrets volume so consumer services never see creds.
auto_auth {
  method "approle" {
    config = {
      role_id_file_path                    = "/vault/approle/role_id"
      secret_id_file_path                  = "/vault/approle/secret_id"
      remove_secret_id_file_after_reading  = false
    }
  }
  # File sink so other processes on the same host could pick up the token
  # if needed (not used by consumers today — they read uip.env, not tokens).
  sink "file" {
    config = {
      path = "/vault/agent-token"
    }
  }
}

# ── Listener required for config validity (agent API not used by consumers) ─
listener "tcp" {
  address     = "0.0.0.0:8100"
  tls_disable = true
}

# ── Template: render all secrets to /vault/secrets/uip.env (shared volume) ──
# Each KV path becomes a KEY=value line. Services mount this file read-only
# at /run/secrets/uip.env and consume it via docker-compose `env_file:`.
template {
  source      = "/vault/config/vault-agent-template.tpl"
  destination = "/vault/secrets/uip.env"
  # 0644 (world-readable) so non-root consumers (e.g. backend/analytics run as
  # uid 999) can read the file via the entrypoint wrapper. The volume is only
  # mounted into intended consumers; prod hardening (ADR-048 §6.4) would use
  # per-service paths or a sidecar that writes to each service's private dir.
  perms       = 0644
  # Re-render when secret rotates. KV v2 lease duration is 30d default.
  error_on_missing_key = true
}
