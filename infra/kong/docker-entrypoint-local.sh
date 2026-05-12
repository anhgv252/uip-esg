#!/bin/sh
# Kong local-dev entrypoint: substitute KONG_JWT_SECRET before starting
# Called from docker-compose command
set -e

if [ -z "$KONG_JWT_SECRET" ]; then
  echo "ERROR: KONG_JWT_SECRET env var is not set" >&2
  exit 1
fi

awk -v secret="$KONG_JWT_SECRET" '{gsub(/\${KONG_JWT_SECRET}/, secret)} 1' \
  /etc/kong/kong.tpl > /tmp/kong.yml

exec /docker-entrypoint.sh kong docker-start
