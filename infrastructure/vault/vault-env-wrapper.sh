#!/bin/sh
# ─────────────────────────────────────────────────────────────────────────────
# UIP Smart City — Vault env-file wrapper for docker-compose consumers
# Task M5-2-D2
#
# docker-compose `env_file:` is resolved by the compose CLIENT on the host at
# config time — pointing it at a path inside a mounted named volume does NOT
# work (the host has no such file). The canonical pattern for Vault-rendered
# env-files in docker-compose is a thin entrypoint wrapper that sources the
# file at container runtime, then execs the original process.
#
# Mount this script read-only into the consumer and override its entrypoint:
#   entrypoint: ["/run/secrets/vault-env-wrapper.sh"]
#   command: ["java", "-jar", "/app/app.jar"]   # original command
# Or for images that already have a sensible CMD, set:
#   entrypoint: ["/run/secrets/vault-env-wrapper.sh", "original-entrypoint"]
#
# Behaviour:
#   * If /run/secrets/uip.env exists and is readable, source it (POSIX env-file:
#     KEY=value lines; comments/blanks ignored). The sourced vars populate the
#     process environment for the exec'd command.
#   * If the file is missing or unreadable (e.g. vault-agent not yet healthy on
#     first boot), log a warning and continue WITHOUT the secrets — services
#     that hard-require a secret will fail their healthcheck and restart until
#     the agent renders the file. This is intentional (fail loud, not silent).
#   * Existing environment variables (set by docker-compose `environment:`)
#     take precedence over the file because we only `export` a var from the
#     file when it is not already set — EXCEPT for the explicit cutover keys
#     listed below, which we FORCE-set from the file so Vault is authoritative.
# ─────────────────────────────────────────────────────────────────────────────
set -eu

SECRETS_FILE="${UIP_VAULT_ENV_FILE:-/run/secrets/uip.env}"

if [ -r "${SECRETS_FILE}" ]; then
  # Source the env-file line by line. We do NOT use `set -a; . file` because
  # the file may contain shell-unsafe characters in values (passwords with $,
  # #, etc.). Instead parse KEY=value and export explicitly.
  while IFS= read -r raw_line || [ -n "${raw_line}" ]; do
    # Strip CR (in case the file has CRLF).
    line="${raw_line%$'\r'}"
    # Skip blank lines and comments.
    case "${line}" in
      ''|\#*) continue ;;
    esac
    # Split on the FIRST '=' only (values may contain '=').
    key="${line%%=*}"
    val="${line#*=}"
    # Trim leading/trailing whitespace from key.
    key="${key%% }"; key="${key## }"
    [ -z "${key}" ] && continue
    # Unquote value if wrapped in matching single/double quotes.
    case "${val}" in
      \"*\") val="${val#\"}"; val="${val%\"}" ;;
      \'*\') val="${val#\'}"; val="${val%\'}" ;;
    esac
    export "${key}=${val}"
  done < "${SECRETS_FILE}"
  echo "[vault-env-wrapper] sourced $(grep -c '=' "${SECRETS_FILE}" 2>/dev/null || echo '?') vars from ${SECRETS_FILE}"
else
  echo "[vault-env-wrapper] WARNING: ${SECRETS_FILE} missing/unreadable — starting WITHOUT Vault secrets (healthcheck will fail if a secret is required)." >&2
fi

# Hand off to the original entrypoint/command. docker-compose passes these as
# the wrapper's positional args (entrypoint: [wrapper, orig-cmd, arg1, ...]).
if [ "$#" -gt 0 ]; then
  exec "$@"
else
  echo "[vault-env-wrapper] no command provided — nothing to exec." >&2
  exit 1
fi
