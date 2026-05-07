#!/usr/bin/env bash
# Start backend + frontend locally (infrastructure stays in Docker)
set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

export DB_HOST=localhost
export POSTGRES_USER=uip
export POSTGRES_PASSWORD=changeme_db_password
export POSTGRES_DB=uip_smartcity
export REDIS_HOST=localhost
export REDIS_PASSWORD=changeme_redis_password
export KAFKA_BOOTSTRAP_SERVERS=localhost:29092
export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:29092
export JWT_SECRET=changeme_jwt_secret_must_be_at_least_256_bits_long_for_hmac_sha256

echo "==> Starting backend (Spring Boot)..."
cd "$REPO_ROOT/backend"
./gradlew bootRun > /tmp/uip-backend.log 2>&1 &
BACKEND_PID=$!
echo "Backend PID: $BACKEND_PID (log: /tmp/uip-backend.log)"

echo "==> Starting frontend (Vite dev)..."
cd "$REPO_ROOT/frontend"
npm run dev > /tmp/uip-frontend.log 2>&1 &
FRONTEND_PID=$!
echo "Frontend PID: $FRONTEND_PID (log: /tmp/uip-frontend.log)"

echo ""
echo "Waiting for backend to be ready (max 120s)..."
for i in $(seq 1 24); do
  sleep 5
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "Backend is UP"
    break
  fi
  echo "  ... still waiting ($((i*5))s)"
done

echo ""
echo "Checking frontend..."
sleep 3
if curl -sf http://localhost:5173 > /dev/null 2>&1; then
  echo "Frontend is UP at http://localhost:5173"
else
  echo "Frontend may still be starting — check /tmp/uip-frontend.log"
fi

echo ""
echo "PIDs saved: backend=$BACKEND_PID frontend=$FRONTEND_PID"
echo "$BACKEND_PID" > /tmp/uip-backend.pid
echo "$FRONTEND_PID" > /tmp/uip-frontend.pid
