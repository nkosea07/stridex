#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/apps/backend"

if [[ "${1:-}" == "down" ]]; then
  echo "Stopping backend dependency containers..."
  (cd "$BACKEND_DIR" && docker compose stop db redis)
  exit 0
fi

echo "Starting backend dependency containers..."
(cd "$BACKEND_DIR" && docker compose up -d db redis)

wait_for_dependency() {
  local service="$1"
  shift
  local attempt

  for attempt in $(seq 1 60); do
    if (cd "$BACKEND_DIR" && docker compose exec -T "$service" "$@") >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  echo "Timed out waiting for $service" >&2
  return 1
}

if ! wait_for_dependency db pg_isready -U race_admin -d race_tracker \
  || ! wait_for_dependency redis redis-cli ping; then
  (cd "$BACKEND_DIR" && docker compose stop db redis)
  exit 1
fi

echo "Starting backend and frontend..."
(cd "$BACKEND_DIR" && mvn spring-boot:run) &
BACKEND_PID=$!

backend_ready=0
for attempt in $(seq 1 60); do
  if curl --fail --silent http://127.0.0.1:8081/api/actuator/health/readiness >/dev/null 2>&1; then
    backend_ready=1
    break
  fi
  sleep 2
done

if [[ "$backend_ready" -ne 1 ]]; then
  echo "Timed out waiting for the backend readiness endpoint" >&2
  kill "$BACKEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" 2>/dev/null || true
  (cd "$BACKEND_DIR" && docker compose stop db redis)
  exit 1
fi

(cd "$ROOT_DIR" && pnpm --filter web dev) &
FRONTEND_PID=$!

cleanup() {
  echo
  echo "Stopping local services..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}

trap cleanup INT TERM EXIT

# macOS ships Bash 3.2, which does not support `wait -n`. Poll both child
# processes so the launcher stays portable while still stopping the sibling
# when either service exits.
exit_code=0
while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$FRONTEND_PID" 2>/dev/null; do
  sleep 1
done

if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
  wait "$BACKEND_PID" || exit_code=$?
else
  wait "$FRONTEND_PID" || exit_code=$?
fi

kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true

exit "$exit_code"
