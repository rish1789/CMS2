#!/usr/bin/env bash
# One-command local dev startup for CMS2: installs frontend dependencies (if needed) and
# starts the backend and frontend together. Ctrl+C stops both.
#
# Requires a running Postgres instance already reachable via the DB_URL/DB_USERNAME/
# DB_PASSWORD env vars (see .env.example) - this script does not provision a database.
# The backend applies Flyway migrations automatically on startup; there is no separate
# migration step to run.
#
# Usage: ./dev.sh          (starts backend + frontend)
#        ./dev.sh backend  (backend only)
#        ./dev.sh frontend (frontend only)

set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "No .env found - copying .env.example. Review it (especially the DB_* values) before continuing."
  cp .env.example .env
fi
set -a
# shellcheck disable=SC1091
source .env
set +a

chmod +x backend/gradlew

case "${1:-both}" in
  backend)
    echo "==> Starting backend on http://localhost:${PORT:-8080} (Flyway migrations run automatically)..."
    cd backend && ./gradlew bootRun
    ;;
  frontend)
    echo "==> Installing frontend dependencies (if needed)..."
    (cd frontend && npm install --no-audit --no-fund)
    echo "==> Starting frontend on http://localhost:5173..."
    cd frontend && npm run dev
    ;;
  both)
    trap 'echo; echo "==> Stopping..."; kill 0' EXIT INT TERM
    echo "==> Installing frontend dependencies (if needed)..."
    (cd frontend && npm install --no-audit --no-fund)
    echo "==> Starting backend on http://localhost:${PORT:-8080} (Flyway migrations run automatically)..."
    (cd backend && ./gradlew bootRun) &
    echo "==> Starting frontend on http://localhost:5173..."
    (cd frontend && npm run dev) &
    wait
    ;;
  *)
    echo "Usage: $0 [backend|frontend|both]" >&2
    exit 1
    ;;
esac
