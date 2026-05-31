#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/apps/backend"

if [[ "${1:-}" == "down" ]]; then
  echo "Stopping backend DB container..."
  (cd "$BACKEND_DIR" && docker compose stop db)
  exit 0
fi

echo "Starting backend DB container..."
(cd "$BACKEND_DIR" && docker compose up -d db)

echo "Starting backend and frontend..."
(cd "$BACKEND_DIR" && mvn spring-boot:run) &
BACKEND_PID=$!

(cd "$ROOT_DIR" && pnpm --filter web dev) &
FRONTEND_PID=$!

cleanup() {
  echo
  echo "Stopping local services..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
}

trap cleanup INT TERM EXIT

wait -n "$BACKEND_PID" "$FRONTEND_PID"
exit_code=$?

kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true

exit "$exit_code"
